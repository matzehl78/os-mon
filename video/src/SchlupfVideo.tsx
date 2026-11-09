import { useEffect, useLayoutEffect, useMemo } from "react";
import { AbsoluteFill, Audio, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { ThreeCanvas } from "@remotion/three";
import { useThree } from "@react-three/fiber";
import type { PerspectiveCamera } from "three";
import { loadFont } from "@remotion/fonts";
import { createScene } from "./scene/createScene";
import { CAPTIONS, DUR, STAGES } from "./timeline";

// Schrift liegt im Projekt (variable Fredoka, 300–700), damit das Rendern ohne Internet klappt
loadFont({ family: "Fredoka", url: staticFile("fonts/Fredoka-latin.woff2"), weight: "300 700", format: "woff2" });
const FONT = `Fredoka, "Trebuchet MS", system-ui, sans-serif`;
const TEAL = "#35e6bd", PEACH = "#f8cfa2", INK = "#06231d";

type Props = { format: "vertikal" | "quadrat"; sound: boolean };

/** 3D-Teil: Szene einmal bauen, je Bild aus der Zeit t setzen (vor dem Rendern des Bildes). */
const Scene: React.FC<{ t: number; framing: number }> = ({ t, framing }) => {
  const { gl, camera } = useThree();
  const s = useMemo(() => createScene(gl), [gl]);
  useEffect(() => {
    gl.toneMappingExposure = 1.05;
    return () => s.dispose();
  }, [gl, s]);
  useLayoutEffect(() => {
    s.update(t, camera as PerspectiveCamera, { framing });
  }, [t, s, camera, framing]);
  return (
    <>
      <primitive object={s.world} />
      <EnvSetter env={s.environment} />
    </>
  );
};
const EnvSetter: React.FC<{ env: any }> = ({ env }) => {
  const { scene } = useThree();
  useLayoutEffect(() => {
    scene.environment = env;
    scene.background = null;
  }, [scene, env]);
  return null;
};

/** Große Texteinblendung mit Federeffekt */
const Caption: React.FC<{ t: number; u: number }> = ({ t, u }) => {
  const { fps } = useVideoConfig();
  const c = CAPTIONS.find((x) => t >= x.from && t < x.to);
  if (!c) return null;
  const local = (t - c.from) * fps;
  const pop = spring({ frame: local, fps, config: { damping: 11, stiffness: 160, mass: 0.7 } });
  const out = interpolate(t, [c.to - 0.25, c.to], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div style={{ position: "absolute", top: 180 * u, left: 70 * u, right: 70 * u, textAlign: "center", opacity: out,
      transform: `scale(${0.6 + 0.4 * pop}) translateY(${(1 - pop) * 40 * u}px)` }}>
      <div style={{ fontFamily: FONT, fontWeight: 700, fontSize: 92 * u, lineHeight: 1.05, color: "#fff",
        WebkitTextStroke: `${10 * u}px ${INK}`, paintOrder: "stroke fill", textShadow: `0 ${6 * u}px ${24 * u}px rgba(53,230,189,.45)`, textWrap: "balance" as any }}>
        {c.text}
      </div>
      {c.sub && (
        <div style={{ marginTop: 22 * u, fontFamily: FONT, fontWeight: 600, fontSize: 42 * u, color: TEAL,
          WebkitTextStroke: `${6 * u}px ${INK}`, paintOrder: "stroke fill" }}>{c.sub}</div>
      )}
    </div>
  );
};

/** Stufen-Abzeichen „Stufe 2/4 · Kind“, springt bei jedem Wachstum neu auf */
const StageBadge: React.FC<{ t: number; u: number }> = ({ t, u }) => {
  const { fps } = useVideoConfig();
  const st = [...STAGES].reverse().find((s) => t >= s.t);
  if (!st || t > 36.3) return null;
  const pop = spring({ frame: (t - st.t) * fps, fps, config: { damping: 9, stiffness: 180 } });
  return (
    <div style={{ position: "absolute", top: 175 * u, left: 0, right: 0, display: "flex", flexDirection: "column", alignItems: "center", gap: 16 * u,
      transform: `scale(${0.4 + 0.6 * pop})`, opacity: Math.min(1, pop * 1.4) }}>
      <div style={{ fontFamily: FONT, fontWeight: 600, fontSize: 40 * u, letterSpacing: 4 * u, color: INK, background: TEAL,
        padding: `${10 * u}px ${30 * u}px`, borderRadius: 999 }}>STUFE {st.nr}/4</div>
      <div style={{ fontFamily: FONT, fontWeight: 700, fontSize: 128 * u, lineHeight: 1, color: PEACH,
        WebkitTextStroke: `${12 * u}px ${INK}`, paintOrder: "stroke fill" }}>{st.name}</div>
    </div>
  );
};

/** Fortschritt oben: Linie mit Punkten für Schlüpfen und die vier Stufen */
const Progress: React.FC<{ t: number; u: number }> = ({ t, u }) => {
  const marks = [7.5, ...STAGES.map((s) => s.t)];
  return (
    <div style={{ position: "absolute", top: 110 * u, left: 120 * u, right: 120 * u, height: 10 * u, borderRadius: 99, background: "rgba(255,255,255,.14)" }}>
      <div style={{ position: "absolute", inset: 0, width: `${(t / DUR) * 100}%`, borderRadius: 99, background: `linear-gradient(90deg, ${PEACH}, ${TEAL})` }} />
      {marks.map((m) => (
        <div key={m} style={{ position: "absolute", top: "50%", left: `${(m / DUR) * 100}%`, width: 26 * u, height: 26 * u, marginLeft: -13 * u, marginTop: -13 * u,
          borderRadius: 99, background: t >= m ? TEAL : "#1a2522", border: `${4 * u}px solid ${INK}`, boxShadow: t >= m ? `0 0 ${18 * u}px ${TEAL}` : "none" }} />
      ))}
    </div>
  );
};

export const SchlupfVideo: React.FC<Props> = ({ format, sound }) => {
  const frame = useCurrentFrame();
  const { fps, width, height } = useVideoConfig();
  const t = frame / fps;
  const u = width / 1080;
  // Nahtlose Schleife: Ende blendet in Lichtblitz, Anfang kommt aus demselben Blitz
  const flash = Math.max(interpolate(t, [DUR - 0.9, DUR - 1 / fps], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
    interpolate(t, [0, 0.45], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }));
  // Hochformat: Figur etwas tiefer, Platz für Texte oben
  const framing = format === "vertikal" ? 0.2 : 0.04;
  return (
    <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 58%, #0f2a25 0%, #070b0c 45%, #030405 100%)" }}>
      <ThreeCanvas width={width} height={height} dpr={1} gl={{ antialias: true, alpha: true, preserveDrawingBuffer: true }}
        camera={{ fov: format === "vertikal" ? 36 : 32, near: 0.1, far: 100, position: [0, 2, 9] }}>
        <Scene t={t} framing={framing} />
      </ThreeCanvas>
      <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 50%, transparent 55%, rgba(0,0,0,.55) 100%)" }} />
      <Progress t={t} u={u} />
      <StageBadge t={t} u={u} />
      <Caption t={t} u={u} />
      <AbsoluteFill style={{ background: "radial-gradient(circle at 50% 55%, #ffffff 0%, #b8fff0 40%, #35e6bd 100%)", opacity: flash }} />
      {sound && <Audio src={staticFile("sfx.wav")} />}
    </AbsoluteFill>
  );
};
