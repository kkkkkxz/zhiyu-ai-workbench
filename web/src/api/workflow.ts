import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface WfDefinition {
  id: string;
  code: string;
  name: string;
  nodesJson: string;
  version: number;
  status: number;
}

export interface WfInstance {
  id: string;
  definitionId: string;
  bizType: string;
  bizId: string;
  title: string;
  initiatorId: string;
  currentNode?: string;
  status: string;
  formData?: string;
  createTime: string;
  finishTime?: string;
}

export interface WfTask {
  id: string;
  instanceId: string;
  nodeCode: string;
  nodeName?: string;
  assigneeId: string;
  action?: string;
  comment?: string;
  status: string;
  createTime: string;
  finishTime?: string;
}

export const workflowApi = {
  listDefinitions: () => http.get<WfDefinition[]>('/workflow/definitions'),
  createDefinition: (data: Partial<WfDefinition>) => http.post<WfDefinition>('/workflow/definitions', data),
  startInstance: (data: { definitionId: string; bizType: string; bizId: string; title: string; formData?: string }) =>
    http.post<WfInstance>('/workflow/instances', data),
  getInstance: (id: string) => http.get<WfInstance>(`/workflow/instances/${id}`),
  myInitiated: (page = 1, size = 20) =>
    http.get<PageResult<WfInstance>>('/workflow/my-initiated', { params: { page, size } }),
  myTasks: (page = 1, size = 20) =>
    http.get<PageResult<WfTask>>('/workflow/my-tasks', { params: { page, size } }),
  approve: (taskId: string, comment?: string) =>
    http.post<void>(`/workflow/tasks/${taskId}/approve`, { comment }),
  reject: (taskId: string, comment?: string) =>
    http.post<void>(`/workflow/tasks/${taskId}/reject`, { comment }),
};
