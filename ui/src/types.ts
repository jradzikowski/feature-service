export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export type ValueType = 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON';
export type FlagKind = 'RELEASE' | 'EXPERIMENT' | 'OPS' | 'PERMISSION';
export type AdminRole = 'ADMIN' | 'VIEWER';

export const VALUE_TYPES: ValueType[] = ['BOOLEAN', 'STRING', 'NUMBER', 'JSON'];
export const FLAG_KINDS: FlagKind[] = ['RELEASE', 'EXPERIMENT', 'OPS', 'PERMISSION'];

export interface SessionUser {
  username: string;
  role: AdminRole;
}

export interface ApplicationResponse {
  id: string;
  slug: string;
  name: string;
  configVersion: number;
  flagCount: number;
  createdAt: string;
}

export interface FlagResponse {
  flagKey: string;
  name: string;
  description?: string;
  valueType: ValueType;
  defaultValue: JsonValue;
  flagKind: FlagKind;
  locked: boolean;
  archived: boolean;
  expiresAt?: string | null;
  owner?: string | null;
  overrideCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface OverrideResponse {
  workgroupId: string;
  value: JsonValue;
  note?: string | null;
  updatedAt: string;
}

export interface FlagDetailResponse extends FlagResponse {
  overrides: OverrideResponse[];
}

export interface CreateFlagRequest {
  flagKey: string;
  name: string;
  description?: string;
  valueType: ValueType;
  defaultValue: JsonValue;
  flagKind: FlagKind;
  expiresAt?: string | null;
  owner?: string | null;
}

export interface UpdateFlagRequest {
  name?: string;
  description?: string;
  defaultValue?: JsonValue;
  locked?: boolean;
  archived?: boolean;
  expiresAt?: string | null;
  /** null/absent expiresAt means "leave unchanged"; set this to clear the expiry (never expires). */
  clearExpiresAt?: boolean;
  owner?: string | null;
}

export type AuditOperation =
  | 'FLAG_CREATED'
  | 'FLAG_UPDATED'
  | 'FLAG_ARCHIVED'
  | 'FLAG_LOCKED'
  | 'FLAG_UNLOCKED'
  | 'OVERRIDE_SET'
  | 'OVERRIDE_REMOVED'
  | 'TOKEN_CREATED'
  | 'TOKEN_REVOKED';

export interface AuditLogEntry {
  flagKey?: string | null;
  operation: AuditOperation;
  workgroupId?: string | null;
  oldValue?: JsonValue | null;
  newValue?: JsonValue | null;
  actorUsername: string;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface TokenResponse {
  id: string;
  name: string;
  tokenPrefix: string;
  createdAt: string;
  revokedAt?: string | null;
}

export interface TokenCreatedResponse extends TokenResponse {
  token: string;
}

export interface WorkgroupOverride {
  flagKey: string;
  value: JsonValue;
  note?: string | null;
  updatedAt: string;
}

export interface AdminUser {
  id: string;
  username: string;
  role: AdminRole;
  enabled: boolean;
}

export interface WorkgroupResponse {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface PlanResponse {
  id: string;
  name: string;
  description?: string | null;
  flagCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlanFlagEntry {
  flagKey: string;
  value: JsonValue;
}

export interface PlanDetailResponse {
  id: string;
  name: string;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
  flags: PlanFlagEntry[];
}

export interface WorkgroupPlanResponse {
  workgroupId: string;
  workgroupName: string;
  planId: string;
  planName: string;
  assignedAt: string;
}
