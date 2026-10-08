import { http } from '@/lib/api';
import type {
  VoiceKeyword,
  VoiceKeywordBatchUpsertRequest,
  VoiceKeywordUpsertRequest,
} from '@/types/api';

export const voiceKeywordApi = {
  list: (params?: { q?: string; limit?: number }) =>
    http.get<VoiceKeyword[]>('/voice/keywords', { params }),

  extract: (text: string, limit = 8) =>
    http.post<string[]>('/voice/keywords/extract', { text, limit }, { skipErrorToast: true } as any),

  upsert: (req: VoiceKeywordUpsertRequest) =>
    http.post<VoiceKeyword>('/voice/keywords', req),

  batch: (req: VoiceKeywordBatchUpsertRequest) =>
    http.post<VoiceKeyword[]>('/voice/keywords/batch', req, { skipErrorToast: true } as any),

  remove: (id: string) => http.del<void>(`/voice/keywords/${id}`),
};
