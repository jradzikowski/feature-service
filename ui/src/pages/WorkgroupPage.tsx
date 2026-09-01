import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import {
  Badge,
  EmptyState,
  ErrorBox,
  FlagStatusBadges,
  KindBadge,
  Loading,
  Toggle,
  ValueCode,
  ValueField,
} from '../components/ui';
import { useToast } from '../toast';
import type { FlagKind, FlagResponse, JsonValue, WorkgroupOverride } from '../types';
import { FLAG_KINDS } from '../types';
import { formatDate, formatValue, isStale, isValidUuid, parseValueInput, valueToRaw } from '../values';

type ValueSource = 'DEFAULT' | 'OVERRIDE' | 'LOCKED';

/** A flag joined with the workgroup's override, resolved the same way EvaluationService does. */
interface ResolvedFlag {
  flag: FlagResponse;
  effectiveValue: JsonValue;
  source: ValueSource;
  override: WorkgroupOverride | null;
}

type PendingAction =
  | { type: 'setOverride'; row: ResolvedFlag; value: JsonValue; note: string }
  | { type: 'removeOverride'; row: ResolvedFlag };

/** Mirrors backend resolution order: locked > workgroup override > default. */
function resolveFlags(flags: FlagResponse[], overrides: WorkgroupOverride[]): ResolvedFlag[] {
  const overridesByKey = new Map(overrides.map((o) => [o.flagKey, o]));
  return flags.map((flag) => {
    const override = overridesByKey.get(flag.flagKey) ?? null;
    if (flag.locked) {
      return { flag, effectiveValue: flag.defaultValue, source: 'LOCKED' as const, override };
    }
    if (override) {
      return { flag, effectiveValue: override.value, source: 'OVERRIDE' as const, override };
    }
    return { flag, effectiveValue: flag.defaultValue, source: 'DEFAULT' as const, override };
  });
}

function ScopeBadge({ resolved }: { resolved: ResolvedFlag }) {
  switch (resolved.source) {
    case 'OVERRIDE':
      return (
        <Badge tone="blue" title={resolved.override?.note ? `Note: ${resolved.override.note}` : 'Workgroup override'}>
          WORKGROUP
        </Badge>
      );
    case 'LOCKED':
      return (
        <Badge
          tone="red"
          title={
            resolved.override
              ? 'Kill switch active: the workgroup override is ignored, the global default applies'
              : 'Kill switch active: the global default applies'
          }
        >
          GLOBAL
        </Badge>
      );
    default:
      return (
        <Badge tone="gray" title="No override for this workgroup — the global default value applies">
          GLOBAL
        </Badge>
      );
  }
}

export function WorkgroupPage() {
  const { slug = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const workgroupId = searchParams.get('workgroupId') ?? '';

  const [input, setInput] = useState(workgroupId);
  const [inputError, setInputError] = useState<string | null>(null);
  const [resolved, setResolved] = useState<ResolvedFlag[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // filters
  const [search, setSearch] = useState('');
  const [kind, setKind] = useState<'' | FlagKind>('');
  const [onlyOverridden, setOnlyOverridden] = useState(false);

  // inline override editing
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [pendingBusy, setPendingBusy] = useState(false);
  const [editTarget, setEditTarget] = useState<ResolvedFlag | null>(null);

  const load = useCallback(() => {
    if (!workgroupId) {
      setResolved(null);
      return;
    }
    setError(null);
    Promise.all([api.listFlags(slug, false), api.listWorkgroupOverrides(slug, workgroupId)])
      .then(([flags, overrides]) => setResolved(resolveFlags(flags, overrides)))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [slug, workgroupId]);

  useEffect(() => {
    setInput(workgroupId);
    setResolved(null);
    if (workgroupId) setLoading(true);
    load();
  }, [workgroupId, load]);

  const lookup = (e: FormEvent) => {
    e.preventDefault();
    const id = input.trim();
    if (!isValidUuid(id)) {
      setInputError('Enter a valid workgroup UUID, e.g. 123e4567-e89b-12d3-a456-426614174000');
      return;
    }
    setInputError(null);
    setSearchParams({ workgroupId: id });
  };

  const runPending = async () => {
    if (!pending) return;
    setPendingBusy(true);
    const flagKey = pending.row.flag.flagKey;
    try {
      if (pending.type === 'setOverride') {
        await api.setOverride(slug, flagKey, workgroupId, pending.value, pending.note || undefined);
        toast.success(`Override for ${flagKey} saved.`);
      } else {
        await api.removeOverride(slug, flagKey, workgroupId);
        toast.success(`Override for ${flagKey} removed — the workgroup uses the default value again.`);
      }
      setPending(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setPendingBusy(false);
    }
  };

  const visible = useMemo(() => {
    if (!resolved) return null;
    const q = search.trim().toLowerCase();
    return resolved.filter(({ flag, source }) => {
      if (q && !flag.flagKey.toLowerCase().includes(q) && !flag.name.toLowerCase().includes(q) && !(flag.owner ?? '').toLowerCase().includes(q)) {
        return false;
      }
      if (kind && flag.flagKind !== kind) return false;
      if (onlyOverridden && source !== 'OVERRIDE') return false;
      return true;
    });
  }, [resolved, search, kind, onlyOverridden]);

  const overrideCount = resolved ? resolved.filter((r) => r.source === 'OVERRIDE').length : 0;

  return (
    <div>
      <div className="page-header">
        <h1>Workgroup lookup</h1>
      </div>
      <p className="muted">
        Shows every flag of this application with the value the given workgroup receives, and whether that value
        comes from the global default or a workgroup override.
      </p>

      <form className="toolbar" onSubmit={lookup}>
        <input
          type="text"
          className="mono wide-input"
          placeholder="Workgroup UUID…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Looking up…' : 'Look up'}
        </button>
      </form>
      {inputError && <div className="field-error">{inputError}</div>}

      {error && <ErrorBox message={error} />}
      {loading && <Loading />}

      {resolved && (
        <>
          <h2>
            Flags for <span className="mono">{workgroupId}</span>{' '}
            <span className="muted">
              ({overrideCount} override{overrideCount === 1 ? '' : 's'})
            </span>
          </h2>

          <div className="toolbar">
            <input
              type="search"
              placeholder="Search key, name or owner…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select value={kind} onChange={(e) => setKind(e.target.value as '' | FlagKind)}>
              <option value="">All kinds</option>
              {FLAG_KINDS.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
            <label className="checkbox-label">
              <input type="checkbox" checked={onlyOverridden} onChange={(e) => setOnlyOverridden(e.target.checked)} />
              Only overridden
            </label>
          </div>

          {visible && visible.length === 0 && <EmptyState>No flags match the current filters.</EmptyState>}

          {visible && visible.length > 0 && (
            <table className="table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Kind</th>
                  <th>Value for this workgroup</th>
                  <th>Scope</th>
                  <th>Status</th>
                  <th>Expires</th>
                  <th>Owner</th>
                  {isAdmin && <th />}
                </tr>
              </thead>
              <tbody>
                {visible.map((row) => {
                  const { flag, effectiveValue, source, override } = row;
                  return (
                    <tr key={flag.flagKey}>
                      <td>
                        <Link
                          to={`/apps/${encodeURIComponent(slug)}/flags/${encodeURIComponent(flag.flagKey)}`}
                          className="mono row-title"
                        >
                          {flag.flagKey}
                        </Link>
                      </td>
                      <td>{flag.name}</td>
                      <td>{flag.valueType}</td>
                      <td>
                        <KindBadge kind={flag.flagKind} />
                      </td>
                      <td>
                        {flag.valueType === 'BOOLEAN' ? (
                          <Toggle
                            checked={effectiveValue === true}
                            disabled={!isAdmin || flag.locked}
                            onChange={(next) =>
                              setPending({ type: 'setOverride', row, value: next, note: override?.note ?? '' })
                            }
                            label={`Value of ${flag.flagKey} for this workgroup`}
                          />
                        ) : (
                          <ValueCode value={effectiveValue} />
                        )}
                        {source === 'OVERRIDE' && (
                          <span className="muted"> (default: {formatValue(flag.defaultValue)})</span>
                        )}
                        {source === 'LOCKED' && override && (
                          <span className="muted"> (override {formatValue(override.value)} ignored)</span>
                        )}
                      </td>
                      <td>
                        <ScopeBadge resolved={row} />
                      </td>
                      <td>
                        <FlagStatusBadges flag={flag} />
                      </td>
                      <td className={isStale(flag) ? 'nowrap warning-text' : 'nowrap'}>
                        {flag.expiresAt ? formatDate(flag.expiresAt) : '—'}
                      </td>
                      <td>{flag.owner ?? '—'}</td>
                      {isAdmin && (
                        <td className="row-actions">
                          <button type="button" className="btn btn-sm" onClick={() => setEditTarget(row)}>
                            {override ? 'Edit override' : 'Override'}
                          </button>
                          {override && (
                            <button
                              type="button"
                              className="btn btn-sm btn-danger"
                              onClick={() => setPending({ type: 'removeOverride', row })}
                            >
                              Remove
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </>
      )}

      {editTarget && (
        <OverrideModal
          row={editTarget}
          workgroupId={workgroupId}
          onClose={() => setEditTarget(null)}
          onSubmit={(value, note) => {
            setEditTarget(null);
            setPending({ type: 'setOverride', row: editTarget, value, note });
          }}
        />
      )}

      {pending && (
        <ConfirmModal
          title={pending.type === 'setOverride' ? 'Set workgroup override' : 'Remove override'}
          confirmLabel={pending.type === 'setOverride' ? 'Save override' : 'Remove override'}
          danger={pending.type === 'removeOverride'}
          busy={pendingBusy}
          onCancel={() => setPending(null)}
          onConfirm={runPending}
        >
          {pending.type === 'setOverride' ? (
            <>
              <p>
                Set <code className="value-code">{pending.row.flag.flagKey}</code> for workgroup{' '}
                <code className="value-code">{workgroupId}</code> to{' '}
                <code className="value-code">{formatValue(pending.value)}</code> (default:{' '}
                <code className="value-code">{formatValue(pending.row.flag.defaultValue)}</code>).
              </p>
              <p className="warning-text">Scope: this single workgroup only.</p>
            </>
          ) : (
            <>
              <p>
                Remove the override on <code className="value-code">{pending.row.flag.flagKey}</code> for workgroup{' '}
                <code className="value-code">{workgroupId}</code>?
              </p>
              <p className="warning-text">
                The workgroup will fall back to the default value (
                <code className="value-code">{formatValue(pending.row.flag.defaultValue)}</code>).
              </p>
            </>
          )}
        </ConfirmModal>
      )}
    </div>
  );
}

function OverrideModal({
  row,
  workgroupId,
  onSubmit,
  onClose,
}: {
  row: ResolvedFlag;
  workgroupId: string;
  onSubmit: (value: JsonValue, note: string) => void;
  onClose: () => void;
}) {
  const { flag, override } = row;
  const [raw, setRaw] = useState(() => valueToRaw(flag.valueType, override?.value ?? flag.defaultValue));
  const [note, setNote] = useState(override?.note ?? '');
  const [valueError, setValueError] = useState<string | undefined>(undefined);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const parsed = parseValueInput(flag.valueType, raw);
    if (!parsed.ok) {
      setValueError(parsed.error);
      return;
    }
    onSubmit(parsed.value, note.trim());
  };

  return (
    <Modal title={`${override ? 'Edit' : 'Set'} override: ${flag.flagKey}`} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="muted">
          Workgroup <span className="mono">{workgroupId}</span> — default value is{' '}
          <code className="value-code">{formatValue(flag.defaultValue)}</code>.
          {flag.locked && ' Flag is LOCKED: the override will be stored but ignored until the flag is unlocked.'}
        </p>
        <div className="form-row">
          <label htmlFor="wg-ov-value">Override value ({flag.valueType})</label>
          <ValueField id="wg-ov-value" valueType={flag.valueType} raw={raw} onChange={setRaw} error={valueError} />
        </div>
        <div className="form-row">
          <label htmlFor="wg-ov-note">Note</label>
          <input
            id="wg-ov-note"
            type="text"
            value={note}
            maxLength={512}
            placeholder="Human-readable workgroup name, e.g. Noratel"
            onChange={(e) => setNote(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary">
            Save override
          </button>
        </div>
      </form>
    </Modal>
  );
}
