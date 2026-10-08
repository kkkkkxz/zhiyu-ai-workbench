import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface Dispatch {
  id: string;
  tenantId: string;
  title: string;
  description?: string;
  type: string;
  priority: string;
  dispatcherId: string;
  deptId?: string;
  dueDate?: string;
  status: string;
  completionNote?: string;
  completedAt?: string;
  createTime: string;
}

export interface DispatchAssignee {
  id: string;
  dispatchId: string;
  userId: string;
  status: string;
  progress: number;
  note?: string;
  completedAt?: string;
}

export interface DispatchDeptOption {
  id: string;
  name: string;
  code?: string;
}

export interface DispatchUserOption {
  id: string;
  deptId: string;
  username: string;
  nickname?: string;
}

export const dispatchApi = {
  mySent: (page = 1, size = 20) =>
    http.get<PageResult<Dispatch>>('/dispatch/sent', { params: { page, size } }),
  myReceived: (page = 1, size = 20) =>
    http.get<PageResult<Dispatch>>('/dispatch/received', { params: { page, size } }),
  assignableDepartments: () =>
    http.get<DispatchDeptOption[]>('/dispatch/assignable-departments'),
  assignableUsers: (deptId?: string) =>
    http.get<DispatchUserOption[]>('/dispatch/assignable-users', { params: { deptId } }),
  get: (id: string) => http.get<Dispatch>(`/dispatch/${id}`),
  create: (data: { title: string; description?: string; type?: string; priority?: string; assigneeIds: string[] }) =>
    http.post<Dispatch>('/dispatch', data),
  updateProgress: (dispatchId: string, assigneeId: string, progress: number, note?: string) =>
    http.put<void>(`/dispatch/${dispatchId}/progress`, null, { params: { assigneeId, progress, note } }),
  complete: (id: string, note?: string) =>
    http.put<void>(`/dispatch/${id}/complete`, null, { params: { note } }),
  listAssignees: (dispatchId: string) =>
    http.get<DispatchAssignee[]>(`/dispatch/${dispatchId}/assignees`),
};
