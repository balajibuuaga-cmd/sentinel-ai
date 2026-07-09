import { useMemo, useRef, useState } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Text, Stars } from '@react-three/drei';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';
import type { RiskGlobeTeam } from '../api/transform';

const statusColor: Record<string, string> = {
  healthy: '#34e0a1',
  warning: '#fbbf24',
  'high-risk': '#fb4d6a',
};

const GLOBE_RADIUS = 5;

function teamPosition(index: number, total: number): [number, number, number] {
  const offset = 2 / total;
  const increment = Math.PI * (3 - Math.sqrt(5));
  const y = index * offset - 1 + offset / 2;
  const r = Math.sqrt(Math.max(0, 1 - y * y));
  const phi = index * increment;
  const x = Math.cos(phi) * r;
  const z = Math.sin(phi) * r;
  return [x * GLOBE_RADIUS, y * GLOBE_RADIUS, z * GLOBE_RADIUS];
}

function Globe() {
  return (
    <mesh>
      <sphereGeometry args={[GLOBE_RADIUS - 0.2, 48, 48]} />
      <meshStandardMaterial color="#12203a" emissive="#0a3d4a" emissiveIntensity={0.25} wireframe transparent opacity={0.55} />
    </mesh>
  );
}

function TeamNode({
  team,
  position,
  selected,
  onClick,
}: {
  team: RiskGlobeTeam;
  position: [number, number, number];
  selected: boolean;
  onClick: () => void;
}) {
  const meshRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);
  const color = statusColor[team.status] ?? statusColor.healthy;
  const size = 0.34 + Math.min(team.serviceCount, 5) * 0.06;

  useFrame(({ clock }) => {
    if (!meshRef.current) return;
    const pulse = team.status === 'high-risk' ? Math.sin(clock.elapsedTime * 3) * 0.14 + 1 : 1;
    meshRef.current.scale.setScalar(pulse);
    const material = meshRef.current.material as THREE.MeshStandardMaterial;
    const base = team.status === 'high-risk' ? 1.3 : team.status === 'warning' ? 0.85 : 0.5;
    material.emissiveIntensity = hovered || selected ? base + 0.6 : base;
  });

  return (
    <group position={position}>
      <mesh
        ref={meshRef}
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
        <icosahedronGeometry args={[size, 1]} />
        <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.5} transparent opacity={0.9} />
      </mesh>
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[size + 0.08, size + (selected ? 0.15 : 0.1), 32]} />
        <meshBasicMaterial color={color} transparent opacity={selected ? 0.85 : 0.3} side={THREE.DoubleSide} />
      </mesh>
      <Text position={[0, size + 0.38, 0]} fontSize={0.26} color="#e8ecf7" anchorX="center" anchorY="bottom">
        {team.team}
      </Text>
      <Text position={[0, size + 0.12, 0]} fontSize={0.17} color={color} anchorX="center" anchorY="bottom">
        {team.levelLabel}
      </Text>
    </group>
  );
}

interface SceneProps {
  teams: RiskGlobeTeam[];
  selectedTeam: string | null;
  onSelect: (team: string) => void;
}

function Scene({ teams, selectedTeam, onSelect }: SceneProps) {
  const positions = useMemo(() => teams.map((_, i) => teamPosition(i, teams.length)), [teams]);

  return (
    <>
      <ambientLight intensity={0.4} />
      <pointLight position={[10, 10, 10]} intensity={55} color="#8fd8ff" />
      <Stars radius={90} depth={40} count={1400} factor={2} fade speed={0.3} />
      <Globe />

      {teams.map((team, i) => (
        <TeamNode
          key={team.team}
          team={team}
          position={positions[i]}
          selected={selectedTeam === team.team}
          onClick={() => onSelect(team.team)}
        />
      ))}

      <OrbitControls enablePan={false} minDistance={7} maxDistance={22} autoRotate autoRotateSpeed={0.4} />

      <EffectComposer>
        <Bloom intensity={1.2} luminanceThreshold={0.2} luminanceSmoothing={0.4} mipmapBlur radius={0.7} />
      </EffectComposer>
    </>
  );
}

interface Props {
  teams: RiskGlobeTeam[];
  selectedTeam: string | null;
  onSelectTeam: (team: string) => void;
}

export default function RiskGlobe3D({ teams, selectedTeam, onSelectTeam }: Props) {
  if (teams.length === 0) {
    return (
      <div className="universe-empty">
        No teams found yet. Import architecture and deployment data to populate the risk globe.
      </div>
    );
  }

  return (
    <Canvas camera={{ position: [0, 2, 14], fov: 50 }} className="universe-3d-canvas">
      <Scene teams={teams} selectedTeam={selectedTeam} onSelect={onSelectTeam} />
    </Canvas>
  );
}
