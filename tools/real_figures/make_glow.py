"""Leuchtebene je Figurenbild: nur Antennenkugeln und Herz (für das Pulsieren in der 2,5D-Ansicht).

Aufruf: python3 tools/real_figures/make_glow.py  (liest und schreibt in Android drawable-nodpi und iOS App/RealFigures)
Erkennung: Farbton der Kugelfarbe je Figur + Helligkeit; Kugeln = Flecken im oberen Viertel der Figur,
Herz = größter heller Fleck mittig darunter (beim Ei nur das Herz). Ergebnis: real_<figur>_<stufe>_glow.webp.
"""
import sys, numpy as np, colorsys
from pathlib import Path
from PIL import Image, ImageFilter
from scipy import ndimage as ndi
from skimage.measure import regionprops
from skimage.morphology import convex_hull_image
ORB={"lumo":"E45BC8","minto":"F4917A","lila":"F5D84A","glut":"20C8E0","kasto":"F5B020","tropfi":"A65BE0",
     "wolki":"1EC8A0","rauti":"1EC8E8","sunni":"F04BB8","orbo":"2E6FE8","schnecki":"8ED62E"}
def hue(h): r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]; return colorsys.rgb_to_hsv(r,g,b)[0]
def glow(src, fig, egg=False, relaxed=False):
    im = Image.open(src).convert('RGBA'); a = np.asarray(im).astype(np.float32) / 255
    rgb = a[..., :3]; al = a[..., 3]
    mx = rgb.max(2); mn = rgb.min(2); s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    h = np.asarray(im.convert('RGB').convert('HSV')).astype(np.float32)[..., 0] / 255
    d = np.abs(h - hue(ORB[fig])); d = np.minimum(d, 1 - d)
    col = ((d < 0.12) & (s > 0.1) & (mx > 0.55) if relaxed else (d < 0.08) & (s > 0.25) & (mx > 0.62)) & (al > 0.5)
    col = ndi.binary_opening(col, iterations=1)
    body = al > 0.5
    ys, xs = np.where(body); top, bot = ys.min(), ys.max(); H = bot - top
    cx = ndi.center_of_mass(body)[1]; W = xs.max() - xs.min()
    lab, n = ndi.label(ndi.binary_closing(col, iterations=2))
    keep = np.zeros_like(col); best = None
    for r in regionprops(lab, intensity_image=mx):
        if r.area < 8 or r.intensity_mean < (0.6 if relaxed else 0.7):
            continue
        cy, cxx = r.centroid
        filled = ndi.binary_fill_holes(lab == r.label)
        if not egg and cy < top + 0.25 * H:
            # Antennenkugeln: ganz oben und nahe der Mitte (Ohren und Eierschale liegen tiefer bzw. seitlich)
            if r.area >= 20 and r.solidity > 0.72 and cy < top + 0.16 * H and abs(cxx - cx) < 0.35 * max(W, 1):
                keep |= filled & body
            continue
        lo, hi = (0.3, 0.75) if egg else (0.3, 1.0)
        if abs(cxx - cx) < 0.25 * max(W, 1) and top + lo * H < cy < top + hi * H and r.solidity > 0.7:
            # Herz trägt den hellen Blitz: weiße Pixel im gefüllten Fleck geben den Ausschlag (Ohren haben keinen)
            bolt = int(((mx > 0.9) & (s < 0.3) & filled).sum())
            score = (bolt + 0.01 * r.area) * r.solidity ** 2
            if best is None or score > best[0]:
                best = (score, filled)
    if best:
        heart = convex_hull_image(best[1]) if egg else best[1]   # Ei: Blitz reicht bis zum Rand, Hülle schließt die Lücke
        keep |= ndi.binary_erosion(heart, iterations=1) & body
    if not relaxed and (best is None or (egg and best[1].sum() < 0.02 * body.sum())):
        return glow(src, fig, egg, relaxed=True)   # blasses Herz (z. B. cremefarbenes Ei, Schlüpfling)
    mask = ndi.gaussian_filter(keep.astype(np.float32), 1.0)
    out = np.dstack([rgb, np.clip(mask * 1.15, 0, 1) * al])
    return Image.fromarray((out * 255).astype(np.uint8), 'RGBA')

ROOT = Path(__file__).resolve().parents[2]
DIRS = [ROOT / "android/app/src/main/res/drawable-nodpi", ROOT / "ios/OSMon/App/RealFigures"]
STAGES = ["egg", "hatchling", "baby", "child", "teen", "adult"]

def main():
    n = 0
    for f in ORB:
        for s in STAGES:
            src = DIRS[1] / f"real_{f}_{s}.webp"
            layer = glow(src, f, s == "egg")
            for d in DIRS:
                layer.save(d / f"real_{f}_{s}_glow.webp", "WEBP", quality=85, method=6)
            n += 1
    print(f"{n} Leuchtebenen je Plattform geschrieben.")

if __name__ == "__main__":
    main()
