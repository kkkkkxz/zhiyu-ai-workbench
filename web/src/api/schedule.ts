import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface Schedule {
  id: string;
  tenantId: string;
  userId: string;
  title: string;
  description?: string;
  type: string;
  startTime: string;
  endTime?: string;
  allDay: number;
  location?: string;
  bizType?: string;
  bizId?: string;
  recurrence?: string;
  color?: string;
  status: string;
  createTime: string;
}

export interface TaskReminder {
  id: string;
  userId: string;
  title: string;
  content?: string;
  remindTime: string;
  remindType: string;
  channel: string;
  bizType?: string;
  bizId?: string;
  status: string;
}

export const scheduleApi = {
  calendar: (start: string, end: string) =>
    http.get<Schedule[]>('/schedule/calendar', { params: { start, end } }),
  list: (page = 1, size = 20) =>
    http.get<PageResult<Schedule>>('/schedule', { params: { page, size } }),
  get: (id: string) => http.get<Schedule>(`/schedule/${id}`),
  create: (data: {
    title: string; startTime: string; endTime?: string; type?: string;
    description?: string; location?: string; color?: string; allDay?: boolean;
    participantIds?: string[];
  }) => http.post<Schedule>('/schedule', data),
  update: (id: string, data: Partial<Schedule>) => http.put<Schedule>(`/schedule/${id}`, data),
  delete: (id: string) => http.del<void>(`/schedule/${id}`),

  myReminders: (page = 1, size = 20) =>
    http.get<PageResult<TaskReminder>>('/schedule/reminders', { params: { page, size } }),
  createReminder: (data: Partial<TaskReminder>) =>
    http.post<TaskReminder>('/schedule/reminders', data),
  dismissReminder: (id: string) => http.put<void>(`/schedule/reminders/${id}/dismiss`),
};
