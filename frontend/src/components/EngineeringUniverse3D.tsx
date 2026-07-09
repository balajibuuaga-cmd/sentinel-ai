import { forwardRef, useImperativeHandle, useMemo, useRef, useState } from 'react';
import type { ForwardedRef, RefObject } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Text, Line, Stars } from '@react-three/drei';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';
import type { OrbitControls as OrbitControlsImpl } from 'three-stdlib';
import type { ServiceEdge, ServiceNode, ServiceStatus } from '../types/dashboard';

const statusColor: Record<ServiceStatus, string> = {
  healthy: '#34e0a1',
  warning: '#fbbf24',
  'high-risk': '#fb4d6a',
  unknown: '#38bdf8',
};

const statusHeight: Record<ServiceStatus, number> = {
  healthy: 1.4,
  warning: 2.1,
  'high-risk': 3,
  unknown: 1.4,
};

interface SpatialNode extends ServiceNode {
  position: [number, number, number];
}

function toSpatial(nodes: ServiceNode[]): SpatialNode[] {
  return nodes.map((node) => ({
    ...node,
    position: [(node.x - 50) / 2.6, 0, (node.y - 50) / 2.6],
  }));
}

function Building({
  node,
  selected,
  recommended,
  riskScore,
  onClick,
}: {
  node: SpatialNode;
  selected: boolean;
  recommended: boolean;
  riskScore?: number;
  onClick: () => void;
}) {
  const meshRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const height = statusHeight[node.status];
  const color = recommended ? '#a855f7' : statusColor[node.status];

  useFrame(({ clock }) => {
    if (!meshRef.current) return;
    const pulse = node.status === 'high-risk' ? Math.sin(clock.elapsedTime * 3) * 0.15 + 1 : 1;
    meshRef.current.scale.set(1, pulse, 1);
    const material = meshRef.current.material as THREE.MeshStandardMaterial;
    const baseIntensity = recommended ? 1.4 : node.status === 'high-risk' ? 1.1 : 0.55;
    material.emissiveIntensity = hovered || selected ? baseIntensity + 0.6 : baseIntensity;
  });

  return (
    <group position={node.position}>
      <mesh
        ref={meshRef}
        position={[0, height / 2, 0]}
        onClick={(e) => {
          e.stopPropagation();
          onClick();
        }}
        onPointerOver={(e) => {
          e.stopPropagation();
          setHovered(true);
          document.body.style.cursor = 'pointer';
        }}
        onPointerOut={() => {
          setHovered(false);
          document.body.style.cursor = 'default';
        }}
      >
        <boxGeometry args={[1.1, height, 1.1]} />
        <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.55} transparent opacity={0.88} />
      </mesh>
      <mesh position={[0, 0.02, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[0.75, selected ? 0.95 : 0.85, 32]} />
        <meshBasicMaterial color={color} transparent opacity={selected ? 0.9 : 0.35} />
      </mesh>
      <Text position={[0, height + 0.55, 0]} fontSize={0.32} color="#e8ecf7" anchorX="center" anchorY="bottom">
        {node.name}
      </Text>
      <Text position={[0, height + 0.22, 0]} fontSize={0.2} color={color} anchorX="center" anchorY="bottom">
        {riskScore !== undefined ? `${node.value} · Risk ${riskScore}%` : node.value}
      </Text>
    </group>
  );
}

function Beam({ from, to, color }: { from: [number, number, number]; to: [number, number, number]; color: string }) {
  const points = useMemo(() => [new THREE.Vector3(...from), new THREE.Vector3(...to)], [from, to]);
  return <Line points={points} color={color} lineWidth={1.4} transparent opacity={0.45} />;
}

function Floor() {
  return (
    <gridHelper args={[60, 40, '#2a3350', '#151a2c']} position={[0, 0, 0]}>
      <meshBasicMaterial attach="material" />
    </gridHelper>
  );
}

interface SceneProps {
  nodes: ServiceNode[];
  edges: ServiceEdge[];
  selectedId: string | null;
  recommendedService: string | null;
  riskScores?: Record<string, number>;
  controlsRef: RefObject<OrbitControlsImpl | null>;
  onSelect: (id: string) => void;
}

function Scene({ nodes, edges, selectedId, recommendedService, riskScores, controlsRef, onSelect }: SceneProps) {
  const spatialNodes = useMemo(() => toSpatial(nodes), [nodes]);
  const byId = useMemo(() => Object.fromEntries(spatialNodes.map((n) => [n.id, n])), [spatialNodes]);

  return (
    <>
      <ambientLight intensity={0.35} />
      <pointLight position={[0, 12, 0]} intensity={60} color="#8fd8ff" />
      <Stars radius={80} depth={30} count={1200} factor={2} fade speed={0.4} />
      <Floor />

      {edges.map((edge, i) => {
        const from = byId[edge.from];
        const to = byId[edge.to];
        if (!from || !to) return null;
        const color = from.status === 'high-risk' || to.status === 'high-risk' ? '#fb4d6a' : '#38bdf8';
        return (
          <Beam
            key={`${edge.from}-${edge.to}-${i}`}
            from={[from.position[0], 0.4, from.position[2]]}
            to={[to.position[0], 0.4, to.position[2]]}
            color={color}
          />
        );
      })}

      {spatialNodes.map((node) => (
        <Building
          key={node.id}
          node={node}
          selected={selectedId === node.id}
          recommended={recommendedService === node.id}
          riskScore={riskScores?.[node.id]}
          onClick={() => onSelect(node.id)}
        />
      ))}

      <OrbitControls
        ref={controlsRef}
        enablePan
        minDistance={6}
        maxDistance={40}
        maxPolarAngle={Math.PI / 2.15}
        autoRotate
        autoRotateSpeed={0.35}
      />

      <EffectComposer>
        <Bloom intensity={1.4} luminanceThreshold={0.15} luminanceSmoothing={0.35} mipmapBlur radius={0.7} />
      </EffectComposer>
    </>
  );
}

interface Props {
  nodes: ServiceNode[];
  edges: ServiceEdge[];
  selectedId: string | null;
  recommendedService?: string | null;
  riskScores?: Record<string, number>;
  onNodeClick: (node: ServiceNode) => void;
}

export interface EngineeringUniverse3DHandle {
  zoomIn: () => void;
  zoomOut: () => void;
}

function EngineeringUniverse3D(
  { nodes, edges, selectedId, recommendedService = null, riskScores, onNodeClick }: Props,
  ref: ForwardedRef<EngineeringUniverse3DHandle>,
) {
  const controlsRef = useRef<OrbitControlsImpl | null>(null);

  useImperativeHandle(ref, () => ({
    zoomIn: () => {
      controlsRef.current?.dollyIn(1.25);
      controlsRef.current?.update();
    },
    zoomOut: () => {
      controlsRef.current?.dollyOut(1.25);
      controlsRef.current?.update();
    },
  }));

  if (nodes.length === 0) {
    return (
      <div className="universe-empty">
        No architecture imported yet. Call <code>POST /api/architecture/import</code> to map your services.
      </div>
    );
  }

  const byId = Object.fromEntries(nodes.map((n) => [n.id, n]));

  return (
    <Canvas camera={{ position: [0, 9, 14], fov: 50 }} className="universe-3d-canvas">
      <Scene
        nodes={nodes}
        edges={edges}
        selectedId={selectedId}
        recommendedService={recommendedService}
        riskScores={riskScores}
        controlsRef={controlsRef}
        onSelect={(id) => {
          const node = byId[id];
          if (node) onNodeClick(node);
        }}
      />
    </Canvas>
  );
}

export default forwardRef(EngineeringUniverse3D);
