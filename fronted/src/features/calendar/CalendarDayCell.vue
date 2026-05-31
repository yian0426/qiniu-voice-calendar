<template>
  <div class="calendar-day-cell" :class="[mode, { today: isToday }]">
    <template v-if="mode === 'month'">
      <div v-if="dayNumber !== null" class="cell-day-number">
        {{ dayNumber }}
      </div>
      <div v-if="dayNumber !== null" class="cell-tasks">
        <div
          v-for="task in displayTasks"
          :key="task.id"
          class="cell-task"
          :class="{ completed: task.completed }"
          @click.stop="$emit('task-click', task)"
        >
          <span class="cell-task-text">{{ task.title }}</span>
        </div>
        <div v-if="moreCount > 0" class="cell-more">+{{ moreCount }} 更多</div>
      </div>
    </template>

    <template v-else-if="mode === 'week'">
      <div
        v-for="task in tasks"
        :key="task.id"
        class="week-task-chip"
        :class="{ completed: task.completed }"
        @click.stop="$emit('task-click', task)"
      >
        <span class="chip-title">{{ task.title }}</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  mode: "week" | "month";
  dayNumber?: number | null;
  isToday?: boolean;
  tasks?: any[];
  maxDisplay?: number;
}>();

defineEmits(["task-click"]);

const displayTasks = computed(() => {
  if (!props.tasks) return [];
  if (props.mode === "month" && props.maxDisplay) {
    return props.tasks.slice(0, props.maxDisplay);
  }
  return props.tasks;
});

const moreCount = computed(() => {
  if (!props.tasks || !props.maxDisplay) return 0;
  return Math.max(0, props.tasks.length - props.maxDisplay);
});
</script>

<style scoped>
.calendar-day-cell {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.cell-day-number {
  font-size: 14px;
  color: #fff;
  margin-bottom: 4px;
  text-align: right;
  padding-right: 4px;
}

.cell-tasks {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cell-task {
  cursor: pointer;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 4px 8px;
  display: flex;
  align-items: center;
  transition: all 0.2s;
}

.cell-task:hover {
  background: rgba(255, 255, 255, 0.2);
}

.cell-task.completed {
  opacity: 0.5;
  text-decoration: line-through;
}

.cell-task-text {
  font-size: 12px;
  color: #eee;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-more {
  font-size: 10px;
  color: #aaa;
  text-align: center;
  margin-top: 2px;
}

.week-task-chip {
  cursor: pointer;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 4px 8px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  transition: all 0.2s;
}

.week-task-chip:hover {
  background: rgba(255, 255, 255, 0.2);
}

.week-task-chip.completed {
  opacity: 0.5;
  text-decoration: line-through;
}

.chip-title {
  font-size: 12px;
  color: #eee;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
