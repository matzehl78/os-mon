# OS-Mon Evolution – 3D-Loop-Video (Remotion + Three.js)

20-Sekunden-Video (9:16, 1080×1920, 30 fps) als **nahtlose Schleife**:
Ei wackelt → leuchtende Risse → Schale platzt → Schlüpfling → Baby → Kind → Teenager → Erwachsen →
Lichtball → neues Ei (Frame 599 ≈ Frame 0, damit TikTok/Reels/Shorts endlos loopen).

## Start

```bash
cd video
npm install
npm run studio          # Vorschau im Browser (Remotion Studio), Timeline scrubben
npm run render          # -> out/osmon-evolution.mp4  (9:16)
npm run render:square   # -> out/osmon-evolution-square.mp4 (1:1)
npm run render:gif      # -> out/osmon-evolution.gif (Vorschau, klein)
```

Ohne GPU (Server, CI, Container) WebGL per Software rendern:

```bash
npx remotion render src/index.ts OsMonEvolution out/osmon-evolution.mp4 --gl=swangle
```

## Ablauf (Frames @ 30 fps)

| Frames | Szene |
|---|---|
| 0–20 | Ei schwebt, Hook-Text „WAS SCHLÜPFT DA?!“ |
| 20–112 | Ei wackelt in Stößen, Risse wachsen vom Herz aus, Kamera fährt heran |
| 112 | Blitz, Oberschale fliegt weg, Splitter, Schockwelle |
| 116–196 | **TAG 1 · GESCHLÜPFT** (schüttelt sich trocken) |
| 196–218 | Verwandlung (weiße Silhouette, Flackern alt/neu, Burst) |
| 218–286 | **TAG 7 · BABY** |
| 308–376 | **TAG 30 · KIND** |
| 398–466 | **TAG 90 · TEENAGER** |
| 488–548 | **TAG 365 · ERWACHSEN** |
| 548–600 | Leuchten, Schrumpfen zum Lichtball, neues Ei entsteht → Loop |

Alle Zeiten, Größen und Texte stehen zentral in `src/timeline.ts`.

## Aufbau

| Datei | Inhalt |
|---|---|
| `src/timeline.ts` | Timing, Stufen (Bild, Text, Größe), Loop-Helfer, deterministischer Zufall |
| `src/scene/Egg.tsx` | Echtes 3D-Ei: Lathe-Geometrie aus der Silhouette des Ei-Bildes, Foto per planarer Projektion, Canvas-Textur für Risse, zwei Hälften mit Zickzack-Bruchkante, Splitter |
| `src/scene/Creature.tsx` | Figuren als Bildebenen mit Shader (Weiß-Silhouette, Aura), Atmen/Wippen/Drehen, Verwandlungslogik |
| `src/scene/Effects.tsx` | Sterne, aufsteigender Glitzer, Partikel-Bursts + Schockwellen, Strahlenkranz, Boden-Glow, Lichtball |
| `src/scene/Scene.tsx` | Kamerafahrt (Orbit, Push-in, Shake, FOV-Kick), Licht |
| `src/Overlay.tsx` | Texte (Spring-Pop), Fortschrittsleiste, Vignette, Blitz |
| `scripts/cutout.py` | Stellt die Bilder frei (`assets-src/` → `public/`): schwarzer Hintergrund → Alpha, Pupillen bleiben deckend |
| `scripts/contact-sheet.sh` | Einzelframes als Kontaktbogen zur Kontrolle |

## Eigene Bilder / Stufen tauschen

1. Bilder (schwarzer Hintergrund, quadratisch) nach `assets-src/` legen.
2. `npm run cutout` (braucht Python 3 mit Pillow + NumPy).
3. Dateinamen in `src/timeline.ts` (`STAGES`) und `src/scene/useTex.ts` (`TEXTURE_FILES`) eintragen.
4. Bei neuem Ei-Bild: Ei-Profil in `src/scene/eggProfile.ts` neu vermessen (Silhouette → Radius pro Höhe).

## Hinweise für „viral“

- Ton fehlt bewusst: einen aktuellen Trend-Sound direkt in TikTok/Instagram hinzufügen (wird dort besser ausgespielt).
  Eigener Ton: `<Audio src={staticFile("sound.mp3")} />` in `src/Evolution.tsx`.
- Hook in Sekunde 0–1, Schlüpfen bei ~3,7 s, alle ~3 s ein neuer Reiz, Loop ohne sichtbaren Schnitt → höhere Wiederholrate.
- Schrift: Baloo 2 (SIL Open Font License, `public/fonts/OFL.txt`), lokal eingebunden.
