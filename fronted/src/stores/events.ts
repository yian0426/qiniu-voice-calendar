import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, type EventVO } from "@/utils/request";

/* ── 日程事件类型 ── */
export interface CalendarEvent {
  id: number;
  title: string;
  description: string;
  completed: boolean;
  startDate: Date;
  endDate: Date;
  duration: string;
  tags: string[];
  participants: string[];
  location: string;
  reminderBefore: number;
  createdAt: Date;
  /** 乐观更新标记：true 表示此事件尚未被后端确认 */
  _optimistic?: boolean;
  /** 乐观更新时的临时 ID */
  _tempId?: string;
}

/* ── EventVO → CalendarEvent 转换 ── */
function fromEventVO(ev: EventVO): CalendarEvent {
  return {
    id: ev.id,
    title: ev.title,
    description: ev.description || "",
    completed: ev.status === 1,
    startDate: new Date(ev.startTime),
    endDate: new Date(ev.endTime),
    duration: ev.duration || "",
    tags: ev.tags || [],
    participants: ev.participants || [],
    location: ev.location || "",
    reminderBefore: ev.reminderBefore || 0,
    createdAt: new Date(ev.createdAt),
  };
}

export const useEventStore = defineStore("events", () => {
  /* ── 核心状态：Map 实现 O(1) 增删改查 ── */
  const eventsMap = ref<Map<number, CalendarEvent>>(new Map());
  const loading = ref(false);
  const fetchError = ref<string | null>(null);

  /* ── 派生数据：对外暴露数组格式（保持组件兼容） ── */
  const events = computed(() =>
    Array.from(eventsMap.value.values()).sort(
      (a, b) => a.startDate.getTime() - b.startDate.getTime(),
    ),
  );
  const eventCount = computed(() => eventsMap.value.size);

  /* ── 按日期获取事件 ── */
  function getEventsByDate(dateStr: string) {
    return events.value.filter(
      (e) => e.startDate.toISOString().slice(0, 10) === dateStr,
    );
  }

  /* ── 防抖控制 ── */
  let fetchTimer: ReturnType<typeof setTimeout> | null = null;

  /* ── 拉取事件（增量合并，不删除已有数据） ── */
  async function fetchEvents(startDate: string, endDate: string) {
    loading.value = true;
    fetchError.value = null;
    try {
      const res = await api.listEvents({
        startDate,
        endDate,
        page: 1,
        size: 500,
      });
      if (res.code === 200 && res.data) {
        const newEvents = res.data.records.map(fromEventVO);
        // 增量合并：只添加/更新，不删除已有
        for (const ev of newEvents) {
          eventsMap.value.set(ev.id, ev);
        }
      }
    } catch (e: any) {
      fetchError.value = e?.message || "加载失败";
      throw e;
    } finally {
      loading.value = false;
    }
  }

  /** 防抖版拉取 */
  function debouncedFetch(startDate: string, endDate: string, delay = 300) {
    if (fetchTimer) clearTimeout(fetchTimer);
    fetchTimer = setTimeout(() => fetchEvents(startDate, endDate), delay);
  }

  /* ── 乐观更新：添加事件 ── */
  function addOptimistic(
    eventData: Partial<CalendarEvent> & {
      title: string;
      startDate: Date;
      endDate: Date;
    },
  ) {
    const tempId = Date.now();
    const optimisticEvent: CalendarEvent = {
      id: -tempId, // 临时负数 ID
      title: eventData.title,
      description: eventData.description || "",
      completed: false,
      startDate: eventData.startDate,
      endDate: eventData.endDate,
      duration: eventData.duration || "",
      tags: eventData.tags || [],
      participants: eventData.participants || [],
      location: eventData.location || "",
      reminderBefore: eventData.reminderBefore || 0,
      createdAt: new Date(),
      _optimistic: true,
      _tempId: String(tempId),
    };

    eventsMap.value.set(optimisticEvent.id, optimisticEvent);
    return String(tempId);
  }

  /* ── 乐观更新：替换临时数据为真实数据 ── */
  function confirmOptimistic(tempId: string, realEvent: CalendarEvent) {
    const tempIdNum = -Number(tempId);
    eventsMap.value.delete(tempIdNum);
    eventsMap.value.set(realEvent.id, { ...realEvent, _optimistic: false });
  }

  /* ── 乐观更新：回滚移除临时数据 ── */
  function rollbackOptimistic(tempId: string) {
    eventsMap.value.delete(-Number(tempId));
  }

  /* ── 直接操作：添加事件（已确认） ── */
  function addEvent(event: CalendarEvent) {
    eventsMap.value.set(event.id, event);
  }

  /* ── 直接操作：更新事件 ── */
  function updateEvent(id: number, patch: Partial<CalendarEvent>) {
    const existing = eventsMap.value.get(id);
    if (existing) {
      eventsMap.value.set(id, { ...existing, ...patch });
    }
  }

  /* ── 直接操作：删除事件 ── */
  function removeEvent(id: number) {
    eventsMap.value.delete(id);
  }

  /* ── 从 AI tool_result 批量应用（增量） ── */
  function applyFromAI(data: {
    action: "create" | "update" | "delete" | "query";
    events: Array<{
      id: number;
      title?: string;
      description?: string;
      startTime?: string;
      endTime?: string;
      duration?: string;
      location?: string;
      status?: number;
      participants?: string[];
      tags?: string[];
      reminderBefore?: number;
    }>;
  }) {
    for (const ev of data.events) {
      if (data.action === "delete") {
        eventsMap.value.delete(ev.id);
      } else if (data.action === "create" || data.action === "update") {
        const calendarEvent: CalendarEvent = {
          id: ev.id,
          title: ev.title || "未命名事件",
          description: ev.description || "",
          completed: ev.status === 1,
          startDate: ev.startTime ? new Date(ev.startTime) : new Date(),
          endDate: ev.endTime ? new Date(ev.endTime) : new Date(),
          duration: ev.duration || "",
          tags: ev.tags || [],
          participants: ev.participants || [],
          location: ev.location || "",
          reminderBefore: ev.reminderBefore || 0,
          createdAt: new Date(),
          _optimistic: false,
        };
        eventsMap.value.set(ev.id, calendarEvent);
      }
    }
  }

  return {
    // 状态
    events,
    loading,
    fetchError,
    eventCount,
    // 查询
    getEventsByDate,
    // 拉取
    fetchEvents,
    debouncedFetch,
    // 乐观更新
    addOptimistic,
    confirmOptimistic,
    rollbackOptimistic,
    // 直接操作
    addEvent,
    updateEvent,
    removeEvent,
    // AI 集成
    applyFromAI,
  };
});
