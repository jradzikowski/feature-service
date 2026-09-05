import { useEffect, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import { EmptyState, ErrorBox, Loading } from '../components/ui';
import { useToast } from '../toast';
import type { WorkgroupResponse } from '../types';
import { isValidUuid } from '../values';

export function WorkgroupsPage() {
  const { isAdmin } = useAuth();
  const toast = useToast();

  const [workgroups, setWorkgroups] = useState<WorkgroupResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nameQuery, setNameQuery] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [renameTarget, setRenameTarget] = useState<WorkgroupResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<WorkgroupResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const load = (q?: string) => {
    api
      .listWorkgroups(q)
      .then(setWorkgroups)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(() => { load(); }, []);

  if (!isAdmin) return <Navigate to="/" replace />;

  const search = (e: FormEvent) => {
    e.preventDefault();
    load(nameQuery.trim() || undefined);
  };

  const doDelete = async () => {
    if (!deleteTarget) return;
    setBusy(true);
    try {
      await api.deleteWorkgroup(deleteTarget.id);
      toast.success(`Workgroup "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      load(nameQuery.trim() || undefined);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Workgroups</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Register workgroup
        </button>
      </div>
      <p className="muted">Named registry of workgroup UUIDs from your application. Allows searching by name instead of UUID.</p>

      <form className="toolbar" onSubmit={search}>
        <input
          type="search"
          placeholder="Search by name…"
          value={nameQuery}
          onChange={(e) => setNameQuery(e.target.value)}
        />
        <button type="submit" className="btn">Search</button>
        {nameQuery && (
          <button type="button" className="btn" onClick={() => { setNameQuery(''); load(); }}>
            Clear
          </button>
        )}
      </form>

      {error && <ErrorBox message={error} />}
      {!workgroups && !error && <Loading />}
      {workgroups && workgroups.length === 0 && <EmptyState>No workgroups registered yet.</EmptyState>}

      {workgroups && workgroups.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>ID (external UUID)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {workgroups.map((w) => (
              <tr key={w.id}>
                <td>{w.name}</td>
                <td className="mono">{w.id}</td>
                <td className="row-actions">
                  <button type="button" className="btn btn-sm" onClick={() => setRenameTarget(w)}>
                    Rename
                  </button>
                  <button type="button" className="btn btn-sm btn-danger" onClick={() => setDeleteTarget(w)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <RegisterWorkgroupModal
          onClose={() => setShowCreate(false)}
          onCreated={(name) => {
            setShowCreate(false);
            toast.success(`Workgroup "${name}" registered.`);
            load(nameQuery.trim() || undefined);
          }}
        />
      )}

      {renameTarget && (
        <RenameWorkgroupModal
          workgroup={renameTarget}
          onClose={() => setRenameTarget(null)}
          onSaved={(name) => {
            setRenameTarget(null);
            toast.success(`Workgroup renamed to "${name}".`);
            load(nameQuery.trim() || undefined);
          }}
        />
      )}

      {deleteTarget && (
        <ConfirmModal
          title="Delete workgroup"
          confirmLabel="Delete workgroup"
          danger
          busy={busy}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={doDelete}
        >
          <p>
            Delete workgroup <strong>{deleteTarget.name}</strong>?
          </p>
          <p className="muted mono">{deleteTarget.id}</p>
          <p className="warning-text">Plan assignments for this workgroup will also be removed.</p>
        </ConfirmModal>
      )}
    </div>
  );
}

function RegisterWorkgroupModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (name: string) => void;
}) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [idError, setIdError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const trimmedId = id.trim();
    if (!isValidUuid(trimmedId)) {
      setIdError('Must be a valid UUID, e.g. 123e4567-e89b-12d3-a456-426614174000');
      return;
    }
    setIdError(null);
    setError(null);
    setBusy(true);
    try {
      await api.createWorkgroup(trimmedId, name.trim());
      onCreated(name.trim());
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Register workgroup" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="wg-id">Workgroup UUID</label>
          <input
            id="wg-id"
            type="text"
            className="mono"
            value={id}
            required
            autoFocus
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            autoComplete="off"
            onChange={(e) => { setId(e.target.value); setIdError(null); }}
          />
          {idError && <div className="field-error">{idError}</div>}
        </div>
        <div className="form-row">
          <label htmlFor="wg-name">Name</label>
          <input
            id="wg-name"
            type="text"
            value={name}
            required
            maxLength={255}
            placeholder="e.g. Acme Corp"
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Registering…' : 'Register'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function RenameWorkgroupModal({
  workgroup,
  onClose,
  onSaved,
}: {
  workgroup: WorkgroupResponse;
  onClose: () => void;
  onSaved: (name: string) => void;
}) {
  const [name, setName] = useState(workgroup.name);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.renameWorkgroup(workgroup.id, name.trim());
      onSaved(name.trim());
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title={`Rename workgroup`} onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <p className="muted mono">{workgroup.id}</p>
        <div className="form-row">
          <label htmlFor="wg-rename">New name</label>
          <input
            id="wg-rename"
            type="text"
            value={name}
            required
            maxLength={255}
            autoFocus
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
