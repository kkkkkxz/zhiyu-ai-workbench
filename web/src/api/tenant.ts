import { http } from '@/lib/api';
import type {
  CreateTenantRequest,
  CreateTenantUserRequest,
  PageResult,
  TenantAiConfig,
  TenantFeature,
  TenantInfo,
  TenantBossStat,
  TenantRole,
  TenantUser,
  UpdateTenantRequest,
  UpdateTenantAiConfigRequest,
  UpdateTenantUserRequest,
} from '@/types/api';

export const tenantApi = {
  listTenants: (params?: { page?: number; size?: number; keyword?: string; status?: number }) =>
    http.get<PageResult<TenantInfo>>('/tenants', { params }),

  listBossStats: () =>
    http.get<TenantBossStat[]>('/tenants/boss-stats'),

  createTenant: (body: CreateTenantRequest) =>
    http.post<TenantInfo>('/tenants', body),

  updateTenant: (tenantId: string, body: UpdateTenantRequest) =>
    http.put<TenantInfo>(`/tenants/${tenantId}`, body),

  deleteTenant: (tenantId: string) =>
    http.del<void>(`/tenants/${tenantId}`),

  listUsers: (
    tenantId: string,
    params?: { page?: number; size?: number; keyword?: string; status?: number },
  ) => http.get<PageResult<TenantUser>>(`/tenants/${tenantId}/users`, { params }),

  createUser: (tenantId: string, body: CreateTenantUserRequest) =>
    http.post<TenantUser>(`/tenants/${tenantId}/users`, body),

  updateUser: (tenantId: string, userId: string, body: UpdateTenantUserRequest) =>
    http.put<TenantUser>(`/tenants/${tenantId}/users/${userId}`, body),

  deleteUser: (tenantId: string, userId: string) =>
    http.del<void>(`/tenants/${tenantId}/users/${userId}`),

  listRoles: (tenantId: string) =>
    http.get<TenantRole[]>(`/tenants/${tenantId}/roles`),

  listCurrentFeatures: () =>
    http.get<TenantFeature[]>('/tenant-console/features'),

  listFeatures: (tenantId: string) =>
    http.get<TenantFeature[]>(`/tenant-console/tenants/${tenantId}/features`),

  updateFeature: (tenantId: string, featureCode: string, enabled: boolean) =>
    http.put<TenantFeature>(`/tenant-console/tenants/${tenantId}/features/${featureCode}`, { enabled }),

  getAiConfig: (tenantId: string) =>
    http.get<TenantAiConfig>(`/tenant-console/tenants/${tenantId}/ai-config`),

  updateAiConfig: (tenantId: string, body: UpdateTenantAiConfigRequest) =>
    http.put<TenantAiConfig>(`/tenant-console/tenants/${tenantId}/ai-config`, body),
};
