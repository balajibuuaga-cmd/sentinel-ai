import type { ServiceEdge, ServiceNode, ServiceStatus } from '../types/dashboard';

const statusColor: Record<ServiceStatus, string> = {
  healthy: 'var(--green)',
  warning: 'var(--amber)',
  'high-risk': 'var(--red)',
  unknown: 'var(--blue)',
};

interface Props {
  nodes: ServiceNode[];
  edges: ServiceEdge[];
  selectedId?: string | null;
  onNodeClick?: (node: ServiceNode) => void;
}

export default function ServiceGraph({ nodes, edges, selectedId, onNodeClick }: Props) {
  const nodeById = Object.fromEntries(nodes.map((n) => [n.id, n]));

  if (nodes.length === 0) {
    return (
      <div className="universe-empty">
        No architecture imported yet. Call <code>POST /api/architecture/import</code> to map your services.
      </div>
    );
  }

  return (
    <>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="universe-edges">
        {edges.map((edge) => {
          const from = nodeById[edge.from];
          const to = nodeById[edge.to];
          if (!from || !to) return null;
          return (
            <line
              key={`${edge.from}-${edge.to}`}
              x1={from.x}
              y1={from.y}
              x2={to.x}
              y2={to.y}
              stroke={statusColor[from.status]}
              strokeOpacity={0.35}
              strokeWidth={0.35}
              vectorEffect="non-scaling-stroke"
            />
          );
        })}
      </svg>

      {nodes.map((node) => (
        <div
          key={node.id}
          className={`service-node status-${node.status}${selectedId === node.id ? ' selected' : ''}${onNodeClick ? ' clickable' : ''}`}
          style={{ left: `${node.x}%`, top: `${node.y}%` }}
          onClick={onNodeClick ? () => onNodeClick(node) : undefined}
        >
          <div className="service-node-core">
            <span className="service-node-icon" />
          </div>
          <div className="service-node-label">
            <span className="service-node-name">{node.name}</span>
            <span className="service-node-value">
              <span className="status-dot" /> {node.value}
            </span>
          </div>
        </div>
      ))}
    </>
  );
}
