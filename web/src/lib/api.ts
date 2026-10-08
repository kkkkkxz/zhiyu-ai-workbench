import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import JSONbig from 'json-bigint';
import { toast } from 'sonner';

import { useAuthStore } from '@/stores/auth';
import type { ApiResult } from '@/types/api';

/**
 * Long ID 序列化策略：
 * - 后端把雪花 Long 直接当 number 序列化，会超出 JS Number.MAX_SAFE_INTEGER。
 * - 这里在 axios 阶段使用 json-bigint 解析，>2^53 的整数自动转成字符串。
 * - 同时配置请求侧的 stringify，兼容把字符串 ID 直接传回去（后端能解析）。
 */
const JSONbigString = JSONbig({ storeAsString: true });

const API_BASE = '/api';

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 30_000,
  withCredentials: false,
  // 自定义 JSON 解析：保留大整数为字符串
  transformResponse: [
    (data, headers) => {
      const ct = (headers?.['content-type'] || headers?.['Content-Type'] || '') as string;
      if (typeof data === 'string' && data.length > 0 && ct.includes('application/json')) {
        try {
          return JSONbigString.parse(data);
        } catch {
          return data;
        }
      }
      return data;
    },
  ],
});

// ----- 请求拦截：注入 Authorization -----
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * 是否跳过自动 toast（对接调用方的精细化错误展示，如登录页根据 code=3003 自己控制 UI）。
 * 用法：在 axios config 上挂 `skipErrorToast: true`，例如：
 *   http.post('/auth/login', body, { skipErrorToast: true } as any)
 * 拦截器读取这个标志决定要不要弹 toast，但**仍然 reject**——调用方用 try/catch 自己处理。
 */
function shouldSkipErrorToast(config?: AxiosRequestConfig | InternalAxiosRequestConfig): boolean {
  return Boolean((config as any)?.skipErrorToast);
}

function shouldSkipAuthRedirect(config?: AxiosRequestConfig | InternalAxiosRequestConfig): boolean {
  return Boolean((config as any)?.skipAuthRedirect);
}

// ----- 响应拦截：统一处理 Result<T> -----
api.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResult<unknown> | unknown;
    // 业务包装统一展开为 data；非包装格式（例如 SSE）原样返回
    if (body && typeof body === 'object' && 'code' in (body as ApiResult<unknown>)) {
      const r = body as ApiResult<unknown>;
      if (r.code === 0) {
        // 把 data 提升到 resp.data，保留原 result 在 resp.config.metadata
        (resp as any).result = r;
        resp.data = r.data as any;
        return resp;
      }
      // 业务失败：HTTP 200 + 非 0 业务码（GlobalExceptionHandler.handleBusiness 的标准产物）。
      // 历史上这里只 reject 不 toast，导致很多页面调用方忘了 catch，用户什么提示也看不到——
      // 例如「无权访问该知识库」这种典型 BusinessException 静默失败。
      // 现在统一 toast.error，调用方仍会拿到 reject，可以在 catch 里 setState 等做精细处理；
      // 想要完全自管 UI 的接口（如登录的 3003 引导）可以在 axios config 设 skipErrorToast=true。
      const message = r.message || `业务错误 code=${r.code}`;
      if (!shouldSkipErrorToast(resp.config)) {
        toast.error(message);
      }
      const err = new Error(message) as Error & {
        code?: number;
        result?: ApiResult<unknown>;
      };
      err.code = r.code;
      err.result = r;
      return Promise.reject(err);
    }
    return resp;
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const skipToast = shouldSkipErrorToast(error.config);
    const skipAuthRedirect = shouldSkipAuthRedirect(error.config);
    if (error.response) {
      const status = error.response.status;
      const r = error.response.data;
      const msg = r?.message || error.message || `HTTP ${status}`;

      if (status === 401) {
        if (!skipAuthRedirect) useAuthStore.getState().clear();
        if (!skipToast) toast.error('登录已过期，请重新登录');
        // 用 hash 跳避免依赖 react-router 实例
        if (!skipAuthRedirect && !window.location.pathname.startsWith('/login')) {
          const next = encodeURIComponent(window.location.pathname + window.location.search);
          window.location.href = `/login?next=${next}`;
        }
      } else if (status === 403) {
        if (!skipToast) {
          const url = error.config?.url || '未知接口';
          toast.error(`无权访问该资源：${url}`);
        }
      } else if (status >= 500) {
        if (!skipToast) toast.error(`服务器异常：${msg}`);
      } else {
        if (!skipToast) toast.error(msg);
      }
      const err = new Error(msg) as Error & {
        status?: number;
        result?: ApiResult<unknown>;
      };
      err.status = status;
      err.result = r;
      return Promise.reject(err);
    }
    if (error.code === 'ECONNABORTED') {
      if (!skipToast) toast.error('请求超时');
    } else {
      if (!skipToast) toast.error(error.message || '网络异常');
    }
    return Promise.reject(error);
  },
);

/**
 * 统一从已解包的响应里取出 data。
 */
export async function unwrap<T>(p: Promise<{ data: T }>): Promise<T> {
  const r = await p;
  return r.data;
}

/** axios get/post 简便方法（已自动解包 data） */
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    unwrap<T>(api.get<T>(url, config) as any),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(api.post<T>(url, body, config) as any),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(api.put<T>(url, body, config) as any),
  del: <T>(url: string, config?: AxiosRequestConfig) =>
    unwrap<T>(api.delete<T>(url, config) as any),
};

export const API_BASE_URL = API_BASE;
