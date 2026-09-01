import type { FlagResponse, JsonValue, ValueType } from './types';

/** Compact single-line rendering of any JSON value. */
export function formatValue(value: JsonValue | undefined): string {
  if (value === undefined) return '—';
  return JSON.stringify(value);
}

export type ParseResult = { ok: true; value: JsonValue } | { ok: false; error: string };

/** Parse a raw text input into a JSON value matching the flag's valueType. */
export function parseValueInput(valueType: ValueType, raw: string): ParseResult {
  switch (valueType) {
    case 'BOOLEAN': {
      if (raw === 'true') return { ok: true, value: true };
      if (raw === 'false') return { ok: true, value: false };
      return { ok: false, error: 'Value must be true or false' };
    }
    case 'STRING':
      return { ok: true, value: raw };
    case 'NUMBER': {
      if (raw.trim() === '') return { ok: false, error: 'Value is required' };
      const n = Number(raw);
      if (Number.isNaN(n)) return { ok: false, error: 'Value must be a number' };
      return { ok: true, value: n };
    }
    case 'JSON': {
      if (raw.trim() === '') return { ok: false, error: 'Value is required' };
      try {
        return { ok: true, value: JSON.parse(raw) as JsonValue };
      } catch {
        return { ok: false, error: 'Invalid JSON' };
      }
    }
  }
}

/** Initial raw text for editing an existing value. */
export function valueToRaw(valueType: ValueType, value: JsonValue | undefined): string {
  if (value === undefined) return valueType === 'BOOLEAN' ? 'false' : '';
  switch (valueType) {
    case 'BOOLEAN':
      return value === true ? 'true' : 'false';
    case 'STRING':
      return typeof value === 'string' ? value : JSON.stringify(value);
    case 'NUMBER':
      return typeof value === 'number' ? String(value) : JSON.stringify(value);
    case 'JSON':
      return JSON.stringify(value, null, 2);
  }
}

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export function isValidUuid(value: string): boolean {
  return UUID_RE.test(value.trim());
}

/** Stale = expiresAt is in the past. */
export function isStale(flag: Pick<FlagResponse, 'expiresAt'>): boolean {
  if (!flag.expiresAt) return false;
  const expires = new Date(flag.expiresAt);
  if (Number.isNaN(expires.getTime())) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return expires.getTime() < today.getTime();
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-GB', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('en-GB', { year: 'numeric', month: 'short', day: '2-digit' });
}
