import React, { useMemo } from "react";
import { Easing, useCurrentFrame } from "remotion";
import * as THREE from "three";
import { makeGlowMaterial, makePointsMaterial, makeRaysMaterial, RAY_COUNT } from "./materials";
import { BURSTS, DURATION, EGG, FINALE, STAGES, MORPH, loopSin, ramp, seeded } from "../timeline";

const PALETTE = ["#ff7ad9", "#c86bff", "#ffd1f4", "#9b5cff", "#ffffff"].map((c) => new THREE.Color(c));

/** Hilfsobjekt: Points mit per-Frame berechneten Attributen */
const usePoints = (count: number) =>
  useMemo(() => {
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(new Float32Array(count * 3), 3));
    geo.setAttribute("aSize", new THREE.BufferAttribute(new Float32Array(count), 1));
    geo.setAttribute("aAlpha", new THREE.BufferAttribute(new Float32Array(count), 1));
    geo.setAttribute("aColor", new THREE.BufferAttribute(new Float32Array(count * 3), 3));
    const pts = new THREE.Points(geo, makePointsMaterial());
    pts.frustumCulled = false;
    return pts;
  }, [count]);

const touch = (p: THREE.Points) => {
  for (const k of ["position", "aSize", "aAlpha", "aColor"]) p.geometry.attributes[k].needsUpdate = true;
};

/** Hintergrund-Sterne, funkeln periodisch (nahtloser Loop) */
export const Stars: React.FC = () => {
  const frame = useCurrentFrame();
  const N = 420;
  const pts = usePoints(N);
  const seeds = useMemo(() => {
    const r = seeded(3);
    return Array.from({ length: N }, () => ({
      x: (r() - 0.5) * 34,
      y: (r() - 0.5) * 50,
      z: -8 - r() * 16,
      s: 0.04 + r() ** 3 * 0.22,
      c: PALETTE[Math.floor(r() * PALETTE.length)],
      tw: 1 + Math.floor(r() * 6),
      ph: r() * 6.28,
    }));
  }, []);
  const g = pts.geometry.attributes;
  seeds.forEach((s, i) => {
    g.position.setXYZ(i, s.x, s.y, s.z);
    g.aSize.setX(i, s.s);
    g.aAlpha.setX(i, 0.35 + 0.65 * (0.5 + 0.5 * loopSin(frame, s.tw * 3, s.ph)));
    g.aColor.setXYZ(i, s.c.r, s.c.g, s.c.b);
  });
  touch(pts);
  return <primitive object={pts} rotation={[0, 0, 0.08 * loopSin(frame, 1)]} />;
};

/** Aufsteigende Glitzerpartikel um die Figur. Wegstrecke pro Loop = ganzzahliges Vielfaches der Höhe. */
export const Sparkles: React.FC = () => {
  const frame = useCurrentFrame();
  const N = 160;
  const H = 9;
  const pts = usePoints(N);
  const seeds = useMemo(() => {
    const r = seeded(11);
    return Array.from({ length: N }, () => ({
      a: r() * Math.PI * 2,
      rad: 1.2 + r() * 2.8,
      y0: r() * H,
      laps: 1 + Math.floor(r() * 3),
      s: 0.03 + r() * 0.09,
      c: PALETTE[Math.floor(r() * PALETTE.length)],
      ph: r() * 6.28,
    }));
  }, []);
  // nach jedem Burst kurz mehr Glitzer
  const boost = BURSTS.reduce((m, b) => (frame >= b ? Math.max(m, 1 - ramp(frame, b, b + 45)) : m), 0);
  const g = pts.geometry.attributes;
  seeds.forEach((s, i) => {
    const y = ((s.y0 + (frame / DURATION) * H * s.laps) % H) - H / 2;
    const a = s.a + (frame / DURATION) * Math.PI * 2 * (s.laps % 2 ? 1 : -1);
    g.position.setXYZ(i, Math.cos(a) * s.rad, y, Math.sin(a) * s.rad * 0.6);
    const edge = Math.min(1, (H / 2 - Math.abs(y)) / 1.2); // oben/unten weich ausblenden
    g.aSize.setX(i, s.s * (1 + boost * 0.8));
    g.aAlpha.setX(i, edge * (0.45 + 0.55 * (0.5 + 0.5 * loopSin(frame, 20, s.ph))) * (0.7 + boost));
    g.aColor.setXYZ(i, s.c.r, s.c.g, s.c.b);
  });
  touch(pts);
  return <primitive object={pts} />;
};

/** Partikel-Explosion + Schockwelle bei jedem Burst (Schlüpfen, Verwandlung, Finale) */
const Burst: React.FC<{ at: number; seed: number; strength: number }> = ({ at, seed, strength }) => {
  const frame = useCurrentFrame();
  const N = 140;
  const pts = usePoints(N);
  const ring = useMemo(() => makeGlowMaterial("#ff9df0"), []);
  const seeds = useMemo(() => {
    const r = seeded(seed);
    return Array.from({ length: N }, () => {
      const th = r() * Math.PI * 2;
      const ph = Math.acos(2 * r() - 1);
      const sp = 1.5 + r() * 4.5;
      return {
        v: new THREE.Vector3(Math.sin(ph) * Math.cos(th) * sp, Math.cos(ph) * sp * 0.8 + 0.6, Math.sin(ph) * Math.sin(th) * sp * 0.5),
        s: 0.05 + r() * 0.16,
        c: PALETTE[Math.floor(r() * PALETTE.length)],
        life: 0.6 + r() * 0.4,
      };
    });
  }, [seed]);
  const t = (frame - at) / 40;
  if (t < 0 || t > 1) return null;
  const g = pts.geometry.attributes;
  const e = Easing.out(Easing.cubic)(t);
  seeds.forEach((s, i) => {
    g.position.setXYZ(i, s.v.x * e * strength, s.v.y * e * strength - t * t * 1.2, s.v.z * e * strength);
    g.aSize.setX(i, s.s * (1 - t * 0.6));
    g.aAlpha.setX(i, Math.max(0, 1 - t / s.life) * 1.4);
    g.aColor.setXYZ(i, s.c.r, s.c.g, s.c.b);
  });
  touch(pts);
  const rt = ramp(frame, at, at + 18, Easing.out(Easing.quad));
  ring.uniforms.uIntensity.value = (1 - rt) * 0.9;
  return (
    <group position={[0, 0.2, 0]}>
      <primitive object={pts} />
      <mesh material={ring} scale={0.5 + rt * 9 * strength}>
        <ringGeometry args={[0.42, 0.5, 96]} />
      </mesh>
    </group>
  );
};

export const Bursts: React.FC = () => (
  <>
    {BURSTS.map((b, i) => (
      <Burst key={b} at={b} seed={100 + i} strength={i === 0 ? 1.1 : i === BURSTS.length - 1 ? 0.8 : 0.9} />
    ))}
  </>
);

/** Intensität der "Energie" im Bild: hoch bei Rissen, Bursts und Verwandlungen */
export const energyAt = (f: number) => {
  let e = ramp(f, EGG.crackStart, EGG.burst, Easing.in(Easing.quad)) * (f < EGG.burst + 20 ? 1 : 0);
  e = Math.max(e, (1 - ramp(f, EGG.burst, EGG.burst + 30)) * (f >= EGG.burst ? 1 : 0));
  for (const s of STAGES.slice(0, -1)) {
    if (f >= s.out && f < s.out + MORPH + 20) {
      const u = (f - s.out) / MORPH;
      e = Math.max(e, u < 1 ? ramp(u, 0, 0.6) : 1 - ramp(f, s.out + MORPH, s.out + MORPH + 20));
    }
  }
  if (f >= FINALE.start) e = Math.max(e, ramp(f, FINALE.start, FINALE.orb) * (1 - ramp(f, FINALE.orb, DURATION)));
  return e;
};

/** Strahlenkranz + Bodenschein + Lichtball im Finale */
export const Glows: React.FC = () => {
  const frame = useCurrentFrame();
  const rays = useMemo(() => makeRaysMaterial(), []);
  const floor = useMemo(() => makeGlowMaterial("#ff5fd2"), []);
  const orb = useMemo(() => makeGlowMaterial("#ffe6ff"), []);
  const e = energyAt(frame);
  // eine volle Symmetrie-Periode pro Loop -> nahtlos
  rays.uniforms.uAngle.value = (frame / DURATION) * ((Math.PI * 2) / RAY_COUNT) * 2;
  rays.uniforms.uIntensity.value = 0.13 + e * 0.35 + 0.02 * loopSin(frame, 8);
  floor.uniforms.uIntensity.value = 0.32 + e * 0.3;
  const orbT = ramp(frame, FINALE.start + 10, FINALE.orb) * (1 - ramp(frame, FINALE.orb + 2, FINALE.orb + 20));
  orb.uniforms.uIntensity.value = orbT * 2.2;
  return (
    <>
      <mesh material={rays} position={[0, 0.4, -3]} scale={14 + e * 3}>
        <planeGeometry args={[1, 1]} />
      </mesh>
      <mesh material={floor} position={[0, -1.75, -0.4]} rotation={[-Math.PI / 2.2, 0, 0]} scale={[4.2, 2.4, 1]}>
        <planeGeometry args={[1, 1]} />
      </mesh>
      {orbT > 0.001 && (
        <mesh material={orb} position={[0, 0.3, 0.3]} scale={1.2 + orbT * 1.6 + 0.15 * Math.sin(frame * 1.3)}>
          <planeGeometry args={[1, 1]} />
        </mesh>
      )}
    </>
  );
};
