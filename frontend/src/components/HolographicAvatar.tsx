import { useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';

function pointsOnSphere(count: number, radius: number) {
  const positions = new Float32Array(count * 3);
  for (let i = 0; i < count; i += 1) {
    const phi = Math.acos(1 - 2 * ((i + 0.5) / count));
    const theta = Math.PI * (1 + Math.sqrt(5)) * i;
    positions[i * 3] = radius * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = radius * Math.sin(phi) * Math.sin(theta) * 1.15;
    positions[i * 3 + 2] = radius * Math.cos(phi);
  }
  return positions;
}

function Core({ active }: { active: boolean }) {
  const groupRef = useRef<THREE.Group>(null);
  const coreRef = useRef<THREE.Mesh>(null);
  const positions = useMemo(() => pointsOnSphere(420, 1), []);

  useFrame(({ clock }) => {
    const t = clock.elapsedTime;
    if (groupRef.current) {
      groupRef.current.rotation.y = t * 0.25;
      groupRef.current.rotation.x = Math.sin(t * 0.2) * 0.08;
    }
    if (coreRef.current) {
      const pulse = active ? Math.sin(t * 4) * 0.06 + 1.08 : Math.sin(t * 1.2) * 0.04 + 1;
      coreRef.current.scale.setScalar(pulse);
    }
  });

  return (
    <group ref={groupRef}>
      <mesh ref={coreRef}>
        <icosahedronGeometry args={[0.62, 2]} />
        <meshStandardMaterial
          color="#22d3ee"
          emissive="#22d3ee"
          emissiveIntensity={active ? 2.2 : 1.4}
          wireframe
          transparent
          opacity={0.85}
        />
      </mesh>
      <points>
        <bufferGeometry>
          <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        </bufferGeometry>
        <pointsMaterial color="#a855f7" size={0.028} transparent opacity={0.9} sizeAttenuation />
      </points>
      <mesh rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[1.05, 1.08, 64]} />
        <meshBasicMaterial color="#22d3ee" transparent opacity={0.35} side={THREE.DoubleSide} />
      </mesh>
      <mesh rotation={[Math.PI / 2.4, 0.4, 0]}>
        <ringGeometry args={[1.22, 1.24, 64]} />
        <meshBasicMaterial color="#a855f7" transparent opacity={0.22} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

interface Props {
  active?: boolean;
  size?: number;
}

export default function HolographicAvatar({ active = false, size = 120 }: Props) {
  return (
    <div style={{ width: size, height: size }}>
      <Canvas camera={{ position: [0, 0, 3.4], fov: 40 }} gl={{ alpha: true }} style={{ background: 'transparent' }}>
        <ambientLight intensity={0.6} />
        <pointLight position={[2, 2, 2]} intensity={40} color="#8fd8ff" />
        <Core active={active} />
        <EffectComposer>
          <Bloom intensity={1.1} luminanceThreshold={0.15} luminanceSmoothing={0.4} mipmapBlur />
        </EffectComposer>
      </Canvas>
    </div>
  );
}
