// Erzeugt public/sfx.wav: Klangteppich und Effekte passend zur Zeitachse (src/timeline.ts).
// Alles synthetisch und deterministisch, keine fremden Tondateien (keine Lizenzfragen).
// Aufruf: npm run sfx
import { writeFileSync, mkdirSync } from "node:fs";

const SR = 44100, DUR = 40, N = SR * DUR;
const buf = new Float32Array(N);
const TAU = Math.PI * 2;
let seed = 12345;
const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);

/** Ton ab Startzeit t0 (s), Länge len (s); fn(lokale Zeit) -> Abtastwert */
function add(t0, len, fn) {
  const a = Math.max(0, Math.floor(t0 * SR)), b = Math.min(N, Math.floor((t0 + len) * SR));
  for (let i = a; i < b; i++) buf[i] += fn(i / SR - t0);
}
const bell = (t0, f, amp = 0.18, dec = 1.2) => add(t0, dec * 3, (t) => amp * Math.exp(-t / dec * 2.2) * (Math.sin(TAU * f * t) + 0.35 * Math.sin(TAU * f * 2.76 * t) * Math.exp(-t * 6)) * Math.min(1, t * 400));
const sweep = (t0, len, f0, f1, amp, vib = 0) => { let ph = 0; add(t0, len, (t) => { const k = t / len; ph += TAU * (f0 * Math.pow(f1 / f0, k) * (1 + vib * Math.sin(TAU * 18 * t))) / SR; return amp * Math.sin(ph) * Math.sin(Math.PI * Math.min(1, k * 1.05)); }); };
function noise(t0, len, amp, lp = 0.2, env = (k) => Math.exp(-k * 6)) { let y = 0; add(t0, len, (t) => { y += lp * ((rnd() * 2 - 1) - y); return amp * y * env(t / len); }); }
const thump = (t0, amp = 0.5, f = 58) => add(t0, 0.35, (t) => amp * Math.sin(TAU * f * t * (1 - t * 0.6)) * Math.exp(-t * 14) * Math.min(1, t * 300));

// Klangteppich: C – Am – F – G (je 5 s), weich, endet auf G -> löst sich in der Schleife nach C auf
const CH = [[261.6, 329.6, 392.0], [220.0, 261.6, 329.6], [174.6, 220.0, 261.6], [196.0, 246.9, 293.7]];
for (let i = 0; i < N; i++) {
  const t = i / SR, idx = Math.floor(t / 5) % 4, k = (t % 5) / 5;
  const fadeIn = Math.min(1, k * 8), fadeOut = Math.min(1, (1 - k) * 8);
  let s = 0;
  for (const f of CH[idx]) s += Math.sin(TAU * f * 0.5 * t) * 0.6 + Math.sin(TAU * f * t + Math.sin(TAU * 0.3 * t)) * 0.4;
  const swell = 0.55 + 0.45 * Math.sin(TAU * t / 10);
  const level = t < 7.5 ? 0.018 : 0.03;      // im Ei leiser, dann voller
  buf[i] += s * level * fadeIn * fadeOut * swell;
}
// Herzschlag im Ei (passt zum Leuchtpuls)
for (let t = 0.49; t < 7.6; t += 1.963) { thump(t, 0.45); thump(t + 0.22, 0.28); }
// Risse: Klicken wird dichter
for (let t = 4.0; t < 7.4;) { noise(t, 0.03, 0.35 + 0.3 * rnd(), 0.6); t += 0.35 * (1 - (t - 4) / 3.6) + 0.04 + rnd() * 0.08; }
// Aufbrechen
noise(7.5, 0.35, 0.6, 0.35); sweep(7.5, 0.22, 300, 900, 0.3); thump(7.5, 0.5, 70);
for (let k = 0; k < 6; k++) bell(7.6 + k * 0.11, 2000 + rnd() * 1500, 0.07, 0.25);
// Tropfen
for (let t = 8.2; t < 11; t += 0.42 + rnd() * 0.3) sweep(t, 0.07, 1400, 650, 0.14);
// Trockenschütteln
{ let y = 0; add(11.2, 1.4, (t) => { y += 0.15 * ((rnd() * 2 - 1) - y); return 0.5 * y * (0.5 + 0.5 * Math.sin(TAU * 28 * t)) * Math.sin(Math.PI * t / 1.4); }); }
// Sprung und Landung
sweep(12.8, 0.45, 180, 520, 0.22, 0.04); thump(13.8, 0.4, 65);
// Wachstum: Glockenspiel-Arpeggio
const ARP = [523.3, 659.3, 784.0, 1046.5, 1318.5];
[8.0, 20, 26, 32].forEach((g, n) => { ARP.forEach((f, k) => bell(g + k * 0.09, f, 0.12 + n * 0.02, 0.9)); noise(g, 1.6, 0.05, 0.9, (k) => Math.sin(Math.PI * k)); });
// Hüpfer (Kind)
for (let t = 22.5; t < 26; t += 1.6) sweep(t, 0.25, 260, 480, 0.12, 0.03);
// Schluss-Anstieg in den Lichtblitz (Übergang zum Anfang)
sweep(37.8, 2.2, 200, 1600, 0.12); noise(37.8, 2.2, 0.2, 0.05, (k) => k * k);
bell(39.6, 1046.5, 0.12, 0.5);

// Abmischen: weich begrenzen, kurze Rampen an den Enden (kein Knacken in der Schleife)
const out = Buffer.alloc(44 + N * 2);
out.write("RIFF", 0); out.writeUInt32LE(36 + N * 2, 4); out.write("WAVE", 8); out.write("fmt ", 12);
out.writeUInt32LE(16, 16); out.writeUInt16LE(1, 20); out.writeUInt16LE(1, 22); out.writeUInt32LE(SR, 24); out.writeUInt32LE(SR * 2, 28);
out.writeUInt16LE(2, 32); out.writeUInt16LE(16, 34); out.write("data", 36); out.writeUInt32LE(N * 2, 40);
for (let i = 0; i < N; i++) {
  const edge = Math.min(1, i / 400, (N - 1 - i) / 400);
  out.writeInt16LE(Math.round(Math.tanh(buf[i] * 1.4) * 0.85 * edge * 32767), 44 + i * 2);
}
mkdirSync(new URL("../public/", import.meta.url), { recursive: true });
writeFileSync(new URL("../public/sfx.wav", import.meta.url), out);
console.log("public/sfx.wav geschrieben:", (out.length / 1e6).toFixed(1), "MB");
