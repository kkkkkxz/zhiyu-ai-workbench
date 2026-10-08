import { http } from '@/lib/api';
import type { Customer } from './crm';
import type { Contract } from './contract';
import type { Schedule, TaskReminder } from './schedule';
import type { WorkOrder } from './workorder';

export interface CustomerScoreLog {
  id: string;
  customerId: string;
  oldGrade?: string;
  newGrade?: string;
  oldScore?: number;
  newScore?: number;
  oldStage?: string;
  newStage?: string;
  reason?: string;
  nextAction?: string;
  createTime: string;
}

export interface PaymentReminder {
  id: string;
  contractId: string;
  milestoneId?: string;
  customerId?: string;
  title: string;
  amount?: number;
  dueDate: string;
  priority: string;
  aiRiskLevel?: string;
  aiSuggestion?: string;
  status: string;
}

export interface ApprovalSuggestion {
  taskId?: string;
  suggestion: string;
  riskLevel: string;
  waitHours?: number;
}

export const aiEnhancementApi = {
  scoreCustomer: (customerId: string) => http.post<Customer>(`/crm/ai/customers/${customerId}/score`),
  batchScoreCustomers: () => http.post<Customer[]>('/crm/ai/customers/batch-score'),
  customerScoreHistory: (customerId: string) => http.get<CustomerScoreLog[]>(`/crm/ai/customers/${customerId}/score-history`),
  customerLifecycleSummary: () => http.get<Record<string, unknown>>('/crm/ai/customers/lifecycle-summary'),

  analyzeContractRisk: (contractId: string) => http.post<Contract>(`/contracts/ai/${contractId}/risk-analysis`),
  generatePaymentReminders: (daysAhead = 7) =>
    http.post<PaymentReminder[]>('/contracts/ai/payment-reminders/generate', null, { params: { daysAhead } }),
  pendingPaymentReminders: () => http.get<PaymentReminder[]>('/contracts/ai/payment-reminders'),
  paymentRiskSummary: () => http.get<Record<string, unknown>>('/contracts/ai/payment-risk-summary'),

  analyzeWorkOrder: (workOrderId: string) => http.post<WorkOrder>(`/work-orders/ai/${workOrderId}/analyze`),
  smartAssignWorkOrder: (workOrderId: string) => http.post<WorkOrder>(`/work-orders/ai/${workOrderId}/smart-assign`),
  workOrderProgressReport: () => http.get<Record<string, unknown>>('/work-orders/ai/progress-report'),

  approvalSuggestion: (taskId: string) => http.get<ApprovalSuggestion>(`/workflow/ai/tasks/${taskId}/suggestion`),
  workflowBottleneckReport: () => http.get<Record<string, unknown>>('/workflow/ai/bottleneck-report'),

  generateSmartSchedules: () => http.post<Schedule[]>('/schedule/ai/smart-schedules'),
  generateSmartReminders: () => http.post<TaskReminder[]>('/schedule/ai/smart-reminders'),
  todayBriefing: () => http.get<Record<string, unknown>>('/schedule/ai/today-briefing'),

  riskOverview: () => http.get<Record<string, Record<string, unknown>>>('/analytics/ai-risk/overview'),
  customerRisk: () => http.get<Record<string, unknown>>('/analytics/ai-risk/customers'),
  contractRisk: () => http.get<Record<string, unknown>>('/analytics/ai-risk/contracts'),
  workOrderRisk: () => http.get<Record<string, unknown>>('/analytics/ai-risk/work-orders'),
  dispatchRisk: () => http.get<Record<string, unknown>>('/analytics/ai-risk/dispatches'),
};
