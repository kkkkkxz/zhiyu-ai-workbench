import { http } from '@/lib/api';
import type { UserInfo } from '@/types/api';

export const userApi = {
  me: () => http.get<UserInfo>('/users/me'),
};
