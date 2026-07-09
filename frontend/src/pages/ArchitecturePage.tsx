import { useEffect, useMemo, useState } from 'react';
import { Boxes } from 'lucide-react';
import { api } from '../api/client';
import { buildServiceGraph } from '../api/transform';
import EngineeringUniverse3D from '../components/EngineeringUniverse3D';
import ServiceDetailPanel from '../components/ServiceDetailPanel';
import type { ArchitectureBrain } from '../api/types';
import type { ServiceEdge, ServiceNode } from '../types/dashboard';

export default function ArchitecturePage() {
  const [brain, setBrain] = useState<ArchitectureBrain | null>(null);
  const [graph, setGraph] = useState<{ nodes: ServiceNode[]; edges: ServiceEdge[] }>({ nodes: [], edges: [] });
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .architectureBrain()
      .then((data) => {
        setBrain(data);
        setGraph(buildServiceGraph(data));
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load architecture'));
  }, []);

  const recommendedServiceName = useMemo(() => {
    if (!brain?.recommendedRefactor) return null;
    const match = brain.services.find((s) => brain.recommendedRefactor.includes(s.serviceName));
    return match?.serviceName ?? null;
  }, [brain]);

  if (error) {
    return <div className="page-empty-state">Could not load Architecture Brain: {error}</div>;
  }

  if (!brain) {
    return <div className="page-empty-state">Mapping your service architecture...</div>;
  }

  return (
    <div className="architecture-page">
      <div className="panel architecture-summary">
        <div className="architecture-summary-header">
          <Boxes size={16} /> Engineering Universe
        </div>
        <p>{brain.summary}</p>
        <div className="architecture-summary-stats">
          <span><b>{brain.serviceCount}</b> services</span>
          <span><b>{brain.dependencyCount}</b> dependencies</span>
          <span><b>{brain.riskCount}</b> risks</span>
        </div>
        {brain.recommendedRefactor ? (
          <div className="architecture-refactor">
            <span className="ai-briefing-block-label">Recommended Refactor</span>
            <p>{brain.recommendedRefactor}</p>
          </div>
        ) : null}
      </div>

      <div className="architecture-body">
        <div className="panel architecture-canvas universe-canvas-3d">
          <EngineeringUniverse3D
            nodes={graph.nodes}
            edges={graph.edges}
            selectedId={selected}
            recommendedService={recommendedServiceName}
            onNodeClick={(node) => setSelected(node.id)}
          />
          <div className="universe-3d-hint">Drag to orbit &middot; scroll to zoom &middot; click a building to inspect</div>
        </div>
        <ServiceDetailPanel brain={brain} serviceName={selected} onClose={() => setSelected(null)} />
      </div>
    </div>
  );
}
