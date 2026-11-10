"""Realistische Figuren (ChatGPT-Bilder, freigestellt) als App-Assets exportieren.

Eingabe: Ordner mit <figur>-<n>-<stufe>.png (1024×1024, transparent, gleicher Maßstab und gleiche Bodenlinie),
z. B. lumo-1-ei.png … schnecki-6-erwachsen.png (11 Figuren × 6 Stufen = 66 Dateien).
Ausgabe: real_<figur>_<stufe>.webp (512×512, WebP mit Alpha) nach Android drawable-nodpi und iOS App/RealFigures.

Aufruf: python3 tools/real_figures/export_real_figures.py <quellordner>
"""
from pathlib import Path
import sys
from PIL import Image

FIGURES = ["lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki"]
STAGES = {"1-ei": "egg", "2-schluepfling": "hatchling", "3-baby": "baby",
          "4-jungtier": "child", "5-teenager": "teen", "6-erwachsen": "adult"}
SIZE, QUALITY = 512, 88

root = Path(__file__).resolve().parents[2]
targets = [root / "android/app/src/main/res/drawable-nodpi", root / "ios/OSMon/App/RealFigures"]

def main(src: Path) -> None:
    missing = [f"{f}-{s}.png" for f in FIGURES for s in STAGES if not (src / f"{f}-{s}.png").exists()]
    if missing:
        sys.exit("Fehlende Quelldateien: " + ", ".join(missing))
    for t in targets:
        t.mkdir(parents=True, exist_ok=True)
    for f in FIGURES:
        for s, key in STAGES.items():
            im = Image.open(src / f"{f}-{s}.png").convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
            for t in targets:
                im.save(t / f"real_{f}_{key}.webp", "WEBP", quality=QUALITY, method=6)
    print(f"{len(FIGURES) * len(STAGES)} Bilder je Plattform geschrieben.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
