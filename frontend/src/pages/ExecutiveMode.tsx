import { useEffect, useState } from 'react';
import { CheckCircle2, ArrowRight } from 'lucide-react';
import { api } from '../api/client';
import type { Deployment, ExecutiveBriefing } from '../api/types';

interface Snapshot {
  briefing: ExecutiveBriefing;
  riskiest: Deployment | null;
  attentionCount: number;
  teamsNeedingHelp: string[];
  confidence: number;
}

function buildSnapshot(briefing: ExecutiveBriefing, deployments: Deployment[]): Snapshot {
  const atRisk = deployments.filter((d) => d.riskAssessment && ['HIGH', 'CRITICAL'].includes(d.riskAssessment.level));
  const riskiest = [...deployments].sort((a, b) => (b.riskAssessment?.score ?? 0) - (a.riskAssessment?.score ?? 0))[0] ?? null;
  const teamsNeedingHelp = [...new Set(atRisk.map((d) => d.ownerTeam))];
  const score = riskiest?.riskAssessment?.score ?? 0;
  const confidence = Math.max(0, Math.min(100, Math.round(Math.abs(score - 50) * 2)));

  return {
    briefing,
    riskiest: atRisk.length > 0 ? riskiest : null,
    attentionCount: atRisk.length,
    teamsNeedingHelp,
    confidence,
  };
}

export default function ExecutiveMode() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.executiveBriefing(), api.deployments()])
      .then(([briefing, deployments]) => {
        if (!cancelled) setSnapshot(buildSnapshot(briefing, deployments));
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : 'Failed to load executive summary'));
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not load executive summary: {error}</div>;
  }

  if (!snapshot) {
    return <div className="page-empty-state">Preparing the executive summary...</div>;
  }

  const healthy = snapshot.attentionCount === 0;

  return (
    <div className="executive-page">
      <div className="executive-sheet">
        <div className="executive-greeting">{snapshot.briefing.greeting}</div>

        <div className={`executive-status${healthy ? ' healthy' : ''}`}>
          {healthy ? (
            <>
              <CheckCircle2 size={22} /> Everything is Healthy.
            </>
          ) : (
            `${snapshot.attentionCount} release${snapshot.attentionCount === 1 ? '' : 's'} need${snapshot.attentionCount === 1 ? 's' : ''} your attention.`
          )}
        </div>

        {!healthy && snapshot.riskiest ? (
          <div className="executive-metric-row">
            <div className="executive-metric">
              <span className="executive-metric-value tone-bad-text">{snapshot.riskiest.riskAssessment?.score}%</span>
              <span className="executive-metric-label">Riskiest release &middot; {snapshot.riskiest.serviceName}</span>
            </div>
            <div className="executive-metric">
              <span className="executive-metric-value">{snapshot.teamsNeedingHelp.length}</span>
              <span className="executive-metric-label">
                {snapshot.teamsNeedingHelp.length === 1 ? 'Team needs' : 'Teams need'} help
                {snapshot.teamsNeedingHelp.length > 0 ? `: ${snapshot.teamsNeedingHelp.join(', ')}` : ''}
              </span>
            </div>
          </div>
        ) : null}

        <div className="executive-decision">
          <span className="ai-briefing-block-label">Recommended Decision</span>
          <div className="executive-decision-title">
            <ArrowRight size={16} /> {snapshot.briefing.recommendationTitle}
          </div>
          <p>{snapshot.briefing.recommendation}</p>
        </div>

        {!healthy ? (
          <div className="executive-confidence">
            <span>Confidence</span>
            <div className="executive-confidence-track">
              <div className="executive-confidence-fill" style={{ width: `${snapshot.confidence}%` }} />
            </div>
            <span className="executive-confidence-value">{snapshot.confidence}%</span>
          </div>
        ) : null}
      </div>
    </div>
  );
}
