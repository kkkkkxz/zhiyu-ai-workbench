import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { LoginResponse } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  accessExpiresAt: number;
  refreshExpiresAt: number;
  userId: string | null;
  username: string | null;
  nickname: string | null;
  tenantId: string | null;
  tenantCode: string | null;
  roles: string[];
  permissions: string[];

  isAuthenticated: () => boolean;
  setSession: (resp: LoginResponse) => void;
  clear: () => void;
}

const STORAGE_KEY = 'zwsk.auth';

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      accessExpiresAt: 0,
      refreshExpiresAt: 0,
      userId: null,
      username: null,
      nickname: null,
      tenantId: null,
      tenantCode: null,
      roles: [],
      permissions: [],

      isAuthenticated: () => {
        const s = get();
        if (!s.accessToken) return false;
        // 认为 access token 过期前 5s 之外都有效；过期后由 401 拦截器引导登出
        return Date.now() < s.accessExpiresAt - 5_000;
      },

      setSession: (r) =>
        set({
          accessToken: r.accessToken,
          refreshToken: r.refreshToken,
          accessExpiresAt: r.accessExpiresAt,
          refreshExpiresAt: r.refreshExpiresAt,
          userId: String(r.userId),
          username: r.username,
          nickname: r.nickname ?? null,
          tenantId: String(r.tenantId),
          tenantCode: r.tenantCode ?? null,
          roles: r.roles ?? [],
          permissions: r.permissions ?? [],
        }),

      clear: () =>
        set({
          accessToken: null,
          refreshToken: null,
          accessExpiresAt: 0,
          refreshExpiresAt: 0,
          userId: null,
          username: null,
          nickname: null,
          tenantId: null,
          tenantCode: null,
          roles: [],
          permissions: [],
        }),
    }),
    { name: STORAGE_KEY },
  ),
);
