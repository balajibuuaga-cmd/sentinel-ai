import { useEffect, useMemo, useState } from 'react';
import { Globe2, Layers, Rocket, AlertTriangle } from 'lucide-react';
import { api } from '../api/client';
import { buildRiskGlobe } from '../api/transform';
import RiskGlobe3D from '../components/RiskGlobe3D';
import type { ArchitectureBrain, Deployment } from '../api/types';

export default function RiskGlobePage() {
  const [brain, setBrain] = useState<ArchitectureBrain | null>(null);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.architectureBrain(), api.deployments()])
      .then(([b, d]) => {
        if (cancelled) return;
        setBrain(b);
        setDeployments(d);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Failed to load risk globe data');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const teams = useMemo(() => (brain ? buildRiskGlobe(brain, deployments) : []), [brain, deployments]);
  const selected = teams.find((t) => t.team === selectedTeam) ?? null;

  if (error) {
    return <div className="page-empty-state">Could not load the risk globe: {error}</div>;
  }

  if (!brain) {
    return <div className="page-empty-state">Mapping engineering risk across the company...</div>;
  }

  return (
    <div className="architecture-page">
      <div className="panel architecture-summary">
        <div className="architecture-summary-header">
          <Globe2 size={16} /> Risk Globe
        </div>
        <p>
          Sentinel AI mapped {teams.length} team{teams.length === 1 ? '' : 's'} across {brain.serviceCount} services
          and {deployments.length} tracked deployments. Rotate the globe or click a team to inspect its real evidence.
        </p>
      </div>

      <div className="architecture-body">
        <div className="panel architecture-canvas universe-canvas-3d-wrap">
          <RiskGlobe3D teams={teams} selectedTeam={selectedTeam} onSelectTeam={setSelectedTeam} />
          <div className="universe-3d-hint">Drag to orbit &middot; scroll to zoom &middot; click a team to inspect</div>
        </div>

        {selected ? (
          <div className="panel service-detail-panel">
            <div className="service-detail-header">
              <div>
                <div className="service-detail-name">{selected.team}</div>
                <div className="service-detail-sub">{selected.levelLabel}</div>
              </div>
            </div>

            <div className="service-detail-row">
              <Layers size={14} /> {selected.serviceCount} service{selected.serviceCount === 1 ? '' : 's'} owned
            </div>
            <div className="service-detail-row">
              <Rocket size={14} /> {selected.deploymentCount} tracked deployment{selected.deploymentCount === 1 ? '' : 's'}
            </div>

            <div className="service-detail-section">
              <div className="service-detail-section-title">
                <AlertTriangle size={13} /> Evidence
              </div>
              {selected.evidence.length === 0 ? (
                <div className="service-detail-empty-line">No risk evidence recorded for this team.</div>
              ) : (
                <ul className="service-detail-list">
                  {selected.evidence.map((line, i) => (
                    <li key={i}>{line}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        ) : (
          <div className="panel service-detail-panel service-detail-empty">
            Click a team on the globe to inspect its real service count, deployments, and risk evidence.
          </div>
        )}
      </div>
    </div>
  );
}
