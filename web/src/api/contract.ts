import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface Contract {
  id: string;
  tenantId: string;
  contractNo: string;
  title: string;
  customerId?: string;
  type: string;
  amount: number;
  currency: string;
  signDate?: string;
  startDate?: string;
  endDate?: string;
  status: string;
  ownerId?: string;
  fileKey?: string;
  aiRiskScore?: number;
  aiRiskNote?: string;
  remark?: string;
  createTime: string;
}

export interface ContractMilestone {
  id: string;
  contractId: string;
  name: string;
  amount?: number;
  dueDate?: string;
  status: string;
}

export const contractApi = {
  list: (page = 1, size = 20, status?: string, keyword?: string) =>
    http.get<PageResult<Contract>>('/contracts', { params: { page, size, status, keyword } }),
  get: (id: string) => http.get<Contract>(`/contracts/${id}`),
  create: (data: Partial<Contract>) => http.post<Contract>('/contracts', data),
  update: (id: string, data: Partial<Contract>) => http.put<Contract>(`/contracts/${id}`, data),
  delete: (id: string) => http.del<void>(`/contracts/${id}`),

  listMilestones: (contractId: string) =>
    http.get<ContractMilestone[]>(`/contracts/${contractId}/milestones`),
  createMilestone: (contractId: string, data: Partial<ContractMilestone>) =>
    http.post<ContractMilestone>(`/contracts/${contractId}/milestones`, data),
  updateMilestoneStatus: (milestoneId: string, status: string) =>
    http.put<void>(`/contracts/milestones/${milestoneId}/status`, null, { params: { status } }),
};
