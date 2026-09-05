import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import * as api from '../api';
import { ApiError, errorMessage } from '../api';
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
import type { FlagKind, FlagResponse, JsonValue, PlanResponse, WorkgroupOverride, WorkgroupPlanResponse, WorkgroupResponse } from '../types';
import { FLAG_KINDS } from '../types';
import { formatDate, formatValue, isStale, isValidUuid, parseValueInput, valueToRaw } from '../values';

type ValueSource = 'DEFAULT' | 'OVERRIDE' | 'PLAN' | 'LOCKED';

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

/** Mirrors backend resolution order: locked > workgroup override > plan > default. */
function resolveFlags(
  flags: FlagResponse[],
  overrides: WorkgroupOverride[],
  planFlags: Map<string, JsonValue>,
): ResolvedFlag[] {
  const overridesByKey = new Map(overrides.map((o) => [o.flagKey, o]));
  return flags.map((flag) => {
    const override = overridesByKey.get(flag.flagKey) ?? null;
    if (flag.locked) {
      return { flag, effectiveValue: flag.defaultValue, source: 'LOCKED' as const, override };
    }
    if (override) {
      return { flag, effectiveValue: override.value, source: 'OVERRIDE' as const, override };
    }
    if (planFlags.has(flag.flagKey)) {
      return { flag, effectiveValue: planFlags.get(flag.flagKey)!, source: 'PLAN' as const, override };
    }
    return { flag, effectiveValue: flag.defaultValue, source: 'DEFAULT' as const, override };
  });
}

function ScopeBadge({ resolved, planName }: { resolved: ResolvedFlag; planName?: string | null }) {
  switch (resolved.source) {
    case 'OVERRIDE':
      return (
        <Badge tone="blue" title={resolved.override?.note ? `Note: ${resolved.override.note}` : 'Workgroup override'}>
          WORKGROUP
        </Badge>
      );
    case 'PLAN':
      return (
        <Badge tone="purple" title={planName ? `Value from plan: ${planName}` : 'Value from plan'}>
          PLAN
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
  const [nameMatches, setNameMatches] = useState<WorkgroupResponse[]>([]);
  const [resolved, setResolved] = useState<ResolvedFlag[] | null>(null);
  const [planAssignment, setPlanAssignment] = useState<WorkgroupPlanResponse | null>(null);
  const [showPlanModal, setShowPlanModal] = useState(false);
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
      setPlanAssignment(null);
      return;
    }
    setError(null);
    const flagsAndOverrides = Promise.all([
      api.listFlags(slug, false),
      api.listWorkgroupOverrides(slug, workgroupId),
    ]);
    const planData = api.getWorkgroupPlan(slug, workgroupId).catch((err) => {
      if (err instanceof ApiError && err.status === 404) return null;
      throw err;
    });
    Promise.all([flagsAndOverrides, planData])
      .then(async ([[flags, overrides], assignment]) => {
        let planFlags = new Map<string, JsonValue>();
        if (assignment) {
          setPlanAssignment(assignment);
          const planDetail = await api.getPlan(slug, assignment.planId);
          planFlags = new Map(planDetail.flags.map((f) => [f.flagKey, f.value]));
        } else {
          setPlanAssignment(null);
        }
        setResolved(resolveFlags(flags, overrides, planFlags));
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [slug, workgroupId]);

  useEffect(() => {
    setInput(workgroupId);
    setResolved(null);
    setPlanAssignment(null);
    if (workgroupId) setLoading(true);
    load();
  }, [workgroupId, load]);

  const selectWorkgroup = (id: string) => {
    setNameMatches([]);
    setInputError(null);
    setSearchParams({ workgroupId: id });
  };

  const lookup = (e: FormEvent) => {
    e.preventDefault();
    const value = input.trim();
    if (!value) return;
    setNameMatches([]);
    setInputError(null);
    if (isValidUuid(value)) {
      setSearchParams({ workgroupId: value });
      return;
    }
    api.listWorkgroups(value).then((matches) => {
      if (matches.length === 0) {
        setInputError(`No workgroup found matching "${value}".`);
      } else if (matches.length === 1) {
        selectWorkgroup(matches[0].id);
      } else {
        setNameMatches(matches);
      }
    }).catch(() => {
      setInputError('Failed to search workgroups.');
    });
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
          className="wide-input"
          placeholder="Workgroup UUID or name…"
          value={input}
          onChange={(e) => { setInput(e.target.value); setNameMatches([]); setInputError(null); }}
        />
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Looking up…' : 'Look up'}
        </button>
      </form>
      {inputError && <div className="field-error">{inputError}</div>}
      {nameMatches.length > 1 && (
        <div className="muted" style={{ marginTop: '6px' }}>
          Multiple workgroups match — pick one:
          <ul style={{ margin: '4px 0 0', paddingLeft: '1.2em' }}>
            {nameMatches.map((w) => (
              <li key={w.id}>
                <button
                  type="button"
                  className="btn btn-sm"
                  style={{ marginTop: '4px' }}
                  onClick={() => selectWorkgroup(w.id)}
                >
                  {w.name}
                </button>{' '}
                <span className="mono muted" style={{ fontSize: '0.8em' }}>{w.id}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {error && <ErrorBox message={error} />}
      {loading && <Loading />}

      {resolved && (
        <>
          <div className="page-header" style={{ marginTop: '1.5rem' }}>
            <h2 style={{ margin: 0 }}>
              Flags for <span className="mono">{workgroupId}</span>{' '}
              <span className="muted">
                ({overrideCount} override{overrideCount === 1 ? '' : 's'})
              </span>
            </h2>
            {isAdmin && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {planAssignment ? (
                  <>
                    <span className="muted">Plan:</span>
                    <strong>{planAssignment.planName}</strong>
                    <button type="button" className="btn btn-sm" onClick={() => setShowPlanModal(true)}>
                      Change
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={async () => {
                        try {
                          await api.unassignWorkgroupPlan(slug, workgroupId);
                          toast.success('Plan removed.');
                          load();
                        } catch (err) {
                          toast.error(errorMessage(err));
                        }
                      }}
                    >
                      Remove plan
                    </button>
                  </>
                ) : (
                  <>
                    <span className="muted">No plan assigned.</span>
                    <button type="button" className="btn btn-sm btn-primary" onClick={() => setShowPlanModal(true)}>
                      Assign plan
                    </button>
                  </>
                )}
              </div>
            )}
          </div>

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
                        <ScopeBadge resolved={row} planName={planAssignment?.planName} />
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

      {showPlanModal && (
        <AssignPlanModal
          slug={slug}
          workgroupId={workgroupId}
          currentPlanId={planAssignment?.planId ?? null}
          onClose={() => setShowPlanModal(false)}
          onAssigned={() => { setShowPlanModal(false); load(); }}
        />
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

function AssignPlanModal({
  slug,
  workgroupId,
  currentPlanId,
  onClose,
  onAssigned,
}: {
  slug: string;
  workgroupId: string;
  currentPlanId: string | null;
  onClose: () => void;
  onAssigned: () => void;
}) {
  const toast = useToast();
  const [plans, setPlans] = useState<PlanResponse[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState(currentPlanId ?? '');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.listPlans(slug).then(setPlans).catch(() => {});
  }, [slug]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!selectedPlanId) return;
    setBusy(true);
    try {
      await api.assignWorkgroupPlan(slug, workgroupId, selectedPlanId);
      toast.success('Plan assigned.');
      onAssigned();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="Assign plan to workgroup" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="muted">
          Workgroup <span className="mono">{workgroupId}</span>
        </p>
        <div className="form-row">
          <label htmlFor="plan-select">Plan</label>
          <select
            id="plan-select"
            value={selectedPlanId}
            onChange={(e) => setSelectedPlanId(e.target.value)}
            required
          >
            <option value="">— select a plan —</option>
            {plans.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy || !selectedPlanId}>
            {busy ? 'Assigning…' : 'Assign plan'}
          </button>
        </div>
      </form>
    </Modal>
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
