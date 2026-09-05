import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import { EmptyState, ErrorBox, Loading } from '../components/ui';
import { useToast } from '../toast';
import type { PlanResponse } from '../types';
import { formatDateTime } from '../values';

export function PlansPage() {
  const { slug = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();

  const [plans, setPlans] = useState<PlanResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<PlanResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    api
      .listPlans(slug)
      .then(setPlans)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, [slug]);

  const doDelete = async () => {
    if (!deleteTarget) return;
    setBusy(true);
    try {
      await api.deletePlan(slug, deleteTarget.id);
      toast.success(`Plan "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Plans</h1>
        {isAdmin && (
          <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
            + Create plan
          </button>
        )}
      </div>
      <p className="muted">
        Plans define a set of flag values. Assign a plan to a workgroup to control which features they receive.
      </p>

      {error && <ErrorBox message={error} />}
      {!plans && !error && <Loading />}
      {plans && plans.length === 0 && <EmptyState>No plans yet. Create one to get started.</EmptyState>}

      {plans && plans.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Description</th>
              <th>Flags</th>
              <th>Created</th>
              {isAdmin && <th />}
            </tr>
          </thead>
          <tbody>
            {plans.map((p) => (
              <tr key={p.id}>
                <td>
                  <Link to={`/apps/${encodeURIComponent(slug)}/plans/${encodeURIComponent(p.id)}`} className="row-title">
                    {p.name}
                  </Link>
                </td>
                <td className="muted">{p.description ?? '—'}</td>
                <td>{p.flagCount}</td>
                <td className="nowrap">{formatDateTime(p.createdAt)}</td>
                {isAdmin && (
                  <td className="row-actions">
                    <Link
                      to={`/apps/${encodeURIComponent(slug)}/plans/${encodeURIComponent(p.id)}`}
                      className="btn btn-sm"
                    >
                      View
                    </Link>
                    <button type="button" className="btn btn-sm btn-danger" onClick={() => setDeleteTarget(p)}>
                      Delete
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <CreatePlanModal
          slug={slug}
          onClose={() => setShowCreate(false)}
          onCreated={(name) => {
            setShowCreate(false);
            toast.success(`Plan "${name}" created.`);
            load();
          }}
        />
      )}

      {deleteTarget && (
        <ConfirmModal
          title="Delete plan"
          confirmLabel="Delete plan"
          danger
          busy={busy}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={doDelete}
        >
          <p>
            Delete plan <strong>{deleteTarget.name}</strong>?
          </p>
          <p className="warning-text">All workgroup assignments for this plan will be removed.</p>
        </ConfirmModal>
      )}
    </div>
  );
}

function CreatePlanModal({
  slug,
  onClose,
  onCreated,
}: {
  slug: string;
  onClose: () => void;
  onCreated: (name: string) => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.createPlan(slug, name.trim(), description.trim() || undefined);
      onCreated(name.trim());
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Create plan" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="plan-name">Name</label>
          <input
            id="plan-name"
            type="text"
            value={name}
            required
            maxLength={255}
            autoFocus
            placeholder="e.g. Starter, Pro, Enterprise"
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="form-row">
          <label htmlFor="plan-desc">Description (optional)</label>
          <input
            id="plan-desc"
            type="text"
            value={description}
            maxLength={512}
            placeholder="Short description of this plan"
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create plan'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
