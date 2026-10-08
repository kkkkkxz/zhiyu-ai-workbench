import { http } from '@/lib/api';

export interface DispatchPreview {
  targetUserId: string;
  targetUserName: string;
  targetDeptName?: string;
  taskTitle: string;
  taskDescription?: string;
  priority: string;
  deadline?: string;
  confirmMessage: string;
}

export interface DispatchResult {
  id: string;
  title: string;
  status: string;
}

export const aiDispatchApi = {
  preview: (input: string) =>
    http.post<DispatchPreview>('/ai-dispatch/preview', { input }),
  confirm: (preview: DispatchPreview) =>
    http.post<DispatchResult>('/ai-dispatch/confirm', preview),
};
