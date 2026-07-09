import { useEffect, useState } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { api } from '../api/client';
import type { AiUsageSummary, Deployment, Incident, IntegrationConnection, PullRequestReview } from '../api/types';

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

function formatCostUsd(value: number): string {
  if (value === 0) return '$0.00';
  if (value < 0.01) return `$${value.toFixed(4)}`;
  return `$${value.toFixed(2)}`;
}

function formatTokens(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}k`;
  return String(value);
}

export default function Analytics() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [prReviews, setPrReviews] = useState<PullRequestReview[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [integrations, setIntegrations] = useState<IntegrationConnection[]>([]);
  const [aiUsage, setAiUsage] = useState<AiUsageSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const cancelled = { current: false };
    Promise.all([api.deployments(), api.prReviews(), api.incidents(), api.integrationConnections(), api.aiUsage()])
      .then(([deploymentData, prData, incidentData, integrationData, aiUsageData]) => {
        if (cancelled.current) return;
        setDeployments(deploymentData);
        setPrReviews(prData);
        setIncidents(incidentData);
        setIntegrations(integrationData);
        setAiUsage(aiUsageData);
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
      {aiUsage ? (
        <div className="panel dna-breakdown">
          <div className="chart-card-header">AI Usage &amp; Cost (Bedrock)</div>
          {aiUsage.totalCalls === 0 ? (
            <div className="chart-empty">
              No LLM calls recorded yet. Usage appears here as Sentinel makes real Bedrock calls
              (deployment explanations, PR reviews, briefings, copilot answers).
            </div>
          ) : (
            <>
              <div className="ai-usage-stats">
                <div className="ai-usage-stat">
                  <div className="ai-usage-stat-value">{formatCostUsd(aiUsage.estimatedCostUsd)}</div>
                  <div className="ai-usage-stat-label">Estimated cost</div>
                </div>
                <div className="ai-usage-stat">
                  <div className="ai-usage-stat-value">{aiUsage.totalCalls}</div>
                  <div className="ai-usage-stat-label">LLM calls</div>
                </div>
                <div className="ai-usage-stat">
                  <div className="ai-usage-stat-value">
                    {formatTokens(aiUsage.totalInputTokens)} / {formatTokens(aiUsage.totalOutputTokens)}
                  </div>
                  <div className="ai-usage-stat-label">Tokens in / out</div>
                </div>
                <div className="ai-usage-stat">
                  <div className="ai-usage-stat-value">{aiUsage.averageLatencyMs}ms</div>
                  <div className="ai-usage-stat-label">Avg latency</div>
                </div>
                <div className="ai-usage-stat">
                  <div className={`ai-usage-stat-value ${aiUsage.failedCalls > 0 ? 'ai-usage-stat-warn' : ''}`}>
                    {aiUsage.failedCalls}
                  </div>
                  <div className="ai-usage-stat-label">Fallbacks triggered</div>
                </div>
              </div>

              <table className="ai-usage-table">
                <thead>
                  <tr>
                    <th>Operation</th>
                    <th>Calls</th>
                    <th>Tokens in</th>
                    <th>Tokens out</th>
                    <th>Est. cost</th>
                    <th>Avg latency</th>
                    <th>Fallbacks</th>
                  </tr>
                </thead>
                <tbody>
                  {aiUsage.byOperation.map((op) => (
                    <tr key={op.operation}>
                      <td>{humanizeLabel(op.operation)}</td>
                      <td>{op.calls}</td>
                      <td>{formatTokens(op.inputTokens)}</td>
                      <td>{formatTokens(op.outputTokens)}</td>
                      <td>{formatCostUsd(op.estimatedCostUsd)}</td>
                      <td>{op.averageLatencyMs}ms</td>
                      <td className={op.failedCalls > 0 ? 'ai-usage-stat-warn' : ''}>{op.failedCalls}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="chart-card-footnote">
                Token counts and latency are reported by Bedrock per call; cost is estimated from published
                per-token pricing ({aiUsage.recentCalls[0]?.model ?? 'Claude'}). Fallbacks are calls where
                Bedrock failed and the deterministic engine answered instead.
              </div>
            </>
          )}
        </div>
      ) : null}

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
