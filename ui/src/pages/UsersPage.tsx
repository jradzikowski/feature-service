import { useEffect, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import * as api from '../api';
import { ApiError, errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import { Badge, EmptyState, ErrorBox, FieldErrors, Loading } from '../components/ui';
import { useToast } from '../toast';
import type { AdminRole, AdminUser } from '../types';

type PendingUserAction =
  | { type: 'toggleEnabled'; user: AdminUser }
  | { type: 'changeRole'; user: AdminUser; role: AdminRole };

export function UsersPage() {
  const { isAdmin, user: me } = useAuth();
  const toast = useToast();

  const [users, setUsers] = useState<AdminUser[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<AdminUser | null>(null);
  const [pending, setPending] = useState<PendingUserAction | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    api
      .listUsers()
      .then(setUsers)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(() => {
    if (isAdmin) load();
  }, [isAdmin]);

  if (!isAdmin) return <Navigate to="/" replace />;

  const runPending = async () => {
    if (!pending) return;
    setBusy(true);
    try {
      if (pending.type === 'toggleEnabled') {
        const next = !pending.user.enabled;
        await api.updateUser(pending.user.id, { enabled: next });
        toast.success(`User ${pending.user.username} ${next ? 'enabled' : 'disabled'}.`);
      } else {
        await api.updateUser(pending.user.id, { role: pending.role });
        toast.success(`Role of ${pending.user.username} changed to ${pending.role}.`);
      }
      setPending(null);
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
        <h1>Panel users</h1>
        <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
          + Add user
        </button>
      </div>

      {error && <ErrorBox message={error} />}
      {!users && !error && <Loading />}
      {users && users.length === 0 && <EmptyState>No users.</EmptyState>}

      {users && users.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Username</th>
              <th>Role</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((u) => {
              const isSelf = u.username === me?.username;
              return (
                <tr key={u.id} className={u.enabled ? undefined : 'row-muted'}>
                  <td>
                    {u.username}
                    {isSelf && <span className="muted"> (you)</span>}
                  </td>
                  <td>
                    <Badge tone={u.role === 'ADMIN' ? 'blue' : 'gray'}>{u.role}</Badge>
                  </td>
                  <td>
                    {u.enabled ? <Badge tone="green">ENABLED</Badge> : <Badge tone="red">DISABLED</Badge>}
                  </td>
                  <td className="row-actions">
                    <button
                      type="button"
                      className="btn btn-sm"
                      onClick={() =>
                        setPending({ type: 'changeRole', user: u, role: u.role === 'ADMIN' ? 'VIEWER' : 'ADMIN' })
                      }
                      disabled={isSelf}
                      title={isSelf ? 'You cannot change your own role' : undefined}
                    >
                      Make {u.role === 'ADMIN' ? 'VIEWER' : 'ADMIN'}
                    </button>
                    <button type="button" className="btn btn-sm" onClick={() => setPasswordTarget(u)}>
                      Change password
                    </button>
                    <button
                      type="button"
                      className={u.enabled ? 'btn btn-sm btn-danger' : 'btn btn-sm'}
                      onClick={() => setPending({ type: 'toggleEnabled', user: u })}
                      disabled={isSelf}
                      title={isSelf ? 'You cannot disable your own account' : undefined}
                    >
                      {u.enabled ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {showCreate && (
        <CreateUserModal
          onClose={() => setShowCreate(false)}
          onCreated={(username) => {
            setShowCreate(false);
            toast.success(`User ${username} created.`);
            load();
          }}
        />
      )}

      {passwordTarget && (
        <ChangePasswordModal
          user={passwordTarget}
          onClose={() => setPasswordTarget(null)}
          onSaved={() => {
            setPasswordTarget(null);
            toast.success(`Password of ${passwordTarget.username} changed.`);
          }}
        />
      )}

      {pending && (
        <ConfirmModal
          title={
            pending.type === 'toggleEnabled'
              ? pending.user.enabled
                ? 'Disable user'
                : 'Enable user'
              : 'Change role'
          }
          confirmLabel="Confirm"
          danger={pending.type === 'toggleEnabled' && pending.user.enabled}
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={runPending}
        >
          {pending.type === 'toggleEnabled' ? (
            <p>
              {pending.user.enabled
                ? `Disable ${pending.user.username}? They will no longer be able to sign in to the panel.`
                : `Enable ${pending.user.username}? They will be able to sign in again.`}
            </p>
          ) : (
            <p>
              Change the role of {pending.user.username} to <strong>{pending.role}</strong>?{' '}
              {pending.role === 'ADMIN'
                ? 'They will gain full write access to all applications, flags, tokens and users.'
                : 'They will lose all write access and keep read-only access.'}
            </p>
          )}
        </ConfirmModal>
      )}
    </div>
  );
}

function CreateUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: (username: string) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<AdminRole>('VIEWER');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors(null);
    try {
      await api.createUser(username.trim(), password, role);
      onCreated(username.trim());
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) setFieldErrors(err.validationErrors);
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Add user" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="user-name">Username</label>
          <input
            id="user-name"
            type="text"
            value={username}
            required
            autoFocus
            autoComplete="off"
            onChange={(e) => setUsername(e.target.value)}
          />
          <FieldErrors errors={fieldErrors} field="username" />
        </div>
        <div className="form-row">
          <label htmlFor="user-password">Password</label>
          <input
            id="user-password"
            type="password"
            value={password}
            required
            autoComplete="new-password"
            onChange={(e) => setPassword(e.target.value)}
          />
          <FieldErrors errors={fieldErrors} field="password" />
        </div>
        <div className="form-row">
          <label htmlFor="user-role">Role</label>
          <select id="user-role" value={role} onChange={(e) => setRole(e.target.value as AdminRole)}>
            <option value="VIEWER">VIEWER (read-only)</option>
            <option value="ADMIN">ADMIN (full access)</option>
          </select>
          <FieldErrors errors={fieldErrors} field="role" />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create user'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function ChangePasswordModal({
  user,
  onClose,
  onSaved,
}: {
  user: AdminUser;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.updateUser(user.id, { password });
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title={`Change password — ${user.username}`} onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="new-password">New password</label>
          <input
            id="new-password"
            type="password"
            value={password}
            required
            autoFocus
            autoComplete="new-password"
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Change password'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
