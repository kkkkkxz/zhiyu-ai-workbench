import { http } from '@/lib/api';
import type { PageResult } from './crm';

export type ExportBizType = 'customer' | 'contract' | 'work_order' | 'session';
export type ExportStatus = 'pending' | 'processing' | 'completed' | 'failed';

export interface ExportTask {
  id: string;
  tenantId: string;
  userId: string;
  bizType: ExportBizType | string;
  bizId?: string;
  format: 'pdf' | 'docx' | string;
  status: ExportStatus | string;
  fileKey?: string;
  fileName?: string;
  errorMsg?: string;
  createTime: string;
  finishTime?: string;
}

export interface ExportDownloadResponse {
  url: string;
  expiresIn: number;
}

export const exportApi = {
  create: (bizType: ExportBizType, bizId?: string | number, format: 'pdf' | 'docx' = 'pdf') =>
    http.post<ExportTask>('/export', null, { params: { bizType, bizId, format } }),
  get: (id: string) => http.get<ExportTask>(`/export/${id}`),
  downloadUrl: (id: string) => http.get<ExportDownloadResponse>(`/export/${id}/download-url`),
  myTasks: (page = 1, size = 20) =>
    http.get<PageResult<ExportTask>>('/export', { params: { page, size } }),
};
