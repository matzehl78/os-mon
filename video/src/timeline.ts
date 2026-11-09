import { Easing, interpolate } from "remotion";

export const FPS = 30;
export const WIDTH = 1080;
export const HEIGHT = 1920;
/** Gesamtlänge der Schleife. Frame DURATION ist identisch mit Frame 0 -> nahtloser Loop. */
export const DURATION = 600; // 20 s

/** Ablauf des Eis */
export const EGG = {
  shakeStart: 20, // Ei beginnt zu wackeln
  crackStart: 45, // erste Risse leuchten
  burst: 112, // Schale platzt
  reformStart: 572, // am Ende entsteht das Ei wieder aus dem Lichtball
} as const;

export type Stage = {
  img: string;
  label: string;
  day: string;
  /** Frame, an dem die Stufe voll sichtbar ist */
  in: number;
  /** Frame, an dem die Verwandlung in die nächste Stufe beginnt */
  out: number;
  /** Größe der Bildebene in Welt-Einheiten */
  size: number;
  /** vertikale Lage des Bildmittelpunkts */
  y: number;
};

/** Länge einer Verwandlung (weißes Aufleuchten + Flackern + Reveal) */
export const MORPH = 22;

export const STAGES: Stage[] = [
  { img: "1-schlupf.png", label: "GESCHLÜPFT", day: "TAG 1", in: 116, out: 196, size: 2.75, y: -0.25 },
  { img: "2-baby.png", label: "BABY", day: "TAG 7", in: 196 + MORPH, out: 286, size: 2.7, y: -0.25 },
  { img: "3-kind.png", label: "KIND", day: "TAG 30", in: 286 + MORPH, out: 376, size: 2.8, y: -0.2 },
  { img: "4-teen.png", label: "TEENAGER", day: "TAG 90", in: 376 + MORPH, out: 466, size: 2.9, y: -0.15 },
  { img: "5-erwachsen.png", label: "ERWACHSEN", day: "TAG 365", in: 466 + MORPH, out: 548, size: 3.0, y: -0.1 },
];

/** Ende: Erwachsener leuchtet auf und schrumpft zu einem Lichtball, aus dem das neue Ei entsteht */
export const FINALE = { start: 548, orb: 576 } as const;

export const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

export const ramp = (f: number, a: number, b: number, ease: (t: number) => number = Easing.linear) =>
  interpolate(f, [a, b], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing: ease });

/** Deterministischer Zufall (Remotion rendert Frames parallel -> kein Math.random) */
export const seeded = (seed: number) => {
  let s = seed >>> 0 || 1;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

/** Periodische Hilfsfunktion: genau `cycles` volle Schwingungen pro Loop -> nahtlos */
export const loopSin = (f: number, cycles: number, phase = 0) =>
  Math.sin((f / DURATION) * Math.PI * 2 * cycles + phase);

/** Zeitpunkte, an denen ein "Burst" (Partikel + Schockwelle) startet */
export const BURSTS: number[] = [EGG.burst, ...STAGES.slice(1).map((s) => s.in - 8), FINALE.orb];
