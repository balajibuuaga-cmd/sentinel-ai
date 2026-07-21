import { useEffect, useRef, useState } from 'react';
import { Rocket, GitBranch, Layers, ShieldAlert, Sparkles, ArrowLeft, Plus } from 'lucide-react';
import { ApiError, api } from '../api/client';
import type { Deployment } from '../api/types';

// A blank form. The page used to open with a filled-in payment-api deployment
// for a repository nobody owns, which reads as real data until you look.
const emptyForm = {
  provider: 'github-actions',
  repository: '',
  serviceName: '',
  ownerTeam: '',
  environment: 'production',
  commitSha: '',
  pipelineName: '',
  buildUrl: '',
  status: 'success',
  failedTests: 0,
  coverageDelta: 0,
  actor: '',
  failedSuites: '',
  dependencies: '',
};

type FormState = typeof emptyForm;

/**
 * Carries over what Sentinel actually recorded for a deployment. Repository is
 * left blank on purpose: it is not stored on the deployment, and inventing one
 * from the team and service name would put a repository that may not exist into
 * a field the sync then tries to read.
 */
function formFromDeployment(deployment: Deployment): FormState {
  return {
    ...emptyForm,
    serviceName: deployment.serviceName,
    ownerTeam: deployment.ownerTeam,
    environment: deployment.environment,
    commitSha: deployment.commitSha ?? '',
    dependencies: deployment.dependencies.join(', '),
  };
}

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

// The deployment key records its origin: GH- for a signed GitHub webhook,
// SIG- for a CI signal or a simulation run from this page.
function deploymentSource(deployment: Deployment): 'GitHub' | 'Simulated' {
  return deployment.deploymentKey.startsWith('GH-') ? 'GitHub' : 'Simulated';
}

type Mode = 'detail' | 'simulate';

export default function DeploymentSimulator() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [selected, setSelected] = useState<Deployment | null>(null);
  const [mode, setMode] = useState<Mode>('detail');
  const [form, setForm] = useState<FormState>(emptyForm);
  const [stage, setStage] = useState<Stage>('idle');
  const [result, setResult] = useState<Deployment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const cancelledRef = useRef(false);

  async function loadDeployments() {
    try {
      const all = await api.deployments();
      if (!cancelledRef.current) {
        setDeployments(all);
      }
    } catch (err) {
      if (!cancelledRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to load deployments');
      }
    } finally {
      if (!cancelledRef.current) setLoading(false);
    }
  }

  useEffect(() => {
    cancelledRef.current = false;
    loadDeployments();
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  function openDeployment(deployment: Deployment) {
    setSelected(deployment);
    setMode('detail');
    setResult(null);
    setStage('idle');
    setError(null);
  }

  function simulateFrom(deployment: Deployment) {
    setForm(formFromDeployment(deployment));
    setMode('simulate');
    setResult(null);
    setStage('idle');
  }

  function newSimulation() {
    setSelected(null);
    setForm(emptyForm);
    setMode('simulate');
    setResult(null);
    setStage('idle');
  }

  async function runSimulation() {
    if (stage !== 'idle' && stage !== 'done') return;
    setError(null);
    setResult(null);
    setStage('ingesting');

    // The engine scores from service, environment, status, tests, coverage and
    // dependencies. Repository, pipeline and commit are labels on the resulting
    // record and are not stored on a deployment, so a "Simulate this deployment"
    // leaves them blank. The backend still requires them, so fill simulation
    // defaults rather than fail: an explicitly SIMULATED record inventing its own
    // pipeline label is honest in a way a demo connection claiming to be live was
    // not.
    const service = form.serviceName.trim() || 'service';
    try {
      const created = await api.simulateCiSignal({
        provider: form.provider,
        repository: form.repository.trim() || `simulation/${service}`,
        serviceName: form.serviceName,
        ownerTeam: form.ownerTeam,
        environment: form.environment,
        commitSha: form.commitSha || Math.random().toString(16).slice(2, 9),
        pipelineName: form.pipelineName.trim() || `simulated-${form.environment.trim() || 'deploy'}`,
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
      loadDeployments();
    } catch (err) {
      if (cancelledRef.current) return;
      // Name the fields the API rejected rather than a bare "Request validation
      // failed", which told the user nothing about what to fix.
      setError(err instanceof ApiError ? err.detailedMessage() : err instanceof Error ? err.message : 'Simulation failed');
      setStage('idle');
    }
  }

  const running = stage !== 'idle' && stage !== 'done';
  const assessment = result?.riskAssessment ?? null;

  return (
    <div className="deployments-page">
      <div className="panel deployments-list-panel">
        <div className="deployments-list-head">
          <div className="chart-card-header">Deployments</div>
          <button className="action-btn" onClick={newSimulation}>
            <Plus size={14} /> New simulation
          </button>
        </div>

        {loading ? (
          <div className="page-empty-state">Loading deployments...</div>
        ) : deployments.length === 0 ? (
          <div className="page-empty-state">
            No deployments yet. Push to a connected repository, or run a simulation.
          </div>
        ) : (
          <div className="operator-list">
            {deployments.map((d) => (
              <button
                key={d.id}
                className={`operator-row deployment-row-button${selected?.id === d.id ? ' selected' : ''}`}
                onClick={() => openDeployment(d)}
              >
                <span className={`risk-pill risk-${(d.riskAssessment?.level ?? 'low').toLowerCase()}`}>
                  {d.riskAssessment?.level ?? 'N/A'}
                </span>
                <div className="operator-row-body">
                  <div className="operator-row-title">
                    {d.serviceName} &middot; {d.deploymentKey}
                    <span className={`deployment-source source-${deploymentSource(d).toLowerCase()}`}>
                      {deploymentSource(d)}
                    </span>
                  </div>
                  <div className="operator-row-meta">
                    {d.riskAssessment?.score ?? 0}% risk &middot; {d.status} &middot; {d.environment}
                    {d.commitSha ? ` · ${d.commitSha.slice(0, 7)}` : ''}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="deployments-detail-col">
        {mode === 'detail' && selected ? (
          <div className="panel deployment-detail-panel">
            <div className="deployment-detail-head">
              <div>
                <div className="engineer-form-header">
                  <Rocket size={16} /> {selected.serviceName}
                </div>
                <div className="operator-row-meta">
                  {selected.deploymentKey} &middot; {deploymentSource(selected)} &middot;{' '}
                  {new Date(selected.createdAt).toLocaleString()}
                </div>
              </div>
              <button className="action-btn" onClick={() => simulateFrom(selected)}>
                <Sparkles size={14} /> Simulate this deployment
              </button>
            </div>

            {selected.pullRequestTitle ? (
              <p className="deployment-detail-title">{selected.pullRequestTitle}</p>
            ) : null}

            {selected.riskAssessment ? (
              <>
                <div className="simulator-result-header">
                  <span className={`risk-pill risk-${selected.riskAssessment.level.toLowerCase()}`}>
                    {selected.riskAssessment.level}
                  </span>
                  <span className="simulator-result-score">{selected.riskAssessment.score}% risk</span>
                </div>

                <div className="simulator-result-row">
                  <GitBranch size={13} /> {selected.environment} &middot; {selected.status}
                  {selected.commitSha ? ` · ${selected.commitSha.slice(0, 7)}` : ''}
                </div>
                <div className="simulator-result-row">
                  <Layers size={13} /> Dependencies:{' '}
                  {selected.dependencies.join(', ') || 'none recorded'}
                </div>
                <div className="simulator-result-row">
                  <ShieldAlert size={13} /> {selected.riskAssessment.recommendation}
                </div>

                <p className="simulator-result-explanation">{selected.riskAssessment.aiExplanation}</p>

                {selected.riskAssessment.reasons.length > 0 ? (
                  <>
                    <div className="chart-card-header">Evidence</div>
                    <ul className="service-detail-list">
                      {selected.riskAssessment.reasons.map((reason, i) => (
                        <li key={i}>
                          <b>[{reason.category}]</b> {reason.evidence}{' '}
                          <span className="tag">impact {reason.impact}</span>
                        </li>
                      ))}
                    </ul>
                  </>
                ) : null}

                <div className="deployment-detail-meta">
                  Owned by {selected.ownerTeam} &middot; assessed{' '}
                  {new Date(selected.riskAssessment.assessedAt).toLocaleString()}
                </div>
              </>
            ) : (
              <div className="page-empty-state">This deployment has not been assessed yet.</div>
            )}
          </div>
        ) : mode === 'simulate' ? (
          <>
            <div className="panel engineer-form simulator-form">
              <div className="deployments-list-head">
                <div className="engineer-form-header">
                  <Rocket size={16} />
                  {selected ? `Simulate from ${selected.deploymentKey}` : 'New simulation'}
                </div>
                {selected ? (
                  <button className="action-btn" onClick={() => setMode('detail')}>
                    <ArrowLeft size={14} /> Back to deployment
                  </button>
                ) : null}
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
                <input
                  value={form.repository}
                  placeholder="owner/name"
                  onChange={(e) => setForm({ ...form, repository: e.target.value })}
                />
              </label>
              <div className="engineer-form-row">
                <label>
                  Service
                  <input
                    value={form.serviceName}
                    placeholder="checkout-service"
                    onChange={(e) => setForm({ ...form, serviceName: e.target.value })}
                  />
                </label>
                <label>
                  Owner team
                  <input
                    value={form.ownerTeam}
                    placeholder="Team name"
                    onChange={(e) => setForm({ ...form, ownerTeam: e.target.value })}
                  />
                </label>
              </div>
              <div className="engineer-form-row">
                <label>
                  Environment
                  <input value={form.environment} onChange={(e) => setForm({ ...form, environment: e.target.value })} />
                </label>
                <label>
                  Pipeline
                  <input
                    value={form.pipelineName}
                    placeholder="deploy-production"
                    onChange={(e) => setForm({ ...form, pipelineName: e.target.value })}
                  />
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
                <input
                  value={form.dependencies}
                  placeholder="customer-ledger, fraud-screening"
                  onChange={(e) => setForm({ ...form, dependencies: e.target.value })}
                />
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
                    <GitBranch size={13} /> {result.deploymentKey} &middot; {result.serviceName} &middot;{' '}
                    {result.environment}
                  </div>
                  <div className="simulator-result-row">
                    <ShieldAlert size={13} /> {assessment.recommendation}
                  </div>
                  <p className="simulator-result-explanation">{assessment.aiExplanation}</p>
                  <button className="action-btn" onClick={() => openDeployment(result)}>
                    Open this deployment
                  </button>
                </div>
              ) : (
                <div className="chart-empty">
                  Fill in the CI signal and run the simulation to see a real risk assessment.
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="panel deployment-detail-panel">
            <div className="page-empty-state">
              Select a deployment to see what happened, or start a new simulation.
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
