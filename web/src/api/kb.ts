import { http } from '@/lib/api';
import type { CreateKbRequest, KnowledgeBase } from '@/types/api';

export const kbApi = {
  list: () => http.get<KnowledgeBase[]>('/kb'),
  get: (id: string) => http.get<KnowledgeBase>(`/kb/${id}`),
  create: (req: CreateKbRequest) => http.post<KnowledgeBase>('/kb', req),
  remove: (id: string) => http.del<void>(`/kb/${id}`),
};
