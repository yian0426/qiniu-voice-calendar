<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { User, LogOut, MoonStar } from "@lucide/vue";

const showUserMenu = ref(false);

const toggleUserMenu = (e: Event) => {
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
  console.log("Action:", action);
};
</script>

<template>
  <div class="sidebar">
    <div class="avatar-container" @click="toggleUserMenu">
      <div class="avatar">yi</div>
      <!-- Popup Menu -->
      <transition name="slide-up">
        <div v-if="showUserMenu" class="user-menu" @click.stop>
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
