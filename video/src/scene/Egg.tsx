import React, { useMemo } from "react";
import { Easing, useCurrentFrame } from "remotion";
import * as THREE from "three";
import { EGG_IMG, EGG_PROFILE } from "./eggProfile";
import { useTex } from "./useTex";
import { DURATION, EGG, FINALE, loopSin, ramp, seeded } from "../timeline";

/** Höhe der Bruchkante (in Ei-Einheiten, 0 = Mitte) */
const CUT_Y = 0.12;
const TEETH = 13;
const SEGMENTS = 128;

/** Zickzack der Bruchkante, periodisch in phi (0..2π) */
const zig = (() => {
  const rnd = seeded(7);
  const jitter = Array.from({ length: TEETH }, () => 0.6 + rnd() * 0.8);
  return (phi: number) => {
    const t = (((phi / (Math.PI * 2)) % 1) + 1) % 1;
    const x = t * TEETH;
    const i = Math.floor(x) % TEETH;
    const tri = 1 - Math.abs((x % 1) * 2 - 1); // 0..1..0
    return (tri - 0.5) * 0.17 * jitter[i];
  };
})();

const radiusAt = (y: number) => {
  for (let i = 1; i < EGG_PROFILE.length; i++) {
    const [r1, y1] = EGG_PROFILE[i];
    if (y1 >= y) {
      const [r0, y0] = EGG_PROFILE[i - 1];
      const k = (y - y0) / Math.max(1e-6, y1 - y0);
      return r0 + (r1 - r0) * k;
    }
  }
  return 0;
};

/** Planare Projektion von vorne: Weltkoordinate (x,y) -> Bildkoordinate im Ei-Foto */
const applyPlanarUv = (geo: THREE.BufferGeometry) => {
  const pos = geo.attributes.position;
  const uv = new Float32Array(pos.count * 2);
  for (let i = 0; i < pos.count; i++) {
    uv[i * 2] = EGG_IMG.cx + pos.getX(i) * EGG_IMG.half;
    uv[i * 2 + 1] = 1 - EGG_IMG.cy + pos.getY(i) * EGG_IMG.half;
  }
  geo.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
};

const buildHalf = (part: "top" | "bottom") => {
  const rc = radiusAt(CUT_Y);
  const pts = EGG_PROFILE.filter(([, y]) => (part === "top" ? y > CUT_Y + 0.02 : y < CUT_Y - 0.02)).map(
    ([r, y]) => new THREE.Vector2(r, y),
  );
  if (part === "top") pts.unshift(new THREE.Vector2(rc, CUT_Y));
  else pts.push(new THREE.Vector2(rc, CUT_Y));
  const geo = new THREE.LatheGeometry(pts, SEGMENTS);
  const pos = geo.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    if (Math.abs(pos.getY(i) - CUT_Y) < 1e-5) {
      const phi = Math.atan2(pos.getX(i), pos.getZ(i));
      pos.setY(i, CUT_Y + zig(phi));
    }
  }
  geo.computeVertexNormals();
  applyPlanarUv(geo);
  return geo;
};

/** Leuchtende Risse auf einer Canvas-Textur (gleiche Projektion wie das Ei-Foto) */
const SIZE = 1024;
const toPx = (x: number, y: number): [number, number] => [
  (EGG_IMG.cx + x * EGG_IMG.half) * SIZE,
  (EGG_IMG.cy - y * EGG_IMG.half) * SIZE,
];

type Branch = { pts: [number, number][]; start: number };
const BRANCHES: Branch[] = (() => {
  const rnd = seeded(42);
  const out: Branch[] = [];
  // Risse wachsen vom leuchtenden Herz nach außen
  for (let b = 0; b < 9; b++) {
    let a = (b / 9) * Math.PI * 2 + rnd() * 0.5;
    let x = Math.cos(a) * 0.32;
    let y = -0.02 + Math.sin(a) * 0.3;
    const pts: [number, number][] = [[x, y]];
    for (let s = 0; s < 9; s++) {
      a += (rnd() - 0.5) * 1.1;
      x += Math.cos(a) * 0.09;
      y += Math.sin(a) * 0.09;
      if (x * x + (y * 0.8) ** 2 > 0.85) break;
      pts.push([x, y]);
    }
    out.push({ pts, start: rnd() * 0.45 });
  }
  return out;
})();

const drawCracks = (ctx: CanvasRenderingContext2D, p: number) => {
  ctx.clearRect(0, 0, SIZE, SIZE);
  if (p <= 0) return;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  const stroke = (pts: [number, number][], frac: number, width: number) => {
    const n = Math.max(1, Math.floor((pts.length - 1) * frac));
    for (const [w, col, blur] of [
      [width * 3.2, "rgba(214,90,255,0.55)", 24],
      [width, "rgba(255,240,255,1)", 8],
    ] as const) {
      ctx.strokeStyle = col;
      ctx.lineWidth = w;
      ctx.shadowColor = "#ff7bf2";
      ctx.shadowBlur = blur;
      ctx.beginPath();
      const [sx, sy] = toPx(...pts[0]);
      ctx.moveTo(sx, sy);
      for (let i = 1; i <= n; i++) ctx.lineTo(...toPx(...pts[i]));
      ctx.stroke();
    }
  };
  for (const b of BRANCHES) {
    const f = (p - b.start) / 0.45;
    if (f > 0) stroke(b.pts, Math.min(1, f), 5);
  }
  // Hauptriss = spätere Bruchkante, wächst von vorne-mitte nach außen
  const seam = Math.max(0, (p - 0.35) / 0.65);
  if (seam > 0) {
    const half = (Math.PI / 2) * seam;
    const pts: [number, number][] = [];
    for (let i = 0; i <= 120; i++) {
      const phi = -half + (2 * half * i) / 120;
      const y = CUT_Y + zig(phi);
      pts.push([radiusAt(y) * Math.sin(phi), y]);
    }
    stroke(pts, 1, 7);
  }
};

const Shards: React.FC<{ t: number }> = ({ t }) => {
  const shards = useMemo(() => {
    const rnd = seeded(99);
    return Array.from({ length: 46 }, () => {
      const phi = (rnd() - 0.5) * Math.PI * 1.6;
      const r = radiusAt(CUT_Y);
      return {
        p: new THREE.Vector3(Math.sin(phi) * r, CUT_Y + (rnd() - 0.5) * 0.5, Math.cos(phi) * r),
        v: new THREE.Vector3(Math.sin(phi) * (1.5 + rnd() * 3), 2 + rnd() * 4, Math.cos(phi) * (1 + rnd() * 3)),
        rot: new THREE.Vector3(rnd() * 9, rnd() * 9, rnd() * 9),
        s: 0.05 + rnd() * 0.13,
      };
    });
  }, []);
  const geo = useMemo(() => {
    const g = new THREE.TetrahedronGeometry(1, 0);
    g.scale(1, 1, 0.25);
    return g;
  }, []);
  if (t <= 0 || t >= 1.2) return null;
  const sec = t * 1.2;
  return (
    <group>
      {shards.map((s, i) => (
        <mesh
          key={i}
          geometry={geo}
          position={[s.p.x + s.v.x * sec, s.p.y + s.v.y * sec - 4.9 * sec * sec, s.p.z + s.v.z * sec]}
          rotation={[s.rot.x * sec, s.rot.y * sec, s.rot.z * sec]}
          scale={s.s * (1 - Math.max(0, t - 0.7) / 0.5)}
        >
          <meshStandardMaterial color="#ff8fd0" emissive="#ff4fc3" emissiveIntensity={0.6} roughness={0.3} />
        </mesh>
      ))}
    </group>
  );
};

export const Egg: React.FC = () => {
  const frame = useCurrentFrame();
  const tex = useTex("0-ei.png");
  const top = useMemo(() => buildHalf("top"), []);
  const bottom = useMemo(() => buildHalf("bottom"), []);

  const canvas = useMemo(() => {
    const c = document.createElement("canvas");
    c.width = c.height = SIZE;
    return c;
  }, []);
  const crackTex = useMemo(() => {
    const t = new THREE.CanvasTexture(canvas);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  }, [canvas]);

  const crackP = frame >= EGG.reformStart ? 0 : ramp(frame, EGG.crackStart, EGG.burst - 3, Easing.in(Easing.quad));
  useMemo(() => {
    drawCracks(canvas.getContext("2d")!, crackP);
    crackTex.needsUpdate = true;
  }, [canvas, crackTex, crackP]);

  const hatched = frame >= EGG.burst && frame < EGG.reformStart;
  const sinceBurst = frame - EGG.burst;
  // nach dem Neuentstehen (reformStart) ist das Ei wieder ganz
  const reformed = frame >= EGG.reformStart;
  const flyT = reformed ? 0 : ramp(frame, EGG.burst, EGG.burst + 30, Easing.out(Easing.cubic));
  const topOpacity = reformed ? 1 : 1 - ramp(frame, EGG.burst + 12, EGG.burst + 28);
  const bottomOpacity = reformed ? 1 : 1 - ramp(frame, EGG.burst + 2, EGG.burst + 9);

  // Ende: Ei entsteht aus dem Lichtball
  const reform = ramp(frame, FINALE.orb - 2, FINALE.orb + 16, Easing.out(Easing.back(1.8)));
  const white = frame >= EGG.reformStart ? 1 - ramp(frame, FINALE.orb + 6, DURATION - 1) : 0;

  // Wackeln: wird stärker, in Stößen
  const shakeAmt = ramp(frame, EGG.shakeStart, EGG.burst, Easing.in(Easing.quad));
  const pulse = Math.max(0, Math.sin(frame * 0.33)) ** 0.6;
  const wobble = shakeAmt * pulse * Math.sin(frame * 1.7) * 0.32;
  const hop = shakeAmt * pulse * Math.abs(Math.sin(frame * 0.85)) * 0.12;
  const glow = 0.55 + crackP * (0.6 + 0.4 * Math.sin(frame * 0.9)) + white * 2;

  const bob = 0.06 * loopSin(frame, 10);
  const spin = 0.35 * loopSin(frame, 1); // leichte Drehung zeigt die 3D-Form
  const scale = frame >= EGG.reformStart ? reform * 0.92 : 0.92;

  if (hatched && sinceBurst > 34) return null;

  const outer = (opacity: number) => (
    <meshPhysicalMaterial
      map={tex}
      emissive="#ffffff"
      emissiveMap={tex}
      emissiveIntensity={glow}
      roughness={0.32}
      clearcoat={1}
      clearcoatRoughness={0.12}
      sheen={0.4}
      sheenColor="#ff9be6"
      transparent={opacity < 1}
      opacity={opacity}
    />
  );
  const inner = (opacity: number) => (
    <meshStandardMaterial
      side={THREE.BackSide}
      color="#ffd6ef"
      emissive="#ff9ad9"
      emissiveIntensity={0.5}
      transparent={opacity < 1}
      opacity={opacity}
    />
  );
  const overlays = (geo: THREE.BufferGeometry, opacity: number) => (
    <>
      <mesh geometry={geo} scale={1.004}>
        <meshBasicMaterial
          map={crackTex}
          transparent
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          toneMapped={false}
          opacity={opacity}
        />
      </mesh>
      {white > 0.001 && (
        <mesh geometry={geo} scale={1.01}>
          <meshBasicMaterial
            color="#fff0ff"
            transparent
            blending={THREE.AdditiveBlending}
            depthWrite={false}
            toneMapped={false}
            opacity={white}
          />
        </mesh>
      )}
    </>
  );

  return (
    <group position={[0, -0.3 + bob + hop, 0]} rotation={[0, spin, wobble]} scale={scale}>
      {/* Oberteil fliegt beim Schlüpfen weg */}
      <group
        position={[flyT * 1.1, flyT * 3.4, flyT * 0.8]}
        rotation={[flyT * 1.2, flyT * 0.6, -flyT * 2.2]}
        visible={topOpacity > 0.01}
      >
        <mesh geometry={top}>{outer(topOpacity)}</mesh>
        <mesh geometry={top}>{inner(topOpacity)}</mesh>
        {overlays(top, topOpacity)}
      </group>
      <group visible={bottomOpacity > 0.01}>
        <mesh geometry={bottom}>{outer(bottomOpacity)}</mesh>
        <mesh geometry={bottom}>{inner(bottomOpacity)}</mesh>
        {overlays(bottom, bottomOpacity)}
      </group>
      <Shards t={hatched ? sinceBurst / 30 : 0} />
      {/* Licht aus dem Inneren kurz vor dem Schlüpfen */}
      <pointLight position={[0, 0.2, 1.4]} intensity={crackP * 6} color="#ff6ef0" distance={6} />
    </group>
  );
};
