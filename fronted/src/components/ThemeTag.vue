<template>
  <span
    class="theme-tag"
    :style="{ backgroundColor: bgColor, color: textColor }"
  >
    <slot></slot>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  color?: string; // Optional predefined color (hex, rgb)
  type?: "primary" | "success" | "warning" | "danger" | "info";
}>();

// Simple color mapping matching dark theme vibe
const themeColors = {
  primary: { bg: "rgba(64, 158, 255, 0.2)", text: "#409eff" },
  success: { bg: "rgba(103, 194, 58, 0.2)", text: "#67c23a" },
  warning: { bg: "rgba(230, 162, 60, 0.2)", text: "#e6a23c" },
  danger: { bg: "rgba(245, 108, 108, 0.2)", text: "#f56c6c" },
  info: { bg: "rgba(144, 147, 153, 0.2)", text: "#909399" },
};

const bgColor = computed(() => {
  if (props.color) return `${props.color}33`; // 20% opacity trick loosely
  if (props.type && themeColors[props.type]) return themeColors[props.type].bg;
  return themeColors.info.bg;
});

const textColor = computed(() => {
  if (props.color) return props.color;
  if (props.type && themeColors[props.type])
    return themeColors[props.type].text;
  return themeColors.info.text;
});
</script>

<style scoped>
.theme-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  border: 1px solid rgba(255, 255, 255, 0.1);
  margin-right: 4px;
  margin-bottom: 4px;
}
</style>
