import {
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import { Link } from 'react-router-dom';
import { Activity, ArrowRight, Plug, TrendingUp, WebhookOff } from 'lucide-react';
import type { BusinessImpactData, RiskHeatmapSlice, RiskTrendPoint } from '../types/dashboard';
import type { RiskProjection } from '../api/transform';

export function RiskHeatmapCard({ data }: { data: RiskHeatmapSlice[] }) {
  const total = data.reduce((sum, d) => sum + d.count, 0);
  const flagged = total - (data.find((d) => d.label === 'Healthy')?.count ?? 0);

  return (
    <div className="panel chart-card">
      <div className="chart-card-header">Risk Heatmap</div>
      <div className="donut-wrap">
        <ResponsiveContainer width="100%" height={160}>
          <PieChart>
            <Pie
              data={data}
              dataKey="count"
              nameKey="label"
              innerRadius={48}
              outerRadius={72}
              paddingAngle={3}
              stroke="none"
            >
              {data.map((entry) => (
                <Cell key={entry.label} fill={entry.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="donut-center">
          <span className="donut-count">{flagged}</span>
          <span className="donut-label">Flagged Services</span>
        </div>
      </div>
      <div className="heatmap-legend">
        {data.map((d) => (
          <span key={d.label}>
            <i className="dot" style={{ background: d.color }} /> {d.label} {d.count}
          </span>
        ))}
      </div>
      <div className="chart-card-footnote">{total} services tracked</div>
    </div>
  );
}

export function RiskTrendCard({ data }: { data: RiskTrendPoint[] }) {
  const hasData = data.some((d) => d.score !== null);

  return (
    <div className="panel chart-card">
      <div className="chart-card-header">Risk Trend (Last 7 Days)</div>
      {hasData ? (
        <ResponsiveContainer width="100%" height={200}>
          <LineChart data={data} margin={{ top: 10, right: 8, left: -20, bottom: 0 }}>
            <CartesianGrid stroke="rgba(255,255,255,0.06)" vertical={false} />
            <XAxis dataKey="day" tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <Tooltip
              contentStyle={{
                background: 'var(--bg-panel-solid)',
                border: '1px solid var(--border-strong)',
                borderRadius: 10,
                fontSize: 12,
              }}
              labelStyle={{ color: 'var(--text)' }}
              formatter={(value) => [typeof value === 'number' ? value : '—', 'Avg Risk Score']}
            />
            <Line
              type="monotone"
              dataKey="score"
              stroke="var(--red)"
              strokeWidth={2.5}
              dot={{ r: 3, fill: 'var(--red)', strokeWidth: 0 }}
              activeDot={{ r: 5 }}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <div className="chart-empty">No deployments assessed in the last 7 days yet.</div>
      )}
    </div>
  );
}

export function FutureTrendCard({ data }: { data: RiskProjection }) {
  const hasProjection = data.points.some((p) => p.projected !== null);

  return (
    <div className="panel chart-card">
      <div className="chart-card-header">
        <TrendingUp size={13} style={{ verticalAlign: -2, marginRight: 5 }} />
        Future Risk Trend
      </div>
      {hasProjection ? (
        <>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={data.points} margin={{ top: 10, right: 8, left: -20, bottom: 0 }}>
              <CartesianGrid stroke="rgba(255,255,255,0.06)" vertical={false} />
              <XAxis dataKey="day" tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} domain={[0, 100]} />
              <Tooltip
                contentStyle={{
                  background: 'var(--bg-panel-solid)',
                  border: '1px solid var(--border-strong)',
                  borderRadius: 10,
                  fontSize: 12,
                }}
                labelStyle={{ color: 'var(--text)' }}
                formatter={(value, name) => [
                  typeof value === 'number' ? value : '—',
                  name === 'historical' ? 'Actual Avg Risk' : 'Projected Avg Risk',
                ]}
              />
              <Line
                type="monotone"
                dataKey="historical"
                stroke="var(--red)"
                strokeWidth={2.5}
                dot={{ r: 3, fill: 'var(--red)', strokeWidth: 0 }}
                activeDot={{ r: 5 }}
                connectNulls={false}
              />
              <Line
                type="monotone"
                dataKey="projected"
                stroke="var(--cyan)"
                strokeWidth={2}
                strokeDasharray="5 4"
                dot={{ r: 3, fill: 'var(--cyan)', strokeWidth: 0 }}
                connectNulls
              />
            </LineChart>
          </ResponsiveContainer>
          <div className="chart-card-footnote">
            Linear projection from {data.sampleSize} day{data.sampleSize === 1 ? '' : 's'} of real risk data
            {data.rSquared !== null ? ` · fit R² ${data.rSquared.toFixed(2)}` : ''}
            {data.trend ? ` · trending ${data.trend}` : ''}. Statistical extrapolation, not a machine-learning forecast.
          </div>
        </>
      ) : (
        <div className="chart-empty">Not enough historical risk data yet to project a trend.</div>
      )}
    </div>
  );
}

export function BusinessImpactCard({ data }: { data: BusinessImpactData }) {
  return (
    <div className="panel chart-card business-impact-card">
      <div className="chart-card-header">Operational Impact</div>
      <div className="impact-gauge">
        <div className="impact-gauge-ring">
          <Activity size={30} />
        </div>
      </div>
      <div className="impact-stats">
        <div className="impact-row">
          <span className="impact-row-label">Blocked Deployments Today</span>
          <span className="impact-row-value tone-bad-text">{data.blockedToday}</span>
        </div>
        <div className="impact-row">
          <span className="impact-row-label"><WebhookOff size={13} /> Failed Webhook Deliveries</span>
          <span className="impact-row-value">{data.failedWebhooks}</span>
        </div>
        <div className="impact-row">
          <span className="impact-row-label"><Plug size={13} /> Integrations Needing Attention</span>
          <span className="impact-row-value">{data.attentionIntegrations}</span>
        </div>
        <div className="impact-row">
          <span className="impact-row-label">Downtime Risk</span>
          <span className="impact-badge">{data.downtimeRisk}</span>
        </div>
      </div>
      <Link className="impact-cta" to="/executive">
        View Impact Analysis <ArrowRight size={14} />
      </Link>
    </div>
  );
}
