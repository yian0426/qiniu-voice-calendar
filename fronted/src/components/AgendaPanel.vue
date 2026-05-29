<script setup lang="ts">
import { Calendar, Eye } from "@lucide/vue";
</script>

<template>
  <div class="agenda-panel glass-panel">
    <div class="panel-header">
      <div class="header-left">
        <span class="nav-arrow">&lt;</span>
        <span class="title">2026年五月</span>
        <span class="nav-arrow">&gt;</span>
      </div>
      <div class="header-right">
        <button class="btn btn-outline"><Eye :size="14" /> 已完成</button>
        <button class="btn btn-filled">日程 <Calendar :size="14" /></button>
      </div>
    </div>

    <div class="agenda-content">
      <div class="day-group">
        <div class="day-timestamp">
          <span class="dot red"></span> 今天, 2026年5月29日 星期五
          <div class="line"></div>
        </div>
        <div class="task-item">
          <div class="radio-circle"></div>
          <span class="time">下午 5:16 - 下午 5:36</span>
          <span class="task-text">👋 跟七牛语音日历打个招呼</span>
          <span class="dot gray"></span>
        </div>
        <div class="task-item">
          <div class="radio-circle"></div>
          <span class="time">下午 5:41 - 下午 6:01</span>
          <span class="task-text">聊天让七牛语音日历创建待办</span>
          <span class="dot gray"></span>
        </div>
        <div class="task-item">
          <div class="radio-circle"></div>
          <span class="time">下午 6:06 - 下午 6:26</span>
          <span class="task-text">让七牛语音日历为任务加标签</span>
          <span class="dot gray"></span>
        </div>
      </div>

      <div class="day-group">
        <div class="day-timestamp">
          2026年5月30日 星期六
          <div class="line light"></div>
        </div>
        <div class="task-item">
          <div class="radio-circle"></div>
          <span class="time">上午 9:00 - 上午 9:20</span>
          <span class="task-text">💡 让七牛语音日历记录闪念</span>
          <span class="dot gray"></span>
        </div>
        <div class="task-item">
          <div class="radio-circle"></div>
          <span class="time">上午 9:25 - 上午 9:45</span>
          <span class="task-text">让七牛语音日历记录图片笔记</span>
          <span class="dot gray"></span>
        </div>
      </div>
    </div>
  </div>
</template>
