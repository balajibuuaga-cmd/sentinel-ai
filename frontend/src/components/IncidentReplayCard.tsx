import { useEffect, useMemo, useState } from 'react';
import { Play, Pause, SkipBack, SkipForward, Gauge, History } from 'lucide-react';
import ServiceGraph from './ServiceGraph';
import type { Incident } from '../api/types';
import type { ServiceEdge, ServiceNode, ServiceStatus } from '../types/dashboard';

interface Props {
  incidents: Incident[];
  serviceGraph: { nodes: ServiceNode[]; edges: ServiceEdge[] };
}

function formatTime(iso: string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '--:--';
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

export default function IncidentReplayCard({ incidents, serviceGraph }: Props) {
  const replayable = useMemo(
    () => incidents.filter((incident) => incident.timeline.length > 0),
    [incidents],
  );
  const [incidentId, setIncidentId] = useState<number | null>(replayable[0]?.id ?? null);
  const [stepIndex, setStepIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<1 | 2>(1);

  const incident = replayable.find((i) => i.id === incidentId) ?? replayable[0] ?? null;
  const timeline = useMemo(
    () => (incident ? [...incident.timeline].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt)) : []),
    [incident],
  );

  useEffect(() => {
    setStepIndex(0);
    setPlaying(false);
  }, [incident?.id]);

  useEffect(() => {
    if (!playing || timeline.length === 0) return undefined;
    if (stepIndex >= timeline.length - 1) {
      setPlaying(false);
      return undefined;
    }
    const timer = setTimeout(() => setStepIndex((i) => i + 1), 1400 / speed);
    return () => clearTimeout(timer);
  }, [playing, stepIndex, speed, timeline.length]);

  if (!incident || timeline.length === 0) {
    return (
      <div className="panel chart-card incident-replay-card">
        <div className="chart-card-header">
          <History size={15} /> Incident Replay
        </div>
        <div className="chart-empty">No incident with a recorded timeline yet.</div>
      </div>
    );
  }

  const affectedNeighbors = new Set(
    serviceGraph.edges
      .filter((edge) => edge.from === incident.serviceName || edge.to === incident.serviceName)
      .map((edge) => (edge.from === incident.serviceName ? edge.to : edge.from)),
  );

  const halfway = Math.floor(timeline.length / 2);
  const isLastStep = stepIndex === timeline.length - 1;
  const recovered = isLastStep && incident.status === 'RESOLVED';

  const replayNodes: ServiceNode[] = serviceGraph.nodes.map((node) => {
    let status: ServiceStatus = node.status;
    if (node.id === incident.serviceName) {
      status = recovered ? 'healthy' : 'high-risk';
    } else if (affectedNeighbors.has(node.id) && stepIndex >= halfway) {
      status = recovered ? node.status : 'warning';
    }
    return { ...node, status };
  });

  return (
    <div className="panel chart-card incident-replay-card">
      <div className="chart-card-header">
        <History size={15} /> Incident Replay: {incident.incidentKey}
        {replayable.length > 1 ? (
          <select
            className="incident-replay-select"
            value={incident.id}
            onChange={(e) => setIncidentId(Number(e.target.value))}
          >
            {replayable.map((i) => (
              <option key={i.id} value={i.id}>
                {i.incidentKey}
              </option>
            ))}
          </select>
        ) : null}
      </div>

      <div className="incident-replay-body">
        <div className="incident-replay-map">
          <ServiceGraph nodes={replayNodes} edges={serviceGraph.edges} />
        </div>

        <div className="incident-replay-timeline">
          {timeline.map((event, i) => (
            <div
              key={`${event.occurredAt}-${i}`}
              className={`incident-replay-step${i === stepIndex ? ' current' : ''}${i < stepIndex ? ' past' : ''}`}
            >
              <span className="incident-replay-step-time">{formatTime(event.occurredAt)}</span>
              <div className="incident-replay-step-body">
                <div className="incident-replay-step-label">{event.label}</div>
                <div className="incident-replay-step-detail">{event.detail}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="incident-replay-controls">
        <button className="icon-pill small" onClick={() => setStepIndex((i) => Math.max(0, i - 1))} title="Previous step">
          <SkipBack size={14} />
        </button>
        <button
          className="icon-pill small incident-replay-play"
          onClick={() => {
            if (stepIndex >= timeline.length - 1) {
              setStepIndex(0);
              setPlaying(true);
            } else {
              setPlaying((p) => !p);
            }
          }}
          title={playing ? 'Pause' : 'Play'}
        >
          {playing ? <Pause size={14} /> : <Play size={14} />}
        </button>
        <button
          className="icon-pill small"
          onClick={() => setStepIndex((i) => Math.min(timeline.length - 1, i + 1))}
          title="Next step"
        >
          <SkipForward size={14} />
        </button>
        <input
          className="incident-replay-scrub"
          type="range"
          min={0}
          max={timeline.length - 1}
          value={stepIndex}
          onChange={(e) => {
            setPlaying(false);
            setStepIndex(Number(e.target.value));
          }}
        />
        <button className="icon-pill small" onClick={() => setSpeed((s) => (s === 1 ? 2 : 1))} title="Playback speed">
          <Gauge size={14} /> {speed.toFixed(1)}x
        </button>
      </div>
    </div>
  );
}
