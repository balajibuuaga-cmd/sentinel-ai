import { useEffect, useState } from 'react';
import { FileText, Printer, ShieldAlert } from 'lucide-react';
import { api } from '../api/client';
import type { ArchitectureBrain, Deployment, EngineeringDna, ExecutiveBriefing, Incident } from '../api/types';

interface ReportData {
  briefing: ExecutiveBriefing;
  dna: EngineeringDna;
  brain: ArchitectureBrain;
  deployments: Deployment[];
  incidents: Incident[];
}

const severityTone: Record<string, string> = { CRITICAL: 'bad', HIGH: 'bad', MEDIUM: 'warn', LOW: 'good' };

function generatedOn() {
  return new Date().toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
}

export default function BoardReport() {
  const [data, setData] = useState<ReportData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.executiveBriefing(), api.engineeringDna(), api.architectureBrain(), api.deployments(), api.incidents()])
      .then(([briefing, dna, brain, deployments, incidents]) => {
        if (!cancelled) setData({ briefing, dna, brain, deployments, incidents });
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : 'Failed to load board report'));
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not build the board report: {error}</div>;
  }

  if (!data) {
    return <div className="page-empty-state">Assembling this week's board report...</div>;
  }

  const { briefing, dna, brain, deployments, incidents } = data;
  const highRisk = deployments.filter((d) => d.riskAssessment && ['HIGH', 'CRITICAL'].includes(d.riskAssessment.level));
  const blocked = deployments.filter((d) => d.status === 'BLOCKED');
  const openIncidents = incidents.filter((i) => i.status !== 'RESOLVED');
  const sortedRisks = [...brain.risks].sort((a, b) => severityRank(b.severity) - severityRank(a.severity));

  return (
    <div className="board-report-page">
      <div className="board-report-actions">
        <button className="board-report-print-btn" onClick={() => window.print()}>
          <Printer size={15} /> Export / Print
        </button>
      </div>

      <div className="panel board-report-sheet">
        <div className="board-report-header">
          <div className="board-report-header-icon">
            <FileText size={22} />
          </div>
          <div>
            <div className="board-report-title">AI Board Report</div>
            <div className="board-report-sub">Generated {generatedOn()} by Sentinel AI</div>
          </div>
        </div>

        <section className="board-report-section">
          <h2>Executive Summary</h2>
          <div className="board-report-metric-row">
            {briefing.metrics.map((m) => (
              <div key={m.label} className="board-report-metric">
                <span className="board-report-metric-value">{m.value}</span>
                <span className="board-report-metric-label">{m.label}</span>
              </div>
            ))}
          </div>
          <p>{briefing.summary}</p>
          <p className="board-report-chief">{briefing.chiefBriefing}</p>
          <div className="board-report-callout">
            <strong>{briefing.recommendationTitle}</strong>
            <p>{briefing.recommendation}</p>
          </div>
        </section>

        <section className="board-report-section">
          <h2>Engineering Health</h2>
          <div className="board-report-dna">
            <div className="board-report-dna-score">
              <span className="board-report-dna-number">{dna.overall}</span>
              <span>/100</span>
            </div>
            <p>{dna.summary}</p>
          </div>
          <div className="board-report-dna-grid">
            {dna.scores.map((s) => (
              <div key={s.label} className="board-report-dna-item">
                <span>{s.label}</span>
                <div className="board-report-bar-track">
                  <div className="board-report-bar-fill" style={{ width: `${s.value}%` }} />
                </div>
                <span>{s.value}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="board-report-section">
          <h2>Release Posture</h2>
          <div className="board-report-metric-row">
            <div className="board-report-metric">
              <span className="board-report-metric-value">{deployments.length}</span>
              <span className="board-report-metric-label">Total deployments</span>
            </div>
            <div className="board-report-metric">
              <span className="board-report-metric-value">{highRisk.length}</span>
              <span className="board-report-metric-label">High / critical risk</span>
            </div>
            <div className="board-report-metric">
              <span className="board-report-metric-value">{blocked.length}</span>
              <span className="board-report-metric-label">Blocked</span>
            </div>
            <div className="board-report-metric">
              <span className="board-report-metric-value">{openIncidents.length}</span>
              <span className="board-report-metric-label">Open incidents</span>
            </div>
          </div>
        </section>

        <section className="board-report-section">
          <h2>
            <ShieldAlert size={15} /> Architecture Risk
          </h2>
          <p>{brain.summary}</p>
          <div className="board-report-metric-row">
            <div className="board-report-metric">
              <span className="board-report-metric-value">{brain.serviceCount}</span>
              <span className="board-report-metric-label">Services</span>
            </div>
            <div className="board-report-metric">
              <span className="board-report-metric-value">{brain.dependencyCount}</span>
              <span className="board-report-metric-label">Dependencies</span>
            </div>
            <div className="board-report-metric">
              <span className="board-report-metric-value">{brain.riskCount}</span>
              <span className="board-report-metric-label">Flagged risks</span>
            </div>
          </div>
          {sortedRisks.length > 0 ? (
            <ul className="board-report-risk-list">
              {sortedRisks.slice(0, 6).map((r) => (
                <li key={r.id}>
                  <span className={`rec-badge tone-${severityTone[r.severity] ?? 'warn'}`}>{r.severity}</span>
                  <div>
                    <strong>{r.serviceName}</strong> — {r.explanation}
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p>No architecture risks flagged this cycle.</p>
          )}
          {brain.recommendedRefactor ? (
            <div className="board-report-callout">
              <strong>Recommended Refactor</strong>
              <p>{brain.recommendedRefactor}</p>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}

function severityRank(severity: string) {
  switch (severity) {
    case 'CRITICAL':
      return 3;
    case 'HIGH':
      return 2;
    case 'MEDIUM':
      return 1;
    default:
      return 0;
  }
}
