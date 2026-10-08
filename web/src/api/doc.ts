import { api, http } from '@/lib/api';
import type {
  KnowledgeDocument,
  UploadTextRequest,
} from '@/types/api';

export const docApi = {
  list: (kbId: string) =>
    http.get<KnowledgeDocument[]>(`/kb/${kbId}/docs`),

  uploadText: (kbId: string, req: UploadTextRequest) =>
    http.post<KnowledgeDocument>(`/kb/${kbId}/docs/upload-text`, req),

  uploadFile: async (
    kbId: string,
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<KnowledgeDocument> => {
    const fd = new FormData();
    fd.append('file', file);
    const resp = await api.post<KnowledgeDocument>(
      `/kb/${kbId}/docs/upload`,
      fd,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => {
          if (e.total && onProgress) {
            onProgress(Math.round((e.loaded * 100) / e.total));
          }
        },
      },
    );
    // 拦截器已展开 data
    return resp.data as unknown as KnowledgeDocument;
  },

  remove: (kbId: string, docId: string) =>
    http.del<void>(`/kb/${kbId}/docs/${docId}`),
};
