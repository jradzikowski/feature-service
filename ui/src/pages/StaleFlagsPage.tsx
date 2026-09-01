import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import { EmptyState, ErrorBox, FlagStatusBadges, KindBadge, Loading } from '../components/ui';
import type { FlagResponse } from '../types';
import { formatDate } from '../values';

export function StaleFlagsPage() {
  const { slug = '' } = useParams();
  const [flags, setFlags] = useState<FlagResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .listStaleFlags(slug)
      .then((data) => {
        if (!cancelled) setFlags(data);
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [slug]);

  return (
    <div>
      <div className="page-header">
        <h1>Stale flags</h1>
      </div>
      <p className="muted">
        Flags past their expiry date, plus RELEASE/EXPERIMENT flags older than 40 days. Review this list weekly and
        clean up: remove the flag from code, then archive and delete it here.
      </p>

      {error && <ErrorBox message={error} />}
      {!flags && !error && <Loading />}
      {flags && flags.length === 0 && <EmptyState>No stale flags — nice and tidy.</EmptyState>}

      {flags && flags.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Key</th>
              <th>Name</th>
              <th>Kind</th>
              <th>Status</th>
              <th>Expires</th>
              <th>Created</th>
              <th>Owner</th>
            </tr>
          </thead>
          <tbody>
            {flags.map((flag) => (
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
                <td>
                  <KindBadge kind={flag.flagKind} />
                </td>
                <td>
                  <FlagStatusBadges flag={flag} />
                </td>
                <td className="nowrap">{formatDate(flag.expiresAt)}</td>
                <td className="nowrap">{formatDate(flag.createdAt)}</td>
                <td>{flag.owner ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
