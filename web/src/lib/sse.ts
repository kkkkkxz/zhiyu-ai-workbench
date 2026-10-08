import { useAuthStore } from '@/stores/auth';
import { API_BASE_URL } from './api';
import { toast } from 'sonner';

export interface SseHandlers {
  onEvent?: (event: string, data: string) => void;
  onError?: (err: unknown) => void;
  onDone?: () => void;
}

/**
 * 用 fetch + ReadableStream 解析 SSE，相比原生 EventSource：
 * - 支持 POST + JSON Body
 * - 支持自定义 Authorization header
 * - 支持 AbortController 主动取消
 */
export async function fetchSse(
  path: string,
  init: {
    method?: 'GET' | 'POST';
    body?: unknown;
    signal?: AbortSignal;
    headers?: Record<string, string>;
  },
  handlers: SseHandlers,
): Promise<void> {
  const token = useAuthStore.getState().accessToken;
  const url = path.startsWith('http') ? path : `${API_BASE_URL}${path}`;

  let resp: Response;
  try {
    resp = await fetch(url, {
      method: init.method ?? 'POST',
      signal: init.signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init.headers ?? {}),
      },
      body: init.body == null ? undefined : JSON.stringify(init.body),
    });
  } catch (e) {
    handlers.onError?.(e);
    return;
  }

  if (!resp.ok || !resp.body) {
    if (resp.status === 401) {
      useAuthStore.getState().clear();
      toast.error('登录已过期，请重新登录');
      if (!window.location.pathname.startsWith('/login')) {
        const next = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `/login?next=${next}`;
      }
    }
    handlers.onError?.(new Error(`SSE HTTP ${resp.status}`));
    return;
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let currentEvent = 'message';
  let currentData: string[] = [];

  const flush = () => {
    if (currentData.length === 0 && currentEvent === 'message') return;
    const data = currentData.join('\n');
    handlers.onEvent?.(currentEvent, data);
    currentEvent = 'message';
    currentData = [];
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // 按行解析；事件之间用空行分隔
      let nl: number;
      while ((nl = buffer.indexOf('\n')) >= 0) {
        let line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        if (line.endsWith('\r')) line = line.slice(0, -1);

        if (line === '') {
          flush();
          continue;
        }
        // 注释行
        if (line.startsWith(':')) continue;

        const idx = line.indexOf(':');
        const field = idx === -1 ? line : line.slice(0, idx);
        // SSE 规范：value 可选前导空格
        const value =
          idx === -1 ? '' : line.slice(idx + 1).replace(/^ /, '');

        if (field === 'event') currentEvent = value || 'message';
        else if (field === 'data') currentData.push(value);
        // 其它字段（id / retry）按需要扩展
      }
    }
    // 流结束前最后的事件
    flush();
    handlers.onDone?.();
  } catch (e) {
    if ((e as any)?.name === 'AbortError') {
      handlers.onDone?.();
    } else {
      handlers.onError?.(e);
    }
  }
}
