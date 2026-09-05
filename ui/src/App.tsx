import type { ReactNode } from 'react';
import { Navigate, NavLink, Route, Routes, useMatch, useNavigate } from 'react-router-dom';
import { useAuth } from './auth';
import { Loading } from './components/ui';
import { ApplicationsPage } from './pages/ApplicationsPage';
import { AuditLogPage } from './pages/AuditLogPage';
import { FlagDetailPage } from './pages/FlagDetailPage';
import { FlagsPage } from './pages/FlagsPage';
import { LoginPage } from './pages/LoginPage';
import { PlanDetailPage } from './pages/PlanDetailPage';
import { PlansPage } from './pages/PlansPage';
import { StaleFlagsPage } from './pages/StaleFlagsPage';
import { TokensPage } from './pages/TokensPage';
import { UsersPage } from './pages/UsersPage';
import { WorkgroupPage } from './pages/WorkgroupPage';
import { WorkgroupsPage } from './pages/WorkgroupsPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="page-center"><Loading /></div>;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function Sidebar() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const match = useMatch('/apps/:slug/*');
  const slug = match?.params.slug;

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="sidebar-logo">⚑</span> Feature Flags
      </div>
      <nav className="sidebar-nav">
        <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
          Applications
        </NavLink>
        {slug && (
          <div className="sidebar-section">
            <div className="sidebar-section-title">{slug}</div>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/flags`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Flags
            </NavLink>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/stale`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Stale flags
            </NavLink>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/tokens`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Tokens
            </NavLink>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/audit`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Audit log
            </NavLink>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/workgroup`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Workgroup lookup
            </NavLink>
            <NavLink
              to={`/apps/${encodeURIComponent(slug)}/plans`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              Plans
            </NavLink>
          </div>
        )}
      </nav>
      <div className="sidebar-footer">
        {isAdmin && (
          <>
            <NavLink to="/users" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Users
            </NavLink>
            <NavLink to="/workgroups" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Workgroups
            </NavLink>
          </>
        )}
        <div className="sidebar-user">
          <div>
            <div className="sidebar-username">{user?.username}</div>
            <div className="sidebar-role">{user?.role}</div>
          </div>
          <button type="button" className="btn btn-sm" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </div>
    </aside>
  );
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="main-content">{children}</main>
    </div>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <Shell>
              <Routes>
                <Route path="/" element={<ApplicationsPage />} />
                <Route path="/apps/:slug/flags" element={<FlagsPage />} />
                <Route path="/apps/:slug/flags/:flagKey" element={<FlagDetailPage />} />
                <Route path="/apps/:slug/stale" element={<StaleFlagsPage />} />
                <Route path="/apps/:slug/tokens" element={<TokensPage />} />
                <Route path="/apps/:slug/audit" element={<AuditLogPage />} />
                <Route path="/apps/:slug/workgroup" element={<WorkgroupPage />} />
                <Route path="/apps/:slug/plans" element={<PlansPage />} />
                <Route path="/apps/:slug/plans/:planId" element={<PlanDetailPage />} />
                <Route path="/workgroups" element={<WorkgroupsPage />} />
                <Route path="/users" element={<UsersPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </Shell>
          </RequireAuth>
        }
      />
    </Routes>
  );
}
