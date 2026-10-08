import { http } from '@/lib/api';

export interface Customer {
  id: string;
  tenantId: string;
  name: string;
  shortName?: string;
  type: string;
  industry?: string;
  scale?: string;
  source?: string;
  ownerId?: string;
  status: string;
  grade?: string;
  aiScore?: number;
  lifecycleStage?: string;
  aiNextAction?: string;
  aiValuePredict?: number;
  aiScoredAt?: string;
  lastFollowAt?: string;
  phone?: string;
  email?: string;
  address?: string;
  remark?: string;
  createTime: string;
}

export interface Contact {
  id: string;
  customerId: string;
  name: string;
  position?: string;
  phone?: string;
  email?: string;
  isPrimary: number;
}

export interface FollowUp {
  id: string;
  customerId: string;
  userId: string;
  type: string;
  content: string;
  aiSummary?: string;
  nextPlan?: string;
  nextTime?: string;
  createTime: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export const crmApi = {
  listCustomers: (page = 1, size = 20, status?: string, keyword?: string) =>
    http.get<PageResult<Customer>>('/crm/customers', { params: { page, size, status, keyword } }),
  getCustomer: (id: string) => http.get<Customer>(`/crm/customers/${id}`),
  createCustomer: (data: Partial<Customer>) => http.post<Customer>('/crm/customers', data),
  updateCustomer: (id: string, data: Partial<Customer>) => http.put<Customer>(`/crm/customers/${id}`, data),
  deleteCustomer: (id: string) => http.del<void>(`/crm/customers/${id}`),

  listContacts: (customerId: string) => http.get<Contact[]>(`/crm/customers/${customerId}/contacts`),
  createContact: (customerId: string, data: Partial<Contact>) =>
    http.post<Contact>(`/crm/customers/${customerId}/contacts`, data),
  deleteContact: (contactId: string) => http.del<void>(`/crm/contacts/${contactId}`),

  listFollowUps: (customerId: string, page = 1) =>
    http.get<PageResult<FollowUp>>(`/crm/customers/${customerId}/follow-ups`, { params: { page } }),
  createFollowUp: (customerId: string, data: Partial<FollowUp>) =>
    http.post<FollowUp>(`/crm/customers/${customerId}/follow-ups`, data),
};
