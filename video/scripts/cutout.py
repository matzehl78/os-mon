"""Stellt die Figuren-Bilder frei (schwarzer Hintergrund -> Alpha).

Nur der schwarze Bereich, der mit dem Bildrand verbunden ist, wird transparent.
Dunkle Stellen IN der Figur (Pupillen, Nase) bleiben deckend.
Weiche Kanten: Glow und Fellspitzen werden halbtransparent und "un-premultiplied".

Aufruf:  python3 scripts/cutout.py            (assets-src/*.png -> public/*.png)
"""
import glob
import os

import numpy as np
from PIL import Image

SRC = os.path.join(os.path.dirname(__file__), "..", "assets-src")
DST = os.path.join(os.path.dirname(__file__), "..", "public")
SIZE = 1024
DARK = 0.32   # Flutfüllung vom Rand läuft durch alles, was dunkler ist (inkl. Glow-Saum)
POCKET = 0.035  # eingeschlossene, fast schwarze Flächen (z. B. zwischen Schwanz und Körper)
POCKET_MIN = 2500  # ... ab dieser Größe in px gelten sie als Hintergrund (Pupillen sind kleiner/heller)
LO, HI = 0.025, 0.30  # weiche Alpha-Rampe im Randbereich


def dilate(m: np.ndarray) -> np.ndarray:
    o = m.copy()
    o[1:] |= m[:-1]
    o[:-1] |= m[1:]
    o[:, 1:] |= m[:, :-1]
    o[:, :-1] |= m[:, 1:]
    return o


def erode(m: np.ndarray) -> np.ndarray:
    return ~dilate(~m)


def grow(seed: np.ndarray, allowed: np.ndarray) -> np.ndarray:
    region = seed & allowed
    while True:
        nxt = dilate(region) & allowed
        if (nxt == region).all():
            return region
        region = nxt


def flood_background(dark: np.ndarray, black: np.ndarray) -> np.ndarray:
    border = np.zeros_like(dark)
    border[0, :] = border[-1, :] = border[:, 0] = border[:, -1] = True
    bg = grow(border, dark)
    # eingeschlossene schwarze Taschen: Kerne nach Erosion als Saat
    core = black & ~bg
    for _ in range(6):
        core = erode(core)
    while core.any():
        y, x = np.argwhere(core)[0]
        seed = np.zeros_like(core)
        seed[y, x] = True
        comp = grow(seed, black & ~bg)
        if comp.sum() >= POCKET_MIN:
            bg |= grow(comp, dark)
        core &= ~comp
    return bg


def process(path: str) -> None:
    img = Image.open(path).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)
    rgb = np.asarray(img).astype(np.float32) / 255.0
    mx = rgb.max(axis=2)
    bg = flood_background(mx < DARK, mx < POCKET)
    # Randzone (2 px um den Hintergrund) ebenfalls weich auslaufen lassen
    edge = dilate(dilate(bg))
    t = np.clip((mx - LO) / (HI - LO), 0, 1)
    soft = t * t * (3 - 2 * t)
    alpha = np.where(edge, soft, 1.0)
    alpha = np.where(bg, soft, alpha)
    col = np.where(alpha[..., None] > 1e-3, rgb / np.maximum(alpha[..., None], 1e-3), 0)
    col = np.clip(col, 0, 1)
    out = np.dstack([col, alpha])
    name = os.path.basename(path)
    Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(DST, name), optimize=True)
    print(f"{name}: {100 * (alpha < 0.5).mean():.1f}% transparent")


if __name__ == "__main__":
    os.makedirs(DST, exist_ok=True)
    for p in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        process(p)
