import { X, Server, Users, GitBranch, AlertTriangle } from 'lucide-react';
import type { ArchitectureBrain } from '../api/types';

interface Props {
  brain: ArchitectureBrain;
  serviceName: string | null;
  onClose: () => void;
}

export default function ServiceDetailPanel({ brain, serviceName, onClose }: Props) {
  if (!serviceName) {
    return (
      <div className="panel service-detail-panel service-detail-empty">
        Click a node in the graph to inspect its owner, runtime, dependencies, and risks.
      </div>
    );
  }

  const service = brain.services.find((s) => s.serviceName === serviceName);
  const outgoing = brain.dependencies.filter((d) => d.sourceService === serviceName);
  const incoming = brain.dependencies.filter((d) => d.targetService === serviceName);
  const risks = brain.risks.filter((r) => r.serviceName === serviceName);

  if (!service) {
    return null;
  }

  return (
    <div className="panel service-detail-panel">
      <div className="service-detail-header">
        <div>
          <div className="service-detail-name">{service.serviceName}</div>
          <div className="service-detail-sub">{service.tier} &middot; {service.runtime}</div>
        </div>
        <button className="icon-pill small" onClick={onClose}>
          <X size={14} />
        </button>
      </div>

      <p className="service-detail-description">{service.description}</p>

      <div className="service-detail-row">
        <Users size={14} /> {service.ownerTeam}
      </div>
      <div className="service-detail-row">
        <Server size={14} /> {service.repository}
      </div>

      <div className="service-detail-section">
        <div className="service-detail-section-title">
          <GitBranch size={13} /> Dependencies ({outgoing.length + incoming.length})
        </div>
        {outgoing.length === 0 && incoming.length === 0 ? (
          <div className="service-detail-empty-line">No dependency edges recorded.</div>
        ) : (
          <ul className="service-detail-list">
            {outgoing.map((d) => (
              <li key={`out-${d.id}`}>
                depends on <b>{d.targetService}</b> <span className="tag">{d.dependencyType}</span>
              </li>
            ))}
            {incoming.map((d) => (
              <li key={`in-${d.id}`}>
                used by <b>{d.sourceService}</b> <span className="tag">{d.dependencyType}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="service-detail-section">
        <div className="service-detail-section-title">
          <AlertTriangle size={13} /> Architecture Risks ({risks.length})
        </div>
        {risks.length === 0 ? (
          <div className="service-detail-empty-line">No risks flagged for this service.</div>
        ) : (
          <ul className="service-detail-list">
            {risks.map((r) => (
              <li key={r.id}>
                <span className={`risk-pill risk-${r.severity.toLowerCase()}`}>{r.severity}</span> {r.explanation}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
