<script setup lang="ts">
import { ref } from "vue";
import { Sparkles, Send, Mic, Image as ImageIcon, X } from "@lucide/vue";
import { ElMessage, ElImage, ElProgress } from "element-plus";

const inputText = ref("");
interface UploadImage {
  url: string;
  progress: number;
  status: "uploading" | "success" | "error";
}
const images = ref<UploadImage[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);

const triggerImageUpload = () => {
  fileInput.value?.click();
};

const handleImageUpload = (e: Event) => {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const file = target.files[0];
    if (file.type.startsWith("image/")) {
      const url = URL.createObjectURL(file);
      const imgObj: UploadImage = { url, progress: 0, status: "uploading" };
      images.value.push(imgObj);

      // Simulate upload progress
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
  URL.revokeObjectURL(images.value[index].url);
  images.value.splice(index, 1);
};

const previewSrcList = ref<string[]>([]);
const openPreview = (url: string) => {
  previewSrcList.value = [url];
};
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
      <div class="input-box glass-input text-area-mode">
        <div class="image-previews" v-if="images.length > 0">
          <div
            class="image-preview-wrapper"
            v-for="(img, index) in images"
            :key="index"
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
              @click="removeImage(index)"
            >
              <X :size="12" />
            </button>
          </div>
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="想到什么就写下来吧"
          resize="none"
          class="custom-chat-input"
        />
        <div class="input-actions" style="margin-top: 8px">
          <button class="action-btn"><Mic :size="18" /></button>
          <button class="action-btn" @click="triggerImageUpload">
            <ImageIcon :size="18" />
          </button>
          <input
            type="file"
            ref="fileInput"
            accept="image/*"
            style="display: none"
            @change="handleImageUpload"
          />
          <button class="send-btn"><Send :size="16" /></button>
        </div>
      </div>
    </div>
  </div>
</template>
