import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { api } from '../api/client';
import type { Deployment, Incident, IntegrationConnection, PullRequestReview } from '../api/types';

const RISK_COLORS: Record<string, string> = {
  CRITICAL: 'var(--red)',
  HIGH: 'var(--amber)',
  MEDIUM: 'var(--cyan)',
  LOW: 'var(--green)',
};

const RECOMMENDATION_COLORS: Record<string, string> = {
  BLOCK: 'var(--red)',
  WAIT: 'var(--amber)',
  MERGE: 'var(--green)',
};

const SEVERITY_COLORS: Record<string, string> = {
  SEV1: 'var(--red)',
  SEV2: 'var(--amber)',
  SEV3: 'var(--cyan)',
};

const INTEGRATION_COLORS: Record<string, string> = {
  CONNECTED: 'var(--green)',
  NEEDS_ATTENTION: 'var(--amber)',
  AVAILABLE: 'var(--text-faint)',
  DISCONNECTED: 'var(--red)',
};

function countBy<T extends string>(items: T[]): { label: string; value: number }[] {
  const counts = new Map<string, number>();
  items.forEach((item) => counts.set(item, (counts.get(item) ?? 0) + 1));
  return Array.from(counts.entries()).map(([label, value]) => ({ label, value }));
}

function humanizeLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

const tooltipStyle = {
  contentStyle: {
    background: 'var(--bg-panel-solid)',
    border: '1px solid var(--border-strong)',
    borderRadius: 10,
    fontSize: 12,
  },
  labelStyle: { color: 'var(--text)' },
};

export default function Analytics() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [prReviews, setPrReviews] = useState<PullRequestReview[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [integrations, setIntegrations] = useState<IntegrationConnection[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const cancelled = { current: false };
    Promise.all([api.deployments(), api.prReviews(), api.incidents(), api.integrationConnections()])
      .then(([deploymentData, prData, incidentData, integrationData]) => {
        if (cancelled.current) return;
        setDeployments(deploymentData);
        setPrReviews(prData);
        setIncidents(incidentData);
        setIntegrations(integrationData);
        setLoading(false);
      })
      .catch((err) => {
        if (cancelled.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load analytics data');
        setLoading(false);
      });
    return () => {
      cancelled.current = true;
    };
  }, []);

  if (loading) {
    return <div className="page-empty-state">Aggregating real engineering data...</div>;
  }

  if (error) {
    return <div className="page-empty-state">Could not load analytics: {error}</div>;
  }

  const riskLevels = deployments.filter((d) => d.riskAssessment).map((d) => d.riskAssessment!.level);
  const riskLevelData = countBy(riskLevels).map((entry) => ({ ...entry, color: RISK_COLORS[entry.label] ?? 'var(--text-faint)' }));

  const deploymentStatusData = countBy(deployments.map((d) => d.status)).map((entry) => ({
    ...entry,
    label: humanizeLabel(entry.label),
  }));

  const recommendationData = countBy(prReviews.map((p) => p.recommendation)).map((entry) => ({
    ...entry,
    color: RECOMMENDATION_COLORS[entry.label] ?? 'var(--text-faint)',
  }));

  const incidentSeverityData = countBy(incidents.map((i) => i.severity)).map((entry) => ({
    ...entry,
    color: SEVERITY_COLORS[entry.label] ?? 'var(--text-faint)',
  }));

  const incidentStatusData = countBy(incidents.map((i) => i.status)).map((entry) => ({
    ...entry,
    label: humanizeLabel(entry.label),
  }));

  const integrationStatusData = countBy(integrations.map((i) => i.status)).map((entry) => ({
    ...entry,
    color: INTEGRATION_COLORS[entry.label] ?? 'var(--text-faint)',
    label: humanizeLabel(entry.label),
  }));

  return (
    <div className="team-page">
      <div className="bottom-charts-row">
        <div className="panel chart-card">
          <div className="chart-card-header">Deployment Risk Levels</div>
          {riskLevelData.length === 0 ? (
            <div className="chart-empty">No assessed deployments yet.</div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie data={riskLevelData} dataKey="value" nameKey="label" innerRadius={48} outerRadius={72} paddingAngle={3} stroke="none">
                    {riskLevelData.map((entry) => (
                      <Cell key={entry.label} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip {...tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
              <div className="heatmap-legend">
                {riskLevelData.map((entry) => (
                  <span key={entry.label}>
                    <i className="dot" style={{ background: entry.color }} /> {humanizeLabel(entry.label)} {entry.value}
                  </span>
                ))}
              </div>
            </>
          )}
          <div className="chart-card-footnote">{deployments.length} deployments reviewed</div>
        </div>

        <div className="panel chart-card">
          <div className="chart-card-header">PR Review Recommendations</div>
          {recommendationData.length === 0 ? (
            <div className="chart-empty">No PR reviews recorded yet.</div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie
                    data={recommendationData}
                    dataKey="value"
                    nameKey="label"
                    innerRadius={48}
                    outerRadius={72}
                    paddingAngle={3}
                    stroke="none"
                  >
                    {recommendationData.map((entry) => (
                      <Cell key={entry.label} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip {...tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
              <div className="heatmap-legend">
                {recommendationData.map((entry) => (
                  <span key={entry.label}>
                    <i className="dot" style={{ background: entry.color }} /> {entry.label} {entry.value}
                  </span>
                ))}
              </div>
            </>
          )}
          <div className="chart-card-footnote">{prReviews.length} pull requests reviewed</div>
        </div>

        <div className="panel chart-card">
          <div className="chart-card-header">Incident Severity</div>
          {incidentSeverityData.length === 0 ? (
            <div className="chart-empty">No incidents recorded yet.</div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie
                    data={incidentSeverityData}
                    dataKey="value"
                    nameKey="label"
                    innerRadius={48}
                    outerRadius={72}
                    paddingAngle={3}
                    stroke="none"
                  >
                    {incidentSeverityData.map((entry) => (
                      <Cell key={entry.label} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip {...tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
              <div className="heatmap-legend">
                {incidentSeverityData.map((entry) => (
                  <span key={entry.label}>
                    <i className="dot" style={{ background: entry.color }} /> {entry.label} {entry.value}
                  </span>
                ))}
              </div>
            </>
          )}
          <div className="chart-card-footnote">{incidents.length} incidents opened</div>
        </div>

        <div className="panel chart-card">
          <div className="chart-card-header">Integration Health</div>
          {integrationStatusData.length === 0 ? (
            <div className="chart-empty">No integrations connected yet.</div>
          ) : (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie
                    data={integrationStatusData}
                    dataKey="value"
                    nameKey="label"
                    innerRadius={48}
                    outerRadius={72}
                    paddingAngle={3}
                    stroke="none"
                  >
                    {integrationStatusData.map((entry) => (
                      <Cell key={entry.label} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip {...tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
              <div className="heatmap-legend">
                {integrationStatusData.map((entry) => (
                  <span key={entry.label}>
                    <i className="dot" style={{ background: entry.color }} /> {entry.label} {entry.value}
                  </span>
                ))}
              </div>
            </>
          )}
          <div className="chart-card-footnote">{integrations.length} integrations tracked</div>
        </div>
      </div>

      <div className="panel dna-breakdown">
        <div className="chart-card-header">Deployment Status Breakdown</div>
        {deploymentStatusData.length === 0 ? (
          <div className="chart-empty">No deployments yet.</div>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={deploymentStatusData} layout="vertical" margin={{ top: 10, right: 30, left: 10, bottom: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis
                type="category"
                dataKey="label"
                width={130}
                tick={{ fill: 'var(--text-dim)', fontSize: 12 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip {...tooltipStyle} />
              <Bar dataKey="value" radius={[0, 6, 6, 0]} barSize={18} fill="var(--cyan)" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="panel dna-breakdown">
        <div className="chart-card-header">Incident Status Breakdown</div>
        {incidentStatusData.length === 0 ? (
          <div className="chart-empty">No incidents yet.</div>
        ) : (
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={incidentStatusData} layout="vertical" margin={{ top: 10, right: 30, left: 10, bottom: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" horizontal={false} />
              <XAxis type="number" allowDecimals={false} tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis
                type="category"
                dataKey="label"
                width={130}
                tick={{ fill: 'var(--text-dim)', fontSize: 12 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip {...tooltipStyle} />
              <Bar dataKey="value" radius={[0, 6, 6, 0]} barSize={18} fill="var(--purple)" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
