<script setup lang="ts">
import { Sparkles } from "@lucide/vue";

/* ── Types ── */
export interface ChatMessage {
  role: "user" | "assistant" | "status";
  content: string;
  isThinking?: boolean;
  timestamp: number;
}

const props = defineProps<{
  msg: ChatMessage;
  index: number;
  isStreaming: boolean;
  isLast: boolean;
}>();

/* ── Time formatting ── */
function formatTime(ts: number): string {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}
</script>

<template>
  <!-- Status -->
  <div v-if="msg.role === 'status'" class="status-line">
    <span class="status-dot" />{{ msg.content }}
  </div>

  <!-- User message (right) -->
  <div v-else-if="msg.role === 'user'" class="msg-row msg-row-user">
    <div class="avatar avatar-user">我</div>
    <div class="msg-col msg-col-right">
      <div class="msg-time msg-time-right">
        {{ formatTime(msg.timestamp) }}
      </div>
      <div class="bubble bubble-user">{{ msg.content }}</div>
    </div>
  </div>

  <!-- AI message (left) -->
  <div v-else class="msg-row msg-row-ai">
    <div class="avatar avatar-ai">
      <Sparkles :size="16" color="#fff" />
    </div>
    <div class="msg-col msg-col-left">
      <div class="msg-time msg-time-left">七牛</div>

      <!-- 生成进度提示 (仅在思考/无内容阶段显示，位于气泡上方) -->
      <div v-if="msg.isThinking" class="generating-progress">
        <div class="progress-dots">
          <span class="dot"></span><span class="dot"></span
          ><span class="dot"></span>
        </div>
        <span class="generating-text">思考中...</span>
      </div>

      <!-- Content Bubble (内容输出时显示) -->
      <div v-if="msg.content" class="bubble bubble-ai">
        {{ msg.content }}
        <span v-if="isStreaming && isLast" class="cursor-blink">|</span>
      </div>

      <!-- 正在生成回复 (气泡下方的流式进度提示) -->
      <div
        v-if="isStreaming && isLast && !msg.isThinking && msg.content"
        class="generating-progress generating-progress--below"
      >
        <div class="progress-dots">
          <span class="dot"></span><span class="dot"></span
          ><span class="dot"></span>
        </div>
        <span class="generating-text">正在生成回复...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ═══ Message Layout ═══ */
.msg-row {
  display: flex;
  gap: 10px;
  padding: 6px 20px;
  margin-bottom: 6px;
  align-items: flex-start;
}
.msg-row-user {
  flex-direction: row-reverse;
  justify-content: flex-start;
}
.msg-row-ai {
  flex-direction: row;
  justify-content: flex-start;
}

.msg-col {
  max-width: 80%;
  min-width: 60px;
}
.msg-col-right {
  text-align: right;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.msg-col-left {
  text-align: left;
}

/* ═══ Avatars ═══ */
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
}
.avatar-ai {
  background: linear-gradient(135deg, #6c63ff, #a78bfa);
}
.avatar-user {
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  color: #fff;
}

/* ═══ Time labels ═══ */
.msg-time {
  font-size: 11px;
  color: #666;
  margin-bottom: 4px;
}
.msg-time-right {
  text-align: right;
}
.msg-time-left {
  text-align: left;
}

/* ═══ Bubbles ═══ */
.bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  text-align: left;
  max-width: 100%;
}
.bubble-user {
  background: linear-gradient(135deg, #6c63ff, #8b5cf6);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble-ai {
  background: rgba(255, 255, 255, 0.08);
  color: #e5e5e5;
  border-bottom-left-radius: 4px;
}

/* ═══ Generating Progress ═══ */
.generating-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  color: #a78bfa;
  font-size: 13px;
}
.generating-progress--below {
  padding-top: 4px;
  opacity: 0.8;
}
.progress-dots {
  display: flex;
  gap: 4px;
}
.progress-dots .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6c63ff;
  animation: gen-pulse 1.4s ease-in-out infinite;
}
.progress-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}
.progress-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes gen-pulse {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.3;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
.generating-text {
  animation: fade-pulse 2s ease-in-out infinite;
}
@keyframes fade-pulse {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

/* ═══ Typing cursor ═══ */
.cursor-blink {
  animation: blink-cursor 1s step-end infinite;
  font-weight: 300;
}
@keyframes blink-cursor {
  0%,
  50% {
    opacity: 1;
  }
  51%,
  100% {
    opacity: 0;
  }
}

/* ═══ Status line ═══ */
.status-line {
  text-align: center;
  color: #888;
  font-size: 12px;
  padding: 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.status-dot {
  width: 6px;
  height: 6px;
  background: #6c63ff;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

/* ═══ Message transition ═══ */
.msg-enter-active {
  transition: all 0.3s ease-out;
}
.msg-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
</style>
