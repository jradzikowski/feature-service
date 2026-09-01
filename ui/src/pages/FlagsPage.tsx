import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../api';
import { ApiError, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import {
  EmptyState,
  ErrorBox,
  FieldErrors,
  FlagStatusBadges,
  KindBadge,
  Loading,
  Toggle,
  ValueCode,
  ValueField,
} from '../components/ui';
import { useToast } from '../toast';
import { FLAG_KINDS, VALUE_TYPES, type CreateFlagRequest, type FlagKind, type FlagResponse, type ValueType } from '../types';
import { formatDate, isStale, parseValueInput } from '../values';

const FLAG_KEY_RE = /^[a-z0-9]+(?:[.-][a-z0-9]+)*$/;

export function FlagsPage() {
  const { slug = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();

  const [flags, setFlags] = useState<FlagResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  // filters
  const [search, setSearch] = useState('');
  const [kind, setKind] = useState<'' | FlagKind>('');
  const [showArchived, setShowArchived] = useState(false);
  const [onlyStale, setOnlyStale] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [toggleTarget, setToggleTarget] = useState<{ flag: FlagResponse; next: boolean } | null>(null);
  const [toggleBusy, setToggleBusy] = useState(false);

  const load = () => {
    setError(null);
    api
      .listFlags(slug, showArchived)
      .then(setFlags)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, [slug, showArchived]);

  const visible = useMemo(() => {
    if (!flags) return null;
    const q = search.trim().toLowerCase();
    return flags.filter((f) => {
      if (q && !f.flagKey.toLowerCase().includes(q) && !f.name.toLowerCase().includes(q) && !(f.owner ?? '').toLowerCase().includes(q)) {
        return false;
      }
      if (kind && f.flagKind !== kind) return false;
      if (onlyStale && !isStale(f)) return false;
      return true;
    });
  }, [flags, search, kind, onlyStale]);

  const confirmToggle = async () => {
    if (!toggleTarget) return;
    setToggleBusy(true);
    try {
      await api.updateFlag(slug, toggleTarget.flag.flagKey, { defaultValue: toggleTarget.next });
      toast.success(`Default value of ${toggleTarget.flag.flagKey} set to ${String(toggleTarget.next)}.`);
      setToggleTarget(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setToggleBusy(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Flags</h1>
        {isAdmin && (
          <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
            + New flag
          </button>
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
          <input type="checkbox" checked={onlyStale} onChange={(e) => setOnlyStale(e.target.checked)} />
          Only stale
        </label>
        <label className="checkbox-label">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
      </div>

      {error && <ErrorBox message={error} />}
      {!visible && !error && <Loading />}
      {visible && visible.length === 0 && <EmptyState>No flags match the current filters.</EmptyState>}

      {visible && visible.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Key</th>
              <th>Name</th>
              <th>Type</th>
              <th>Kind</th>
              <th>Default value</th>
              <th className="num">Overrides</th>
              <th>Status</th>
              <th>Expires</th>
              <th>Owner</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((flag) => (
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
                      checked={flag.defaultValue === true}
                      disabled={!isAdmin || flag.archived}
                      onChange={(next) => setToggleTarget({ flag, next })}
                      label={`Default value of ${flag.flagKey}`}
                    />
                  ) : (
                    <ValueCode value={flag.defaultValue} />
                  )}
                </td>
                <td className="num">{flag.overrideCount}</td>
                <td>
                  <FlagStatusBadges flag={flag} />
                </td>
                <td className={isStale(flag) ? 'nowrap warning-text' : 'nowrap'}>
                  {flag.expiresAt ? formatDate(flag.expiresAt) : '—'}
                </td>
                <td>{flag.owner ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {toggleTarget && (
        <ConfirmModal
          title="Change global default value"
          confirmLabel={`Set to ${String(toggleTarget.next)}`}
          danger
          busy={toggleBusy}
          onCancel={() => setToggleTarget(null)}
          onConfirm={confirmToggle}
        >
          <p>
            You are changing the default value of <code className="value-code">{toggleTarget.flag.flagKey}</code> from{' '}
            <code className="value-code">{String(toggleTarget.flag.defaultValue)}</code> to{' '}
            <code className="value-code">{String(toggleTarget.next)}</code>.
          </p>
          <p className="warning-text">
            This is a GLOBAL change — it affects every workgroup without its own override
            {toggleTarget.flag.overrideCount > 0
              ? ` (${toggleTarget.flag.overrideCount} workgroup${toggleTarget.flag.overrideCount === 1 ? ' has' : 's have'} an override and will keep it).`
              : '.'}
          </p>
        </ConfirmModal>
      )}

      {showCreate && (
        <CreateFlagModal
          slug={slug}
          onClose={() => setShowCreate(false)}
          onCreated={(flagKey) => {
            setShowCreate(false);
            toast.success(`Flag ${flagKey} created.`);
            load();
          }}
        />
      )}
    </div>
  );
}

function CreateFlagModal({
  slug,
  onClose,
  onCreated,
}: {
  slug: string;
  onClose: () => void;
  onCreated: (flagKey: string) => void;
}) {
  const [flagKey, setFlagKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [valueType, setValueType] = useState<ValueType>('BOOLEAN');
  const [defaultRaw, setDefaultRaw] = useState('false');
  const [flagKind, setFlagKind] = useState<FlagKind>('RELEASE');
  const [neverExpires, setNeverExpires] = useState(true);
  const [expiresAt, setExpiresAt] = useState('');
  const [owner, setOwner] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);
  const [busy, setBusy] = useState(false);

  const changeValueType = (next: ValueType) => {
    setValueType(next);
    setDefaultRaw(next === 'BOOLEAN' ? 'false' : '');
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const clientErrors: Record<string, string> = {};
    if (!FLAG_KEY_RE.test(flagKey)) {
      clientErrors.flagKey = 'Key must be lowercase alphanumeric segments separated by "." or "-", e.g. audit.ai-suggestions';
    }
    const parsed = parseValueInput(valueType, defaultRaw);
    if (!parsed.ok) clientErrors.defaultValue = parsed.error;
    if (!neverExpires && !expiresAt) {
      clientErrors.expiresAt = 'Pick an expiry date or check "Never expires".';
    }
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }
    setFieldErrors(null);

    const body: CreateFlagRequest = {
      flagKey,
      name,
      description: description || undefined,
      valueType,
      defaultValue: parsed.ok ? parsed.value : null,
      flagKind,
      expiresAt: neverExpires ? null : expiresAt,
      owner: owner || null,
    };
    setBusy(true);
    try {
      await api.createFlag(slug, body);
      onCreated(flagKey);
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) setFieldErrors(err.validationErrors);
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="New flag" onClose={onClose} wide>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="flag-key">Flag key</label>
            <input
              id="flag-key"
              type="text"
              value={flagKey}
              placeholder="e.g. audit.ai-suggestions"
              maxLength={128}
              required
              onChange={(e) => setFlagKey(e.target.value)}
            />
            <FieldErrors errors={fieldErrors} field="flagKey" />
          </div>
          <div className="form-row">
            <label htmlFor="flag-name">Name</label>
            <input
              id="flag-name"
              type="text"
              value={name}
              maxLength={255}
              required
              onChange={(e) => setName(e.target.value)}
            />
            <FieldErrors errors={fieldErrors} field="name" />
          </div>
        </div>
        <div className="form-row">
          <label htmlFor="flag-description">Description</label>
          <textarea
            id="flag-description"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
          <FieldErrors errors={fieldErrors} field="description" />
        </div>
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="flag-value-type">Value type</label>
            <select id="flag-value-type" value={valueType} onChange={(e) => changeValueType(e.target.value as ValueType)}>
              {VALUE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="form-row">
            <label htmlFor="flag-kind">Kind</label>
            <select id="flag-kind" value={flagKind} onChange={(e) => setFlagKind(e.target.value as FlagKind)}>
              {FLAG_KINDS.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
            <FieldErrors errors={fieldErrors} field="flagKind" />
          </div>
        </div>
        <div className="form-row">
          <label htmlFor="flag-default">Default value</label>
          <ValueField
            id="flag-default"
            valueType={valueType}
            raw={defaultRaw}
            onChange={setDefaultRaw}
            error={fieldErrors?.defaultValue}
          />
        </div>
        <div className="form-grid">
          <div className="form-row">
            <label htmlFor="flag-expires">Expires at</label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={neverExpires}
                onChange={(e) => {
                  setNeverExpires(e.target.checked);
                  if (e.target.checked) setExpiresAt('');
                }}
              />
              Never expires
            </label>
            <input
              id="flag-expires"
              type="date"
              value={expiresAt}
              disabled={neverExpires}
              onChange={(e) => setExpiresAt(e.target.value)}
            />
            <FieldErrors errors={fieldErrors} field="expiresAt" />
          </div>
          <div className="form-row">
            <label htmlFor="flag-owner">Owner</label>
            <input
              id="flag-owner"
              type="text"
              value={owner}
              maxLength={255}
              placeholder="team or person"
              onChange={(e) => setOwner(e.target.value)}
            />
            <FieldErrors errors={fieldErrors} field="owner" />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create flag'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
