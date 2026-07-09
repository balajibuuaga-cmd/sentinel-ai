import { useEffect, useMemo, useState } from 'react';
import { Search, AlertOctagon, Rocket, GitPullRequest, ScrollText, History, Sparkles } from 'lucide-react';
import { api } from '../api/client';
import type { AuditEvent, Deployment, Incident, PullRequestReview } from '../api/types';

const severityTone: Record<string, string> = { SEV1: 'bad', SEV2: 'warn', SEV3: 'good' };

function formatDateTime(iso: string | null) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export default function AIInvestigationRoom() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [prReviews, setPrReviews] = useState<PullRequestReview[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.incidents(), api.deployments(), api.prReviews(), api.auditEvents()])
      .then(([incidentList, deploymentList, prList, auditList]) => {
        if (cancelled) return;
        setIncidents(incidentList);
        setDeployments(deploymentList);
        setPrReviews(prList);
        setAuditEvents(auditList);
        if (incidentList.length > 0) setSelectedId(incidentList[0].id);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load investigation data');
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const selected = incidents.find((i) => i.id === selectedId) ?? null;

  const linkedDeployment = useMemo(
    () => (selected ? deployments.find((d) => d.id === selected.deploymentId) ?? null : null),
    [selected, deployments],
  );

  const linkedPrReview = useMemo(
    () => (selected ? prReviews.find((p) => p.linkedDeploymentId === selected.deploymentId) ?? null : null),
    [selected, prReviews],
  );

  const relatedAuditEvents = useMemo(() => {
    if (!selected) return [];
    return auditEvents
      .filter(
        (event) =>
          event.target.includes(selected.deploymentKey) ||
          event.target.includes(selected.serviceName) ||
          event.details.includes(selected.deploymentKey),
      )
      .slice(0, 5);
  }, [selected, auditEvents]);

  if (loading) {
    return <div className="page-empty-state">Assembling the investigation board...</div>;
  }

  if (error) {
    return <div className="page-empty-state">Could not load investigation data: {error}</div>;
  }

  return (
    <div className="investigation-page">
      <div className="panel investigation-picker">
        <div className="memory-list-header">
          <Search size={14} /> Select an incident to investigate
        </div>
        <div className="investigation-picker-list">
          {incidents.map((i) => (
            <button
              key={i.id}
              className={`incident-row${i.id === selectedId ? ' active' : ''}`}
              onClick={() => setSelectedId(i.id)}
            >
              <span className={`rec-badge tone-${severityTone[i.severity]}`}>{i.severity}</span>
              <div className="incident-row-body">
                <div className="incident-row-service">{i.serviceName}</div>
                <div className="incident-row-summary">{i.incidentKey}</div>
              </div>
              <span className="incident-row-status">{i.status}</span>
            </button>
          ))}
          {incidents.length === 0 ? <div className="service-detail-empty-line">No incidents to investigate.</div> : null}
        </div>
      </div>

      {selected ? (
        <div className="investigation-board">
          <div className="panel investigation-node investigation-node-center">
            <AlertOctagon size={18} className="tone-bad" />
            <div className="investigation-node-title">{selected.incidentKey}</div>
            <div className="investigation-node-sub">{selected.summary}</div>
            <div className="investigation-node-meta">
              {selected.serviceName} &middot; {selected.severity} &middot; risk {selected.riskScore}%
            </div>
          </div>

          <div className="investigation-connector" />

          <div className="investigation-evidence-row">
            <div className="panel investigation-node">
              <Rocket size={15} />
              <div className="investigation-node-title">Linked Deployment</div>
              {linkedDeployment ? (
                <>
                  <div className="investigation-node-sub">
                    {linkedDeployment.deploymentKey} &middot; {linkedDeployment.environment}
                  </div>
                  {linkedDeployment.riskAssessment ? (
                    <span className={`risk-pill risk-${linkedDeployment.riskAssessment.level.toLowerCase()}`}>
                      {linkedDeployment.riskAssessment.level} &middot; {linkedDeployment.riskAssessment.score}%
                    </span>
                  ) : null}
                  <div className="investigation-node-meta">
                    Dependencies: {linkedDeployment.dependencies.join(', ') || 'none recorded'}
                  </div>
                </>
              ) : (
                <div className="investigation-node-empty">No deployment record linked.</div>
              )}
            </div>

            <div className="panel investigation-node">
              <GitPullRequest size={15} />
              <div className="investigation-node-title">Linked Pull Request</div>
              {linkedPrReview ? (
                <>
                  <div className="investigation-node-sub">
                    {linkedPrReview.repository} #{linkedPrReview.prNumber}
                  </div>
                  <span className={`rec-badge tone-${linkedPrReview.recommendation === 'MERGE' ? 'good' : linkedPrReview.recommendation === 'WAIT' ? 'warn' : 'bad'}`}>
                    {linkedPrReview.recommendation}
                  </span>
                  <div className="investigation-node-meta">by {linkedPrReview.author}</div>
                </>
              ) : (
                <div className="investigation-node-empty">No PR review linked to this deployment yet.</div>
              )}
            </div>

            <div className="panel investigation-node">
              <ScrollText size={15} />
              <div className="investigation-node-title">Audit Trail</div>
              {relatedAuditEvents.length > 0 ? (
                <ul className="investigation-audit-list">
                  {relatedAuditEvents.map((event) => (
                    <li key={event.id}>
                      <b>{event.actor}</b> {event.action} &middot; {formatDateTime(event.createdAt)}
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="investigation-node-empty">No matching audit events found.</div>
              )}
            </div>

            <div className="panel investigation-node">
              <History size={15} />
              <div className="investigation-node-title">Incident Timeline</div>
              {selected.timeline.length > 0 ? (
                <ul className="investigation-audit-list">
                  {selected.timeline.slice(0, 5).map((event, i) => (
                    <li key={i}>
                      <b>{event.label}</b> &middot; {formatDateTime(event.occurredAt)}
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="investigation-node-empty">No timeline events recorded.</div>
              )}
            </div>
          </div>

          <div className="investigation-connector" />

          <div className="panel investigation-conclusion">
            <div className="investigation-conclusion-header">
              <Sparkles size={16} /> AI Conclusion
            </div>
            <p>{selected.commanderBrief}</p>
            <div className="investigation-conclusion-recommendation">
              <span className="ai-briefing-block-label">Recommended Action</span>
              <p>{selected.recommendedAction}</p>
            </div>
          </div>
        </div>
      ) : (
        <div className="page-empty-state">Select an incident to open its investigation board.</div>
      )}
    </div>
  );
}
