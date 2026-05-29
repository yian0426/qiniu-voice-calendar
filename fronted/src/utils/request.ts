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
    return res;
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
  return service.request<any, ApiResponse<T>>(config);
};

// 预留的 API 请求示例
export const api = {
  // 获取数据示例
  // getUserInfo: () => request<UserInfo>({ url: '/user/info', method: 'GET' }),
};
