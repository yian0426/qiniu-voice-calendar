<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted } from "vue";
import { Calendar, Eye, Check, Clock, X } from "@lucide/vue";
import { ElDialog, ElMessage, ElInput } from "element-plus";
import CalendarDayCell from "./CalendarDayCell.vue";
import ThemeTag from "@/components/ThemeTag.vue";
import { api } from "@/utils/request";
import { useAuthStore } from "@/stores/auth";
import { useEventStore, type CalendarEvent } from "@/stores/events";

const authStore = useAuthStore();
const eventStore = useEventStore();

/* ── State ── */
const currentDate = ref(new Date());
type ViewMode = "agenda" | "week" | "month";
const viewMode = ref<ViewMode>("agenda");
const showCompleted = ref(false);
const selectedEvent = ref<CalendarEvent | null>(null);
const dialogVisible = ref(false);
const inputTagVisible = ref(false);
const newTagValue = ref("");
const tagInput = ref<InstanceType<typeof ElInput>>();

/* ── 从 store 读取事件列表（响应式） ── */
const events = computed(() => eventStore.events);
const loading = computed(() => eventStore.loading);

/* ── Data fetching ── */
async function fetchEvents() {
  if (!authStore.isLoggedIn) return;
  let startStr: string;
  let endStr: string;
  if (viewMode.value === "week") {
    const ws = getWeekStart(new Date());
    const we = new Date(ws);
    we.setDate(we.getDate() + 6);
    startStr = formatDateStr(ws);
    endStr = formatDateStr(we);
  } else {
    const firstDay = new Date(currentYear.value, currentMonth.value, 1);
    const lastDay = new Date(currentYear.value, currentMonth.value + 1, 0);
    startStr = formatDateStr(firstDay);
    endStr = formatDateStr(lastDay);
  }
  try {
    await eventStore.fetchEvents(startStr, endStr);
  } catch (e: any) {
    if (e?.response?.status === 403 || e?.response?.status === 401) {
      authStore.clearAuth();
    }
  }
}

function formatDateStr(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function formatDateTimeStr(d: Date): string {
  return (
    formatDateStr(d) +
    "T" +
    String(d.getHours()).padStart(2, "0") +
    ":" +
    String(d.getMinutes()).padStart(2, "0") +
    ":00"
  );
}

function reminderToLabel(minutes: number): string {
  if (minutes <= 0) return "无";
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)}h`;
}

function reminderToValue(label: string): number {
  const map: Record<string, number> = {
    "5 min": 5,
    "10 min": 10,
    "15 min": 15,
    "30 min": 30,
    "1h": 60,
  };
  return map[label] || 0;
}

onMounted(() => {
  if (authStore.isLoggedIn) {
    fetchEvents();
  }
});

// 监听登录状态变化 — 登录后自动拉取事件
watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) fetchEvents();
  },
);

/* ── Tag UI helpers ── */
function showTagInput() {
  inputTagVisible.value = true;
  nextTick(() => {
    tagInput.value?.focus();
  });
}

function confirmTag() {
  if (
    newTagValue.value &&
    selectedEvent.value &&
    !selectedEvent.value.tags.includes(newTagValue.value)
  ) {
    selectedEvent.value.tags.push(newTagValue.value);
  }
  inputTagVisible.value = false;
  newTagValue.value = "";
}

function removeTag(tag: string) {
  if (selectedEvent.value) {
    const idx = selectedEvent.value.tags.indexOf(tag);
    if (idx !== -1) selectedEvent.value.tags.splice(idx, 1);
  }
}

/* ── CRUD operations ── */
async function deleteEvent() {
  if (!selectedEvent.value) return;
  try {
    const res = await api.deleteEvent(selectedEvent.value.id);
    if (res.code === 200) {
      eventStore.removeEvent(selectedEvent.value.id);
      closeDialog();
      ElMessage.success("已删除");
    }
  } catch (e: any) {
    ElMessage.error("删除失败: " + (e.message || ""));
  }
}

async function saveEvent() {
  if (!selectedEvent.value) return;
  try {
    const ev = selectedEvent.value;
    const res = await api.patchEvent(ev.id, {
      title: ev.title,
      description: ev.description || undefined,
      startTime: formatDateTimeStr(ev.startDate),
      endTime: formatDateTimeStr(ev.endDate),
      duration: ev.duration || undefined,
      location: ev.location || undefined,
      tags: ev.tags.length > 0 ? ev.tags : undefined,
      reminderBefore: ev.reminderBefore,
    });
    if (res.code === 200 && res.data) {
      // 用 store 更新
      const updated = res.data;
      eventStore.updateEvent(ev.id, {
        title: updated.title,
        description: updated.description || "",
        completed: updated.status === 1,
        startDate: new Date(updated.startTime),
        endDate: new Date(updated.endTime),
        duration: updated.duration || "",
        tags: updated.tags || [],
        participants: updated.participants || [],
        location: updated.location || "",
        reminderBefore: updated.reminderBefore || 0,
      });
      closeDialog();
      ElMessage.success("已保存修改");
    }
  } catch (e: any) {
    ElMessage.error("保存失败: " + (e.message || ""));
  }
}

async function toggleComplete(event: CalendarEvent) {
  const newStatus = event.completed ? 0 : 1;
  try {
    const res = await api.toggleEventStatus(event.id, newStatus);
    if (res.code === 200) {
      eventStore.updateEvent(event.id, { completed: newStatus === 1 });
      ElMessage.success(newStatus === 1 ? "已标记为完成 ✅" : "已标记为未完成");
    }
  } catch (e: any) {
    ElMessage.error("操作失败");
  }
}

/* ── Computed ── */
const currentYear = computed(() => currentDate.value.getFullYear());
const currentMonth = computed(() => currentDate.value.getMonth());
const currentMonthName = computed(
  () =>
    `${currentYear.value}年${["一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月"][currentMonth.value]}`,
);

const filteredEvents = computed(() =>
  events.value.filter((e) => showCompleted.value || !e.completed),
);

const monthEvents = computed(() =>
  filteredEvents.value.filter(
    (e) =>
      e.startDate.getFullYear() === currentYear.value &&
      e.startDate.getMonth() === currentMonth.value,
  ),
);

const agendaGrouped = computed(() => {
  const groups: { date: Date; events: CalendarEvent[] }[] = [];
  const map = new Map<string, CalendarEvent[]>();
  for (const e of monthEvents.value) {
    const key = e.startDate.toDateString();
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(e);
  }
  for (const [key, evts] of map) {
    groups.push({ date: new Date(key), events: evts });
  }
  groups.sort((a, b) => a.date.getTime() - b.date.getTime());
  return groups;
});

/** 获取 date 所在周的周日 */
function getWeekStart(date: Date): Date {
  const d = new Date(date);
  d.setDate(d.getDate() - d.getDay());
  d.setHours(0, 0, 0, 0);
  return d;
}

const weekStart = computed(() => getWeekStart(new Date()));

const weekDays = computed(() => {
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(weekStart.value);
    d.setDate(d.getDate() + i);
    days.push(d);
  }
  return days;
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

function getEventsForDay(day: number) {
  return filteredEvents.value.filter(
    (e) =>
      e.startDate.getFullYear() === currentYear.value &&
      e.startDate.getMonth() === currentMonth.value &&
      e.startDate.getDate() === day,
  );
}

function getEventsForDate(date: Date) {
  return filteredEvents.value.filter(
    (e) => e.startDate.toDateString() === date.toDateString(),
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
  return d.toDateString() === new Date().toDateString();
}

/* ── Navigation ── */
function prevMonth() {
  currentDate.value = new Date(currentYear.value, currentMonth.value - 1, 1);
  fetchEvents();
}
function nextMonth() {
  currentDate.value = new Date(currentYear.value, currentMonth.value + 1, 1);
  fetchEvents();
}
function goToday() {
  currentDate.value = new Date();
  fetchEvents();
}

function openDetail(event: CalendarEvent) {
  selectedEvent.value = event;
  dialogVisible.value = true;
}

function closeDialog() {
  dialogVisible.value = false;
  selectedEvent.value = null;
}

function setView(mode: ViewMode) {
  viewMode.value = mode;
  // 切换视图时重新拉取对应范围的数据
  fetchEvents();
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
      <!-- Loading state -->
      <div v-if="loading" class="empty-state">加载中...</div>

      <!-- Agenda (list) view -->
      <template v-else-if="viewMode === 'agenda'">
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
            v-for="ev in group.events"
            :key="ev.id"
            class="task-item"
            :class="{ 'is-completed': ev.completed }"
            @click="openDetail(ev)"
          >
            <div
              class="radio-circle"
              :class="{ checked: ev.completed }"
              @click.stop="toggleComplete(ev)"
            >
              <Check v-if="ev.completed" :size="10" />
            </div>
            <span class="time"
              >{{ timeLabel(ev.startDate) }} - {{ timeLabel(ev.endDate) }}</span
            >
            <span class="task-text">{{ ev.title }}</span>
          </div>
        </div>
        <div v-if="agendaGrouped.length === 0" class="empty-state">
          当前月份暂无事件
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
                    getEventsForDate(day).filter(
                      (e) => e.startDate.getHours() === si,
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
                  :tasks="day !== null ? getEventsForDay(day) : []"
                  :max-display="3"
                  @task-click="openDetail"
                />
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- Event Detail Dialog -->
    <ElDialog
      v-model="dialogVisible"
      :title="selectedEvent?.title || '事件详情'"
      width="420px"
      class="task-dialog custom-form-dialog"
      :close-on-click-modal="true"
      :append-to-body="false"
      align-center
      @close="closeDialog"
    >
      <template v-if="selectedEvent">
        <el-form label-position="top" :model="selectedEvent">
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
            <el-input v-model="selectedEvent.title" placeholder="请输入标题" />
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
              v-model="selectedEvent.description"
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
            <el-checkbox v-model="selectedEvent.completed">完成</el-checkbox>
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
                v-model="selectedEvent.startDate"
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
                v-model="selectedEvent.duration"
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
                v-model="selectedEvent.endDate"
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
                <span class="el-icon-bell" /> 提醒
              </div>
            </template>
            <el-select
              v-model="selectedEvent.reminderBefore"
              style="width: 120px"
            >
              <el-option label="5分钟前" :value="5" />
              <el-option label="10分钟前" :value="10" />
              <el-option label="15分钟前" :value="15" />
              <el-option label="30分钟前" :value="30" />
              <el-option label="1小时前" :value="60" />
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
                v-for="tag in selectedEvent.tags"
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
              @click="deleteEvent"
              >删除</el-button
            >
          </div>
          <div style="display: flex; gap: 8px">
            <el-button
              type="info"
              color="#444"
              @click="saveEvent"
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
