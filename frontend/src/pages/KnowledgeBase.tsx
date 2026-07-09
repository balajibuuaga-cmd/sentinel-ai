import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { BookOpen, CheckCircle2, AlertTriangle } from 'lucide-react';
import { api } from '../api/client';
import type { BackendReadinessAssessment, EngineeringPlaybook } from '../api/types';

const RADIUS = 54;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function scoreColor(value: number) {
  if (value >= 80) return 'var(--green)';
  if (value >= 60) return 'var(--cyan)';
  if (value >= 40) return 'var(--amber)';
  return 'var(--red)';
}

export default function KnowledgeBase() {
  const [readiness, setReadiness] = useState<BackendReadinessAssessment | null>(null);
  const [playbooks, setPlaybooks] = useState<EngineeringPlaybook[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const cancelled = { current: false };
    Promise.all([api.backendReadiness(), api.playbooks()])
      .then(([readinessData, playbookData]) => {
        if (cancelled.current) return;
        setReadiness(readinessData);
        setPlaybooks(playbookData);
      })
      .catch((err) => {
        if (cancelled.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load knowledge base');
      });
    return () => {
      cancelled.current = true;
    };
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not load knowledge base: {error}</div>;
  }

  if (!readiness) {
    return <div className="page-empty-state">Scoring backend readiness against Sentinel's playbooks...</div>;
  }

  const offset = CIRCUMFERENCE - (readiness.overallScore / 100) * CIRCUMFERENCE;
  const chartData = readiness.checks.map((check) => ({
    label: check.category,
    value: check.score,
    color: scoreColor(check.score),
  }));

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
              style={{ stroke: scoreColor(readiness.overallScore) }}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={offset}
            />
          </svg>
          <div className="dna-ring-value">
            <span className="dna-ring-score">{readiness.overallScore}</span>
            <span className="dna-ring-total">/100</span>
          </div>
        </div>
        <div className="dna-hero-text">
          <div className="dna-hero-title">Backend Readiness &middot; {readiness.maturityLevel}</div>
          <p>{readiness.summary}</p>
        </div>
      </div>

      <div className="panel dna-breakdown">
        <div className="chart-card-header">Readiness By Category</div>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={chartData} layout="vertical" margin={{ top: 10, right: 30, left: 10, bottom: 0 }}>
            <CartesianGrid stroke="rgba(255,255,255,0.06)" horizontal={false} />
            <XAxis type="number" domain={[0, 100]} tick={{ fill: 'var(--text-faint)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis
              type="category"
              dataKey="label"
              width={180}
              tick={{ fill: 'var(--text-dim)', fontSize: 11.5 }}
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
            <Bar dataKey="value" radius={[0, 6, 6, 0]} barSize={16}>
              {chartData.map((entry) => (
                <Cell key={entry.label} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      {readiness.nextActions.length > 0 ? (
        <div className="panel team-invite-card">
          <div className="chart-card-header">
            <AlertTriangle size={15} /> Priority Next Actions
          </div>
          <div className="operator-list">
            {readiness.nextActions.map((action) => (
              <div key={action} className="operator-row">
                <span className="rec-badge tone-warn">Gap</span>
                <div className="operator-row-body">
                  <div className="operator-row-meta">{action}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="panel team-invite-card">
        <div className="chart-card-header">
          <BookOpen size={15} /> Engineering Playbooks
        </div>
        <div className="operator-list">
          {playbooks.map((playbook) => (
            <div key={playbook.id} className="knowledge-playbook">
              <div className="knowledge-playbook-header">
                <span className="rec-badge tone-good">{playbook.category}</span>
                <span className="knowledge-playbook-title">{playbook.title}</span>
              </div>
              <p className="knowledge-playbook-summary">{playbook.summary}</p>
              <div className="knowledge-playbook-checks">
                {playbook.checks.map((check) => (
                  <div key={check} className="knowledge-check-row">
                    <CheckCircle2 size={13} />
                    <span>{check}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
