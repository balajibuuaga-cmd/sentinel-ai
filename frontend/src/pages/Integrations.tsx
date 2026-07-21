import { useEffect, useState } from 'react';
import { GitBranch, Kanban, Workflow, RefreshCw, Unplug, PlugZap, History } from 'lucide-react';
import { api } from '../api/client';
import type { IntegrationConnection, IntegrationProvider, IntegrationStatus, IntegrationSyncEvent } from '../api/types';

const providerIcon: Record<IntegrationProvider, typeof GitBranch> = {
  GITHUB: GitBranch,
  JIRA: Kanban,
  CI: Workflow,
};

const statusTone: Record<IntegrationStatus, 'good' | 'warn' | 'bad' | 'dim'> = {
  CONNECTED: 'good',
  AVAILABLE: 'dim',
  NEEDS_ATTENTION: 'warn',
  DISCONNECTED: 'bad',
};

const syncTone: Record<IntegrationSyncEvent['status'], 'good' | 'warn' | 'bad'> = {
  SUCCESS: 'good',
  DEGRADED: 'warn',
  FAILED: 'bad',
};

// A connection installed without provider credentials never performed an OAuth
// exchange and holds no token, so it cannot reach GitHub or Jira. The backend
// says so in statusDetail; surface it as a badge so a connected-looking card is
// never mistaken for a live one.
function isDemoConnection(connection: IntegrationConnection): boolean {
  return (
    connection.status === 'CONNECTED' &&
    connection.statusDetail.toLowerCase().includes('demo connection')
  );
}

function formatDateTime(iso: string | null) {
  if (!iso) return 'Never';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'Never';
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function Integrations() {
  const [connections, setConnections] = useState<IntegrationConnection[]>([]);
  const [history, setHistory] = useState<IntegrationSyncEvent[]>([]);
  const [busyId, setBusyId] = useState<number | 'install' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  function load(cancelled?: { current: boolean }) {
    Promise.all([api.integrationConnections(), api.integrationSyncHistory()])
      .then(([conns, syncHistory]) => {
        if (cancelled?.current) return;
        setConnections(conns);
        setHistory(syncHistory);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled?.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load integrations');
        setLoading(false);
      });
  }

  useEffect(() => {
    const cancelled = { current: false };
    load(cancelled);
    return () => {
      cancelled.current = true;
    };
  }, []);

  async function connect(provider: IntegrationProvider) {
    const connection = connections.find((c) => c.provider === provider);

    // With provider credentials configured, connecting means sending the browser
    // to the provider to authorize. Posting the install directly skips that, so
    // no authorization code ever comes back and the backend can only register a
    // demo connection. The callback handler in App.tsx completes the install
    // once the provider redirects back with a code.
    if (connection?.oauthAvailable && connection.installUrl) {
      window.location.assign(connection.installUrl);
      return;
    }

    setBusyId('install');
    setError(null);
    try {
      const updated = await api.installIntegration(provider, {});
      setConnections((prev) => {
        const exists = prev.some((c) => c.id === updated.id);
        return exists ? prev.map((c) => (c.id === updated.id ? updated : c)) : [...prev, updated];
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to connect ${provider}`);
    } finally {
      setBusyId(null);
    }
  }

  async function sync(id: number) {
    setBusyId(id);
    setError(null);
    try {
      const updated = await api.syncIntegration(id);
      setConnections((prev) => prev.map((c) => (c.id === id ? updated : c)));
      const syncHistory = await api.integrationSyncHistory();
      setHistory(syncHistory);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sync failed');
    } finally {
      setBusyId(null);
    }
  }

  async function disconnect(id: number) {
    setBusyId(id);
    setError(null);
    try {
      const updated = await api.disconnectIntegration(id);
      setConnections((prev) => prev.map((c) => (c.id === id ? updated : c)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to disconnect');
    } finally {
      setBusyId(null);
    }
  }

  if (loading) {
    return <div className="page-empty-state">Loading integrations...</div>;
  }

  return (
    <div className="integrations-page">
      {error ? <div className="integrations-error">{error}</div> : null}

      <div className="integrations-grid">
        {connections.map((connection) => {
          const Icon = providerIcon[connection.provider];
          const tone = statusTone[connection.status];
          const busy = busyId === connection.id || busyId === 'install';
          return (
            <div key={connection.id} className="panel integration-card">
              <div className="integration-card-header">
                <div className="integration-card-icon">
                  <Icon size={20} />
                </div>
                <div>
                  <div className="integration-card-name">{connection.displayName}</div>
                  <div className="integration-card-account">{connection.externalAccount ?? 'Not connected'}</div>
                </div>
                <span className={`rec-badge tone-${tone === 'dim' ? 'warn' : tone}`}>{connection.status.replace('_', ' ')}</span>
                {isDemoConnection(connection) ? (
                  <span className="rec-badge tone-warn" title="No OAuth exchange was performed; this connection cannot reach the provider.">
                    DEMO
                  </span>
                ) : null}
              </div>

              <div className="integration-card-stats">
                <div>
                  <span className="integration-stat-label">Health</span>
                  <span className="integration-stat-value">{connection.healthScore}%</span>
                </div>
                <div>
                  <span className="integration-stat-label">Last sync</span>
                  <span className="integration-stat-value">{formatDateTime(connection.lastSyncAt)}</span>
                </div>
                <div>
                  <span className="integration-stat-label">Scopes</span>
                  <span className="integration-stat-value">{connection.scopes || '—'}</span>
                </div>
              </div>

              {connection.statusDetail ? <p className="integration-card-detail">{connection.statusDetail}</p> : null}

              <div className="integration-card-actions">
                {connection.status === 'AVAILABLE' || connection.status === 'DISCONNECTED' ? (
                  <button className="action-btn tone-good" onClick={() => connect(connection.provider)} disabled={busy}>
                    <PlugZap size={14} /> {busy ? 'Connecting...' : 'Connect'}
                  </button>
                ) : (
                  <>
                    <button className="action-btn" onClick={() => sync(connection.id)} disabled={busy}>
                      <RefreshCw size={14} /> {busy ? 'Syncing...' : 'Sync Now'}
                    </button>
                    <button className="action-btn tone-bad" onClick={() => disconnect(connection.id)} disabled={busy}>
                      <Unplug size={14} /> Disconnect
                    </button>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <div className="panel integrations-history">
        <div className="chart-card-header">
          <History size={15} /> Sync History
        </div>
        {history.length === 0 ? (
          <div className="chart-empty">No sync events recorded yet.</div>
        ) : (
          <div className="integrations-history-list">
            {history.map((event) => (
              <div key={event.id} className="integrations-history-row">
                <span className={`rec-badge tone-${syncTone[event.status]}`}>{event.status}</span>
                <span className="integrations-history-provider">{event.provider}</span>
                <span className="integrations-history-detail">{event.detail}</span>
                <span className="integrations-history-meta">
                  {event.recordsInspected} records &middot; {event.latencyMs}ms &middot; {formatDateTime(event.createdAt)}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
