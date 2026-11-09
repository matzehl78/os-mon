/** Gemeinsame Zeitachse für 3D-Szene, Texte und Ton (Sekunden). */
export const FPS = 30;
export const DUR = 40;
export const FRAMES = FPS * DUR;

/** Wachstumsstufen (Beginn in Sekunden) */
export const STAGES = [
  { t: 14, nr: 1, name: "Baby" },
  { t: 20, nr: 2, name: "Kind" },
  { t: 26, nr: 3, name: "Jugend" },
  { t: 32, nr: 4, name: "Erwachsen" },
] as const;

/** Texteinblendungen: von, bis, Zeile, kleine Zeile */
export const CAPTIONS: { from: number; to: number; text: string; sub?: string }[] = [
  { from: 0.3, to: 3.8, text: "Was schlüpft hier?", sub: "Bis zum Ende schauen" },
  { from: 3.9, to: 7.4, text: "Es wackelt …" },
  { from: 7.7, to: 10.9, text: "Hallo, Welt!" },
  { from: 11.0, to: 13.8, text: "Erst mal trocken schütteln" },
  { from: 36.4, to: 39.5, text: "Welche Stufe magst du am liebsten?", sub: "1 · 2 · 3 · 4 – schreib's in die Kommentare" },
];

/** Ton-Ereignisse (auch für scripts/make-sfx.mjs) */
export const SFX = {
  crackFrom: 4.0, crackTo: 7.4, pop: 7.5, dripsTo: 11, shake: [11, 12.8], jump: 12.8,
  grow: [8.0, 20, 26, 32], hopsFrom: 22.5, hopsTo: 26, outro: DUR - 2.2,
};
