import { api, http } from '@/lib/api';

export interface BizAttachment {
  id: string;
  tenantId: string;
  bizType: string;
  bizId: string;
  category: string;
  fileName: string;
  fileKey: string;
  fileType?: string;
  contentType?: string;
  fileSize: number;
  fileHash: string;
  versionNo: number;
  source: string;
  visibility: string;
  uploaderId: string;
  createTime: string;
}

export interface AttachmentDownloadUrl {
  url: string;
  expiresIn: number;
}

export interface AttachmentAnalysis {
  id: string;
  attachmentId: string;
  bizType: string;
  bizId: string;
  fileName: string;
  status: 'PENDING' | 'PARSING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH';
  documentType?: string;
  summary?: string;
  keyFields?: Record<string, unknown>;
  missingFields?: string[];
  risks?: string[];
  suggestions?: string[];
  nextActions?: string[];
  parseError?: string;
  parsedTextChars?: number;
  analyzedBy?: string;
  analyzedAt?: string;
  createTime?: string;
  updateTime?: string;
}

export const attachmentApi = {
  list: (bizType: string, bizId: string) =>
    http.get<BizAttachment[]>(`/attachments/${bizType}/${bizId}`),

  listAnalysis: (bizType: string, bizId: string) =>
    http.get<AttachmentAnalysis[]>(`/attachments/${bizType}/${bizId}/analysis`),

  upload: async (bizType: string, bizId: string, file: File, category = 'general') => {
    const fd = new FormData();
    fd.append('file', file);
    const resp = await api.post(
      `/attachments/${bizType}/${bizId}/upload`,
      fd,
      {
        params: { category },
        headers: { 'Content-Type': 'multipart/form-data' },
      },
    );
    return resp.data as BizAttachment;
  },

  downloadUrl: (attachmentId: string) =>
    http.get<AttachmentDownloadUrl>(`/attachments/${attachmentId}/download-url`),

  analysis: (attachmentId: string) =>
    http.get<AttachmentAnalysis | null>(`/attachments/${attachmentId}/analysis`),

  retryAnalysis: (attachmentId: string) =>
    http.post<AttachmentAnalysis>(`/attachments/${attachmentId}/analysis/retry`),

  delete: (attachmentId: string) =>
    http.del<void>(`/attachments/${attachmentId}`),
};
