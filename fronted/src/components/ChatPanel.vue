<script setup lang="ts">
import { ref, nextTick, watch } from "vue";
import { Sparkles, Send, Mic, Image as ImageIcon, X } from "@lucide/vue";
import { ElMessage } from "element-plus";
import { streamChat } from "@/utils/request";

/* ── Types ── */
interface ChatMessage {
  role: "user" | "assistant" | "status";
  content: string;
}

interface UploadImage {
  url: string;
  progress: number;
  status: "uploading" | "success" | "error";
}

/* ── State ── */
const messages = ref<ChatMessage[]>([]);
const conversationId = ref<number | undefined>(undefined);
const inputText = ref("");
const isStreaming = ref(false);
const chatContentRef = ref<HTMLElement | null>(null);

// Image upload (kept for future iteration)
const images = ref<UploadImage[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);

/* ── Auto-scroll ── */
watch(
  () => messages.value.length,
  async () => {
    await nextTick();
    if (chatContentRef.value) {
      chatContentRef.value.scrollTop = chatContentRef.value.scrollHeight;
    }
  },
);

/* ── Send message ── */
const sendMessage = async (text?: string) => {
  const content = (text || inputText.value).trim();
  if (!content || isStreaming.value) return;

  // Add user message
  messages.value.push({ role: "user", content });
  inputText.value = "";

  // Add assistant placeholder
  const assistantMsg: ChatMessage = { role: "assistant", content: "" };
  messages.value.push(assistantMsg);
  isStreaming.value = true;

  try {
    const generator = streamChat(content, conversationId.value);
    for await (const event of generator) {
      switch (event.type) {
        case "content":
          assistantMsg.content += event.content || "";
          break;
        case "status":
          messages.value.push({ role: "status", content: event.content || "处理中..." });
          break;
        case "done":
          if (event.conversationId) {
            conversationId.value = event.conversationId;
          }
          break;
        case "error":
          assistantMsg.content = "抱歉，出错了: " + (event.content || "未知错误");
          ElMessage.error("对话出错");
          break;
      }
    }
  } catch (e: any) {
    assistantMsg.content = "抱歉，网络请求失败: " + (e.message || "未知错误");
    ElMessage.error("网络请求失败");
  } finally {
    // Remove empty assistant message if nothing was received
    if (!assistantMsg.content) {
      assistantMsg.content = "收到空回复，请重试。";
    }
    isStreaming.value = false;
  }
};

/* ── Key binding: Enter to send ── */
const onKeydown = (e: KeyboardEvent) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

/* ── Image upload (simulated, for future use) ── */
const triggerImageUpload = () => {
  fileInput.value?.click();
};

const handleImageUpload = (e: Event) => {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (file) {
    if (file.type.startsWith("image/")) {
      const url = URL.createObjectURL(file);
      const imgObj: UploadImage = { url, progress: 0, status: "uploading" };
      images.value.push(imgObj);

      const interval = setInterval(() => {
        if (imgObj.progress < 100) {
          imgObj.progress += 10;
        } else {
          imgObj.status = "success";
          clearInterval(interval);
        }
      }, 200);
    } else {
      ElMessage.error("请上传图片文件");
    }
  }
  if (fileInput.value) fileInput.value.value = "";
};

const removeImage = (index: number) => {
  const img = images.value[index];
  if (img) {
    URL.revokeObjectURL(img.url);
    images.value.splice(index, 1);
  }
};

/* ── Suggestion chips ── */
const suggestions = [
  "根据最近的笔记帮我写篇日记",
  "今日新闻速览",
  "帮我写周报并发到邮箱",
  "创建我的快捷指令",
  "帮我明天下午3点安排一个会议",
  "我今天有什么日程安排？",
];
</script>

<template>
  <div class="chat-panel glass-panel">
    <!-- Header -->
    <div class="chat-header">
      <div class="bot-info">
        <div class="bot-avatar">
          <Sparkles :size="20" color="#fff" />
        </div>
        <div class="bot-text">
          <div class="bot-name">七牛语音日历</div>
          <div class="bot-status">
            {{ isStreaming ? "正在回复..." : "开始对话" }}
          </div>
        </div>
      </div>
    </div>

    <!-- Chat content -->
    <div class="chat-content" ref="chatContentRef">
      <!-- Welcome screen (when no messages) -->
      <div v-if="messages.length === 0" class="chat-welcome">
        <div class="welcome-icon">
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
          <button
            v-for="(s, i) in suggestions"
            :key="i"
            class="chip"
            @click="sendMessage(s)"
          >
            {{ s }}
          </button>
        </div>
      </div>

      <!-- Message list -->
      <div v-else class="message-list">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', `message-${msg.role}`]"
        >
          <div v-if="msg.role === 'status'" class="status-message">
            {{ msg.content }}
          </div>
          <div v-else :class="['message-bubble', `bubble-${msg.role}`]">
            <span class="bubble-text">{{ msg.content }}</span>
            <span
              v-if="msg.role === 'assistant' && isStreaming && index === messages.length - 1"
              class="typing-cursor"
            >|</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="chat-input-area">
      <div class="input-box glass-input text-area-mode">
        <div class="image-previews" v-if="images.length > 0">
          <div
            class="image-preview-wrapper"
            v-for="(img, idx) in images"
            :key="idx"
          >
            <el-image
              :src="img.url"
              class="image-preview"
              fit="cover"
              :preview-src-list="[img.url]"
              :initial-index="0"
            />
            <div
              v-if="img.status === 'uploading'"
              class="upload-progress-overlay"
            >
              <div class="progress-text">{{ img.progress }}%</div>
            </div>
            <button
              v-if="img.status !== 'uploading'"
              class="remove-image-btn"
              @click="removeImage(idx)"
            >
              <X :size="12" />
            </button>
          </div>
        </div>
        <textarea
          v-model="inputText"
          class="custom-chat-input"
          placeholder="想到什么就写下来吧"
          rows="1"
          @keydown="onKeydown"
          :disabled="isStreaming"
        ></textarea>
        <div class="input-actions">
          <button class="action-btn" :disabled="isStreaming">
            <Mic :size="18" />
          </button>
          <button
            class="action-btn"
            @click="triggerImageUpload"
            :disabled="isStreaming"
          >
            <ImageIcon :size="18" />
          </button>
          <input
            type="file"
            ref="fileInput"
            accept="image/*"
            style="display: none"
            @change="handleImageUpload"
          />
          <button
            class="send-btn"
            @click="sendMessage()"
            :disabled="!inputText.trim() || isStreaming"
          >
            <Send :size="16" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
