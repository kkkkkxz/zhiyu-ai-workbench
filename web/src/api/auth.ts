import { http } from '@/lib/api';
import type { LoginRequest, LoginResponse, RegisterRequest, TenantOption } from '@/types/api';

export const authApi = {
  login: (req: LoginRequest) =>
    http.post<LoginResponse>('/auth/login', req),

  tenants: () =>
    http.get<TenantOption[]>('/auth/tenants'),

  /** 自助注册；成功直接返回 JWT，调用方写入 useAuthStore 即完成登录 */
  register: (req: RegisterRequest) =>
    http.post<LoginResponse>('/auth/register', req),

  refresh: (refreshToken: string) =>
    http.post<LoginResponse>('/auth/refresh', { refreshToken }),

  logout: () => http.post<void>('/auth/logout'),
};
