import type {
  AdminUser,
  ApplicationResponse,
  AuditLogPage,
  CreateFlagRequest,
  FlagDetailResponse,
  FlagResponse,
  JsonValue,
  OverrideResponse,
  SessionUser,
  TokenCreatedResponse,
  TokenResponse,
  UpdateFlagRequest,
  WorkgroupOverride,
} from './types';

const BASE = '/features-api/v1';

interface ErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  validationErrors?: Record<string, string> | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly validationErrors: Record<string, string> | null;

  constructor(status: number, message: string, validationErrors: Record<string, string> | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Unexpected error';
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  /** Do not redirect to the login page on 401 (used by login / session restore). */
  skipAuthRedirect?: boolean;
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, skipAuthRedirect = false } = opts;

  let url = BASE + path;
  if (query) {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') params.set(key, String(value));
    }
    const qs = params.toString();
    if (qs) url += `?${qs}`;
  }

  const res = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && !skipAuthRedirect && !window.location.pathname.startsWith('/admin/login')) {
    window.location.assign('/admin/login');
    throw new ApiError(401, 'Your session has expired. Please sign in again.');
  }

  if (!res.ok) {
    let message = `Request failed (HTTP ${res.status})`;
    let validationErrors: Record<string, string> | null = null;
    try {
      const data = (await res.json()) as ErrorBody;
      if (data.message) message = data.message;
      else if (data.error) message = data.error;
      if (data.validationErrors) validationErrors = data.validationErrors;
    } catch {
      // non-JSON error body — keep the generic message
    }
    throw new ApiError(res.status, message, validationErrors);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// ---- Auth ----

export function login(username: string, password: string): Promise<SessionUser> {
  return request<SessionUser>('/admin/auth/login', {
    method: 'POST',
    body: { username, password },
    skipAuthRedirect: true,
  });
}

export function logout(): Promise<void> {
  return request<void>('/admin/auth/logout', { method: 'POST', skipAuthRedirect: true });
}

export function fetchMe(): Promise<SessionUser> {
  return request<SessionUser>('/admin/auth/me', { skipAuthRedirect: true });
}

// ---- Applications ----

export function listApplications(): Promise<ApplicationResponse[]> {
  return request<ApplicationResponse[]>('/admin/applications');
}

export function createApplication(slug: string, name: string): Promise<ApplicationResponse> {
  return request<ApplicationResponse>('/admin/applications', { method: 'POST', body: { slug, name } });
}

// ---- Flags ----

export function listFlags(slug: string, includeArchived: boolean): Promise<FlagResponse[]> {
  return request<FlagResponse[]>(`/admin/applications/${encodeURIComponent(slug)}/flags`, {
    query: { includeArchived },
  });
}

export function listStaleFlags(slug: string): Promise<FlagResponse[]> {
  return request<FlagResponse[]>(`/admin/applications/${encodeURIComponent(slug)}/flags/stale`);
}

export function createFlag(slug: string, body: CreateFlagRequest): Promise<FlagDetailResponse> {
  return request<FlagDetailResponse>(`/admin/applications/${encodeURIComponent(slug)}/flags`, {
    method: 'POST',
    body,
  });
}

export function getFlag(slug: string, flagKey: string): Promise<FlagDetailResponse> {
  return request<FlagDetailResponse>(
    `/admin/applications/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flagKey)}`,
  );
}

export function updateFlag(slug: string, flagKey: string, body: UpdateFlagRequest): Promise<FlagDetailResponse> {
  return request<FlagDetailResponse>(
    `/admin/applications/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flagKey)}`,
    { method: 'PATCH', body },
  );
}

export function deleteFlag(slug: string, flagKey: string): Promise<void> {
  return request<void>(
    `/admin/applications/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flagKey)}`,
    { method: 'DELETE' },
  );
}

// ---- Overrides ----

export function setOverride(
  slug: string,
  flagKey: string,
  workgroupId: string,
  value: JsonValue,
  note?: string,
): Promise<OverrideResponse> {
  return request<OverrideResponse>(
    `/admin/applications/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flagKey)}/overrides/${encodeURIComponent(workgroupId)}`,
    { method: 'PUT', body: { value, note: note || undefined } },
  );
}

export function removeOverride(slug: string, flagKey: string, workgroupId: string): Promise<void> {
  return request<void>(
    `/admin/applications/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flagKey)}/overrides/${encodeURIComponent(workgroupId)}`,
    { method: 'DELETE' },
  );
}

export function listWorkgroupOverrides(slug: string, workgroupId: string): Promise<WorkgroupOverride[]> {
  return request<WorkgroupOverride[]>(`/admin/applications/${encodeURIComponent(slug)}/overrides`, {
    query: { workgroupId },
  });
}

// ---- Audit log ----

export function getAuditLog(
  slug: string,
  opts: { flagKey?: string; page?: number; size?: number },
): Promise<AuditLogPage> {
  return request<AuditLogPage>(`/admin/applications/${encodeURIComponent(slug)}/audit-log`, {
    query: { flagKey: opts.flagKey, page: opts.page, size: opts.size },
  });
}

// ---- Tokens ----

export function listTokens(slug: string): Promise<TokenResponse[]> {
  return request<TokenResponse[]>(`/admin/applications/${encodeURIComponent(slug)}/tokens`);
}

export function createToken(slug: string, name: string): Promise<TokenCreatedResponse> {
  return request<TokenCreatedResponse>(`/admin/applications/${encodeURIComponent(slug)}/tokens`, {
    method: 'POST',
    body: { name },
  });
}

export function revokeToken(id: string): Promise<void> {
  return request<void>(`/admin/tokens/${encodeURIComponent(id)}/revoke`, { method: 'POST' });
}

// ---- Users ----

export function listUsers(): Promise<AdminUser[]> {
  return request<AdminUser[]>('/admin/users');
}

export function createUser(username: string, password: string, role: AdminUser['role']): Promise<AdminUser> {
  return request<AdminUser>('/admin/users', { method: 'POST', body: { username, password, role } });
}

export function updateUser(
  id: string,
  patch: { role?: AdminUser['role']; enabled?: boolean; password?: string },
): Promise<AdminUser> {
  return request<AdminUser>(`/admin/users/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch });
}
