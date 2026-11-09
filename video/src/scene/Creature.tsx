import React, { useMemo } from "react";
import { Easing, useCurrentFrame } from "remotion";
import { makeAuraMaterial, makeSpriteMaterial } from "./materials";
import { useTex } from "./useTex";
import { EGG, FINALE, MORPH, STAGES, loopSin, ramp } from "../timeline";

type Look = { visible: boolean; white: number; opacity: number; scale: number; aura: number };

const HIDDEN: Look = { visible: false, white: 0, opacity: 0, scale: 1, aura: 0 };

/** Flackern zwischen alter und neuer Silhouette, immer schneller */
const showNew = (u: number) => {
  if (u < 0.3) return false;
  if (u >= 0.75) return true;
  const k = (u - 0.3) / 0.45;
  return Math.floor(k * k * 9) % 2 === 1;
};

/** Zustand der Stufe i im Frame f */
export const lookAt = (i: number, f: number): Look => {
  const s = STAGES[i];
  const prev = STAGES[i - 1];
  const morphIn = prev ? prev.out : -1;
  const pulse = (u: number) => 1 + 0.07 * Math.sin(u * Math.PI);

  // Eintritt
  if (i === 0) {
    if (f < EGG.burst) return HIDDEN;
    if (f < s.in + 14) {
      const w = 1 - ramp(f, EGG.burst + 1, s.in + 14, Easing.out(Easing.quad));
      return {
        visible: true,
        white: w,
        opacity: ramp(f, EGG.burst, EGG.burst + 3),
        scale: 0.85 + 0.15 * ramp(f, EGG.burst, s.in, Easing.out(Easing.back(2.5))),
        aura: 0.4 + w,
      };
    }
  } else if (f < morphIn) {
    return HIDDEN;
  } else if (f < s.in) {
    const u = (f - morphIn) / MORPH;
    if (!showNew(u)) return HIDDEN;
    return {
      visible: true,
      white: u < 0.75 ? 1 : 1 - ramp(u, 0.75, 1),
      opacity: 1,
      scale: pulse(u),
      aura: 1.1,
    };
  }

  // Abgang (Verwandlung in die nächste Stufe / Finale)
  const next = STAGES[i + 1];
  if (next) {
    if (f >= s.out) {
      const u = (f - s.out) / MORPH;
      if (u >= 1 || showNew(u)) return HIDDEN;
      return { visible: true, white: ramp(u, 0, 0.3), opacity: 1, scale: pulse(u), aura: 0.4 + ramp(u, 0, 0.3) };
    }
  } else if (f >= FINALE.start) {
    const w = ramp(f, FINALE.start, FINALE.start + 12);
    const shrink = ramp(f, FINALE.start + 10, FINALE.orb, Easing.in(Easing.cubic));
    if (shrink >= 1) return HIDDEN;
    return { visible: true, white: w, opacity: 1 - shrink * 0.3, scale: 1 - shrink * 0.92, aura: 0.4 + w };
  }

  // Ausklingendes Weiß direkt nach der Verwandlung
  const settle = 1 - ramp(f, s.in, s.in + 6);
  return { visible: true, white: 0, opacity: 1, scale: 1 + 0.03 * settle, aura: 0.4 + settle * 0.6 };
};

const StageSprite: React.FC<{ index: number }> = ({ index }) => {
  const frame = useCurrentFrame();
  const s = STAGES[index];
  const tex = useTex(s.img);
  const mat = useMemo(() => makeSpriteMaterial(tex), [tex]);
  const aura = useMemo(() => makeAuraMaterial(tex), [tex]);
  const look = lookAt(index, frame);
  if (!look.visible) return null;

  mat.uniforms.uWhite.value = look.white;
  mat.uniforms.uOpacity.value = look.opacity;
  aura.uniforms.uStrength.value = look.aura;

  // Lebendigkeit: atmen, wippen, leicht drehen (Ebene im Raum -> Parallaxe zu Partikeln)
  const breathe = 1 + 0.018 * loopSin(frame, 25, index);
  const bob = 0.05 * loopSin(frame, 12, index * 1.3);
  const yaw = 0.16 * loopSin(frame, 2, index);
  const roll = 0.025 * loopSin(frame, 6, index * 0.7);
  // Schlüpfling schüttelt sich kurz trocken
  const shake = index === 0 ? Math.sin(frame * 2.2) * 0.05 * ramp(frame, 150, 158) * (1 - ramp(frame, 166, 178)) : 0;

  const size = s.size * look.scale;
  return (
    <group position={[0, s.y + bob, 0]} rotation={[0, yaw, roll + shake]}>
      <mesh material={aura} scale={[size * 1.18, size * 1.18 * breathe, 1]} position={[0, 0, -0.05]}>
        <planeGeometry args={[1, 1]} />
      </mesh>
      <mesh material={mat} scale={[size / breathe ** 0.5, size * breathe, 1]} position={[0, (size * (breathe - 1)) / 2, 0]}>
        <planeGeometry args={[1, 1]} />
      </mesh>
    </group>
  );
};

export const Creature: React.FC = () => (
  <>
    {STAGES.map((_, i) => (
      <StageSprite key={i} index={i} />
    ))}
  </>
);
