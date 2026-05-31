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
    console.error("Response Error:", error);
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
  type: "content" | "status" | "done" | "error";
  content?: string;
  conversationId?: number;
  title?: string;
}

/**
 * 流式对话 — 使用 fetch + ReadableStream 消费 SSE
 * 返回 AsyncGenerator，逐个 yield StreamEvent
 */
export async function* streamChat(
  content: string,
  conversationId?: number,
): AsyncGenerator<StreamEvent> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
  const token = localStorage.getItem("token");

  const response = await fetch(`${baseUrl}/chat/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: token ? `Bearer ${token}` : "",
    },
    body: JSON.stringify({ content, conversationId }),
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const line of lines) {
      if (line.startsWith("data:")) {
        try {
          const data = JSON.parse(line.slice(5).trim());
          yield data as StreamEvent;
        } catch {
          // skip unparseable lines
        }
      }
    }
  }
}

// API 端点定义
export const api = {
  // 聊天流
  streamChat,
  // 对话列表
  getConversations: () =>
    request<ConversationVO[]>({ url: "/conversations", method: "GET" }),
  // 对话消息
  getMessages: (conversationId: number) =>
    request<MessageVO[]>({
      url: `/conversations/${conversationId}/messages`,
      method: "GET",
    }),
  // 删除对话
  deleteConversation: (conversationId: number) =>
    request<void>({
      url: `/conversations/${conversationId}`,
      method: "DELETE",
    }),
};

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
