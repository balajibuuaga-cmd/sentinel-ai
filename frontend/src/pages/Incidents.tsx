import { useEffect, useRef, useState } from 'react';
import { AlertOctagon, RotateCcw, Server, MessageSquare, FileWarning, UserCheck, CheckCircle2, Check, Play } from 'lucide-react';
import { api, currentSession } from '../api/client';
import type { Incident, IncidentRemediationStep } from '../api/types';

const PIPELINE_STEPS: { key: IncidentRemediationStep; label: string; icon: typeof RotateCcw }[] = [
  { key: 'ROLLBACK_DEPLOYMENT', label: 'Rollback Deployment', icon: RotateCcw },
  { key: 'RESTART_POD', label: 'Restart Pod', icon: Server },
  { key: 'NOTIFY_SLACK', label: 'Notify Slack', icon: MessageSquare },
  { key: 'OPEN_JIRA', label: 'Open Jira', icon: FileWarning },
  { key: 'ASSIGN_ENGINEER', label: 'Assign Engineer', icon: UserCheck },
  { key: 'MONITOR_RESULTS', label: 'Monitor Results', icon: CheckCircle2 },
];

const severityTone: Record<string, string> = { SEV1: 'bad', SEV2: 'warn', SEV3: 'good' };

// Maps each executed step to the outcome the backend recorded, e.g. "Message
// posted to Slack." or "No Slack webhook configured...". The step is always
// recorded; the outcome says whether an external action actually happened.
function executedSteps(incident: Incident): Map<string, string> {
  const done = new Map<string, string>();
  incident.timeline.forEach((event) => {
    const match = PIPELINE_STEPS.find((step) => event.label === `Remediation step: ${step.label}`);
    if (match) done.set(match.key, event.detail);
  });
  return done;
}

export default function Incidents() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [runningStep, setRunningStep] = useState<string | null>(null);
  const [autonomousRunning, setAutonomousRunning] = useState(false);
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    api
      .incidents()
      .then((list) => {
        if (cancelledRef.current) return;
        setIncidents(list);
        if (list.length > 0) setSelectedId(list[0].id);
      })
      .catch((err) => {
        if (cancelledRef.current) return;
        setError(err instanceof Error ? err.message : 'Failed to load incidents');
      });
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  const selected = incidents.find((i) => i.id === selectedId) ?? null;
  const doneSteps = selected ? executedSteps(selected) : new Map<string, string>();

  function applyUpdated(updated: Incident) {
    setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
  }

  async function runStep(step: IncidentRemediationStep) {
    if (!selected || runningStep) return;
    setRunningStep(step);
    setError(null);
    try {
      const updated = await api.executeRemediationStep(selected.id, step, currentSession()?.username ?? 'operator');
      if (!cancelledRef.current) applyUpdated(updated);
    } catch (err) {
      if (!cancelledRef.current) setError(err instanceof Error ? err.message : 'Failed to execute remediation step');
    } finally {
      if (!cancelledRef.current) setRunningStep(null);
    }
  }

  async function runAutonomousRemediation() {
    if (!selected || autonomousRunning || runningStep) return;
    setAutonomousRunning(true);
    setError(null);
    const actor = 'sentinel-ai-autonomous';
    let current = selected;
    try {
      for (const step of PIPELINE_STEPS) {
        if (cancelledRef.current) return;
        if (executedSteps(current).has(step.key)) continue;
        setRunningStep(step.key);
        current = await api.executeRemediationStep(current.id, step.key, actor);
        applyUpdated(current);
      }
    } catch (err) {
      if (!cancelledRef.current) setError(err instanceof Error ? err.message : 'Autonomous remediation failed');
    } finally {
      if (!cancelledRef.current) {
        setRunningStep(null);
        setAutonomousRunning(false);
      }
    }
  }

  if (error && incidents.length === 0) {
    return <div className="page-empty-state">Could not load incidents: {error}</div>;
  }

  const remaining = PIPELINE_STEPS.filter((step) => !doneSteps.has(step.key)).length;

  return (
    <div className="incidents-page">
      <div className="panel incidents-list">
        <div className="memory-list-header">Active Incidents</div>
        {incidents.length === 0 ? <div className="service-detail-empty-line">No incidents open right now.</div> : null}
        {incidents.map((i) => (
          <button
            key={i.id}
            className={`incident-row${i.id === selectedId ? ' active' : ''}`}
            onClick={() => setSelectedId(i.id)}
          >
            <span className={`rec-badge tone-${severityTone[i.severity]}`}>{i.severity}</span>
            <div className="incident-row-body">
              <div className="incident-row-service">{i.serviceName}</div>
              <div className="incident-row-summary">{i.summary}</div>
            </div>
            <span className="incident-row-status">{i.status}</span>
          </button>
        ))}
      </div>

      {selected ? (
        <div className="panel incidents-detail">
          <div className="incidents-detail-header">
            <AlertOctagon size={18} className="tone-bad" />
            <div>
              <div className="engineer-detail-title">{selected.incidentKey}</div>
              <div className="engineer-detail-sub">
                {selected.serviceName} &middot; {selected.environment} &middot; risk {selected.riskScore}%
              </div>
            </div>
          </div>

          <p className="engineer-detail-explanation">{selected.commanderBrief}</p>

          <div className="incidents-recommendation">
            <div className="ai-briefing-block-label">Recommended Action</div>
            <p>{selected.recommendedAction}</p>
          </div>

          {error ? <div className="auth-error">{error}</div> : null}

          <div className="incidents-status-note">Current status: {selected.status}</div>

          <div className="remediation">
            <div className="ai-briefing-block-label">Remediation Pipeline</div>
            {/* Running a step records it on the incident and moves the workflow
                forward. It does not perform the external action itself: there is
                no Slack, Jira or deploy-rollback integration behind these yet, so
                the badge says "Recorded", not "Executed". */}
            <p className="remediation-note">
              Running a step records it on the incident and advances the workflow. Where a real
              integration backs the step it also performs the action — a connected Slack channel is
              notified, a linked deployment is marked rolled back — and each row shows what actually
              happened.
            </p>
            <div className="remediation-list">
              {PIPELINE_STEPS.map((step) => {
                const Icon = step.icon;
                const done = doneSteps.has(step.key);
                const outcome = doneSteps.get(step.key);
                // A performed step reports an actual effect ("posted", "Marked
                // ... rolled back"); a recorded one says nothing happened
                // outside Sentinel. The outcome text is authoritative; this only
                // colours the badge.
                const performed = done && !!outcome && !/^No |^This incident is not|not found|already/i.test(outcome);
                const running = runningStep === step.key;
                return (
                  <div key={step.key} className={`remediation-step${done ? ' done' : ''}${running ? ' running' : ''}`}>
                    <span className="remediation-step-icon">
                      {done ? <Check size={14} /> : <Icon size={14} />}
                    </span>
                    <div className="remediation-step-main">
                      <span className="remediation-step-label">{step.label}</span>
                      {done && outcome ? (
                        <span className="remediation-step-outcome">{outcome}</span>
                      ) : null}
                    </div>
                    {done ? (
                      <span className={`remediation-step-state${performed ? ' performed' : ''}`}>
                        {performed ? 'Performed' : 'Recorded'}
                      </span>
                    ) : (
                      <button
                        className="remediation-step-run"
                        onClick={() => runStep(step.key)}
                        disabled={runningStep !== null || autonomousRunning}
                      >
                        <Play size={12} /> {running ? 'Running...' : 'Run'}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>

            {remaining > 0 ? (
              <button
                className="briefing-cta"
                onClick={runAutonomousRemediation}
                disabled={autonomousRunning || runningStep !== null}
              >
                {autonomousRunning ? 'Recording...' : `Record Autonomous Remediation (${remaining} steps)`}
              </button>
            ) : (
              <div className="remediation-complete">All remediation steps recorded on the incident timeline.</div>
            )}
          </div>
        </div>
      ) : (
        <div className="page-empty-state">Select an incident to inspect it.</div>
      )}
    </div>
  );
}
