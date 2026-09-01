import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../api';
import { ApiError, errorMessage } from '../api';
import { useAuth } from '../auth';
import { AuditLogTable } from '../components/AuditLogTable';
import { ConfirmModal } from '../components/Modal';
import {
  ErrorBox,
  FieldErrors,
  FlagStatusBadges,
  KindBadge,
  Loading,
  ValueCode,
  ValueField,
} from '../components/ui';
import { useToast } from '../toast';
import type { FlagDetailResponse, JsonValue, OverrideResponse, UpdateFlagRequest } from '../types';
import { formatDate, formatDateTime, formatValue, isValidUuid, parseValueInput, valueToRaw } from '../values';

type PendingAction =
  | { type: 'defaultValue'; value: JsonValue }
  | { type: 'lock' }
  | { type: 'unlock' }
  | { type: 'archive' }
  | { type: 'unarchive' }
  | { type: 'delete' }
  | { type: 'saveOverride'; workgroupId: string; value: JsonValue; note: string; isNew: boolean }
  | { type: 'deleteOverride'; workgroupId: string };

export function FlagDetailPage() {
  const { slug = '', flagKey = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [flag, setFlag] = useState<FlagDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<'overrides' | 'history'>('overrides');
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api
      .getFlag(slug, flagKey)
      .then((f) => {
        setFlag(f);
        setError(null);
      })
      .catch((err) => setError(errorMessage(err)));
  }, [slug, flagKey]);

  useEffect(load, [load]);

  const runPending = async () => {
    if (!pending || !flag) return;
    setBusy(true);
    try {
      switch (pending.type) {
        case 'defaultValue':
          await api.updateFlag(slug, flagKey, { defaultValue: pending.value });
          toast.success('Default value updated.');
          break;
        case 'lock':
          await api.updateFlag(slug, flagKey, { locked: true });
          toast.success('Flag locked.');
          break;
        case 'unlock':
          await api.updateFlag(slug, flagKey, { locked: false });
          toast.success('Flag unlocked.');
          break;
        case 'archive':
          await api.updateFlag(slug, flagKey, { archived: true });
          toast.success('Flag archived.');
          break;
        case 'unarchive':
          await api.updateFlag(slug, flagKey, { archived: false });
          toast.success('Flag restored from archive.');
          break;
        case 'delete':
          await api.deleteFlag(slug, flagKey);
          toast.success(`Flag ${flagKey} deleted.`);
          navigate(`/apps/${encodeURIComponent(slug)}/flags`, { replace: true });
          return;
        case 'saveOverride':
          await api.setOverride(slug, flagKey, pending.workgroupId, pending.value, pending.note);
          toast.success(`Override for ${pending.workgroupId} saved.`);
          break;
        case 'deleteOverride':
          await api.removeOverride(slug, flagKey, pending.workgroupId);
          toast.success(`Override for ${pending.workgroupId} removed.`);
          break;
      }
      setPending(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (error) {
    return (
      <div>
        <BackLink slug={slug} />
        <ErrorBox message={error} />
      </div>
    );
  }
  if (!flag) return <Loading />;

  return (
    <div>
      <BackLink slug={slug} />
      <div className="page-header">
        <h1 className="mono">{flag.flagKey}</h1>
        <span className="badge-group">
          <KindBadge kind={flag.flagKind} />
          <FlagStatusBadges flag={flag} />
        </span>
      </div>
      <p className="muted">
        {flag.name}
        {flag.description ? ` — ${flag.description}` : ''}
      </p>

      <div className="detail-grid">
        <MetadataCard flag={flag} slug={slug} isAdmin={isAdmin} onSaved={load} />
        <DefaultValueCard
          flag={flag}
          isAdmin={isAdmin}
          onRequestSave={(value) => setPending({ type: 'defaultValue', value })}
        />
        <ActionsCard flag={flag} isAdmin={isAdmin} onAction={(a) => setPending(a)} />
      </div>

      <div className="tabs">
        <button
          type="button"
          className={tab === 'overrides' ? 'tab active' : 'tab'}
          onClick={() => setTab('overrides')}
        >
          Overrides ({flag.overrides.length})
        </button>
        <button type="button" className={tab === 'history' ? 'tab active' : 'tab'} onClick={() => setTab('history')}>
          History
        </button>
      </div>

      {tab === 'overrides' ? (
        <OverridesSection
          flag={flag}
          isAdmin={isAdmin}
          onSave={(workgroupId, value, note, isNew) =>
            setPending({ type: 'saveOverride', workgroupId, value, note, isNew })
          }
          onDelete={(workgroupId) => setPending({ type: 'deleteOverride', workgroupId })}
        />
      ) : (
        <AuditLogTable slug={slug} fixedFlagKey={flagKey} />
      )}

      {pending && (
        <PendingConfirmModal
          flag={flag}
          pending={pending}
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={runPending}
        />
      )}
    </div>
  );
}

function BackLink({ slug }: { slug: string }) {
  return (
    <Link to={`/apps/${encodeURIComponent(slug)}/flags`} className="back-link">
      ← All flags
    </Link>
  );
}

function MetadataCard({
  flag,
  slug,
  isAdmin,
  onSaved,
}: {
  flag: FlagDetailResponse;
  slug: string;
  isAdmin: boolean;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(flag.name);
  const [description, setDescription] = useState(flag.description ?? '');
  const [owner, setOwner] = useState(flag.owner ?? '');
  const [expiresAt, setExpiresAt] = useState(flag.expiresAt ?? '');
  const [neverExpires, setNeverExpires] = useState(!flag.expiresAt);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setName(flag.name);
    setDescription(flag.description ?? '');
    setOwner(flag.owner ?? '');
    setExpiresAt(flag.expiresAt ?? '');
    setNeverExpires(!flag.expiresAt);
  }, [flag]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!neverExpires && !expiresAt) {
      setFieldErrors({ expiresAt: 'Pick an expiry date or check "Never expires".' });
      return;
    }
    const body: UpdateFlagRequest = {
      name,
      description,
      owner: owner || null,
      expiresAt: neverExpires ? null : expiresAt,
      clearExpiresAt: neverExpires,
    };
    setBusy(true);
    setFieldErrors(null);
    try {
      await api.updateFlag(slug, flag.flagKey, body);
      toast.success('Flag metadata saved.');
      onSaved();
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) setFieldErrors(err.validationErrors);
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2>Metadata</h2>
      <div className="form-row">
        <label htmlFor="md-name">Name</label>
        <input id="md-name" type="text" value={name} maxLength={255} required disabled={!isAdmin} onChange={(e) => setName(e.target.value)} />
        <FieldErrors errors={fieldErrors} field="name" />
      </div>
      <div className="form-row">
        <label htmlFor="md-description">Description</label>
        <textarea id="md-description" rows={2} value={description} disabled={!isAdmin} onChange={(e) => setDescription(e.target.value)} />
        <FieldErrors errors={fieldErrors} field="description" />
      </div>
      <div className="form-row">
        <label htmlFor="md-owner">Owner</label>
        <input id="md-owner" type="text" value={owner} maxLength={255} disabled={!isAdmin} onChange={(e) => setOwner(e.target.value)} />
        <FieldErrors errors={fieldErrors} field="owner" />
      </div>
      <div className="form-row">
        <label htmlFor="md-expires">Expires at</label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={neverExpires}
            disabled={!isAdmin}
            onChange={(e) => {
              setNeverExpires(e.target.checked);
              if (e.target.checked) setExpiresAt('');
            }}
          />
          Never expires
        </label>
        <input
          id="md-expires"
          type="date"
          value={expiresAt}
          disabled={!isAdmin || neverExpires}
          onChange={(e) => setExpiresAt(e.target.value)}
        />
        <FieldErrors errors={fieldErrors} field="expiresAt" />
      </div>
      <div className="form-row">
        <label>Value type</label>
        <div className="readonly-value">{flag.valueType} (immutable)</div>
      </div>
      <div className="form-row">
        <label>Created / updated</label>
        <div className="readonly-value">
          {formatDate(flag.createdAt)} / {formatDate(flag.updatedAt)}
        </div>
      </div>
      {isAdmin && (
        <div className="card-actions">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save metadata'}
          </button>
        </div>
      )}
    </form>
  );
}

function DefaultValueCard({
  flag,
  isAdmin,
  onRequestSave,
}: {
  flag: FlagDetailResponse;
  isAdmin: boolean;
  onRequestSave: (value: JsonValue) => void;
}) {
  const [raw, setRaw] = useState(() => valueToRaw(flag.valueType, flag.defaultValue));
  const [parseError, setParseError] = useState<string | undefined>(undefined);

  useEffect(() => {
    setRaw(valueToRaw(flag.valueType, flag.defaultValue));
    setParseError(undefined);
  }, [flag]);

  const save = () => {
    const parsed = parseValueInput(flag.valueType, raw);
    if (!parsed.ok) {
      setParseError(parsed.error);
      return;
    }
    setParseError(undefined);
    onRequestSave(parsed.value);
  };

  return (
    <div className="card">
      <h2>Default value</h2>
      <p className="muted">Applies to every workgroup without its own override.</p>
      <ValueField
        valueType={flag.valueType}
        raw={raw}
        onChange={setRaw}
        error={parseError}
        disabled={!isAdmin}
      />
      {isAdmin && (
        <div className="card-actions">
          <button type="button" className="btn btn-primary" onClick={save}>
            Save default value
          </button>
        </div>
      )}
    </div>
  );
}

function ActionsCard({
  flag,
  isAdmin,
  onAction,
}: {
  flag: FlagDetailResponse;
  isAdmin: boolean;
  onAction: (a: PendingAction) => void;
}) {
  if (!isAdmin) {
    return (
      <div className="card">
        <h2>Actions</h2>
        <p className="muted">You have read-only access (VIEWER role).</p>
      </div>
    );
  }
  return (
    <div className="card">
      <h2>Actions</h2>
      <div className="actions-list">
        {flag.locked ? (
          <button type="button" className="btn" onClick={() => onAction({ type: 'unlock' })}>
            Unlock
          </button>
        ) : (
          <button type="button" className="btn btn-danger" onClick={() => onAction({ type: 'lock' })}>
            Lock (kill switch)
          </button>
        )}
        {flag.archived ? (
          <>
            <button type="button" className="btn" onClick={() => onAction({ type: 'unarchive' })}>
              Unarchive
            </button>
            <button type="button" className="btn btn-danger" onClick={() => onAction({ type: 'delete' })}>
              Delete permanently
            </button>
          </>
        ) : (
          <button type="button" className="btn" onClick={() => onAction({ type: 'archive' })}>
            Archive
          </button>
        )}
      </div>
      {!flag.archived && <p className="muted small">Delete is available only for archived flags.</p>}
    </div>
  );
}

function OverridesSection({
  flag,
  isAdmin,
  onSave,
  onDelete,
}: {
  flag: FlagDetailResponse;
  isAdmin: boolean;
  onSave: (workgroupId: string, value: JsonValue, note: string, isNew: boolean) => void;
  onDelete: (workgroupId: string) => void;
}) {
  const { slug = '' } = useParams();
  const [editing, setEditing] = useState<OverrideResponse | null>(null);
  const [showForm, setShowForm] = useState(false);

  const startEdit = (o: OverrideResponse) => {
    setEditing(o);
    setShowForm(true);
  };

  return (
    <div>
      <p className="muted">
        Default: <ValueCode value={flag.defaultValue} />
        {flag.locked && ' — flag is LOCKED, overrides below are currently ignored.'}
      </p>

      {flag.overrides.length === 0 ? (
        <p className="muted">No overrides — every workgroup uses the default value.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Workgroup ID</th>
              <th>Override value</th>
              <th>Note</th>
              <th>Updated</th>
              {isAdmin && <th />}
            </tr>
          </thead>
          <tbody>
            {flag.overrides.map((o) => (
              <tr key={o.workgroupId}>
                <td className="mono">
                  <Link
                    to={`/apps/${encodeURIComponent(slug)}/workgroup?workgroupId=${encodeURIComponent(o.workgroupId)}`}
                    title="Show all flags for this workgroup"
                  >
                    {o.workgroupId}
                  </Link>
                </td>
                <td>
                  <span className="muted">default: {formatValue(flag.defaultValue)}, override: </span>
                  <ValueCode value={o.value} />
                </td>
                <td>{o.note ?? '—'}</td>
                <td className="nowrap">{formatDateTime(o.updatedAt)}</td>
                {isAdmin && (
                  <td className="row-actions">
                    <button type="button" className="btn btn-sm" onClick={() => startEdit(o)}>
                      Edit
                    </button>
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => onDelete(o.workgroupId)}>
                      Delete
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {isAdmin && !showForm && (
        <button
          type="button"
          className="btn"
          onClick={() => {
            setEditing(null);
            setShowForm(true);
          }}
        >
          + Add override
        </button>
      )}

      {isAdmin && showForm && (
        <OverrideForm
          flag={flag}
          existing={editing}
          onCancel={() => setShowForm(false)}
          onSubmit={(workgroupId, value, note) => {
            setShowForm(false);
            onSave(workgroupId, value, note, editing === null);
          }}
        />
      )}
    </div>
  );
}

function OverrideForm({
  flag,
  existing,
  onSubmit,
  onCancel,
}: {
  flag: FlagDetailResponse;
  existing: OverrideResponse | null;
  onSubmit: (workgroupId: string, value: JsonValue, note: string) => void;
  onCancel: () => void;
}) {
  const [workgroupId, setWorkgroupId] = useState(existing?.workgroupId ?? '');
  const [raw, setRaw] = useState(() => valueToRaw(flag.valueType, existing?.value));
  const [note, setNote] = useState(existing?.note ?? '');
  const [uuidError, setUuidError] = useState<string | undefined>(undefined);
  const [valueError, setValueError] = useState<string | undefined>(undefined);

  useEffect(() => {
    setWorkgroupId(existing?.workgroupId ?? '');
    setRaw(valueToRaw(flag.valueType, existing?.value));
    setNote(existing?.note ?? '');
    setUuidError(undefined);
    setValueError(undefined);
  }, [existing, flag.valueType]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    let ok = true;
    if (!isValidUuid(workgroupId)) {
      setUuidError('Enter a valid UUID, e.g. 123e4567-e89b-12d3-a456-426614174000');
      ok = false;
    } else {
      setUuidError(undefined);
    }
    const parsed = parseValueInput(flag.valueType, raw);
    if (!parsed.ok) {
      setValueError(parsed.error);
      ok = false;
    } else {
      setValueError(undefined);
    }
    if (!ok || !parsed.ok) return;
    onSubmit(workgroupId.trim(), parsed.value, note.trim());
  };

  return (
    <form className="card override-form" onSubmit={submit}>
      <h3>{existing ? 'Edit override' : 'Add override'}</h3>
      <div className="form-row">
        <label htmlFor="ov-wg">Workgroup UUID</label>
        <input
          id="ov-wg"
          type="text"
          className="mono"
          value={workgroupId}
          disabled={existing !== null}
          placeholder="123e4567-e89b-12d3-a456-426614174000"
          required
          onChange={(e) => setWorkgroupId(e.target.value)}
        />
        {uuidError && <div className="field-error">{uuidError}</div>}
      </div>
      <div className="form-row">
        <label htmlFor="ov-value">Override value ({flag.valueType})</label>
        <ValueField id="ov-value" valueType={flag.valueType} raw={raw} onChange={setRaw} error={valueError} />
      </div>
      <div className="form-row">
        <label htmlFor="ov-note">Note</label>
        <input
          id="ov-note"
          type="text"
          value={note}
          maxLength={512}
          placeholder="Human-readable workgroup name, e.g. Noratel"
          onChange={(e) => setNote(e.target.value)}
        />
        <div className="muted small">
          The service does not know workgroup names — use the note to record one.
        </div>
      </div>
      <div className="card-actions">
        <button type="button" className="btn" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary">
          {existing ? 'Save override' : 'Add override'}
        </button>
      </div>
    </form>
  );
}

function PendingConfirmModal({
  flag,
  pending,
  busy,
  onConfirm,
  onCancel,
}: {
  flag: FlagDetailResponse;
  pending: PendingAction;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const common = { busy, onConfirm, onCancel };
  switch (pending.type) {
    case 'defaultValue':
      return (
        <ConfirmModal title="Change global default value" confirmLabel="Save default value" danger {...common}>
          <p>
            New default value for <code className="value-code">{flag.flagKey}</code>:{' '}
            <code className="value-code">{formatValue(pending.value)}</code>
          </p>
          <p className="warning-text">
            This is a GLOBAL change — it affects every workgroup without its own override
            {flag.overrides.length > 0 ? ` (${flag.overrides.length} override${flag.overrides.length === 1 ? '' : 's'} will keep their values).` : '.'}
          </p>
        </ConfirmModal>
      );
    case 'lock':
      return (
        <ConfirmModal title="Lock flag (kill switch)" confirmLabel="Lock flag" danger {...common}>
          <p>
            You are locking <code className="value-code">{flag.flagKey}</code>.
          </p>
          <p className="warning-text">
            Every workgroup will receive the default value (<code className="value-code">{formatValue(flag.defaultValue)}</code>).
            Overrides will be kept but IGNORED while the flag is locked.
          </p>
        </ConfirmModal>
      );
    case 'unlock':
      return (
        <ConfirmModal title="Unlock flag" confirmLabel="Unlock flag" {...common}>
          <p>
            You are unlocking <code className="value-code">{flag.flagKey}</code>.
          </p>
          <p className="warning-text">
            Workgroup overrides ({flag.overrides.length}) will apply again immediately.
          </p>
        </ConfirmModal>
      );
    case 'archive':
      return (
        <ConfirmModal title="Archive flag" confirmLabel="Archive flag" danger {...common}>
          <p>
            Archive <code className="value-code">{flag.flagKey}</code>? The flag will no longer be evaluated for any
            workgroup. This is a global change; you can unarchive it later.
          </p>
        </ConfirmModal>
      );
    case 'unarchive':
      return (
        <ConfirmModal title="Unarchive flag" confirmLabel="Unarchive flag" {...common}>
          <p>
            Restore <code className="value-code">{flag.flagKey}</code> from the archive? It will be evaluated again
            for all workgroups.
          </p>
        </ConfirmModal>
      );
    case 'delete':
      return (
        <ConfirmModal title="Delete flag" confirmLabel="Delete permanently" danger {...common}>
          <p className="warning-text">
            Permanently delete <code className="value-code">{flag.flagKey}</code> and all its overrides? This cannot
            be undone.
          </p>
        </ConfirmModal>
      );
    case 'saveOverride':
      return (
        <ConfirmModal
          title={pending.isNew ? 'Add override' : 'Update override'}
          confirmLabel="Save override"
          {...common}
        >
          <p>
            Set override for workgroup <code className="value-code">{pending.workgroupId}</code>
            {pending.note ? ` (${pending.note})` : ''}:
          </p>
          <p>
            default: <code className="value-code">{formatValue(flag.defaultValue)}</code>, override:{' '}
            <code className="value-code">{formatValue(pending.value)}</code>
          </p>
          <p className="warning-text">Scope: this single workgroup only.</p>
        </ConfirmModal>
      );
    case 'deleteOverride':
      return (
        <ConfirmModal title="Remove override" confirmLabel="Remove override" danger {...common}>
          <p>
            Remove the override for workgroup <code className="value-code">{pending.workgroupId}</code>?
          </p>
          <p className="warning-text">
            The workgroup will fall back to the default value (
            <code className="value-code">{formatValue(flag.defaultValue)}</code>). Scope: this single workgroup only.
          </p>
        </ConfirmModal>
      );
  }
}
