import { useEffect, useMemo, useState } from 'react';
import { ShieldAlert, Rocket, Boxes, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { buildUnifiedRisks, humanize } from '../api/transform';
import type { UnifiedRisk } from '../api/transform';
import type { RiskLevel } from '../api/types';

const SEVERITY_ORDER: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export default function RisksRecommendations() {
  const [risks, setRisks] = useState<UnifiedRisk[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<RiskLevel | 'ALL'>('ALL');

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.architectureBrain(), api.deployments()])
      .then(([brain, deployments]) => {
        if (cancelled) return;
        setRisks(buildUnifiedRisks(brain, deployments));
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load risks');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const counts = useMemo(() => {
    const base: Record<RiskLevel, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    (risks ?? []).forEach((risk) => {
      base[risk.severity] += 1;
    });
    return base;
  }, [risks]);

  const visible = useMemo(
    () => (risks ?? []).filter((risk) => filter === 'ALL' || risk.severity === filter),
    [risks, filter],
  );

  if (error) {
    return <div className="page-empty-state">{error}</div>;
  }

  if (!risks) {
    return <div className="page-empty-state">Loading risks and recommendations...</div>;
  }

  return (
    <div className="risks-page">
      <div className="panel risks-panel">
        <div className="risks-head">
          <div className="chart-card-header risks-heading">
            <ShieldAlert size={16} />
            <span>Risks &amp; Recommendations</span>
          </div>
          <div className="risk-filter-row">
            <button
              className={`risk-filter${filter === 'ALL' ? ' active' : ''}`}
              onClick={() => setFilter('ALL')}
            >
              All {risks.length}
            </button>
            {SEVERITY_ORDER.map((level) => (
              <button
                key={level}
                className={`risk-filter risk-filter-${level.toLowerCase()}${filter === level ? ' active' : ''}`}
                onClick={() => setFilter(level)}
                disabled={counts[level] === 0}
              >
                {humanize(level)} {counts[level]}
              </button>
            ))}
          </div>
        </div>

        {visible.length === 0 ? (
          <div className="page-empty-state">
            {risks.length === 0
              ? 'No open risks. Sentinel found nothing that needs attention right now.'
              : `No ${humanize(filter as string).toLowerCase()} risks.`}
          </div>
        ) : (
          <div className="risk-list">
            {visible.map((risk) => (
              <article key={risk.key} className={`risk-card risk-card-${risk.severity.toLowerCase()}`}>
                <header className="risk-card-head">
                  <span className={`risk-sev risk-sev-${risk.severity.toLowerCase()}`}>
                    {humanize(risk.severity)}
                  </span>
                  <span className="risk-source">
                    {risk.source === 'ARCHITECTURE' ? <Boxes size={14} /> : <Rocket size={14} />}
                    {risk.source === 'ARCHITECTURE' ? 'Architecture' : 'Deployment'}
                  </span>
                  <span className="risk-subject">{risk.subject}</span>
                </header>

                <h3 className="risk-title">{risk.title}</h3>
                <p className="risk-explanation">{risk.explanation}</p>

                <div className="risk-recommendation">
                  <strong>Recommended action</strong>
                  <p>{risk.recommendation}</p>
                </div>

                {risk.link ? (
                  <Link className="risk-link" to={risk.link}>
                    Investigate <ArrowRight size={14} />
                  </Link>
                ) : null}
              </article>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
