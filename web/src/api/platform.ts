import { http } from '@/lib/api';

export interface PlatformOverview {
  tenantTotal: number;
  activeTenantTotal: number;
  userTotal: number;
  todayActiveUserTotal: number;
  todayChatTotal: number;
  kbTotal: number;
  documentTotal: number;
  departmentTotal: number;
  pendingAdminApplyTotal: number;
  todayTokenTotal: number;
  todayErrorTotal: number;
}

export interface TenantRanking {
  tenantId: string;
  tenantName: string;
  tenantCode: string;
  status: number;
  userTotal: number;
  chatTotal: number;
  kbTotal: number;
  documentTotal: number;
  tokenTotal: number;
  lastActiveTime?: string;
}

export interface DepartmentNode {
  id: string;
  tenantId: string;
  parentId: string;
  name: string;
  code?: string;
  sort: number;
  status: number;
  leaderUserId?: string;
  path?: string;
  level: number;
  createTime?: string;
  updateTime?: string;
  children: DepartmentNode[];
}

export interface CreateDepartmentRequest {
  parentId?: string;
  name: string;
  code?: string;
  sort?: number;
  leaderUserId?: string;
}

export interface UpdateDepartmentRequest {
  parentId?: string;
  name?: string;
  code?: string;
  sort?: number;
  status?: number;
  leaderUserId?: string;
}

export interface ReportingRelation {
  id: string;
  tenantId: string;
  userId: string;
  managerUserId: string;
  deptId?: string;
  relationType: string;
  createTime?: string;
}

export interface AdminApplyItem {
  id: string;
  tenantId: string;
  userId: string;
  applyType: string;
  targetRole: string;
  reason?: string;
  status: string;
  reviewerId?: string;
  reviewTime?: string;
  rejectReason?: string;
  createTime?: string;
}

export interface DocumentAclEntry {
  id?: string;
  subjectType: string;
  subjectId: string;
  permission?: string;
}

export const platformApi = {
  overview: () => http.get<PlatformOverview>('/platform/dashboard/overview'),
  tenantRanking: (limit = 20) =>
    http.get<TenantRanking[]>(`/platform/dashboard/tenant-ranking?limit=${limit}`),

  // 部门
  deptTree: (tenantId: string) =>
    http.get<DepartmentNode[]>(`/tenants/${tenantId}/departments`),
  createDept: (tenantId: string, req: CreateDepartmentRequest) =>
    http.post<DepartmentNode>(`/tenants/${tenantId}/departments`, req),
  updateDept: (tenantId: string, deptId: string, req: UpdateDepartmentRequest) =>
    http.put<DepartmentNode>(`/tenants/${tenantId}/departments/${deptId}`, req),
  deleteDept: (tenantId: string, deptId: string) =>
    http.del<void>(`/tenants/${tenantId}/departments/${deptId}`),
  assignUserDept: (tenantId: string, userId: string, deptId: string | null) =>
    http.put<void>(`/tenants/${tenantId}/departments/users/${userId}`, { deptId }),

  // 上下级
  listReporting: (tenantId: string, userId: string) =>
    http.get<ReportingRelation[]>(`/tenants/${tenantId}/departments/reporting/${userId}`),
  addReporting: (tenantId: string, userId: string, managerUserId: string, deptId?: string) =>
    http.post<ReportingRelation>(`/tenants/${tenantId}/departments/reporting`, {
      userId,
      managerUserId,
      deptId,
    }),
  removeReporting: (tenantId: string, relationId: string) =>
    http.del<void>(`/tenants/${tenantId}/departments/reporting/${relationId}`),

  // 管理员审核
  submitAdminApply: (userId: string, targetRole: string, reason?: string) =>
    http.post<AdminApplyItem>('/admin-applies', { userId, targetRole, reason }),
  listAdminApplies: (page = 1, size = 20) =>
    http.get<{ total: number; page: number; size: number; records: AdminApplyItem[] }>(
      `/admin-applies?page=${page}&size=${size}`,
    ),
  approveAdminApply: (applyId: string) =>
    http.post<AdminApplyItem>(`/admin-applies/${applyId}/approve`),
  rejectAdminApply: (applyId: string, reason?: string) =>
    http.post<AdminApplyItem>(`/admin-applies/${applyId}/reject`, { rejectReason: reason }),

  // 文档 ACL
  listDocAcl: (kbId: string, docId: string) =>
    http.get<DocumentAclEntry[]>(`/kb/${kbId}/docs/${docId}/acl`),
  updateDocAcl: (kbId: string, docId: string, entries: DocumentAclEntry[]) =>
    http.put<void>(`/kb/${kbId}/docs/${docId}/acl`, { entries }),
};
