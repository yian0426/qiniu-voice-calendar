<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onBeforeUnmount } from "vue";
import {
  Sparkles,
  Send,
  Square,
  Mic,
  Image as ImageIcon,
  X,
  CheckCircle,
  ArrowDown,
} from "@lucide/vue";
import { ElMessage } from "element-plus";
import { streamChat, abortCurrentStream, uploadVoice } from "@/utils/request";
import { useAuthStore } from "@/stores/auth";
import { useEventStore } from "@/stores/events";
import ChatMessageItem from "./ChatMessageItem.vue";
import type { ChatMessage } from "./ChatMessageItem.vue";
import axios from "axios";

const authStore = useAuthStore();
const eventStore = useEventStore();

/* ── Types ── */
interface UploadImage {
  url: string;
  file?: File;
  status: "uploading" | "success" | "error";
}

/* ── State ── */
const messages = ref<ChatMessage[]>([]);
const conversationId = ref<number | undefined>(undefined);
const inputText = ref("");
const isStreaming = ref(false);
const chatContentRef = ref<HTMLElement | null>(null);
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const images = ref<UploadImage[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);
const previewVisible = ref(false);
const previewUrls = ref<string[]>([]);

/* ── 智能滚动 ── */
const isUserScrolledUp = ref(false);
const showNewMessageBadge = ref(false);
let scrollCheckRaf: number | null = null;

/** 检测用户是否在底部附近 */
function checkScrollPosition() {
  if (!chatContentRef.value) return;
  const el = chatContentRef.value;
  // 距离底部 80px 内视为"在底部"
  const threshold = 80;
  const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
  isUserScrolledUp.value = !atBottom;
  if (atBottom) {
    showNewMessageBadge.value = false;
  }
}

/** 监听滚动事件（throttle via rAF） */
function onScroll() {
  if (scrollCheckRaf) cancelAnimationFrame(scrollCheckRaf);
  scrollCheckRaf = requestAnimationFrame(checkScrollPosition);
}

onMounted(() => {
  chatContentRef.value?.addEventListener("scroll", onScroll, { passive: true });
});
onBeforeUnmount(() => {
  chatContentRef.value?.removeEventListener("scroll", onScroll);
  if (scrollCheckRaf) cancelAnimationFrame(scrollCheckRaf);
});

/** 平滑滚动到底部 */
const scrollToBottom = async (smooth = true) => {
  await nextTick();
  if (chatContentRef.value) {
    chatContentRef.value.scrollTo({
      top: chatContentRef.value.scrollHeight,
      behavior: smooth ? "smooth" : "instant",
    });
  }
};

/** 新消息到达时的滚动策略 */
function onNewMessage() {
  if (isUserScrolledUp.value) {
    // 用户往上翻了，显示"新消息"标记而非强跳
    showNewMessageBadge.value = true;
  } else {
    scrollToBottom(true);
  }
}

/** 手动点击"新消息"回到底部 */
function jumpToBottom() {
  showNewMessageBadge.value = false;
  isUserScrolledUp.value = false;
  scrollToBottom(false);
}

watch(() => messages.value.length, onNewMessage);

/* ── Stop streaming ── */
const stopStreaming = () => {
  console.debug("[ChatPanel] stopStreaming 被调用");
  abortCurrentStream();
  isStreaming.value = false;
};

/* ── Thinking timeout (5s max) ── */
let thinkingTimer: ReturnType<typeof setTimeout> | null = null;

/* ── Send message ── */
const sendMessage = async (text?: string) => {
  if (isStreaming.value) {
    stopStreaming();
    return;
  }
  // 未登录时提示登录
  if (!authStore.isLoggedIn) {
    ElMessage.warning("请先登录");
    return;
  }
  const content = (text || inputText.value).trim();
  if (!content) return;

  messages.value.push({ role: "user", content, timestamp: Date.now() });
  inputText.value = "";

  const assistantMsg: ChatMessage = {
    role: "assistant",
    content: "",
    isThinking: true,
    timestamp: Date.now(),
  };
  messages.value.push(assistantMsg);
  isStreaming.value = true;
  await scrollToBottom();
  console.debug("[ChatPanel] 开始流式请求");

  // Thinking timeout: force stop thinking after 5s
  thinkingTimer = setTimeout(() => {
    if (assistantMsg.isThinking) {
      assistantMsg.isThinking = false;
      if (!assistantMsg.content) assistantMsg.content = "正在理解你的意图...";
    }
  }, 5000);

  try {
    const generator = streamChat(content, conversationId.value);
    for await (const event of generator) {
      // 如果已被取消（AbortController），立即退出
      if (!isStreaming.value) {
        console.debug("[ChatPanel] isStreaming=false，退出事件循环");
        break;
      }

      console.debug(`[ChatPanel] 收到事件: type=${event.type}`);
      switch (event.type) {
        case "content":
          if (assistantMsg.isThinking) {
            assistantMsg.isThinking = false;
            if (thinkingTimer) clearTimeout(thinkingTimer);
          }
          assistantMsg.content += event.content || "";
          scrollToBottom();
          break;
        case "status":
          if (assistantMsg.isThinking) {
            assistantMsg.isThinking = false;
            if (thinkingTimer) clearTimeout(thinkingTimer);
          }
          assistantMsg.content = event.content || "处理中...";
          scrollToBottom();
          break;
        case "tool_result":
          if (thinkingTimer) clearTimeout(thinkingTimer);
          assistantMsg.isThinking = false;
          assistantMsg.content =
            (event as any).result ||
            (event as any).content ||
            assistantMsg.content ||
            "操作已完成";
          // 工具执行后重新拉取最新数据
          eventStore.debouncedFetch(
            new Date(new Date().getFullYear(), new Date().getMonth(), 1)
              .toISOString()
              .slice(0, 10),
            new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)
              .toISOString()
              .slice(0, 10),
          );
          scrollToBottom();
          break;
        case "event_data":
          if (event.action && event.events) {
            // 直接写入全局 store，左侧日历自动响应式更新
            eventStore.applyFromAI({
              action: event.action,
              events: event.events,
            });
          }
          break;
        case "done":
          console.debug(
            "[ChatPanel] 收到 done 信号, conversationId=",
            event.conversationId,
          );
          if (event.conversationId) conversationId.value = event.conversationId;
          // done 事件到达，流正常结束 — 不需要额外操作，finally 会处理清理
          break;
        case "error":
          if (thinkingTimer) clearTimeout(thinkingTimer);
          assistantMsg.isThinking = false;
          assistantMsg.content =
            "抱歉，出错了: " + (event.content || "未知错误");
          ElMessage.error("对话出错");
          break;
      }
    }
    console.debug("[ChatPanel] 事件循环正常结束");
  } catch (e: any) {
    console.error("[ChatPanel] 流处理异常:", e);
    if (thinkingTimer) clearTimeout(thinkingTimer);
    assistantMsg.isThinking = false;
    assistantMsg.content = "抱歉，网络请求失败: " + (e.message || "未知错误");
    ElMessage.error("网络请求失败");
  } finally {
    console.debug("[ChatPanel] finally 块执行 — 清理状态");
    if (thinkingTimer) clearTimeout(thinkingTimer);
    assistantMsg.isThinking = false;
    if (!assistantMsg.content) assistantMsg.content = "收到空回复，请重试。";
    isStreaming.value = false;

    // 从 AI 回复文本中提取 calendar-json 代码块（兜底方案）
    extractCalendarJson(assistantMsg.content);
    // 移除 calendar-json 代码块，不在聊天界面显示
    assistantMsg.content = assistantMsg.content
      .replace(/```calendar-json\s*\n?[\s\S]*?\n?\s*```/g, "")
      .trim();
    if (!assistantMsg.content) assistantMsg.content = "操作已完成";

    if (
      assistantMsg.content.includes("已创建") ||
      assistantMsg.content.includes("已修改") ||
      assistantMsg.content.includes("已删除") ||
      assistantMsg.content.includes("已标记") ||
      assistantMsg.content.includes("已安排")
    ) {
      // 关键词命中时防抖拉取最新数据
      eventStore.debouncedFetch(
        new Date(new Date().getFullYear(), new Date().getMonth(), 1)
          .toISOString()
          .slice(0, 10),
        new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)
          .toISOString()
          .slice(0, 10),
      );
    }
  }
};

/* ── 从 AI 回复中提取 calendar-json 代码块 ── */
function extractCalendarJson(text: string) {
  const regex = /```calendar-json\s*\n?([\s\S]*?)\n?\s*```/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    try {
      const jsonStr = (match[1] ?? "").trim();
      if (!jsonStr) continue;
      const data = JSON.parse(jsonStr);
      if (data.action && Array.isArray(data.events)) {
        // 直接写入全局 store
        eventStore.applyFromAI({
          action: data.action,
          events: data.events,
        });
      }
    } catch {
      console.warn("[calendar-json] 解析失败，跳过此代码块");
    }
  }
}

/* ── Voice recording ── */
const isRecording = ref(false);
const mediaRecorder = ref<MediaRecorder | null>(null);
const audioChunks = ref<Blob[]>([]);

const toggleRecording = async () => {
  if (isRecording.value) {
    mediaRecorder.value?.stop();
    return;
  }
  if (!authStore.isLoggedIn) {
    ElMessage.warning("请先登录");
    return;
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    let mimeType = "audio/webm";
    if (!MediaRecorder.isTypeSupported("audio/webm;codecs=opus")) {
      mimeType = "audio/webm";
    }
    const recorder = new MediaRecorder(stream, {
      mimeType: MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm",
    });
    mediaRecorder.value = recorder;
    audioChunks.value = [];

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) audioChunks.value.push(e.data);
    };

    recorder.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop());
      isRecording.value = false;
      const blob = new Blob(audioChunks.value, { type: recorder.mimeType });
      if (blob.size > 0) {
        await sendVoiceMessage(blob);
      }
    };

    recorder.start();
    isRecording.value = true;
  } catch (e: any) {
    if (e.name === "NotAllowedError") {
      ElMessage.error("请允许麦克风权限");
    } else {
      ElMessage.error("无法启动录音");
    }
  }
};

const sendVoiceMessage = async (audioBlob: Blob) => {
  messages.value.push({
    role: "user",
    content: "[语音消息]",
    timestamp: Date.now(),
  });

  const assistantMsg: ChatMessage = {
    role: "assistant",
    content: "",
    isThinking: true,
    timestamp: Date.now(),
  };
  messages.value.push(assistantMsg);
  isStreaming.value = true;
  await scrollToBottom();

  thinkingTimer = setTimeout(() => {
    if (assistantMsg.isThinking) {
      assistantMsg.isThinking = false;
      if (!assistantMsg.content) assistantMsg.content = "正在处理语音...";
    }
  }, 5000);

  try {
    const generator = uploadVoice(audioBlob, conversationId.value);
    for await (const event of generator) {
      if (!isStreaming.value) break;

      switch (event.type) {
        case "transcription":
          const userMsg = messages.value.find(
            (m) => m.role === "user" && m.content === "[语音消息]",
          );
          if (userMsg) userMsg.content = event.content || "[语音]";
          break;
        case "content":
          if (assistantMsg.isThinking) {
            assistantMsg.isThinking = false;
            if (thinkingTimer) clearTimeout(thinkingTimer);
          }
          assistantMsg.content += event.content || "";
          scrollToBottom();
          break;
        case "audio":
          assistantMsg.audioUrl = event.url;
          break;
        case "status":
          if (assistantMsg.isThinking) {
            assistantMsg.isThinking = false;
            if (thinkingTimer) clearTimeout(thinkingTimer);
          }
          assistantMsg.content = event.content || "处理中...";
          scrollToBottom();
          break;
        case "tool_result":
          if (thinkingTimer) clearTimeout(thinkingTimer);
          assistantMsg.isThinking = false;
          assistantMsg.content =
            (event as any).result ||
            event.content ||
            assistantMsg.content ||
            "操作已完成";
          eventStore.debouncedFetch(
            new Date(new Date().getFullYear(), new Date().getMonth(), 1)
              .toISOString()
              .slice(0, 10),
            new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)
              .toISOString()
              .slice(0, 10),
          );
          scrollToBottom();
          break;
        case "event_data":
          if (event.action && event.events) {
            eventStore.applyFromAI({
              action: event.action,
              events: event.events,
            });
          }
          break;
        case "done":
          if (event.conversationId) conversationId.value = event.conversationId;
          break;
        case "error":
          if (thinkingTimer) clearTimeout(thinkingTimer);
          assistantMsg.isThinking = false;
          assistantMsg.content =
            "抱歉，语音处理出错: " + (event.content || "未知错误");
          ElMessage.error("语音处理出错");
          break;
      }
    }
  } catch (e: any) {
    console.error("[ChatPanel] 语音流异常:", e);
    if (thinkingTimer) clearTimeout(thinkingTimer);
    assistantMsg.isThinking = false;
    assistantMsg.content = "抱歉，网络请求失败: " + (e.message || "未知错误");
    ElMessage.error("网络请求失败");
  } finally {
    if (thinkingTimer) clearTimeout(thinkingTimer);
    assistantMsg.isThinking = false;
    if (!assistantMsg.content) assistantMsg.content = "收到空回复，请重试。";
    isStreaming.value = false;

    extractCalendarJson(assistantMsg.content);
    assistantMsg.content = assistantMsg.content
      .replace(/```calendar-json\s*\n?[\s\S]*?\n?\s*```/g, "")
      .trim();
    if (!assistantMsg.content) assistantMsg.content = "操作已完成";

    if (
      assistantMsg.content.includes("已创建") ||
      assistantMsg.content.includes("已修改") ||
      assistantMsg.content.includes("已删除") ||
      assistantMsg.content.includes("已标记") ||
      assistantMsg.content.includes("已安排")
    ) {
      eventStore.debouncedFetch(
        new Date(new Date().getFullYear(), new Date().getMonth(), 1)
          .toISOString()
          .slice(0, 10),
        new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0)
          .toISOString()
          .slice(0, 10),
      );
    }
  }
};

/* ── Key binding ── */
const onKeydown = (e: KeyboardEvent) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

/* ── Textarea auto-resize ── */
const autoResize = () => {
  const el = textareaRef.value;
  if (!el) return;
  el.style.height = "auto";
  // 最大高度 120px，超出则出滚动条
  el.style.height = Math.min(el.scrollHeight, 120) + "px";
};
// 输入清空时重置高度
watch(inputText, (val) => {
  if (!val) nextTick(autoResize);
});

/* ── Image upload ── */
const triggerImageUpload = () => {
  fileInput.value?.click();
};
const handleImageUpload = (e: Event) => {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    ElMessage.error("请上传图片文件");
    target.value = "";
    return;
  }
  const localUrl = URL.createObjectURL(file);
  const imgObj: UploadImage = {
    url: localUrl,
    file,
    status: "uploading",
  };
  images.value.push(imgObj);
  const formData = new FormData();
  formData.append("file", file);
  axios
    .post("/api/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    })
    .then((res) => {
      imgObj.status = "success";
      if (res.data?.url) imgObj.url = res.data.url;
    })
    .catch(() => {
      imgObj.status = "error";
    });
  target.value = "";
};
const removeImage = (index: number) => {
  const img = images.value[index];
  if (img) {
    URL.revokeObjectURL(img.url);
    images.value.splice(index, 1);
  }
};
const openPreview = (url: string) => {
  previewUrls.value = [url];
  previewVisible.value = true;
};

/* ── Suggestions ── */
const suggestions = [
  "帮我明天下午3点安排一个会议",
  "我今天有什么日程安排？",
  "帮我把面试改到后天下午2点",
  "帮我创建一个标签叫「重要」",
  "最近的笔记有哪些？",
  "帮我写周报并发到邮箱",
];
</script>

<template>
  <div class="chat-panel glass-panel">
    <!-- Header -->
    <div class="chat-header">
      <div class="bot-info">
        <div class="bot-avatar"><Sparkles :size="20" color="#fff" /></div>
        <div class="bot-text">
          <div class="bot-name">七牛语音日历</div>
          <div class="bot-status">
            {{ isStreaming ? "思考中..." : "开始对话" }}
          </div>
        </div>
      </div>
    </div>

    <!-- Chat content -->
    <div class="chat-content" ref="chatContentRef">
      <!-- Welcome -->
      <div v-if="messages.length === 0" class="chat-welcome">
        <div class="welcome-logo"><Sparkles :size="40" color="#6c63ff" /></div>
        <h3 class="welcome-title">七牛语音日历</h3>
        <p class="welcome-sub">我可以帮你管理日程、创建事件、安排会议</p>
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

      <!-- Messages -->
      <div v-else class="message-list">
        <TransitionGroup name="msg">
          <ChatMessageItem
            v-for="(msg, index) in messages"
            :key="msg.timestamp + '-' + msg.role"
            :msg="msg"
            :index="index"
            :is-streaming="isStreaming"
            :is-last="index === messages.length - 1"
          />
        </TransitionGroup>
      </div>
    </div>

    <!-- "New messages" badge -->
    <Transition name="badge-pop">
      <button
        v-if="showNewMessageBadge"
        class="new-message-badge"
        @click="jumpToBottom"
      >
        <ArrowDown :size="14" />
        <span>新消息</span>
      </button>
    </Transition>

    <!-- Input -->
    <div class="chat-input-area">
      <div class="input-box">
        <div class="image-previews" v-if="images.length > 0">
          <div class="img-wrap" v-for="(img, idx) in images" :key="idx">
            <el-image
              :src="img.url"
              class="img-thumb"
              fit="cover"
              @click="openPreview(img.url)"
            />
            <div v-if="img.status === 'success'" class="img-ok">
              <CheckCircle :size="14" />
            </div>
            <div v-if="img.status === 'error'" class="img-error">
              <X :size="14" />
            </div>
            <button class="img-remove" @click="removeImage(idx)">
              <X :size="10" />
            </button>
          </div>
        </div>
        <div class="input-row">
          <textarea
            ref="textareaRef"
            v-model="inputText"
            class="chat-textarea"
            placeholder="输入消息..."
            rows="1"
            @input="autoResize"
            @keydown="onKeydown"
          />
          <div class="input-actions">
            <button
              class="icon-btn"
              :class="{ recording: isRecording }"
              :disabled="isStreaming && !isRecording"
              @click="toggleRecording"
              title="语音输入"
            >
              <Square v-if="isRecording" :size="14" fill="currentColor" />
              <Mic v-else :size="18" />
            </button>
            <button
              class="icon-btn"
              @click="triggerImageUpload"
              :disabled="isStreaming"
              title="上传图片"
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
              v-if="!isStreaming"
              class="send-btn"
              :class="{ ready: inputText.trim() }"
              :disabled="!inputText.trim()"
              @click="sendMessage()"
              title="发送"
            >
              <Send :size="16" />
            </button>
            <button
              v-else
              class="stop-btn"
              @click="stopStreaming()"
              title="停止生成"
            >
              <Square :size="12" fill="currentColor" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ═══ Message transition ═══ */
.msg-enter-active {
  transition: all 0.35s ease-out;
}
.msg-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

/* ═══ New message badge ═══ */
.new-message-badge {
  position: absolute;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: rgba(108, 99, 255, 0.9);
  backdrop-filter: blur(8px);
  color: #fff;
  border: none;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.4);
  transition: all 0.2s;
}
.new-message-badge:hover {
  background: rgba(91, 82, 224, 0.95);
  transform: translateX(-50%) translateY(-2px);
  box-shadow: 0 6px 20px rgba(108, 99, 255, 0.5);
}
.badge-pop-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.badge-pop-leave-active {
  transition: all 0.2s ease-in;
}
.badge-pop-enter-from,
.badge-pop-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px) scale(0.8);
}

/* ═══ Welcome ═══ */
.chat-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 30px;
  text-align: center;
  margin: auto 0;
}
.welcome-logo {
  margin-bottom: 12px;
  opacity: 0.8;
}
.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  margin: 0 0 6px;
}
.welcome-sub {
  font-size: 14px;
  color: #888;
  margin: 0 0 28px;
}
.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 500px;
}
.chip {
  background: rgba(108, 99, 255, 0.12);
  color: #a78bfa;
  border: 1px solid rgba(108, 99, 255, 0.2);
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.chip:hover {
  background: rgba(108, 99, 255, 0.25);
  border-color: rgba(108, 99, 255, 0.4);
  cursor: pointer;
}

/* ═══ Image previews ═══ */
.image-previews {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px 0;
}
.img-wrap {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.img-wrap :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.img-ok {
  position: absolute;
  top: 3px;
  right: 3px;
  color: #22c55e;
}
.img-remove {
  position: absolute;
  bottom: 3px;
  right: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}
.img-wrap:hover .img-remove {
  opacity: 1;
}

/* ═══ Input area ═══ */
.chat-input-area {
  padding: 8px 0 14px;
}
.input-box {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  overflow: hidden;
  transition: border-color 0.2s;
  margin: 0 12px;
}
.input-box:focus-within {
  border-color: rgba(108, 99, 255, 0.5);
}
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 8px 12px;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-left: auto;
}

.chat-textarea {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #e5e5e5;
  font-size: 14px;
  padding: 4px 0;
  resize: none;
  font-family: inherit;
  line-height: 1.5;
  min-height: 36px;
  max-height: 120px;
  overflow-y: auto;
}
.chat-textarea::placeholder {
  color: #666;
}

.icon-btn {
  background: none;
  border: none;
  color: #666;
  cursor: pointer;
  padding: 6px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}
.icon-btn:hover {
  color: #aaa;
  background: rgba(255, 255, 255, 0.05);
  cursor: pointer;
}
.icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.icon-btn.recording {
  color: #ef4444;
  animation: pulse-record 1.2s infinite;
}
@keyframes pulse-record {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}

/* ═══ Send / Stop ═══ */
.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #333;
  color: #666;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: not-allowed;
  transition: all 0.2s;
  flex-shrink: 0;
}
.send-btn.ready {
  background: #6c63ff;
  color: #fff;
  cursor: pointer;
}
.send-btn.ready:hover {
  background: #5b52e0;
}

.stop-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
  animation: pulse-stop 1.5s infinite;
}
.stop-btn:hover {
  background: #dc2626;
  cursor: pointer;
}
@keyframes pulse-stop {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(239, 68, 68, 0);
  }
}

/* ═══ Scrollbar ═══ */
.chat-content::-webkit-scrollbar {
  width: 4px;
}
.chat-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}
.chat-content::-webkit-scrollbar-track {
  background: transparent;
}
</style>
