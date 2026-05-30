<script setup lang="ts">
import { ref, computed, nextTick } from "vue";
import { Calendar, Eye, Check, Clock, X } from "@lucide/vue";
import { ElDialog, ElMessage, ElInput } from "element-plus";
import CalendarDayCell from "./CalendarDayCell.vue";
import ThemeTag from "./ThemeTag.vue";

/* ── Task type ── */
interface Task {
  id: string;
  title: string;
  description: string;
  completed: boolean;
  startDate: Date;
  endDate: Date;
  duration: string;
  tags: string[];
  participants: string[];
  createdAt: Date;
  reminder: string;
}

/* ── Mock data ── */
const tasks = ref<Task[]>([
  {
    id: "1",
    title: "👋 跟七牛语音日历打个招呼",
    description:
      "首次使用七牛语音日历，了解基本功能和操作指南。\nhttps://www.qiniu.com/calendar/guide",
    completed: false,
    startDate: new Date(2026, 4, 29, 17, 16),
    endDate: new Date(2026, 4, 29, 17, 36),
    duration: "20m",
    tags: ["新手引导"],
    participants: [],
    createdAt: new Date(2026, 4, 29, 9, 6),
    reminder: "5 min",
  },
  {
    id: "2",
    title: "聊天让七牛语音日历创建待办",
    description: "通过与日历助手对话来创建新的待办事项。",
    completed: false,
    startDate: new Date(2026, 4, 29, 17, 41),
    endDate: new Date(2026, 4, 29, 18, 1),
    duration: "20m",
    tags: ["效率"],
    participants: [],
    createdAt: new Date(2026, 4, 29, 9, 6),
    reminder: "5 min",
  },
  {
    id: "3",
    title: "让七牛语音日历为任务加标签",
    description: "学习如何使用标签对任务进行分类管理。",
    completed: false,
    startDate: new Date(2026, 4, 29, 18, 6),
    endDate: new Date(2026, 4, 29, 18, 26),
    duration: "20m",
    tags: ["分类"],
    participants: [],
    createdAt: new Date(2026, 4, 29, 9, 6),
    reminder: "5 min",
  },
  {
    id: "4",
    title: "💡 让七牛语音日历记录闪念",
    description: "快速记录灵感和想法的功能演示。",
    completed: false,
    startDate: new Date(2026, 4, 30, 9, 0),
    endDate: new Date(2026, 4, 30, 9, 20),
    duration: "20m",
    tags: ["灵感"],
    participants: [],
    createdAt: new Date(2026, 4, 29, 9, 6),
    reminder: "10 min",
  },
  {
    id: "5",
    title: "让七牛语音日历记录图片笔记",
    description: "通过语音描述来创建包含图片的笔记。",
    completed: false,
    startDate: new Date(2026, 4, 30, 9, 25),
    endDate: new Date(2026, 4, 30, 9, 45),
    duration: "20m",
    tags: ["笔记"],
    participants: [],
    createdAt: new Date(2026, 4, 29, 9, 6),
    reminder: "5 min",
  },
  {
    id: "6",
    title: "面试",
    description: "候选人技术面试，请提前准备面试问题列表。",
    completed: false,
    startDate: new Date(2026, 4, 29, 17, 0),
    endDate: new Date(2026, 4, 29, 18, 0),
    duration: "1h",
    tags: ["工作"],
    participants: [],
    createdAt: new Date(2026, 4, 25, 10, 0),
    reminder: "15 min",
  },
  {
    id: "7",
    title: "📝 周报整理",
    description: "整理本周工作内容并输出周报。",
    completed: true,
    startDate: new Date(2026, 4, 28, 14, 0),
    endDate: new Date(2026, 4, 28, 15, 0),
    duration: "1h",
    tags: ["工作", "周报"],
    participants: [],
    createdAt: new Date(2026, 4, 25, 10, 0),
    reminder: "10 min",
  },
  {
    id: "8",
    title: "🏋️ 健身",
    description: "去健身房做力量训练。",
    completed: true,
    startDate: new Date(2026, 4, 27, 18, 0),
    endDate: new Date(2026, 4, 27, 19, 30),
    duration: "1h 30m",
    tags: ["健康"],
    participants: [],
    createdAt: new Date(2026, 4, 26, 8, 0),
    reminder: "30 min",
  },
  {
    id: "9",
    title: "团队站会",
    description: "每日站会，同步进度和阻塞项。",
    completed: false,
    startDate: new Date(2026, 4, 24, 9, 30),
    endDate: new Date(2026, 4, 24, 9, 45),
    duration: "15m",
    tags: ["工作"],
    participants: [],
    createdAt: new Date(2026, 4, 20, 8, 0),
    reminder: "5 min",
  },
  {
    id: "10",
    title: "📅 月度复盘会议",
    description: "五月工作复盘，讨论成果和改进点。",
    completed: false,
    startDate: new Date(2026, 4, 30, 15, 0),
    endDate: new Date(2026, 4, 30, 16, 30),
    duration: "1h 30m",
    tags: ["工作", "会议"],
    participants: [],
    createdAt: new Date(2026, 4, 20, 8, 0),
    reminder: "15 min",
  },
  {
    id: "11",
    title: "🎂 同事生日派对",
    description: "庆祝小明生日，地点在休息区。",
    completed: false,
    startDate: new Date(2026, 4, 25, 16, 0),
    endDate: new Date(2026, 4, 25, 17, 0),
    duration: "1h",
    tags: ["社交"],
    participants: [],
    createdAt: new Date(2026, 4, 22, 9, 0),
    reminder: "30 min",
  },
  {
    id: "12",
    title: "读《设计模式》第三章",
    description: "学习策略模式和观察者模式。",
    completed: false,
    startDate: new Date(2026, 4, 26, 20, 0),
    endDate: new Date(2026, 4, 26, 21, 0),
    duration: "1h",
    tags: ["学习"],
    participants: [],
    createdAt: new Date(2026, 4, 23, 18, 0),
    reminder: "10 min",
  },
]);

/* ── State ── */
const currentDate = ref(new Date(2026, 4, 1)); // May 2026
type ViewMode = "agenda" | "week" | "month";
const viewMode = ref<ViewMode>("agenda");
const showCompleted = ref(false);
const selectedTask = ref<Task | null>(null);
const dialogVisible = ref(false);
const inputTagVisible = ref(false);
const newTagValue = ref("");
const tagInput = ref<InstanceType<typeof ElInput>>();

function showTagInput() {
  inputTagVisible.value = true;
  nextTick(() => {
    tagInput.value?.focus();
  });
}

function confirmTag() {
  if (
    newTagValue.value &&
    selectedTask.value &&
    !selectedTask.value.tags.includes(newTagValue.value)
  ) {
    selectedTask.value.tags.push(newTagValue.value);
  }
  inputTagVisible.value = false;
  newTagValue.value = "";
}

function removeTag(tag: string) {
  if (selectedTask.value) {
    const idx = selectedTask.value.tags.indexOf(tag);
    if (idx !== -1) {
      selectedTask.value.tags.splice(idx, 1);
    }
  }
}

function deleteTask() {
  if (selectedTask.value) {
    const idx = tasks.value.findIndex((t) => t.id === selectedTask.value!.id);
    if (idx > -1) {
      tasks.value.splice(idx, 1);
    }
  }
  closeDialog();
  ElMessage.success("已删除任务");
}

function saveTask() {
  closeDialog();
  ElMessage.success("已保存修改");
}

/* ── Computed ── */
const currentYear = computed(() => currentDate.value.getFullYear());
const currentMonth = computed(() => currentDate.value.getMonth());
const currentMonthName = computed(
  () =>
    `${currentYear.value}年${["一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"][currentMonth.value]}`,
);

const filteredTasks = computed(() =>
  tasks.value.filter((t) => showCompleted.value || !t.completed),
);

const monthTasks = computed(() =>
  filteredTasks.value.filter(
    (t) =>
      t.startDate.getFullYear() === currentYear.value &&
      t.startDate.getMonth() === currentMonth.value,
  ),
);

const agendaGrouped = computed(() => {
  const groups: { date: Date; tasks: Task[] }[] = [];
  const map = new Map<string, Task[]>();
  for (const t of monthTasks.value) {
    const key = t.startDate.toDateString();
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(t);
  }
  for (const [key, tasks] of map) {
    groups.push({ date: new Date(key), tasks });
  }
  groups.sort((a, b) => a.date.getTime() - b.date.getTime());
  return groups;
});

const weekStart = computed(() => {
  const d = new Date(currentDate.value);
  d.setDate(d.getDate() - d.getDay());
  return d;
});

const weekDays = computed(() => {
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(weekStart.value);
    d.setDate(d.getDate() + i);
    days.push(d);
  }
  return days;
});

const weekTasks = computed(() => {
  const end = new Date(weekDays.value[6]);
  end.setHours(23, 59, 59, 999);
  return filteredTasks.value.filter(
    (t) => t.startDate >= weekStart.value && t.startDate <= end,
  );
});

const monthGrid = computed(() => {
  const firstDay = new Date(currentYear.value, currentMonth.value, 1);
  const lastDay = new Date(currentYear.value, currentMonth.value + 1, 0);
  const startPad = firstDay.getDay();
  const totalDays = lastDay.getDate();
  const weeks: (number | null)[][] = [];
  let week: (number | null)[] = [];
  for (let i = 0; i < startPad; i++) week.push(null);
  for (let d = 1; d <= totalDays; d++) {
    week.push(d);
    if (week.length === 7) {
      weeks.push(week);
      week = [];
    }
  }
  if (week.length > 0) {
    while (week.length < 7) week.push(null);
    weeks.push(week);
  }
  return weeks;
});

function getTasksForDay(day: number) {
  return filteredTasks.value.filter(
    (t) =>
      t.startDate.getFullYear() === currentYear.value &&
      t.startDate.getMonth() === currentMonth.value &&
      t.startDate.getDate() === day,
  );
}

function getTasksForDate(date: Date) {
  return filteredTasks.value.filter(
    (t) => t.startDate.toDateString() === date.toDateString(),
  );
}

function timeLabel(d: Date): string {
  const h = d.getHours();
  const m = d.getMinutes().toString().padStart(2, "0");
  const ampm = h < 12 ? "上午" : "下午";
  const hh = h % 12 || 12;
  return `${ampm} ${hh}:${m}`;
}

function isToday(d: Date): boolean {
  const t = new Date();
  return d.toDateString() === t.toDateString();
}

/* ── Actions ── */
function prevMonth() {
  currentDate.value = new Date(currentYear.value, currentMonth.value - 1, 1);
}

function nextMonth() {
  currentDate.value = new Date(currentYear.value, currentMonth.value + 1, 1);
}

function goToday() {
  currentDate.value = new Date();
  currentDate.value.setDate(1);
}

function toggleComplete(task: Task) {
  task.completed = !task.completed;
  ElMessage.success(task.completed ? "已标记为完成 ✅" : "已标记为未完成");
}

function openDetail(task: Task) {
  selectedTask.value = task;
  dialogVisible.value = true;
}

function closeDialog() {
  dialogVisible.value = false;
  selectedTask.value = null;
}

function setView(mode: ViewMode) {
  viewMode.value = mode;
}

const dayNames = ["日", "一", "二", "三", "四", "五", "六"];
const timeSlots = Array.from({ length: 24 }, (_, i) => `${i}:00`);
</script>

<template>
  <div class="agenda-panel glass-panel">
    <!-- Header -->
    <div class="panel-header">
      <div class="header-left">
        <span class="nav-arrow" @click="prevMonth">&lt;</span>
        <span class="title" @click="goToday" style="cursor: pointer">{{
          currentMonthName
        }}</span>
        <span class="nav-arrow" @click="nextMonth">&gt;</span>
      </div>
      <div class="header-right">
        <button
          class="btn"
          :class="showCompleted ? 'btn-filled' : 'btn-outline'"
          @click="showCompleted = !showCompleted"
        >
          <Eye :size="14" /> 已完成
        </button>
        <div class="view-switch">
          <button
            v-for="mode in ['agenda', 'week', 'month'] as ViewMode[]"
            :key="mode"
            class="btn"
            :class="viewMode === mode ? 'btn-filled' : 'btn-outline'"
            @click="setView(mode)"
          >
            <template v-if="mode === 'agenda'">日程</template>
            <template v-else-if="mode === 'week'">本周</template>
            <template v-else>本月</template>
            <Calendar v-if="mode === viewMode" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <!-- Content -->
    <div class="agenda-content">
      <!-- Agenda (list) view -->
      <template v-if="viewMode === 'agenda'">
        <div
          v-for="group in agendaGrouped"
          :key="group.date.toDateString()"
          class="day-group"
        >
          <div class="day-timestamp">
            <span class="dot" :class="isToday(group.date) ? 'red' : ''"></span>
            {{ isToday(group.date) ? "今天, " : ""
            }}{{ group.date.getFullYear() }}年{{
              group.date.getMonth() + 1
            }}月{{ group.date.getDate() }}日
            {{
              [
                "星期日",
                "星期一",
                "星期二",
                "星期三",
                "星期四",
                "星期五",
                "星期六",
              ][group.date.getDay()]
            }}
            <div class="line" :class="isToday(group.date) ? '' : 'light'"></div>
          </div>
          <div
            v-for="task in group.tasks"
            :key="task.id"
            class="task-item"
            :class="{ 'is-completed': task.completed }"
            @click="openDetail(task)"
          >
            <div
              class="radio-circle"
              :class="{ checked: task.completed }"
              @click.stop="toggleComplete(task)"
            >
              <Check v-if="task.completed" :size="10" />
            </div>
            <span class="time"
              >{{ timeLabel(task.startDate) }} -
              {{ timeLabel(task.endDate) }}</span
            >
            <span class="task-text">{{ task.title }}</span>
          </div>
        </div>
        <div v-if="agendaGrouped.length === 0" class="empty-state">
          当前月份暂无任务
        </div>
      </template>

      <!-- Week grid view -->
      <template v-if="viewMode === 'week'">
        <div class="week-grid">
          <div class="week-header">
            <div class="time-corner"></div>
            <div
              v-for="(day, idx) in weekDays"
              :key="idx"
              class="week-day-header"
              :class="{ today: isToday(day) }"
            >
              <div class="day-name">{{ dayNames[idx] }}</div>
              <div class="day-number">{{ day.getDate() }}</div>
            </div>
          </div>
          <div class="week-body">
            <div v-for="(slot, si) in timeSlots" :key="si" class="week-row">
              <div class="time-label">{{ slot }}</div>
              <div
                v-for="(day, di) in weekDays"
                :key="di"
                class="week-cell"
                :class="{ today: isToday(day) }"
              >
                <CalendarDayCell
                  mode="week"
                  :is-today="isToday(day)"
                  :tasks="
                    getTasksForDate(day).filter(
                      (t) => t.startDate.getHours() === si,
                    )
                  "
                  @task-click="openDetail"
                />
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- Month grid view -->
      <template v-if="viewMode === 'month'">
        <div class="month-grid">
          <div class="month-header">
            <div v-for="name in dayNames" :key="name" class="month-day-name">
              {{ name }}
            </div>
          </div>
          <div class="month-body">
            <div v-for="(week, wi) in monthGrid" :key="wi" class="month-row">
              <div
                v-for="(day, di) in week"
                :key="di"
                class="month-cell"
                :class="{
                  'other-month': day === null,
                  today:
                    day !== null &&
                    isToday(new Date(currentYear, currentMonth, day)),
                }"
              >
                <CalendarDayCell
                  mode="month"
                  :day-number="day"
                  :is-today="
                    day !== null &&
                    isToday(new Date(currentYear, currentMonth, day))
                  "
                  :tasks="day !== null ? getTasksForDay(day) : []"
                  :max-display="3"
                  @task-click="openDetail"
                />
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- Task Detail Dialog -->
    <ElDialog
      v-model="dialogVisible"
      :title="selectedTask?.title || '任务详情'"
      width="420px"
      class="task-dialog custom-form-dialog"
      :close-on-click-modal="true"
      :append-to-body="false"
      align-center
      @close="closeDialog"
    >
      <template v-if="selectedTask">
        <el-form label-position="top" :model="selectedTask">
          <el-form-item>
            <template #label>
              <div
                style="
                  display: flex;
                  align-items: center;
                  gap: 4px;
                  color: #888;
                "
              >
                <span class="el-icon-edit" /> 标题
              </div>
            </template>
            <el-input v-model="selectedTask.title" placeholder="请输入标题" />
          </el-form-item>

          <el-form-item>
            <template #label>
              <div
                style="
                  display: flex;
                  align-items: center;
                  gap: 4px;
                  color: #888;
                "
              >
                <span class="el-icon-document" /> 描述
              </div>
            </template>
            <el-input
              v-model="selectedTask.description"
              type="textarea"
              :rows="2"
              placeholder="添加描述..."
            />
          </el-form-item>

          <el-form-item>
            <template #label>
              <div
                style="
                  display: flex;
                  align-items: center;
                  gap: 4px;
                  color: #888;
                "
              >
                <span class="el-icon-circle-check" /> 状态
              </div>
            </template>
            <el-checkbox v-model="selectedTask.completed">完成</el-checkbox>
          </el-form-item>

          <div style="display: flex; gap: 16px; margin-bottom: -4px">
            <el-form-item style="flex: 1">
              <template #label>
                <div
                  style="
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    color: #888;
                  "
                >
                  <span class="el-icon-date" /> 开始时间
                </div>
              </template>
              <el-date-picker
                v-model="selectedTask.startDate"
                type="datetime"
                format="YYYY年MM月DD日 HH:mm"
                style="width: 100%"
                placeholder="选择开始时间"
              />
            </el-form-item>
          </div>

          <div style="display: flex; gap: 16px; margin-bottom: -4px">
            <el-form-item style="flex: 1">
              <template #label>
                <div
                  style="
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    color: #888;
                  "
                >
                  <span class="el-icon-time" /> 时长
                </div>
              </template>
              <el-input
                v-model="selectedTask.duration"
                placeholder="例如：1h, 30m"
              />
            </el-form-item>
          </div>

          <div style="display: flex; gap: 16px; margin-bottom: -4px">
            <el-form-item style="flex: 1">
              <template #label>
                <div
                  style="
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    color: #888;
                  "
                >
                  <span class="el-icon-date" /> 结束时间
                </div>
              </template>
              <el-date-picker
                v-model="selectedTask.endDate"
                type="datetime"
                format="YYYY年MM月DD日 HH:mm"
                style="width: 100%"
                placeholder="选择结束时间"
              />
            </el-form-item>
          </div>

          <el-form-item>
            <template #label>
              <div
                style="
                  display: flex;
                  align-items: center;
                  gap: 8px;
                  color: #888;
                "
              >
                <el-checkbox :checked="true" />
                <span class="el-icon-bell" /> 提醒
              </div>
            </template>
            <el-select v-model="selectedTask.reminder" style="width: 120px">
              <el-option label="5分钟前" value="5 min" />
              <el-option label="10分钟前" value="10 min" />
              <el-option label="15分钟前" value="15 min" />
              <el-option label="30分钟前" value="30 min" />
              <el-option label="1小时前" value="1h" />
            </el-select>
          </el-form-item>

          <el-form-item style="margin-bottom: 0">
            <template #label>
              <div
                style="
                  display: flex;
                  align-items: center;
                  gap: 4px;
                  color: #888;
                "
              >
                <span class="el-icon-collection-tag" /> 标签
              </div>
            </template>
            <div
              style="
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: 4px;
              "
            >
              <ThemeTag
                v-for="tag in selectedTask.tags"
                :key="tag"
                type="info"
                style="cursor: pointer"
                @click="removeTag(tag)"
                title="点击移除"
              >
                {{ tag }} <X :size="10" style="margin-left: 4px" />
              </ThemeTag>

              <el-input
                v-if="inputTagVisible"
                ref="tagInput"
                v-model="newTagValue"
                size="small"
                style="width: 100px"
                @keyup.enter="confirmTag"
                @blur="confirmTag"
              />
              <el-button
                v-else
                size="small"
                @click="showTagInput"
                style="height: 28px; padding: 0 12px; border-radius: 14px"
              >
                选择标签
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-top: 1px solid rgba(0, 0, 0, 0.05);
            padding-top: 12px;
            margin-top: 12px;
          "
        >
          <div style="display: flex; gap: 8px">
            <el-button
              type="danger"
              plain
              size="small"
              style="border-radius: 6px; padding: 8px 16px"
              @click="deleteTask"
              >删除</el-button
            >
          </div>
          <div style="display: flex; gap: 8px">
            <el-button
              type="info"
              color="#444"
              @click="saveTask"
              style="border-radius: 6px; color: #fff; padding: 8px 16px"
              >修改保存</el-button
            >
          </div>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
/* ── View switch ── */
.view-switch {
  display: flex;
  gap: 4px;
  background: rgba(0, 0, 0, 0.15);
  padding: 2px;
  border-radius: 16px;
}

/* ── Completed style ── */
.task-item.is-completed .task-text {
  text-decoration: line-through;
  opacity: 0.5;
}

.radio-circle.checked {
  background: #67c23a;
  border-color: #67c23a;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

/* ── Empty state ── */
.empty-state {
  text-align: center;
  color: #888;
  padding: 60px 0;
  font-size: 14px;
}

/* ── Week Grid ── */
.week-grid {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.week-header {
  display: grid;
  grid-template-columns: 60px repeat(7, 1fr);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 8px;
  margin-bottom: 4px;
  flex-shrink: 0;
}

.time-corner {
  width: 60px;
}

.week-day-header {
  text-align: center;
  padding: 4px 0;
  border-radius: 8px;
}

.week-day-header.today {
  background: rgba(255, 82, 82, 0.15);
}

.day-name {
  font-size: 11px;
  color: #999;
  margin-bottom: 2px;
}

.day-number {
  font-size: 14px;
  font-weight: 600;
  color: #ccc;
}

.week-day-header.today .day-number {
  color: #ff5252;
}

.week-body {
  flex: 1;
  overflow-y: auto;
}

.week-body::-webkit-scrollbar {
  width: 4px;
}
.week-body::-webkit-scrollbar-track {
  background: transparent;
}
.week-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 4px;
}

.week-row {
  display: grid;
  grid-template-columns: 60px repeat(7, 1fr);
  height: 100px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.time-label {
  font-size: 11px;
  color: #888;
  padding: 4px 4px 0 0;
  text-align: right;
}

.week-cell {
  padding: 2px;
  height: 100px;
  overflow-y: auto;
  overflow-x: hidden;
  border-left: 1px solid rgba(255, 255, 255, 0.04);
}

.week-cell::-webkit-scrollbar {
  width: 2px;
}
.week-cell::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.week-cell.today {
  background: rgba(255, 82, 82, 0.05);
}

.week-task-chip {
  background: rgba(103, 194, 58, 0.15);
  border: 1px solid rgba(103, 194, 58, 0.3);
  border-radius: 4px;
  padding: 2px 4px;
  margin-bottom: 2px;
  cursor: pointer;
  transition: background 0.2s;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.week-task-chip:hover {
  background: rgba(103, 194, 58, 0.25);
}

.week-task-chip.completed {
  opacity: 0.5;
}

.chip-title {
  font-size: 11px;
  color: #b3e19d;
}

/* ── Month Grid ── */
.month-grid {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.month-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 8px;
  margin-bottom: 4px;
  flex-shrink: 0;
}

.month-day-name {
  text-align: center;
  font-size: 12px;
  color: #999;
  font-weight: 500;
}

.month-body {
  flex: 1;
  overflow-y: auto;
}

.month-body::-webkit-scrollbar {
  width: 4px;
}
.month-body::-webkit-scrollbar-track {
  background: transparent;
}
.month-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 4px;
}

.month-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.month-cell {
  min-height: 90px;
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.04);
  display: flex;
  flex-direction: column;
}

.month-cell.other-month {
  opacity: 0.2;
  pointer-events: none;
}

.month-cell.today {
  background: rgba(255, 82, 82, 0.08);
  border-color: rgba(255, 82, 82, 0.2);
  border-radius: 4px;
}

.cell-day-number {
  font-size: 12px;
  font-weight: 600;
  color: #bbb;
  margin-bottom: 4px;
}

.month-cell.today .cell-day-number {
  color: #ff5252;
}

.cell-tasks {
  flex: 1;
  overflow: hidden;
}

.cell-task {
  background: rgba(64, 158, 255, 0.12);
  border-radius: 3px;
  padding: 1px 4px;
  margin-bottom: 2px;
  cursor: pointer;
  transition: background 0.2s;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell-task:hover {
  background: rgba(64, 158, 255, 0.25);
}

.cell-task.completed {
  background: rgba(103, 194, 58, 0.12);
  opacity: 0.6;
}

.cell-task-text {
  font-size: 11px;
  color: #a0cfff;
}

.cell-more {
  font-size: 10px;
  color: #888;
  padding: 1px 4px;
}

/* ── Dialog ── */
:deep(.task-dialog) {
  --el-dialog-bg-color: #25252d;
  --el-dialog-title-font-size: 16px;
  --el-dialog-content-font-size: 14px;
  --el-text-color-primary: #eee;
  --el-border-color: rgba(255, 255, 255, 0.12);
  --el-tag-bg-color: rgba(255, 255, 255, 0.08);
  --el-tag-text-color: #ccc;
  --el-checkbox-text-color: #ddd;
}

.task-dialog :deep(.el-dialog) {
  background: #25252d;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  color: #eee;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4);
  margin: 0 !important;
  max-height: 90%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.task-dialog :deep(.el-dialog__title) {
  color: #eee;
  font-weight: 500;
}

.task-dialog :deep(.el-dialog__headerbtn) {
  color: #aaa;
}

.task-dialog :deep(.el-dialog__body) {
  color: #ccc;
  padding: 12px 20px 20px;
  overflow: hidden;
  flex: 1;
}

/* Override Form components for Dark Theme inside dialog */
.task-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}

.task-dialog :deep(.el-checkbox__label),
.task-dialog :deep(.el-form-item__content) {
  color: #ddd;
}

.task-dialog :deep(.el-date-editor.el-input),
.task-dialog :deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
}

/* ── Glass panel override for better scroll ── */
.agenda-panel {
  overflow: hidden;
  position: relative;
}

:deep(.el-overlay) {
  position: absolute;
}

.agenda-content {
  flex: 1;
  overflow-y: auto;
}

.agenda-content::-webkit-scrollbar {
  width: 4px;
}
.agenda-content::-webkit-scrollbar-track {
  background: transparent;
}
.agenda-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 4px;
}
</style>
