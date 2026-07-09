import { useEffect, useRef, useState } from 'react';
import { AlertOctagon, RotateCcw, Server, MessageSquare, FileWarning, UserCheck, CheckCircle2 } from 'lucide-react';
import { api } from '../api/client';
import type { Incident } from '../api/types';

const PIPELINE_STEPS = [
  { label: 'Rollback Deployment', icon: RotateCcw },
  { label: 'Restart Pod', icon: Server },
  { label: 'Notify Slack', icon: MessageSquare },
  { label: 'Open Jira', icon: FileWarning },
  { label: 'Assign Engineer', icon: UserCheck },
  { label: 'Monitor Results', icon: CheckCircle2 },
];

const severityTone: Record<string, string> = { SEV1: 'bad', SEV2: 'warn', SEV3: 'good' };

export default function Incidents() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pipelineStep, setPipelineStep] = useState<number | null>(null);
  const timeoutRef = useRef<number | null>(null);

  useEffect(() => {
    api
      .incidents()
      .then((list) => {
        setIncidents(list);
        if (list.length > 0) setSelectedId(list[0].id);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load incidents'));
    return () => {
      if (timeoutRef.current) window.clearTimeout(timeoutRef.current);
    };
  }, []);

  const selected = incidents.find((i) => i.id === selectedId) ?? null;

  function runAutonomousAction() {
    if (!selected || pipelineStep !== null) return;
    setPipelineStep(0);
    const advance = (step: number) => {
      timeoutRef.current = window.setTimeout(async () => {
        if (step < PIPELINE_STEPS.length - 1) {
          setPipelineStep(step + 1);
          advance(step + 1);
          return;
        }
        try {
          const updated = await api.updateIncidentStatus(selected.id, {
            status: 'MITIGATING',
            actor: 'sentinel-ai-autonomous',
            note: 'Autonomous remediation pipeline executed: rollback, restart, Slack notify, Jira ticket, engineer assigned. Awaiting human confirmation.',
          });
          setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
          setPipelineStep(PIPELINE_STEPS.length);
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Failed to record autonomous action');
        }
      }, 550);
    };
    advance(0);
  }

  if (error) {
    return <div className="page-empty-state">Could not load incidents: {error}</div>;
  }

  return (
    <div className="incidents-page">
      <div className="panel incidents-list">
        <div className="memory-list-header">Active Incidents</div>
        {incidents.length === 0 ? <div className="service-detail-empty-line">No incidents open right now.</div> : null}
        {incidents.map((i) => (
          <button
            key={i.id}
            className={`incident-row${i.id === selectedId ? ' active' : ''}`}
            onClick={() => {
              setSelectedId(i.id);
              setPipelineStep(null);
            }}
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

          {selected.status === 'ACTIVE' ? (
            <button className="briefing-cta" onClick={runAutonomousAction} disabled={pipelineStep !== null}>
              {pipelineStep === null ? 'Approve Autonomous Remediation' : 'Executing...'}
            </button>
          ) : (
            <div className="incidents-status-note">Current status: {selected.status}</div>
          )}

          {pipelineStep !== null ? (
            <div className="pipeline">
              {PIPELINE_STEPS.map((step, i) => {
                const Icon = step.icon;
                const state = i < pipelineStep ? 'done' : i === pipelineStep ? 'active' : 'pending';
                return (
                  <div key={step.label} className={`pipeline-step pipeline-${state}`}>
                    <Icon size={14} />
                    <span>{step.label}</span>
                  </div>
                );
              })}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="page-empty-state">Select an incident to inspect it.</div>
      )}
    </div>
  );
}
