<script setup lang="ts">
import { Sparkles, Send, Mic, Image as ImageIcon } from "@lucide/vue";
</script>

<template>
  <div class="chat-panel glass-panel">
    <div class="chat-header">
      <div class="bot-info">
        <div class="bot-avatar">
          <Sparkles :size="20" color="#fff" />
        </div>
        <div class="bot-text">
          <div class="bot-name">七牛语音日历</div>
          <div class="bot-status">开始对话</div>
        </div>
      </div>
    </div>

    <div class="chat-content">
      <div class="chat-welcome">
        <div class="welcome-icon">
          <!-- placeholder for large chat icon -->
          <svg
            width="40"
            height="40"
            viewBox="0 0 24 24"
            fill="none"
            class="msg-icon"
          >
            <path
              d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <h3 class="welcome-title">提问 / 聊天 / 指挥 七牛语音日历...</h3>

        <div class="suggestion-chips">
          <button class="chip">根据最近的笔记帮我写篇日记</button>
          <button class="chip">今日新闻速览</button>
          <button class="chip">帮我写周报并发到邮箱</button>
          <button class="chip text-only">创建我的快捷指令</button>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-box glass-input">
        <input type="text" placeholder="想到什么就写下来吧" />
        <div class="input-actions">
          <button class="action-btn"><Mic :size="18" /></button>
          <button class="action-btn"><ImageIcon :size="18" /></button>
          <button class="send-btn"><Send :size="16" /></button>
        </div>
      </div>
    </div>
  </div>
</template>
