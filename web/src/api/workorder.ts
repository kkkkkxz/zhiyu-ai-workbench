import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface WorkOrder {
  id: string;
  tenantId: string;
  orderNo: string;
  title: string;
  type: string;
  priority: string;
  status: string;
  customerId?: string;
  reporterId: string;
  assigneeId?: string;
  deptId?: string;
  description?: string;
  aiCategory?: string;
  aiSuggestion?: string;
  aiAssignedReason?: string;
  aiSimilarIds?: string;
  aiResolutionHint?: string;
  slaDeadline?: string;
  resolvedAt?: string;
  closedAt?: string;
  createTime: string;
}

export interface WorkOrderComment {
  id: string;
  orderId: string;
  userId: string;
  content: string;
  isInternal: number;
  createTime: string;
}

export const workOrderApi = {
  list: (page = 1, size = 20, status?: string, priority?: string, keyword?: string) =>
    http.get<PageResult<WorkOrder>>('/work-orders', { params: { page, size, status, priority, keyword } }),
  get: (id: string) => http.get<WorkOrder>(`/work-orders/${id}`),
  create: (data: Partial<WorkOrder>) => http.post<WorkOrder>('/work-orders', data),
  update: (id: string, data: Partial<WorkOrder>) => http.put<WorkOrder>(`/work-orders/${id}`, data),
  assign: (id: string, assigneeId: string) =>
    http.put<void>(`/work-orders/${id}/assign`, null, { params: { assigneeId } }),
  changeStatus: (id: string, status: string) =>
    http.put<void>(`/work-orders/${id}/status`, null, { params: { status } }),

  listComments: (orderId: string) => http.get<WorkOrderComment[]>(`/work-orders/${orderId}/comments`),
  addComment: (orderId: string, data: { content: string; isInternal?: number }) =>
    http.post<WorkOrderComment>(`/work-orders/${orderId}/comments`, data),
};
