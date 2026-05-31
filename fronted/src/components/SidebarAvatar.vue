<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { User, LogOut, MoonStar, LogIn } from "@lucide/vue";
import { useAuthStore } from "@/stores/auth";
import { ElMessage } from "element-plus";

const authStore = useAuthStore();
const emit = defineEmits<{ showLogin: [] }>();

const showUserMenu = ref(false);

const displayName = computed(() => {
  return authStore.username ? authStore.username.slice(0, 2) : "?";
});

const toggleUserMenu = (e: Event) => {
  if (!authStore.isLoggedIn) {
    emit("showLogin");
    return;
  }
  e.stopPropagation();
  showUserMenu.value = !showUserMenu.value;
};

const hideMenu = () => {
  showUserMenu.value = false;
};

onMounted(() => {
  window.addEventListener("click", hideMenu);
});

onUnmounted(() => {
  window.removeEventListener("click", hideMenu);
});

const handleMenuClick = (action: string) => {
  showUserMenu.value = false;
  if (action === "logout") {
    authStore.logout();
    ElMessage.success("已退出登录");
    emit("showLogin");
  } else if (action === "login") {
    emit("showLogin");
  }
};
</script>

<template>
  <div class="sidebar">
    <div class="avatar-container" @click="toggleUserMenu">
      <div class="avatar" :class="{ logged: authStore.isLoggedIn }">
        {{ displayName }}
      </div>
      <!-- Popup Menu -->
      <transition name="slide-up">
        <div
          v-if="showUserMenu && authStore.isLoggedIn"
          class="user-menu"
          @click.stop
        >
          <div class="menu-user-info">
            <div class="menu-username">{{ authStore.username }}</div>
          </div>
          <div class="menu-divider"></div>
          <div class="menu-item" @click="handleMenuClick('profile')">
            <User class="menu-icon" :size="16" />
            <span>个人中心</span>
          </div>
          <div class="menu-item" @click="handleMenuClick('theme')">
            <MoonStar class="menu-icon" :size="16" />
            <span>切换主题</span>
          </div>
          <div class="menu-divider"></div>
          <div class="menu-item danger" @click="handleMenuClick('logout')">
            <LogOut class="menu-icon" :size="16" />
            <span>退出登录</span>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* You can extract Sidebar specific styles here or let them live in layout.scss */
</style>
