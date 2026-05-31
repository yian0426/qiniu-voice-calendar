<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import SidebarAvatar from "@/components/SidebarAvatar.vue";
import AgendaPanel from "@/features/calendar/AgendaPanel.vue";
import ChatPanel from "@/features/chat/ChatPanel.vue";
import LoginDialog from "@/features/auth/LoginDialog.vue";
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
const showLogin = ref(!authStore.isLoggedIn);

// 监听认证过期事件 — 403/401 时自动弹出登录框
function onAuthExpired() {
  authStore.clearAuth();
  showLogin.value = true;
}
onMounted(() => window.addEventListener("auth:expired", onAuthExpired));
onUnmounted(() => window.removeEventListener("auth:expired", onAuthExpired));

function handleShowLogin() {
  showLogin.value = true;
}
</script>

<template>
  <div class="app-container">
    <!-- Starry background seamlessly looping -->
    <div class="bg-controller">
      <div class="bg-image-wrapper">
        <div class="bg-image"></div>
        <div class="bg-image"></div>
      </div>
      <div class="stars-wrapper">
        <div class="star-layer star-layer-1"></div>
        <div class="star-layer star-layer-2"></div>
        <div class="star-layer star-layer-3"></div>
        <div class="star-layer star-layer-4"></div>
        <div class="star-layer star-layer-5"></div>
      </div>
    </div>

    <SidebarAvatar @show-login="handleShowLogin" />

    <div class="main-content">
      <AgendaPanel />
      <ChatPanel />
    </div>

    <LoginDialog v-if="showLogin" />
  </div>
</template>
