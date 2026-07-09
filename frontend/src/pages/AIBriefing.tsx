import { useEffect, useState } from 'react';
import { Sparkles, ArrowRight } from 'lucide-react';
import { api } from '../api/client';
import type { Deployment, ExecutiveBriefing, Incident, PullRequestReview } from '../api/types';

interface Snapshot {
  briefing: ExecutiveBriefing;
  deploymentsToday: number;
  activeIncidents: number;
  prsReviewed: number;
  rollbacks: number;
  productionOutages: number;
}

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function buildSnapshot(briefing: ExecutiveBriefing, deployments: Deployment[], incidents: Incident[], reviews: PullRequestReview[]): Snapshot {
  const today = todayKey();
  return {
    briefing,
    deploymentsToday: deployments.filter((d) => d.createdAt.slice(0, 10) === today).length,
    activeIncidents: incidents.length,
    prsReviewed: reviews.length,
    rollbacks: deployments.filter((d) => d.status === 'ROLLED_BACK').length,
    productionOutages: incidents.filter((i) => i.severity === 'SEV1').length,
  };
}

export default function AIBriefing() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.executiveBriefing(), api.deployments(), api.incidents(), api.prReviews()])
      .then(([briefing, deployments, incidents, reviews]) => {
        if (!cancelled) setSnapshot(buildSnapshot(briefing, deployments, incidents, reviews));
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : 'Failed to load briefing'));
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not load the AI Briefing: {error}</div>;
  }

  if (!snapshot) {
    return <div className="page-empty-state">Preparing this morning's briefing...</div>;
  }

  const savings = snapshot.briefing.metrics.find((m) => m.label === 'Expected savings')?.value ?? '$0';

  return (
    <div className="ai-briefing-page">
      <div className="panel ai-briefing-hero">
        <div className="ai-briefing-icon">
          <Sparkles size={28} />
        </div>
        <h1>{snapshot.briefing.greeting}</h1>
        <p className="ai-briefing-sub">Here is what happened across your engineering organization.</p>

        <div className="ai-briefing-stats">
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.deploymentsToday}</span>
            <span className="ai-briefing-stat-label">Deployments</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.activeIncidents}</span>
            <span className="ai-briefing-stat-label">Incidents</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.prsReviewed}</span>
            <span className="ai-briefing-stat-label">PRs Reviewed</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.rollbacks}</span>
            <span className="ai-briefing-stat-label">Rollbacks</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.productionOutages}</span>
            <span className="ai-briefing-stat-label">Production Outages</span>
          </div>
        </div>

        <div className="ai-briefing-divider" />

        <div className="ai-briefing-block">
          <div className="ai-briefing-block-label">AI Summary</div>
          <p>{snapshot.briefing.summary}</p>
        </div>

        <div className="ai-briefing-block">
          <div className="ai-briefing-block-label">Recommendation</div>
          <p className="ai-briefing-recommendation">
            <ArrowRight size={16} /> {snapshot.briefing.recommendationTitle}
          </p>
          <p>{snapshot.briefing.recommendation}</p>
        </div>

        <div className="ai-briefing-savings">
          <span>Expected savings</span>
          <strong>{savings}</strong>
        </div>
      </div>
    </div>
  );
}
