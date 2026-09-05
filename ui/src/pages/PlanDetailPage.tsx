import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { useAuth } from '../auth';
import { ConfirmModal, Modal } from '../components/Modal';
import { EmptyState, ErrorBox, Loading, ValueCode, ValueField } from '../components/ui';
import { useToast } from '../toast';
import type { FlagResponse, JsonValue, PlanDetailResponse } from '../types';
import { parseValueInput, valueToRaw } from '../values';

export function PlanDetailPage() {
  const { slug = '', planId = '' } = useParams();
  const { isAdmin } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [plan, setPlan] = useState<PlanDetailResponse | null>(null);
  const [allFlags, setAllFlags] = useState<FlagResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [showEdit, setShowEdit] = useState(false);
  const [showDelete, setShowDelete] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const [editFlagKey, setEditFlagKey] = useState<string | null>(null);
  const [removeFlagKey, setRemoveFlagKey] = useState<string | null>(null);
  const [removeBusy, setRemoveBusy] = useState(false);

  const load = () => {
    Promise.all([api.getPlan(slug, planId), api.listFlags(slug, false)])
      .then(([p, flags]) => {
        setPlan(p);
        setAllFlags(flags);
      })
      .catch((err) => setError(errorMessage(err)));
  };

  useEffect(load, [slug, planId]);

  const doDelete = async () => {
    setDeleteBusy(true);
    try {
      await api.deletePlan(slug, planId);
      toast.success(`Plan "${plan?.name}" deleted.`);
      navigate(`/apps/${encodeURIComponent(slug)}/plans`, { replace: true });
    } catch (err) {
      toast.error(errorMessage(err));
      setDeleteBusy(false);
    }
  };

  const doRemoveFlag = async () => {
    if (!removeFlagKey) return;
    setRemoveBusy(true);
    try {
      await api.removePlanFlag(slug, planId, removeFlagKey);
      toast.success(`Flag "${removeFlagKey}" removed from plan.`);
      setRemoveFlagKey(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setRemoveBusy(false);
    }
  };

  if (error) return <ErrorBox message={error} />;
  if (!plan || !allFlags) return <Loading />;

  const planFlagKeys = new Set(plan.flags.map((f) => f.flagKey));
  const availableFlags = allFlags.filter((f) => !planFlagKeys.has(f.flagKey));

  const editingFlag = editFlagKey ? plan.flags.find((f) => f.flagKey === editFlagKey) : null;
  const editingFlagDef = editFlagKey ? allFlags.find((f) => f.flagKey === editFlagKey) : null;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{plan.name}</h1>
          {plan.description && <p className="muted">{plan.description}</p>}
        </div>
        {isAdmin && (
          <div className="row-actions">
            <button type="button" className="btn" onClick={() => setShowEdit(true)}>
              Edit plan
            </button>
            <button type="button" className="btn btn-danger" onClick={() => setShowDelete(true)}>
              Delete plan
            </button>
          </div>
        )}
      </div>

      <h2>Flags in this plan ({plan.flags.length})</h2>
      {plan.flags.length === 0 && (
        <EmptyState>No flags in this plan yet. Add flags below.</EmptyState>
      )}
      {plan.flags.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Flag key</th>
              <th>Value in plan</th>
              {isAdmin && <th />}
            </tr>
          </thead>
          <tbody>
            {plan.flags.map((pf) => (
              <tr key={pf.flagKey}>
                <td className="mono">{pf.flagKey}</td>
                <td>
                  <ValueCode value={pf.value} />
                </td>
                {isAdmin && (
                  <td className="row-actions">
                    <button type="button" className="btn btn-sm" onClick={() => setEditFlagKey(pf.flagKey)}>
                      Edit
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={() => setRemoveFlagKey(pf.flagKey)}
                    >
                      Remove
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {isAdmin && availableFlags.length > 0 && (
        <>
          <h2>Add flag to plan</h2>
          <AddFlagForm
            slug={slug}
            planId={planId}
            availableFlags={availableFlags}
            onAdded={() => {
              toast.success('Flag added to plan.');
              load();
            }}
          />
        </>
      )}

      {showEdit && (
        <EditPlanModal
          plan={plan}
          slug={slug}
          onClose={() => setShowEdit(false)}
          onSaved={(updated) => {
            setPlan((prev) => prev ? { ...prev, name: updated.name, description: updated.description } : prev);
            setShowEdit(false);
            toast.success('Plan updated.');
          }}
        />
      )}

      {showDelete && (
        <ConfirmModal
          title="Delete plan"
          confirmLabel="Delete plan"
          danger
          busy={deleteBusy}
          onCancel={() => setShowDelete(false)}
          onConfirm={doDelete}
        >
          <p>Delete plan <strong>{plan.name}</strong>?</p>
          <p className="warning-text">All workgroup assignments for this plan will be removed.</p>
        </ConfirmModal>
      )}

      {editFlagKey && editingFlag && editingFlagDef && (
        <EditPlanFlagModal
          slug={slug}
          planId={planId}
          flagKey={editFlagKey}
          currentValue={editingFlag.value}
          valueType={editingFlagDef.valueType}
          onClose={() => setEditFlagKey(null)}
          onSaved={() => {
            setEditFlagKey(null);
            toast.success(`Value for "${editFlagKey}" updated.`);
            load();
          }}
        />
      )}

      {removeFlagKey && (
        <ConfirmModal
          title="Remove flag from plan"
          confirmLabel="Remove"
          danger
          busy={removeBusy}
          onCancel={() => setRemoveFlagKey(null)}
          onConfirm={doRemoveFlag}
        >
          <p>
            Remove <code className="value-code">{removeFlagKey}</code> from plan <strong>{plan.name}</strong>?
          </p>
          <p className="muted">Workgroups on this plan will fall back to the global default for this flag.</p>
        </ConfirmModal>
      )}
    </div>
  );
}

function AddFlagForm({
  slug,
  planId,
  availableFlags,
  onAdded,
}: {
  slug: string;
  planId: string;
  availableFlags: FlagResponse[];
  onAdded: () => void;
}) {
  const toast = useToast();
  const [selectedKey, setSelectedKey] = useState(availableFlags[0]?.flagKey ?? '');
  const [raw, setRaw] = useState('');
  const [valueError, setValueError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);

  const selectedFlag = availableFlags.find((f) => f.flagKey === selectedKey);

  useEffect(() => {
    if (selectedFlag) {
      setRaw(valueToRaw(selectedFlag.valueType, selectedFlag.defaultValue));
      setValueError(undefined);
    }
  }, [selectedKey, selectedFlag]);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!selectedFlag) return;
    const parsed = parseValueInput(selectedFlag.valueType, raw);
    if (!parsed.ok) {
      setValueError(parsed.error);
      return;
    }
    setBusy(true);
    setValueError(undefined);
    try {
      await api.setPlanFlag(slug, planId, selectedKey, parsed.value);
      onAdded();
      setSelectedKey(availableFlags[0]?.flagKey ?? '');
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (!selectedFlag) return null;

  return (
    <form className="toolbar" onSubmit={onSubmit} style={{ alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem' }}>
      <div className="form-row" style={{ margin: 0 }}>
        <label htmlFor="add-flag-key">Flag</label>
        <select
          id="add-flag-key"
          value={selectedKey}
          onChange={(e) => setSelectedKey(e.target.value)}
        >
          {availableFlags.map((f) => (
            <option key={f.flagKey} value={f.flagKey}>
              {f.flagKey} ({f.valueType})
            </option>
          ))}
        </select>
      </div>
      <div className="form-row" style={{ margin: 0 }}>
        <label>Value ({selectedFlag.valueType})</label>
        <ValueField
          valueType={selectedFlag.valueType}
          raw={raw}
          onChange={setRaw}
          error={valueError}
        />
      </div>
      <button type="submit" className="btn btn-primary" disabled={busy} style={{ alignSelf: 'center', marginTop: '1.4rem' }}>
        {busy ? 'Adding…' : 'Add to plan'}
      </button>
    </form>
  );
}

function EditPlanModal({
  plan,
  slug,
  onClose,
  onSaved,
}: {
  plan: PlanDetailResponse;
  slug: string;
  onClose: () => void;
  onSaved: (updated: PlanDetailResponse) => void;
}) {
  const [name, setName] = useState(plan.name);
  const [description, setDescription] = useState(plan.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = await api.updatePlan(slug, plan.id, {
        name: name.trim(),
        description: description.trim() || undefined,
      });
      onSaved(updated);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title="Edit plan" onClose={onClose}>
      <form onSubmit={onSubmit}>
        {error && <div className="error-box">{error}</div>}
        <div className="form-row">
          <label htmlFor="edit-plan-name">Name</label>
          <input
            id="edit-plan-name"
            type="text"
            value={name}
            required
            maxLength={255}
            autoFocus
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="form-row">
          <label htmlFor="edit-plan-desc">Description (optional)</label>
          <input
            id="edit-plan-desc"
            type="text"
            value={description}
            maxLength={512}
            onChange={(e) => setDescription(e.target.value)}
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

function EditPlanFlagModal({
  slug,
  planId,
  flagKey,
  currentValue,
  valueType,
  onClose,
  onSaved,
}: {
  slug: string;
  planId: string;
  flagKey: string;
  currentValue: JsonValue;
  valueType: import('../types').ValueType;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [raw, setRaw] = useState(() => valueToRaw(valueType, currentValue));
  const [valueError, setValueError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const parsed = parseValueInput(valueType, raw);
    if (!parsed.ok) {
      setValueError(parsed.error);
      return;
    }
    setBusy(true);
    try {
      await api.setPlanFlag(slug, planId, flagKey, parsed.value);
      onSaved();
    } catch (err) {
      toast.error(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <Modal title={`Edit plan value: ${flagKey}`} onClose={onClose}>
      <form onSubmit={onSubmit}>
        <div className="form-row">
          <label>Value ({valueType})</label>
          <ValueField valueType={valueType} raw={raw} onChange={setRaw} error={valueError} />
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
