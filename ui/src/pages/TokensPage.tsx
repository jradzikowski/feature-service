import { useEffect, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import { Badge, EmptyState, ErrorBox, Loading } from '../components/ui';
import { useToast } from '../toast';
import type { TokenCreatedResponse, TokenResponse } from '../types';
import { formatDateTime } from '../values';

export function TokensPage() {
  const { slug = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();

  const [tokens, setTokens] = useState<TokenResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [created, setCreated] = useState<TokenCreatedResponse | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<TokenResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    api
      .listTokens(slug)
      .then(setTokens)
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, [slug]);

  const doRevoke = async () => {
    if (!revokeTarget) return;
    setBusy(true);
    try {
      await api.revokeToken(revokeTarget.id);
      toast.success(`Token "${revokeTarget.name}" revoked.`);
      setRevokeTarget(null);
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
        <h1>Backend tokens</h1>
        {isAdmin && (
          <button type="button" className="btn btn-primary" onClick={() => setShowCreate(true)}>
            + Generate token
          </button>
        )}
      </div>
      <p className="muted">
        Bearer tokens used by application backends to call the evaluation API.{' '}
        <span
          className="tooltip-hint"
          title="Rotation: generate a new token, update the consumer's environment, then revoke the old one."
        >
          How do I rotate a token?
        </span>
      </p>

      {error && <ErrorBox message={error} />}
      {!tokens && !error && <Loading />}
      {tokens && tokens.length === 0 && <EmptyState>No tokens yet.</EmptyState>}

      {tokens && tokens.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Prefix</th>
              <th>Created</th>
              <th>Status</th>
              {isAdmin && <th />}
            </tr>
          </thead>
          <tbody>
            {tokens.map((t) => (
              <tr key={t.id} className={t.revokedAt ? 'row-muted' : undefined}>
                <td>{t.name}</td>
                <td className="mono">{t.tokenPrefix}…</td>
                <td className="nowrap">{formatDateTime(t.createdAt)}</td>
                <td>
                  {t.revokedAt ? (
                    <Badge tone="red" title={`Revoked ${formatDateTime(t.revokedAt)}`}>
                      REVOKED
                    </Badge>
                  ) : (
                    <Badge tone="green">ACTIVE</Badge>
                  )}
                </td>
                {isAdmin && (
                  <td className="row-actions">
                    {!t.revokedAt && (
                      <button type="button" className="btn btn-sm btn-danger" onClick={() => setRevokeTarget(t)}>
                        Revoke
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <CreateTokenModal
          slug={slug}
          onClose={() => setShowCreate(false)}
          onCreated={(token) => {
            setShowCreate(false);
            setCreated(token);
            load();
          }}
        />
      )}

      {created && <TokenCreatedModal token={created} onClose={() => setCreated(null)} />}

      {revokeTarget && (
        <ConfirmModal
          title="Revoke token"
          confirmLabel="Revoke token"
          danger
          busy={busy}
          onCancel={() => setRevokeTarget(null)}
          onConfirm={doRevoke}
        >
          <p>
            Revoke token <strong>{revokeTarget.name}</strong> (<span className="mono">{revokeTarget.tokenPrefix}…</span>)?
          </p>
          <p className="warning-text">
            Any backend still using this token will immediately lose access to the evaluation API. This cannot be
            undone.
          </p>
        </ConfirmModal>
      )}
    </div>
  );
}

function CreateTokenModal({
  slug,
  onClose,
  onCreated,
}: {
  slug: string;
  onClose: () => void;
  onCreated: (token: TokenCreatedResponse) => void;
}) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const token = await api.createToken(slug, name.trim());
      onCreated(token);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Generate token" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="token-name">Token name</label>
          <input
            id="token-name"
            type="text"
            value={name}
            maxLength={255}
            placeholder={`e.g. ${slug}-backend-prod`}
            required
            autoFocus
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Generating…' : 'Generate'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function TokenCreatedModal({ token, onClose }: { token: TokenCreatedResponse; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(token.token);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard unavailable (e.g. insecure context) — user can select the text manually.
    }
  };

  return (
    <Modal
      title="Token created"
      onClose={onClose}
      footer={
        <button type="button" className="btn btn-primary" onClick={onClose}>
          Done
        </button>
      }
    >
      <p>
        Token <strong>{token.name}</strong> has been created.
      </p>
      <div className="token-box">
        <code className="mono token-value">{token.token}</code>
        <button type="button" className="btn btn-sm" onClick={copy}>
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
      <p className="warning-text">
        This is the ONLY time the full token is shown. Copy it now and store it securely — it cannot be retrieved
        later.
      </p>
    </Modal>
  );
}
