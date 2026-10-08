import { http } from '@/lib/api';
import type { PageResult } from './crm';

export interface Notification {
  id: string;
  userId: string;
  type: string;
  title: string;
  content?: string;
  bizType?: string;
  bizId?: string;
  isRead: number;
  channel: string;
  createTime: string;
}

export const notificationApi = {
  list: (page = 1, size = 20, unreadOnly?: boolean) =>
    http.get<PageResult<Notification>>('/notifications', { params: { page, size, unreadOnly } }),
  unreadCount: () => http.get<{ count: number }>('/notifications/unread-count'),
  markRead: (id: string) => http.put<void>(`/notifications/${id}/read`),
  markAllRead: () => http.put<void>('/notifications/read-all'),
};
