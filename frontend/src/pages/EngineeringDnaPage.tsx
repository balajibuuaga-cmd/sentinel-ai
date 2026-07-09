import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { api } from '../api/client';
import type { EngineeringDna } from '../api/types';

const RADIUS = 54;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function scoreColor(value: number) {
  if (value >= 80) return 'var(--green)';
  if (value >= 60) return 'var(--cyan)';
  if (value >= 40) return 'var(--amber)';
  return 'var(--red)';
}

export default function EngineeringDnaPage() {
  const [dna, setDna] = useState<EngineeringDna | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .engineeringDna()
      .then(setDna)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load Engineering DNA'));
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not load Engineering DNA: {error}</div>;
  }

  if (!dna) {
    return <div className="page-empty-state">Scoring your engineering organization...</div>;
  }

  const offset = CIRCUMFERENCE - (dna.overall / 100) * CIRCUMFERENCE;
  const chartData = dna.scores.map((s) => ({ ...s, color: scoreColor(s.value) }));

  return (
    <div className="dna-page">
      <div className="panel dna-hero">
        <div className="dna-ring-wrap">
          <svg viewBox="0 0 140 140" className="dna-ring">
            <circle cx="70" cy="70" r={RADIUS} className="dna-ring-track" />
            <circle
              cx="70"
              cy="70"
              r={RADIUS}
              className="dna-ring-fill"
              style={{ stroke: scoreColor(dna.overall) }}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={offset}
            />
          </svg>
          <div className="dna-ring-value">
            <span className="dna-ring-score">{dna.overall}</span>
            <span className="dna-ring-total">/100</span>
          </div>
        </div>
        <div className="dna-hero-text">
          <div className="dna-hero-title">Engineering Credit Score</div>
          <p>{dna.summary}</p>
        </div>
      </div>

      <div className="panel dna-breakdown">
        <div className="chart-card-header">Score Breakdown</div>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={chartData} layout="vertical" margin={{ top: 10, right: 30, left: 10, bottom: 0 }}>
            <CartesianGrid stroke="rgba(255,255,255,0.06)" horizontal={false} />
            <XAxis type="number" domain={[0, 100]} tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis
              type="category"
              dataKey="label"
              width={130}
              tick={{ fill: 'var(--text-dim)', fontSize: 12 }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--bg-panel-solid)',
                border: '1px solid var(--border-strong)',
                borderRadius: 10,
                fontSize: 12,
              }}
              labelStyle={{ color: 'var(--text)' }}
            />
            <Bar dataKey="value" radius={[0, 6, 6, 0]} barSize={18}>
              {chartData.map((entry) => (
                <Cell key={entry.label} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
