<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElDialog, ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import { LogIn, UserPlus, Sparkles } from "@lucide/vue";

const authStore = useAuthStore();

const dialogVisible = ref(true);
const isLogin = ref(true);
const loading = ref(false);

const form = reactive({
  username: "",
  password: "",
  email: "",
});

const rules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 50, message: "用户名长度 3-50 字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码至少 6 位", trigger: "blur" },
  ],
};

async function handleSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  if (form.password.length < 6) {
    ElMessage.warning("密码至少 6 位");
    return;
  }
  loading.value = true;
  try {
    if (isLogin.value) {
      const res = await authStore.login(form.username, form.password);
      if (res.code === 200) {
        ElMessage.success("登录成功");
        dialogVisible.value = false;
      } else {
        ElMessage.error(res.message || "登录失败");
      }
    } else {
      const res = await authStore.register(
        form.username,
        form.password,
        form.email || undefined,
      );
      if (res.code === 200) {
        ElMessage.success("注册成功");
        dialogVisible.value = false;
      } else {
        ElMessage.error(res.message || "注册失败");
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message || "网络错误");
  } finally {
    loading.value = false;
  }
}

function switchMode() {
  isLogin.value = !isLogin.value;
}

function handleClose() {
  // Don't close if not logged in
  if (!authStore.isLoggedIn) return;
  dialogVisible.value = false;
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="isLogin ? '登录七牛语音日历' : '注册七牛语音日历'"
    width="380px"
    class="login-dialog"
    :close-on-click-modal="false"
    :show-close="authStore.isLoggedIn"
    :close-on-press-escape="authStore.isLoggedIn"
    align-center
    @close="handleClose"
  >
    <div class="login-content">
      <div class="login-icon">
        <Sparkles :size="32" color="#6c63ff" />
      </div>

      <el-form
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="UserPlus"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item v-if="!isLogin" label="邮箱（可选）">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱"
            type="email"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="handleSubmit"
          >
            <LogIn :size="16" style="margin-right: 6px" />
            {{ isLogin ? "登 录" : "注 册" }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="switch-mode">
        <span v-if="isLogin">还没有账号？</span>
        <span v-else>已有账号？</span>
        <a href="#" @click.prevent="switchMode">
          {{ isLogin ? "立即注册" : "去登录" }}
        </a>
      </div>
    </div>
  </ElDialog>
</template>

<style scoped>
.login-dialog :deep(.el-dialog__header) {
  text-align: center;
  padding-bottom: 0;
}

.login-content {
  padding: 20px 10px 0;
}

.login-icon {
  text-align: center;
  margin-bottom: 20px;
}

.switch-mode {
  text-align: center;
  margin-top: 12px;
  font-size: 13px;
  color: #888;
}

.switch-mode a {
  color: #6c63ff;
  text-decoration: none;
  margin-left: 4px;
}

.switch-mode a:hover {
  text-decoration: underline;
}
</style>
