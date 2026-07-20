import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Boxes, LayoutGrid, RefreshCw, Maximize2, Plus, Minus } from 'lucide-react';
import ServiceGraph from './ServiceGraph';
import EngineeringUniverse3D, { type EngineeringUniverse3DHandle } from './EngineeringUniverse3D';
import type { ServiceEdge, ServiceNode } from '../types/dashboard';

const tabs = ['Services', 'Dependencies', 'Health', 'Risks'];

interface Props {
  nodes: ServiceNode[];
  edges: ServiceEdge[];
  riskScores?: Record<string, number>;
  onRefresh?: () => void;
}

export default function EngineeringUniverse({ nodes, edges, riskScores, onRefresh }: Props) {
  const [activeTab, setActiveTab] = useState('Services');
  const [mode, setMode] = useState<'3d' | '2d'>('3d');
  const [selected, setSelected] = useState<string | null>(null);
  const universeRef = useRef<EngineeringUniverse3DHandle>(null);

  return (
    <div className="panel universe-panel universe-panel-hero">
      <div className="universe-header">
        <div className="universe-title">
          <span>Engineering Universe</span>
          <span className="live-badge">
            <span className="live-dot" /> Live
          </span>
        </div>
        <div className="universe-tabs">
          {tabs.map((tab) => (
            <button
              key={tab}
              className={`universe-tab${activeTab === tab ? ' active' : ''}`}
              onClick={() => setActiveTab(tab)}
            >
              {tab}
            </button>
          ))}
        </div>
        <div className="universe-tools">
          <button
            className={`icon-pill small${mode === '3d' ? ' active' : ''}`}
            onClick={() => setMode(mode === '3d' ? '2d' : '3d')}
            title={mode === '3d' ? 'Switch to 2D map' : 'Switch to 3D universe'}
          >
            {mode === '3d' ? <Boxes size={14} /> : <LayoutGrid size={14} />} {mode === '3d' ? '3D' : '2D'}
          </button>
          <button className="icon-pill small" onClick={onRefresh} title="Refresh live data">
            <RefreshCw size={14} />
          </button>
          {/* The panel is a preview of the full topology; expanding means
              opening the dedicated Architecture view. */}
          <Link className="icon-pill small" to="/architecture" title="Open full architecture view">
            <Maximize2 size={14} />
          </Link>
        </div>
      </div>

      <div className={mode === '3d' ? 'universe-canvas-3d-wrap' : 'universe-canvas'}>
        {mode === '3d' ? (
          <>
            <EngineeringUniverse3D
              ref={universeRef}
              nodes={nodes}
              edges={edges}
              selectedId={selected}
              riskScores={riskScores}
              onNodeClick={(node) => setSelected(node.id)}
            />
            <div className="universe-zoom-controls">
              <button className="icon-pill small" onClick={() => universeRef.current?.zoomIn()} title="Zoom in">
                <Plus size={14} />
              </button>
              <button className="icon-pill small" onClick={() => universeRef.current?.zoomOut()} title="Zoom out">
                <Minus size={14} />
              </button>
            </div>
          </>
        ) : (
          <ServiceGraph nodes={nodes} edges={edges} selectedId={selected} onNodeClick={(node) => setSelected(node.id)} />
        )}
      </div>

      <div className="universe-legend">
        <span><i className="dot" style={{ background: 'var(--green)' }} /> Healthy</span>
        <span><i className="dot" style={{ background: 'var(--amber)' }} /> Warning</span>
        <span><i className="dot" style={{ background: 'var(--red)' }} /> High Risk</span>
      </div>
    </div>
  );
}
