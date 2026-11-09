# Schlupf-Video (Remotion + Three.js)

40-Sekunden-Video als nahtlose Schleife: Das leuchtende Ei bekommt Risse, die Figur schlüpft,
schüttelt sich trocken und wächst über **Baby → Kind → Jugend → Erwachsen**. Am Ende führt ein
Lichtblitz zurück zum Anfang, damit das Video auf TikTok, Reels und Shorts sauber in Schleife läuft.

| Komposition | Format | Einsatz |
|---|---|---|
| `SchlupfVertikal` | 1080 × 1920, 30 fps, 40 s | TikTok, Instagram Reels, YouTube Shorts |
| `SchlupfQuadrat` | 1080 × 1080, 30 fps, 40 s | Feed-Posts |

## Start

```bash
cd video
npm install
npm run sfx          # erzeugt public/sfx.wav (Ton, synthetisch)
npm run studio       # Vorschau im Browser mit Zeitleiste
npm run render       # -> out/schlupf-vertikal.mp4
npm run render:quadrat
npm run still        # Vorschaubild (Bild 560 = Baby)
```

Ohne Grafikkarte (Server, CI, Cloud): `npm run render -- --gl=swangle`.
Eigener Chrome: `REMOTION_BROWSER=/pfad/zu/chrome-headless-shell npm run render`.
Richtwert ohne GPU (4 Kerne): etwa 1,2 s pro Bild, also rund 25 Minuten. Mit GPU (`--gl=angle`) deutlich schneller.

## Aufbau

| Datei | Inhalt |
|---|---|
| `src/timeline.ts` | Zeitachse: Dauer, Stufen, Texte, Ton-Zeitpunkte. Hier Texte und Zeiten ändern. |
| `src/scene/createScene.js` | Komplette 3D-Szene (Ei, Risse, Splitter, Fell-Shader, Figur, Wachstum, Kamera). `update(t, camera)` setzt alles allein aus der Zeit. |
| `src/SchlupfVideo.tsx` | Remotion-Komposition: `ThreeCanvas`, Texteinblendungen, Stufen-Abzeichen, Fortschrittsleiste, Lichtblitz, Ton |
| `src/Root.tsx` | Kompositionen (Hoch- und Quadratformat) |
| `scripts/make-sfx.mjs` | Ton: Klangteppich, Herzschlag, Risse, Aufplatzen, Tropfen, Schütteln, Glockenspiel je Stufe |
| `public/fonts/` | Schrift Fredoka (OFL), lokal, damit das Rendern ohne Internet klappt |

Die Szene ist **zustandslos je Bild**: keine Zufallszahlen zur Laufzeit, keine Werte, die von Bild zu
Bild weitergereicht werden. Nur so kann Remotion Bilder parallel und in beliebiger Reihenfolge rendern,
ohne Flackern.

## Ablauf

| Zeit | Bild | Text | Ton |
|---|---|---|---|
| 0–4 s | Ei leuchtet, Herz pulsiert | „Was schlüpft hier?“ | Herzschlag |
| 4–7,5 s | Wackeln, leuchtender Riss, Kamera rückt näher | „Es wackelt …“ | Knacken wird dichter |
| 7,5–11 s | Deckel klappt auf, Splitter, nasse Figur, Augen gehen auf | „Hallo, Welt!“ | Aufplatzen, Tropfen, Glockenspiel |
| 11–14 s | Trockenschütteln, Sprung aus dem Ei | „Erst mal trocken schütteln“ | Brrr, Boing |
| 14 / 20 / 26 / 32 s | Wachstum mit Funken | „Stufe 1/4 Baby“ … „Stufe 4/4 Erwachsen“ | Glockenspiel je Stufe |
| 36,4–40 s | Erwachsen, Augen zu, Lichtblitz | „Welche Stufe magst du am liebsten?“ | Anstieg in den Blitz |

## Anpassen

- **Texte und Zeiten:** `src/timeline.ts` (Ton danach mit `npm run sfx` neu erzeugen, falls Zeiten geändert).
- **Proportionen der Stufen:** `LOOK` in `createScene.js` (Kopfgröße, Beinlänge, Fell-Länge, Augen …).
- **Farben der Figur:** `PEACH`, `CREAM`, `PINK`, `TEAL` in `createScene.js`.
- **Bildausschnitt im Hochformat:** `framing` in `SchlupfVideo.tsx` (größer = Figur tiefer im Bild).
