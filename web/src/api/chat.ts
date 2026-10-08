import { http } from '@/lib/api';
import { fetchSse, type SseHandlers } from '@/lib/sse';
import type {
  AiMessage,
  AiSession,
  CompletionRequest,
  CreateSessionRequest,
} from '@/types/api';

export const chatApi = {
  listSessions: () => http.get<AiSession[]>('/chat/sessions'),

  getSession: (id: string) => http.get<AiSession>(`/chat/sessions/${id}`),

  listMessages: (id: string) =>
    http.get<AiMessage[]>(`/chat/sessions/${id}/messages`),

  createSession: (req: CreateSessionRequest) =>
    http.post<AiSession>('/chat/sessions', req),

  removeSession: (id: string) => http.del<void>(`/chat/sessions/${id}`),

  /** SSE 流式问答 */
  streamCompletion: (
    sessionId: string,
    req: CompletionRequest,
    handlers: SseHandlers,
    signal?: AbortSignal,
  ) =>
    fetchSse(
      `/chat/sessions/${sessionId}/completions/stream`,
      { method: 'POST', body: req, signal },
      handlers,
    ),
};
