import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api';
import { ApiError, errorMessage } from '../api';
import { useAuth } from '../auth';
import { Modal } from '../components/Modal';
import { EmptyState, ErrorBox, FieldErrors, Loading } from '../components/ui';
import { useToast } from '../toast';
import type { ApplicationResponse } from '../types';
import { formatDate } from '../values';

const SLUG_RE = /^[a-z0-9-]{2,64}$/;

export function ApplicationsPage() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [apps, setApps] = useState<ApplicationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const load = () => {
    api
      .listApplications()
      .then(setApps)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, []);

  return (
    <div>
      <div className="page-header">
        <h1>Applications</h1>
        {isAdmin && (
          <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
            + Add application
          </button>
        )}
      </div>

      {error && <ErrorBox message={error} />}
      {!apps && !error && <Loading />}

      {apps && apps.length === 0 && <EmptyState>No applications yet.</EmptyState>}

      {apps && apps.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Slug</th>
              <th className="num">Flags</th>
              <th className="num">Config version</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {apps.map((app) => (
              <tr key={app.id}>
                <td>
                  <Link to={`/apps/${encodeURIComponent(app.slug)}/flags`} className="row-title">
                    {app.name}
                  </Link>
                </td>
                <td className="mono">{app.slug}</td>
                <td className="num">{app.flagCount}</td>
                <td className="num">{app.configVersion}</td>
                <td className="nowrap">{formatDate(app.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <CreateApplicationModal
          onClose={() => setShowCreate(false)}
          onCreated={(app) => {
            setShowCreate(false);
            toast.success(`Application "${app.name}" created.`);
            load();
          }}
        />
      )}
    </div>
  );
}

function CreateApplicationModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (app: ApplicationResponse) => void;
}) {
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors(null);
    if (!SLUG_RE.test(slug)) {
      setFieldErrors({ slug: 'Slug must be 2–64 characters: lowercase letters, digits and hyphens.' });
      return;
    }
    setBusy(true);
    try {
      const app = await api.createApplication(slug, name);
      onCreated(app);
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) setFieldErrors(err.validationErrors);
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Add application" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="app-slug">Slug</label>
          <input
            id="app-slug"
            type="text"
            value={slug}
            placeholder="e.g. audit"
            required
            onChange={(e) => setSlug(e.target.value)}
          />
          <FieldErrors errors={fieldErrors} field="slug" />
        </div>
        <div className="form-row">
          <label htmlFor="app-name">Name</label>
          <input
            id="app-name"
            type="text"
            value={name}
            maxLength={255}
            required
            onChange={(e) => setName(e.target.value)}
          />
          <FieldErrors errors={fieldErrors} field="name" />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
