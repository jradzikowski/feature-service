import { useParams } from 'react-router-dom';
import { AuditLogTable } from '../components/AuditLogTable';

export function AuditLogPage() {
  const { slug = '' } = useParams();
  return (
    <div>
      <div className="page-header">
        <h1>Audit log</h1>
      </div>
      <p className="muted">Every configuration change in this application: who, when, and what changed.</p>
      <AuditLogTable slug={slug} />
    </div>
  );
}
