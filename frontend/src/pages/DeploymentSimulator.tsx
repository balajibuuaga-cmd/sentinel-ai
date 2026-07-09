import { useEffect, useRef, useState } from 'react';
import { Rocket, GitBranch, Layers, ShieldAlert, Sparkles } from 'lucide-react';
import { api } from '../api/client';
import type { Deployment } from '../api/types';

const emptyForm = {
  provider: 'github-actions',
  repository: 'sentinel-ai/payment-api',
  serviceName: 'payment-api',
  ownerTeam: 'Payments Platform',
  environment: 'production',
  commitSha: '',
  pipelineName: 'deploy-production',
  buildUrl: '',
  status: 'success',
  failedTests: 0,
  coverageDelta: 0,
  actor: '',
  failedSuites: '',
  dependencies: 'customer-ledger, fraud-screening',
};

type Stage = 'idle' | 'ingesting' | 'dependencies' | 'risk' | 'recommendation' | 'done';

const STAGE_ORDER: Stage[] = ['ingesting', 'dependencies', 'risk', 'recommendation', 'done'];

const stageLabel: Record<Stage, string> = {
  idle: '',
  ingesting: 'Ingesting CI signal...',
  dependencies: 'Analyzing service dependencies...',
  risk: 'Calculating deployment risk...',
  recommendation: 'Drafting recommendation...',
  done: 'Simulation complete',
};

export default function DeploymentSimulator() {
  const [form, setForm] = useState(emptyForm);
  const [stage, setStage] = useState<Stage>('idle');
  const [result, setResult] = useState<Deployment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<Deployment[]>([]);
  const cancelledRef = useRef(false);

  useEffect(() => {
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  async function runSimulation() {
    if (stage !== 'idle' && stage !== 'done') return;
    setError(null);
    setResult(null);
    setStage('ingesting');

    try {
      const created = await api.simulateCiSignal({
        provider: form.provider,
        repository: form.repository,
        serviceName: form.serviceName,
        ownerTeam: form.ownerTeam,
        environment: form.environment,
        commitSha: form.commitSha || Math.random().toString(16).slice(2, 9),
        pipelineName: form.pipelineName,
        buildUrl: form.buildUrl || null,
        status: form.status,
        failedTests: form.failedTests,
        coverageDelta: form.coverageDelta,
        actor: form.actor || 'sentinel-simulator',
        failedSuites: form.failedSuites
          .split('\n')
          .map((s) => s.trim())
          .filter(Boolean),
        dependencies: form.dependencies
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
      });

      for (const next of STAGE_ORDER) {
        await new Promise((resolve) => setTimeout(resolve, 550));
        if (cancelledRef.current) return;
        setStage(next);
      }
      if (cancelledRef.current) return;
      setResult(created);
      setHistory((prev) => [created, ...prev].slice(0, 6));
    } catch (err) {
      if (cancelledRef.current) return;
      setError(err instanceof Error ? err.message : 'Simulation failed');
      setStage('idle');
    }
  }

  const running = stage !== 'idle' && stage !== 'done';
  const assessment = result?.riskAssessment ?? null;

  return (
    <div className="simulator-page">
      <div className="panel engineer-form simulator-form">
        <div className="engineer-form-header">
          <Rocket size={16} /> Simulate a Deployment
        </div>
        <div className="engineer-form-row">
          <label>
            Provider
            <select value={form.provider} onChange={(e) => setForm({ ...form, provider: e.target.value })}>
              <option value="github-actions">github-actions</option>
              <option value="circleci">circleci</option>
              <option value="jenkins">jenkins</option>
            </select>
          </label>
          <label>
            Status
            <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
              <option value="success">success</option>
              <option value="failure">failure</option>
            </select>
          </label>
        </div>
        <label>
          Repository
          <input value={form.repository} onChange={(e) => setForm({ ...form, repository: e.target.value })} />
        </label>
        <div className="engineer-form-row">
          <label>
            Service
            <input value={form.serviceName} onChange={(e) => setForm({ ...form, serviceName: e.target.value })} />
          </label>
          <label>
            Owner team
            <input value={form.ownerTeam} onChange={(e) => setForm({ ...form, ownerTeam: e.target.value })} />
          </label>
        </div>
        <div className="engineer-form-row">
          <label>
            Environment
            <input value={form.environment} onChange={(e) => setForm({ ...form, environment: e.target.value })} />
          </label>
          <label>
            Pipeline
            <input value={form.pipelineName} onChange={(e) => setForm({ ...form, pipelineName: e.target.value })} />
          </label>
        </div>
        <div className="engineer-form-row">
          <label>
            Failed tests
            <input
              type="number"
              min={0}
              value={form.failedTests}
              onChange={(e) => setForm({ ...form, failedTests: Number(e.target.value) })}
            />
          </label>
          <label>
            Coverage delta (%)
            <input
              type="number"
              value={form.coverageDelta}
              onChange={(e) => setForm({ ...form, coverageDelta: Number(e.target.value) })}
            />
          </label>
        </div>
        <label>
          Dependencies (comma separated)
          <input value={form.dependencies} onChange={(e) => setForm({ ...form, dependencies: e.target.value })} />
        </label>
        <label>
          Failed test suites (one per line)
          <textarea
            rows={2}
            value={form.failedSuites}
            onChange={(e) => setForm({ ...form, failedSuites: e.target.value })}
          />
        </label>
        <button className="briefing-cta" onClick={runSimulation} disabled={running}>
          {running ? stageLabel[stage] : 'Run Simulation'}
        </button>
        {error ? <div className="engineer-error">{error}</div> : null}
      </div>

      <div className="simulator-right-col">
        <div className="panel simulator-pipeline">
          <div className="engineer-form-header">
            <Sparkles size={16} /> AI Simulation Pipeline
          </div>
          <div className="simulator-stages">
            {STAGE_ORDER.map((s, i) => {
              const currentIndex = STAGE_ORDER.indexOf(stage);
              const reached = stage === 'done' || currentIndex >= i;
              return (
                <div key={s} className={`simulator-stage${reached ? ' active' : ''}`}>
                  <span className="simulator-stage-dot" />
                  {stageLabel[s]}
                </div>
              );
            })}
          </div>

          {result && assessment ? (
            <div className="simulator-result">
              <div className="simulator-result-header">
                <span className={`risk-pill risk-${assessment.level.toLowerCase()}`}>{assessment.level}</span>
                <span className="simulator-result-score">{assessment.score}% risk</span>
              </div>
              <div className="simulator-result-row">
                <GitBranch size={13} /> {result.deploymentKey} &middot; {result.serviceName} &middot; {result.environment}
              </div>
              <div className="simulator-result-row">
                <Layers size={13} /> Dependencies: {result.dependencies.join(', ') || 'none recorded'}
              </div>
              <div className="simulator-result-row">
                <ShieldAlert size={13} /> {assessment.recommendation}
              </div>
              <p className="simulator-result-explanation">{assessment.aiExplanation}</p>
              {assessment.reasons.length > 0 ? (
                <ul className="service-detail-list">
                  {assessment.reasons.map((reason, i) => (
                    <li key={i}>
                      <b>[{reason.category}]</b> {reason.evidence} <span className="tag">impact {reason.impact}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : (
            <div className="chart-empty">Configure a CI signal and run the simulation to see a real risk assessment.</div>
          )}
        </div>

        {history.length > 0 ? (
          <div className="panel simulator-history">
            <div className="chart-card-header">Recent Simulations</div>
            <div className="operator-list">
              {history.map((d) => (
                <div key={d.id} className="operator-row">
                  <span className={`risk-pill risk-${(d.riskAssessment?.level ?? 'low').toLowerCase()}`}>
                    {d.riskAssessment?.level ?? 'N/A'}
                  </span>
                  <div className="operator-row-body">
                    <div className="operator-row-title">{d.serviceName} &middot; {d.deploymentKey}</div>
                    <div className="operator-row-meta">
                      {d.riskAssessment?.score ?? 0}% risk &middot; {d.status}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
