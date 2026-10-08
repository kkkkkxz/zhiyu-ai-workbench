// 后端通用响应包装
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp?: number;
  success?: boolean;
}

// ===== Auth =====
export interface LoginRequest {
  username: string;
  password: string;
  /**
   * 租户编码（可选）。单租户 / 用户名全局唯一时不需要传；
   * 当后端返回 code=3003 (TENANT_CODE_REQUIRED) 时，前端引导用户补传该字段后重试。
   */
  tenantCode?: string;
}

/**
 * 用户自助注册请求体。
 * 注册成功后服务端直接颁发 JWT，返回结构与 {@link LoginResponse} 一致。
 */
export interface RegisterRequest {
  /** 3-32 位，字母开头，可含字母 / 数字 / _ - . */
  username: string;
  /** 8-64 位，必须同时包含字母和数字 */
  password: string;
  /** 昵称（可选；不传时服务端默认用 username） */
  nickname?: string;
  email?: string;
  phone?: string;
  /** 注册必须选择要加入的企业 */
  tenantId: string;
  /** 租户编码（兼容旧接口；新注册页使用 tenantId） */
  tenantCode?: string;
}

export interface TenantOption {
  id: string;
  code: string;
  name: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
  refreshExpiresAt: number;
  tokenType: string;
  /** Long → 字符串 */
  userId: string;
  username: string;
  nickname?: string;
  /** Long → 字符串 */
  tenantId: string;
  tenantCode?: string;
  roles: string[];
  permissions: string[];
}

// ===== User =====
export interface UserInfo {
  userId: string;
  tenantId: string;
  tenantCode?: string;
  username: string;
  nickname?: string;
  roles: string[];
  permissions: string[];
}

// ===== Tenant Management =====
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

export interface TenantInfo {
  id: string;
  code: string;
  name: string;
  status: number;
  planCode?: string;
  maxUsers?: number;
  maxStorageMb?: string;
  expiresAt?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TenantBossStat {
  tenantId: string;
  tenantCode: string;
  tenantName: string;
  status: number;
  superAdminCount: number;
}

export interface TenantUser {
  id: string;
  tenantId: string;
  username: string;
  nickname?: string;
  phone?: string;
  email?: string;
  status: number;
  roles: string[];
  lastLoginTime?: string;
  lastLoginIp?: string;
  createTime?: string;
  updateTime?: string;
}

export interface TenantRole {
  id: string;
  tenantId: string;
  roleCode: string;
  roleName: string;
  description?: string;
}

export interface CreateTenantRequest {
  code: string;
  name: string;
  planCode?: string;
  maxUsers?: number;
  maxStorageMb?: number;
  expiresAt?: string;
  remark?: string;
}

export interface UpdateTenantRequest {
  name?: string;
  status?: number;
  planCode?: string;
  maxUsers?: number;
  maxStorageMb?: number;
  expiresAt?: string | null;
  remark?: string;
}

export interface CreateTenantUserRequest {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  phone?: string;
  roleCodes?: string[];
}

export interface UpdateTenantUserRequest {
  nickname?: string;
  email?: string;
  phone?: string;
  status?: number;
  password?: string;
  roleCodes?: string[];
}

export interface TenantFeature {
  code: string;
  name: string;
  category: string;
  description?: string;
  enabled: boolean;
  enabledByDefault?: boolean;
  sourceTemplateCode?: string;
  configJson?: string;
  sort: number;
}

export interface TenantAiConfig {
  provider: string;
  baseUrl?: string;
  apiKeyMask?: string;
  apiKeyConfigured: boolean;
  chatModel: string;
  embeddingModel: string;
  visionModel: string;
  temperature: number;
  enabled: boolean;
  remark?: string;
  updateTime?: string;
}

export interface UpdateTenantAiConfigRequest {
  provider?: string;
  baseUrl?: string;
  apiKey?: string;
  chatModel?: string;
  embeddingModel?: string;
  visionModel?: string;
  temperature?: number;
  enabled?: boolean;
  remark?: string;
}

// ===== Knowledge Base =====
export interface KnowledgeBase {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  ownerId: string;
  /** PUBLIC / DEPT / ROLE / USER / PRIVATE */
  visibility: 'PUBLIC' | 'DEPT' | 'ROLE' | 'USER' | 'PRIVATE';
  status: number;
  createTime: string;
  updateTime: string;
}

export interface CreateKbRequest {
  name: string;
  description?: string;
  visibility?: KnowledgeBase['visibility'];
}

// ===== Document =====
export type ParseStatus = 'PENDING' | 'PARSING' | 'SUCCESS' | 'FAILED';

export interface KnowledgeDocument {
  id: string;
  tenantId: string;
  kbId: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  fileHash: string;
  fileUrl?: string;
  parseStatus: ParseStatus;
  parseError?: string;
  chunkCount: number;
  uploaderId: string;
  createTime: string;
  updateTime: string;
}

export interface UploadTextRequest {
  fileName: string;
  content: string;
}

// ===== Chat =====
export interface AiSession {
  id: string;
  tenantId: string;
  userId: string;
  title?: string;
  /** TEXT / VOICE */
  sessionType: 'TEXT' | 'VOICE';
  /** 后端存逗号分隔字符串 */
  kbIds?: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface AiMessage {
  id: string;
  tenantId: string;
  sessionId: string;
  userId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  audioUrl?: string;
  latencyMs?: number;
  tokenIn?: number;
  tokenOut?: number;
  feedback?: number;
  createTime: string;
}

export interface CreateSessionRequest {
  title?: string;
  /** 数字 ID 在请求体里允许传字符串数字（json-bigint 序列化） */
  kbIds?: (string | number)[];
  sessionType?: 'TEXT' | 'VOICE';
}

export interface CompletionRequest {
  question: string;
  kbIds?: (string | number)[];
  personaId?: string;
}

// ===== Personas =====
export interface Persona {
  id: string;
  name: string;
  description?: string;
  tone?: string;
}

// ===== SSE 事件 =====
export interface ReferenceItem {
  chunkId: string;
  documentId: string;
  kbId: string;
  title: string;
  score: number;
  snippet: string;
}

export interface CompletionDoneEvent {
  messageId: string;
  latencyMs: number;
  tokenIn: number;
  tokenOut: number;
}

// ===== Voice Keywords =====
export interface VoiceKeyword {
  id: string;
  tenantId: string;
  userId: string;
  sessionId?: string;
  turnId?: string;
  keyword: string;
  sourceType: 'AUTO' | 'MANUAL' | string;
  sourceText?: string;
  hitCount: number;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface VoiceKeywordUpsertRequest {
  keyword: string;
  sourceText?: string;
  sessionId?: string | number;
  turnId?: string;
}

export interface VoiceKeywordBatchUpsertRequest {
  keywords: string[];
  sourceText?: string;
  sessionId?: string | number;
  turnId?: string;
}
