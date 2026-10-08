import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface AiActionExecutionLog {
  id: string;
  actionCode: string;
  actionName?: string;
  phase: string;
  riskLevel: string;
  userInput?: string;
  status: string;
  errorMessage?: string;
  createTime: string;
}

export const aiActionLogApi = {
  list: (page = 1, size = 20) =>
    http.get<PageResult<AiActionExecutionLog>>('/ai-action/logs', { params: { page, size } }),
};
