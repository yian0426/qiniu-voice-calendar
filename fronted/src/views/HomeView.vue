<script setup lang="ts">
import { Bell, PanelRightClose, Sparkles } from "@lucide/vue";
import SidebarAvatar from "../components/SidebarAvatar.vue";
import AgendaPanel from "../components/AgendaPanel.vue";
import ChatPanel from "../components/ChatPanel.vue";
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

    <SidebarAvatar />

    <div class="main-content">
      <AgendaPanel />
      <ChatPanel />
    </div>

    <div class="right-floating-actions">
      <button class="float-btn"><PanelRightClose :size="18" /></button>
      <button class="float-btn active"><Sparkles :size="18" /></button>
    </div>

    <div class="notification-fab">
      <div class="badge">1</div>
      <Bell :size="20" />
    </div>
  </div>
</template>
