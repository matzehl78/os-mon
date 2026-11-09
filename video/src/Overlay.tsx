import React from "react";
import { AbsoluteFill, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { loadFont } from "@remotion/fonts";
import { BURSTS, EGG, FINALE, STAGES, ramp } from "./timeline";

// Schrift liegt lokal in public/fonts (OFL) -> Rendern funktioniert offline
const fontFamily = "Baloo 2";
loadFont({ family: fontFamily, url: staticFile("fonts/baloo2-800-latin.woff2"), weight: "800" });

const textStyle: React.CSSProperties = {
  fontFamily: `"${fontFamily}", Inter, sans-serif`,
  fontWeight: 800,
  color: "white",
  textAlign: "center",
  letterSpacing: 2,
  WebkitTextStroke: "3px #5a136e",
  paintOrder: "stroke fill",
  textShadow: "0 0 24px rgba(255,90,220,.9), 0 6px 0 #3b0a4a",
};

const Pop: React.FC<{ from: number; to: number; children: React.ReactNode; top: number }> = ({ from, to, children, top }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  if (frame < from || frame > to) return null;
  const s = spring({ frame: frame - from, fps, config: { damping: 9, stiffness: 160 } });
  const out = ramp(frame, to - 6, to, Easing.in(Easing.quad));
  return (
    <div
      style={{
        position: "absolute",
        top,
        width: "100%",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        transform: `scale(${s * (1 - out * 0.4)}) rotate(${(1 - s) * -8}deg)`,
        opacity: 1 - out,
      }}
    >
      {children}
    </div>
  );
};

const Pill: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div
    style={{
      ...textStyle,
      fontSize: 52,
      WebkitTextStroke: "0px",
      textShadow: "none",
      padding: "6px 34px",
      borderRadius: 999,
      background: "linear-gradient(90deg,#ff4fc3,#9b4dff)",
      boxShadow: "0 0 40px rgba(255,80,220,.7)",
      marginBottom: 6,
    }}
  >
    {children}
  </div>
);

/** Fortschritt Ei -> Erwachsen als Punktleiste */
const Progress: React.FC = () => {
  const frame = useCurrentFrame();
  const marks = [0, EGG.burst, ...STAGES.slice(1).map((s) => s.in - 6)];
  const current = marks.reduce((c, m, i) => (frame >= m && frame < FINALE.orb ? i : c), 0);
  const fill =
    frame >= FINALE.orb
      ? 0
      : interpolate(frame, [...marks, FINALE.start], [...marks.map((_, i) => i / (marks.length - 1)), 1], {
          extrapolateRight: "clamp",
        });
  // aus- und zum Loop-Ende leer wieder einblenden (Frame 599 == Frame 0)
  const fade = frame >= FINALE.orb ? ramp(frame, FINALE.orb + 8, 598) : 1 - ramp(frame, FINALE.start + 10, FINALE.orb);
  return (
    <div style={{ position: "absolute", bottom: 210, left: 140, right: 140, height: 40, opacity: fade }}>
      <div style={{ position: "absolute", top: 17, left: 0, right: 0, height: 6, borderRadius: 3, background: "rgba(255,255,255,.18)" }} />
      <div
        style={{
          position: "absolute",
          top: 17,
          left: 0,
          width: `${fill * 100}%`,
          height: 6,
          borderRadius: 3,
          background: "linear-gradient(90deg,#ff4fc3,#c86bff)",
          boxShadow: "0 0 18px #ff4fc3",
        }}
      />
      {marks.map((_, i) => {
        const on = i <= current && frame < FINALE.orb;
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: `calc(${(i / (marks.length - 1)) * 100}% - 20px)`,
              width: 40,
              height: 40,
              borderRadius: 20,
              background: on ? "radial-gradient(circle,#fff 0,#ff7ad9 45%,#9b4dff 100%)" : "rgba(255,255,255,.18)",
              boxShadow: on ? "0 0 26px #ff6ad5" : "none",
              transform: `scale(${i === current ? 1.25 + 0.08 * Math.sin(frame * 0.3) : 1})`,
            }}
          />
        );
      })}
    </div>
  );
};

export const Overlay: React.FC = () => {
  const frame = useCurrentFrame();
  // Blitz bei jedem Burst
  let flash = 0;
  for (const b of BURSTS) if (frame >= b - 1) flash = Math.max(flash, (1 - ramp(frame, b - 1, b + 9)) * (b === EGG.burst ? 0.8 : 0.5));
  const hookWobble = Math.sin(frame * 0.5) * ramp(frame, EGG.shakeStart, EGG.burst) * 4;

  return (
    <AbsoluteFill>
      {/* Hook – die ersten Sekunden entscheiden über "viral" */}
      <Pop from={3} to={EGG.burst - 4} top={210}>
        <div style={{ ...textStyle, fontSize: 118, lineHeight: 1.0, transform: `rotate(${hookWobble}deg)` }}>
          WAS SCHLÜPFT
          <br />
          DA?!
        </div>
      </Pop>

      {STAGES.map((s, i) => (
        <Pop key={s.img} from={i === 0 ? EGG.burst + 12 : s.in - 4} to={i === STAGES.length - 1 ? FINALE.start + 6 : s.out} top={190}>
          <Pill>{s.day}</Pill>
          <div style={{ ...textStyle, fontSize: 112 }}>{s.label}</div>
        </Pop>
      ))}

      <Pop from={FINALE.start + 8} to={598} top={250}>
        <div style={{ ...textStyle, fontSize: 82, lineHeight: 1.05 }}>
          …UND ALLES
          <br />
          BEGINNT VON VORN
        </div>
      </Pop>

      <Progress />

      {/* Vignette */}
      <AbsoluteFill style={{ background: "radial-gradient(ellipse at 50% 48%, transparent 45%, rgba(8,0,18,.75) 100%)" }} />
      <AbsoluteFill style={{ background: "#fff4ff", opacity: flash, mixBlendMode: "screen" }} />
    </AbsoluteFill>
  );
};
