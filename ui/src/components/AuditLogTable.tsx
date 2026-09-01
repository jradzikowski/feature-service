import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../api';
import { errorMessage } from '../api';
import type { AuditLogPage } from '../types';
import { formatDateTime, formatValue } from '../values';
import { Badge, EmptyState, ErrorBox, Loading, Pagination } from './ui';

const PAGE_SIZE = 20;

/**
 * Audit log table with pagination.
 * When `fixedFlagKey` is set the log is filtered to that flag and no filter input is shown;
 * otherwise a flagKey filter input is rendered (application-wide audit log).
 */
export function AuditLogTable({ slug, fixedFlagKey }: { slug: string; fixedFlagKey?: string }) {
  const [pageData, setPageData] = useState<AuditLogPage | null>(null);
  const [page, setPage] = useState(0);
  const [filterInput, setFilterInput] = useState('');
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const effectiveFlagKey = fixedFlagKey ?? (filter || undefined);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .getAuditLog(slug, { flagKey: effectiveFlagKey, page, size: PAGE_SIZE })
      .then((data) => {
        if (!cancelled) setPageData(data);
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [slug, effectiveFlagKey, page]);

  return (
    <div>
      {fixedFlagKey === undefined && (
        <form
          className="toolbar"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setFilter(filterInput.trim());
          }}
        >
          <input
            type="text"
            placeholder="Filter by flag key…"
            value={filterInput}
            onChange={(e) => setFilterInput(e.target.value)}
          />
          <button type="submit" className="btn">
            Filter
          </button>
          {filter && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setFilterInput('');
                setFilter('');
                setPage(0);
              }}
            >
              Clear
            </button>
          )}
        </form>
      )}

      {error && <ErrorBox message={error} />}
      {loading && !pageData && <Loading />}

      {pageData && (
        <>
          {pageData.content.length === 0 ? (
            <EmptyState>No audit log entries.</EmptyState>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Operation</th>
                  {fixedFlagKey === undefined && <th>Flag</th>}
                  <th>Workgroup</th>
                  <th>Change</th>
                  <th>Actor</th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((entry, i) => (
                  <tr key={`${entry.createdAt}-${i}`}>
                    <td className="nowrap">{formatDateTime(entry.createdAt)}</td>
                    <td>
                      <Badge tone="blue">{entry.operation}</Badge>
                    </td>
                    {fixedFlagKey === undefined && (
                      <td>
                        {entry.flagKey ? (
                          <Link to={`/apps/${encodeURIComponent(slug)}/flags/${encodeURIComponent(entry.flagKey)}`}>
                            {entry.flagKey}
                          </Link>
                        ) : (
                          '—'
                        )}
                      </td>
                    )}
                    <td className="mono">{entry.workgroupId ?? '—'}</td>
                    <td>
                      {entry.oldValue === null && entry.newValue === null ? (
                        '—'
                      ) : (
                        <span className="change">
                          <code className="value-code">{formatValue(entry.oldValue ?? null)}</code>
                          <span className="change-arrow">→</span>
                          <code className="value-code">{formatValue(entry.newValue ?? null)}</code>
                        </span>
                      )}
                    </td>
                    <td>{entry.actorUsername}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination
            page={pageData.number}
            totalPages={pageData.totalPages}
            totalElements={pageData.totalElements}
            onPage={setPage}
          />
        </>
      )}
    </div>
  );
}
