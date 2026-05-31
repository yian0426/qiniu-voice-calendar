import axios from "axios";
import type {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";

// 定义接口返回数据的标准结构
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 在这里添加 token 等头部信息
    const token = localStorage.getItem("token");
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: any) => {
    console.error("Request Error:", error);
    return Promise.reject(error);
  },
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data;

    // 根据后端的错误码进行判断处理
    if (res.code !== 200 && res.code !== 0) {
      console.error("API Error:", res.message || "Error");
      // 处理特定错误码，比如 401 token 失效等
      if (res.code === 401) {
        // 跳转登录页等逻辑
      }
      return Promise.reject(new Error(res.message || "Error"));
    }
    // The interceptor unwraps ApiResponse; request() adjusts the return type
    return res as any;
  },
  (error: any) => {
    const status = error?.response?.status;
    console.error("Response Error:", error);

    // 401/403: token 失效或未登录，清除本地认证状态
    if (status === 401 || status === 403) {
      console.warn(`[request] HTTP ${status} — 清除登录状态`);
      localStorage.removeItem("token");
      // 触发一个自定义事件，让 UI 层响应
      window.dispatchEvent(new CustomEvent("auth:expired"));
    }

    return Promise.reject(error);
  },
);

/**
 * 封装好的请求函数
 * 不直接暴露 axios 实例
 */
export const request = <T = any>(
  config: AxiosRequestConfig,
): Promise<ApiResponse<T>> => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return service.request<any, any>(config);
};

// ── 聊天相关 ──

export interface StreamEvent {
  type: "content" | "status" | "done" | "error" | "tool_result" | "event_data";
  content?: string;
  conversationId?: number;
  title?: string;
  toolName?: string;
  result?: string;
  // event_data fields
  action?: "create" | "update" | "delete" | "query";
  events?: Array<{
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
}

// ── 流式对话 AbortController 管理 ──
let currentAbortController: AbortController | null = null;

/** 取消当前正在进行的流式请求 */
export function abortCurrentStream() {
  if (currentAbortController) {
    console.debug("[stream] 手动取消流式请求");
    currentAbortController.abort();
    currentAbortController = null;
  }
}

/**
 * 流式对话 — 使用 fetch + ReadableStream 消费 SSE
 * 返回 AsyncGenerator，逐个 yield StreamEvent
 *
 * 增强点：
 * - AbortController 支持外部取消
 * - 超时检测（30s 无数据自动断开）
 * - 完整的 done/error 信号保证
 * - 调试日志
 */
export async function* streamChat(
  content: string,
  conversationId?: number,
): AsyncGenerator<StreamEvent> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  const token = localStorage.getItem("token");

  // 创建新的 AbortController，取消之前的（如果有）
  abortCurrentStream();
  const controller = new AbortController();
  currentAbortController = controller;

  const streamId = Math.random().toString(36).slice(2, 8);
  console.debug(
    `[stream:${streamId}] 开始请求, content="${content.slice(0, 30)}..."`,
  );

  let response: Response;
  try {
    response = await fetch(`${baseUrl}/chat/stream`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: token ? `Bearer ${token}` : "",
      },
      body: JSON.stringify({ content, conversationId }),
      signal: controller.signal,
    });
  } catch (e: any) {
    currentAbortController = null;
    if (e.name === "AbortError") {
      console.debug(`[stream:${streamId}] 请求被取消`);
      yield { type: "error", content: "请求已取消" };
      return;
    }
    console.error(`[stream:${streamId}] 请求失败:`, e);
    yield { type: "error", content: `网络请求失败: ${e.message}` };
    return;
  }

  if (!response.ok) {
    currentAbortController = null;
    const errText = await response.text().catch(() => "");
    console.error(`[stream:${streamId}] HTTP ${response.status}: ${errText}`);

    // 401/403: 清除登录状态并通知 UI
    if (response.status === 401 || response.status === 403) {
      console.warn(`[stream] HTTP ${response.status} — 清除登录状态`);
      localStorage.removeItem("token");
      window.dispatchEvent(new CustomEvent("auth:expired"));
      yield { type: "error", content: "登录已过期，请重新登录" };
    } else {
      yield {
        type: "error",
        content: `HTTP ${response.status}: ${response.statusText}`,
      };
    }
    return;
  }

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventCount = 0;
  let lastEventTime = Date.now();
  let receivedDone = false;

  // 超时检测：30 秒无数据则自动断开
  const TIMEOUT_MS = 30_000;
  const timeoutId = setInterval(() => {
    if (Date.now() - lastEventTime > TIMEOUT_MS) {
      console.warn(
        `[stream:${streamId}] 超时 ${TIMEOUT_MS}ms 无数据，自动断开`,
      );
      controller.abort();
    }
  }, 5_000);

  try {
    while (true) {
      let readResult: ReadableStreamReadResult<Uint8Array>;
      try {
        readResult = await reader.read();
      } catch (e: any) {
        if (e.name === "AbortError") {
          console.debug(`[stream:${streamId}] 读取被中断 (abort)`);
          break;
        }
        throw e;
      }

      const { done, value } = readResult;
      if (done) {
        console.debug(
          `[stream:${streamId}] 流读取完毕 (done=true), 共 ${eventCount} 个事件`,
        );
        break;
      }

      lastEventTime = Date.now();
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        // 跳过 SSE event 名称行（如 event:message）
        if (trimmed.startsWith("event:")) continue;

        if (trimmed.startsWith("data:")) {
          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr) continue;

          try {
            const data = JSON.parse(jsonStr) as StreamEvent;
            eventCount++;
            lastEventTime = Date.now();
            console.debug(
              `[stream:${streamId}] 事件 #${eventCount}: type=${data.type}`,
            );

            if (data.type === "done") {
              receivedDone = true;
              console.debug(`[stream:${streamId}] 收到 done 信号`);
            }

            yield data;

            // 收到 done 或 error 后不再继续读取
            if (data.type === "done" || data.type === "error") {
              // 清空 buffer 中的剩余数据
              buffer = "";
              break;
            }
          } catch {
            // 无法解析的行，跳过
            console.debug(
              `[stream:${streamId}] 跳过无法解析的行: ${jsonStr.slice(0, 80)}`,
            );
          }
        }
      }

      // 如果已收到 done/error，退出外层循环
      if (receivedDone) break;
    }

    // 兜底：如果流正常结束但没收到 done 信号，手动发送
    if (!receivedDone) {
      console.warn(`[stream:${streamId}] 流已结束但未收到 done 信号，手动补发`);
      yield { type: "done" };
    }
  } catch (e: any) {
    console.error(`[stream:${streamId}] 流处理异常:`, e);
    yield { type: "error", content: `流处理异常: ${e.message}` };
  } finally {
    clearInterval(timeoutId);
    currentAbortController = null;
    try {
      reader.releaseLock();
    } catch {
      /* ignore */
    }
    console.debug(`[stream:${streamId}] 资源释放完成`);
  }
}

// ── 类型定义 ──

export interface ConversationVO {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface MessageVO {
  id: number;
  role: string;
  content: string;
  createdAt: string;
}

export interface EventVO {
  id: number;
  title: string;
  description: string;
  startTime: string;
  endTime: string;
  duration: string;
  location: string;
  status: number; // 0=未完成, 1=已完成
  participants: string[];
  tags: string[];
  reminderBefore: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateEventRequest {
  title: string;
  description?: string;
  startTime: string;
  endTime: string;
  duration?: string;
  location?: string;
  participants?: string[];
  tags?: string[];
  reminderBefore?: number;
}

export interface UpdateEventRequest extends CreateEventRequest {}

export interface PatchEventRequest {
  title?: string;
  description?: string;
  startTime?: string;
  endTime?: string;
  duration?: string;
  location?: string;
  participants?: string[];
  tags?: string[];
  reminderBefore?: number;
}

export interface TagVO {
  id: number;
  name: string;
  color: string;
  eventCount: number;
}

export interface PageData<T> {
  total: number;
  page: number;
  size: number;
  pages: number;
  records: T[];
}

export interface LoginResponse {
  userId: number;
  username: string;
  token: string;
}

export interface ProfileResponse {
  id: number;
  username: string;
  email: string;
  avatarUrl: string;
  createdAt: string;
}

// ── API 端点定义 ──

export const api = {
  // 聊天流
  streamChat,

  // ── 认证模块 ──
  register: (data: { username: string; password: string; email?: string }) =>
    request<LoginResponse>({ url: "/auth/register", method: "POST", data }),
  login: (data: { username: string; password: string }) =>
    request<LoginResponse>({ url: "/auth/login", method: "POST", data }),
  getProfile: () =>
    request<ProfileResponse>({ url: "/auth/profile", method: "GET" }),
  updateProfile: (data: { email?: string; avatarUrl?: string }) =>
    request<void>({ url: "/auth/profile", method: "PUT", data }),

  // ── 事件模块 ──
  listEvents: (params?: {
    startDate?: string;
    endDate?: string;
    status?: number;
    tag?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }) => request<PageData<EventVO>>({ url: "/events", method: "GET", params }),
  getEvent: (id: number) =>
    request<EventVO>({ url: `/events/${id}`, method: "GET" }),
  createEvent: (data: CreateEventRequest) =>
    request<EventVO>({ url: "/events", method: "POST", data }),
  updateEvent: (id: number, data: UpdateEventRequest) =>
    request<EventVO>({ url: `/events/${id}`, method: "PUT", data }),
  patchEvent: (id: number, data: PatchEventRequest) =>
    request<EventVO>({ url: `/events/${id}`, method: "PATCH", data }),
  deleteEvent: (id: number) =>
    request<void>({ url: `/events/${id}`, method: "DELETE" }),
  toggleEventStatus: (id: number, status: number) =>
    request<EventVO>({
      url: `/events/${id}/status`,
      method: "PATCH",
      data: { status },
    }),

  // ── 标签模块 ──
  listTags: () => request<TagVO[]>({ url: "/tags", method: "GET" }),
  createTag: (data: { name: string; color?: string }) =>
    request<TagVO>({ url: "/tags", method: "POST", data }),
  updateTag: (id: number, data: { name?: string; color?: string }) =>
    request<TagVO>({ url: `/tags/${id}`, method: "PUT", data }),
  deleteTag: (id: number) =>
    request<void>({ url: `/tags/${id}`, method: "DELETE" }),

  // ── 对话模块 ──
  getConversations: () =>
    request<ConversationVO[]>({ url: "/conversations", method: "GET" }),
  getMessages: (conversationId: number) =>
    request<MessageVO[]>({
      url: `/conversations/${conversationId}/messages`,
      method: "GET",
    }),
  deleteConversation: (conversationId: number) =>
    request<void>({
      url: `/conversations/${conversationId}`,
      method: "DELETE",
    }),
};
