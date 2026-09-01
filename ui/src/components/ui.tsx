import type { ReactNode } from 'react';
import type { FlagKind, FlagResponse, JsonValue, ValueType } from '../types';
import { formatValue, isStale } from '../values';

type BadgeTone = 'gray' | 'blue' | 'green' | 'amber' | 'red' | 'purple';

export function Badge({ tone = 'gray', children, title }: { tone?: BadgeTone; children: ReactNode; title?: string }) {
  return (
    <span className={`badge badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

const KIND_TONES: Record<FlagKind, BadgeTone> = {
  RELEASE: 'blue',
  EXPERIMENT: 'purple',
  OPS: 'green',
  PERMISSION: 'amber',
};

export function KindBadge({ kind }: { kind: FlagKind }) {
  return <Badge tone={KIND_TONES[kind]}>{kind}</Badge>;
}

/** LOCKED / ARCHIVED / STALE status badges for a flag. */
export function FlagStatusBadges({ flag }: { flag: FlagResponse }) {
  return (
    <span className="badge-group">
      {flag.locked && <Badge tone="red" title="Kill switch active: every workgroup gets the default value; overrides are ignored">LOCKED</Badge>}
      {flag.archived && <Badge tone="gray" title="Archived: excluded from evaluation">ARCHIVED</Badge>}
      {isStale(flag) && <Badge tone="amber" title="Past its expiry date — review and clean up">STALE</Badge>}
    </span>
  );
}

/** Inline monospace rendering of a JSON value. */
export function ValueCode({ value }: { value: JsonValue | undefined }) {
  return <code className="value-code">{formatValue(value)}</code>;
}

export function Toggle({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <label className={disabled ? 'toggle toggle-disabled' : 'toggle'}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        aria-label={label ?? 'toggle'}
      />
      <span className="toggle-track">
        <span className="toggle-thumb" />
      </span>
    </label>
  );
}

/**
 * Value editor appropriate to the flag's value type.
 * `raw` is the textual state ("true"/"false" for BOOLEAN).
 */
export function ValueField({
  valueType,
  raw,
  onChange,
  error,
  id,
  disabled,
}: {
  valueType: ValueType;
  raw: string;
  onChange: (raw: string) => void;
  error?: string;
  id?: string;
  disabled?: boolean;
}) {
  let control: ReactNode;
  switch (valueType) {
    case 'BOOLEAN':
      control = (
        <div className="value-bool">
          <Toggle checked={raw === 'true'} onChange={(v) => onChange(v ? 'true' : 'false')} disabled={disabled} />
          <code className="value-code">{raw === 'true' ? 'true' : 'false'}</code>
        </div>
      );
      break;
    case 'STRING':
      control = (
        <input id={id} type="text" value={raw} disabled={disabled} onChange={(e) => onChange(e.target.value)} />
      );
      break;
    case 'NUMBER':
      control = (
        <input id={id} type="number" step="any" value={raw} disabled={disabled} onChange={(e) => onChange(e.target.value)} />
      );
      break;
    case 'JSON':
      control = (
        <textarea
          id={id}
          className="json-editor"
          rows={5}
          value={raw}
          disabled={disabled}
          spellCheck={false}
          onChange={(e) => onChange(e.target.value)}
        />
      );
      break;
  }
  return (
    <div>
      {control}
      {error && <div className="field-error">{error}</div>}
    </div>
  );
}

export function Loading() {
  return <div className="loading">Loading…</div>;
}

export function ErrorBox({ message }: { message: string }) {
  return <div className="error-box">{message}</div>;
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty-state">{children}</div>;
}

export function FieldErrors({ errors, field }: { errors: Record<string, string> | null; field: string }) {
  const msg = errors?.[field];
  if (!msg) return null;
  return <div className="field-error">{msg}</div>;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1 && totalElements <= 0) return null;
  return (
    <div className="pagination">
      <button type="button" className="btn btn-sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        ‹ Previous
      </button>
      <span className="pagination-info">
        Page {totalPages === 0 ? 0 : page + 1} of {totalPages} ({totalElements} entries)
      </span>
      <button
        type="button"
        className="btn btn-sm"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
      >
        Next ›
      </button>
    </div>
  );
}
