import { lazy, Suspense, useEffect } from 'react';
import { Routes, Route, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import { api, logout } from './api/client';
import type { IntegrationProvider } from './api/types';
import { useDashboard } from './hooks/useDashboard';
import type { DashboardData } from './hooks/useDashboard';
import './styles.css';

const Login = lazy(() => import('./pages/Login'));
const Signup = lazy(() => import('./pages/Signup'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const CommandCenter = lazy(() => import('./pages/CommandCenter'));
const AIBriefing = lazy(() => import('./pages/AIBriefing'));
const AIMemory = lazy(() => import('./pages/AIMemory'));
const AIEngineer = lazy(() => import('./pages/AIEngineer'));
const EngineeringDnaPage = lazy(() => import('./pages/EngineeringDnaPage'));
const Incidents = lazy(() => import('./pages/Incidents'));
const ArchitecturePage = lazy(() => import('./pages/ArchitecturePage'));
const BoardReport = lazy(() => import('./pages/BoardReport'));
const Integrations = lazy(() => import('./pages/Integrations'));
const OperatorReliability = lazy(() => import('./pages/OperatorReliability'));
const DeploymentSimulator = lazy(() => import('./pages/DeploymentSimulator'));
const AIInvestigationRoom = lazy(() => import('./pages/AIInvestigationRoom'));
const ExecutiveMode = lazy(() => import('./pages/ExecutiveMode'));
const RiskGlobePage = lazy(() => import('./pages/RiskGlobePage'));
const Team = lazy(() => import('./pages/Team'));
const Settings = lazy(() => import('./pages/Settings'));
const Analytics = lazy(() => import('./pages/Analytics'));
const KnowledgeBase = lazy(() => import('./pages/KnowledgeBase'));

function useIntegrationOAuthCallback() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const provider = searchParams.get('integrationProvider') as IntegrationProvider | null;
    if (!provider) return;
    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const oauthError = searchParams.get('integrationError');
    setSearchParams({}, { replace: true });

    if (oauthError) {
      navigate('/integrations');
      return;
    }

    api
      .installIntegration(provider, { code, state })
      .finally(() => navigate('/integrations'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
}

function AuthGate({ onAuthenticated }: { onAuthenticated: () => void }) {
  return (
    <Suspense fallback={<div className="full-screen-state">Loading...</div>}>
      <Routes>
        <Route path="/login" element={<Login onAuthenticated={onAuthenticated} />} />
        <Route path="/signup" element={<Signup onAuthenticated={onAuthenticated} />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </Suspense>
  );
}

function AuthenticatedApp({ data, refresh }: { data: DashboardData; refresh: () => void }) {
  useIntegrationOAuthCallback();

  function handleLogout() {
    logout();
    refresh();
  }

  return (
    <div className="shell">
      <Sidebar health={data.engineeringHealth} incidentCount={data.incidentCount} riskCount={data.riskCount} />
      <div className="main">
        <TopBar
          header={data.header}
          notificationCount={data.businessImpact.attentionIntegrations + data.businessImpact.failedWebhooks}
          onLogout={handleLogout}
        />

        <Suspense fallback={<div className="page-empty-state">Loading...</div>}>
          <Routes>
            <Route path="/" element={<CommandCenter data={data} refresh={refresh} />} />
            <Route path="/briefing" element={<AIBriefing />} />
            <Route path="/memory" element={<AIMemory />} />
            <Route path="/pr-review" element={<AIEngineer />} />
            <Route path="/engineering-dna" element={<EngineeringDnaPage />} />
            <Route path="/incidents" element={<Incidents />} />
            <Route path="/architecture" element={<ArchitecturePage />} />
            <Route path="/board-report" element={<BoardReport />} />
            <Route path="/integrations" element={<Integrations />} />
            <Route path="/operator" element={<OperatorReliability />} />
            <Route path="/simulator" element={<DeploymentSimulator />} />
            <Route path="/investigation" element={<AIInvestigationRoom />} />
            <Route path="/executive" element={<ExecutiveMode />} />
            <Route path="/risk-globe" element={<RiskGlobePage />} />
            <Route path="/team" element={<Team />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/knowledge-base" element={<KnowledgeBase />} />
            <Route path="/login" element={<Navigate to="/" replace />} />
            <Route path="/signup" element={<Navigate to="/" replace />} />
            <Route path="/forgot-password" element={<Navigate to="/" replace />} />
            <Route path="/reset-password" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </div>
    </div>
  );
}

function App() {
  const dashboard = useDashboard();
  const { data, error, loading, needsLogin, refresh } = dashboard;

  if (needsLogin) {
    return <AuthGate onAuthenticated={refresh} />;
  }

  if (loading && !data) {
    return <div className="full-screen-state">Connecting to Sentinel AI...</div>;
  }

  if (error && !data) {
    return (
      <div className="full-screen-state">
        Could not reach the Sentinel AI backend at <code>/api</code>.
        <br />
        {error}
      </div>
    );
  }

  if (!data) {
    return null;
  }

  return <AuthenticatedApp data={data} refresh={refresh} />;
}

export default App;
