import { useEffect, useState } from 'react';
import { Building2, RotateCcw, Webhook, CheckCircle2, Circle, AlertTriangle } from 'lucide-react';
import { api } from '../api/client';
import type {
  BackgroundJob,
  BackgroundJobStatus,
  ErrorEventView,
  OrganizationProfile,
  WebhookDelivery,
  WebhookDeliveryStatus,
} from '../api/types';

const jobStatusTone: Record<BackgroundJobStatus, 'good' | 'warn' | 'bad'> = {
  SUCCEEDED: 'good',
  RUNNING: 'warn',
  QUEUED: 'warn',
  FAILED: 'bad',
  CANCELLED: 'bad',
};

const deliveryStatusTone: Record<WebhookDeliveryStatus, 'good' | 'warn' | 'bad'> = {
  SUCCEEDED: 'good',
  REPLAYED: 'good',
  RECEIVED: 'warn',
  REPLAY_QUEUED: 'warn',
  FAILED: 'bad',
  EXPIRED: 'bad',
};

function formatDateTime(iso: string | null) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function OperatorReliability() {
  const [org, setOrg] = useState<OrganizationProfile | null>(null);
  const [jobs, setJobs] = useState<BackgroundJob[]>([]);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [errors, setErrors] = useState<ErrorEventView[]>([]);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  function load(cancelled?: { current: boolean }) {
    Promise.all([api.organizationProfile(), api.backgroundJobs(), api.webhookDeliveries(), api.operatorErrors()])
      .then(([profile, jobList, deliveryList, errorList]) => {
        if (cancelled?.current) return;
        setOrg(profile);
        setJobs(jobList);
        setDeliveries(deliveryList);
        setErrors(errorList);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled?.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load operator reliability data');
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

  async function retryJob(id: number) {
    setBusyId(id);
    setError(null);
    try {
      const updated = await api.retryBackgroundJob(id);
      setJobs((prev) => prev.map((j) => (j.id === id ? updated : j)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to retry job');
    } finally {
      setBusyId(null);
    }
  }

  async function replayDelivery(id: number) {
    setBusyId(id);
    setError(null);
    try {
      const updated = await api.replayWebhookDelivery(id);
      setDeliveries((prev) => prev.map((d) => (d.id === id ? updated : d)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to replay webhook delivery');
    } finally {
      setBusyId(null);
    }
  }

  if (loading) {
    return <div className="page-empty-state">Loading operator reliability data...</div>;
  }

  return (
    <div className="operator-page">
      {error ? <div className="integrations-error">{error}</div> : null}

      {org ? (
        <div className="panel operator-org-card">
          <div className="operator-org-header">
            <div className="operator-org-icon">
              <Building2 size={20} />
            </div>
            <div>
              <div className="operator-org-name">{org.organizationName}</div>
              <div className="operator-org-tenant">{org.tenantId}</div>
            </div>
            <span className="rec-badge tone-good">{org.workspaceStatus}</span>
          </div>

          <div className="operator-org-stats">
            <div>
              <span className="integration-stat-value">{org.deploymentCount}</span>
              <span className="integration-stat-label">Deployments</span>
            </div>
            <div>
              <span className="integration-stat-value">{org.prReviewCount}</span>
              <span className="integration-stat-label">PR Reviews</span>
            </div>
            <div>
              <span className="integration-stat-value">{org.architectureServiceCount}</span>
              <span className="integration-stat-label">Services</span>
            </div>
            <div>
              <span className="integration-stat-value">{org.auditEventCount}</span>
              <span className="integration-stat-label">Audit Events</span>
            </div>
            <div>
              <span className="integration-stat-value">{org.connectedIntegrationCount}</span>
              <span className="integration-stat-label">Integrations</span>
            </div>
          </div>

          {org.onboardingSteps.length > 0 ? (
            <div className="operator-onboarding">
              {org.onboardingSteps.map((step) => (
                <div key={step.label} className={`operator-onboarding-step${step.complete ? ' complete' : ''}`}>
                  {step.complete ? <CheckCircle2 size={14} /> : <Circle size={14} />}
                  <div>
                    <div className="operator-onboarding-label">{step.label}</div>
                    <div className="operator-onboarding-detail">{step.detail}</div>
                  </div>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}

      <div className="operator-body">
        <div className="panel operator-jobs">
          <div className="chart-card-header">
            <RotateCcw size={15} /> Background Jobs
          </div>
          {jobs.length === 0 ? (
            <div className="chart-empty">No background jobs recorded yet.</div>
          ) : (
            <div className="operator-list">
              {jobs.map((job) => (
                <div key={job.id} className="operator-row">
                  <span className={`rec-badge tone-${jobStatusTone[job.status]}`}>{job.status}</span>
                  <div className="operator-row-body">
                    <div className="operator-row-title">
                      {job.jobType.replace(/_/g, ' ')} &middot; {job.targetLabel}
                    </div>
                    <div className="operator-row-meta">
                      Attempt {job.attempts}/{job.maxAttempts} &middot; Next run {formatDateTime(job.nextRunAt)}
                      {job.lastError ? ` · ${job.lastError}` : ''}
                    </div>
                  </div>
                  <button className="action-btn" onClick={() => retryJob(job.id)} disabled={busyId === job.id}>
                    <RotateCcw size={13} /> {busyId === job.id ? 'Retrying...' : 'Retry'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="panel operator-webhooks">
          <div className="chart-card-header">
            <Webhook size={15} /> Webhook Deliveries
          </div>
          {deliveries.length === 0 ? (
            <div className="chart-empty">No webhook deliveries recorded yet.</div>
          ) : (
            <div className="operator-list">
              {deliveries.map((delivery) => (
                <div key={delivery.id} className="operator-row">
                  <span className={`rec-badge tone-${deliveryStatusTone[delivery.status]}`}>{delivery.status}</span>
                  <div className="operator-row-body">
                    <div className="operator-row-title">
                      {delivery.provider} &middot; {delivery.eventType}
                    </div>
                    <div className="operator-row-meta">
                      {delivery.replayEligibility} &middot; {delivery.replayAttempts}/{delivery.maxReplayAttempts} replays
                      {delivery.failureReason ? ` · ${delivery.failureReason}` : ''}
                    </div>
                  </div>
                  <button
                    className="action-btn"
                    onClick={() => replayDelivery(delivery.id)}
                    disabled={busyId === delivery.id || delivery.replayEligibility !== 'ready'}
                    title={delivery.replayEligibility !== 'ready' ? delivery.replayEligibility : 'Replay this delivery'}
                  >
                    <RotateCcw size={13} /> {busyId === delivery.id ? 'Replaying...' : 'Replay'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="panel operator-errors">
        <div className="chart-card-header">
          <AlertTriangle size={15} /> Recent Server Errors
        </div>
        {errors.length === 0 ? (
          <div className="chart-empty">No unhandled server errors recorded. 🎉</div>
        ) : (
          <div className="operator-list">
            {errors.map((err, index) => (
              <div key={`${err.requestId}-${index}`} className="operator-row">
                <span className="rec-badge tone-bad">500</span>
                <div className="operator-row-body">
                  <div className="operator-row-title">
                    {err.httpMethod ? `${err.httpMethod} ` : ''}
                    {err.path ?? '—'} &middot; {err.errorType.replace(/^.*\./, '')}
                  </div>
                  <div className="operator-row-meta">
                    {formatDateTime(err.occurredAt)}
                    {err.message ? ` · ${err.message}` : ''}
                    {err.requestId ? ` · req ${err.requestId}` : ''}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
