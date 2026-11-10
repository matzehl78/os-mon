"""Leuchtebene je Figurenbild: nur Antennenkugeln und Herz (für das Pulsieren in der 2,5D-Ansicht).

Aufruf: python3 tools/real_figures/make_glow.py  (liest und schreibt in Android drawable-nodpi und iOS App/RealFigures)
Erkennung: Farbton der Kugelfarbe je Figur + Helligkeit; Kugeln = Flecken im oberen Viertel der Figur,
Herz = größter heller Fleck mittig darunter (beim Ei nur das Herz). Ergebnis: real_<figur>_<stufe>_glow.webp.
"""
import sys, numpy as np, colorsys
from pathlib import Path
from PIL import Image, ImageFilter
from scipy import ndimage as ndi
ORB={"lumo":"E45BC8","minto":"F4917A","lila":"F5D84A","glut":"20C8E0","kasto":"F5B020","tropfi":"A65BE0",
     "wolki":"1EC8A0","rauti":"1EC8E8","sunni":"F04BB8","orbo":"2E6FE8","schnecki":"8ED62E"}
def hue(h): r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]; return colorsys.rgb_to_hsv(r,g,b)[0]
def glow(src, fig, egg=False):
    im=Image.open(src).convert('RGBA'); a=np.asarray(im).astype(np.float32)/255
    rgb=a[...,:3]; al=a[...,3]
    mx=rgb.max(2); mn=rgb.min(2); s=np.where(mx>0,(mx-mn)/np.maximum(mx,1e-6),0)
    hsv=np.asarray(im.convert('RGB').convert('HSV')).astype(np.float32)/255
    h=hsv[...,0]; d=np.abs(h-hue(ORB[fig])); d=np.minimum(d,1-d)
    col=(d<0.08)&(s>0.25)&(mx>0.62)&(al>0.5)
    white=(mx>0.9)&(s<0.3)&(al>0.5)
    white&=ndi.binary_dilation(col,iterations=6)
    m=col|white
    ys,xs=np.where(al>0.5); top,bot,left,right=ys.min(),ys.max(),xs.min(),xs.max(); H=bot-top; W=right-left; cx=ndi.center_of_mass(al>0.5)[1]
    lab,n=ndi.label(ndi.binary_closing(m,iterations=2))
    keep=np.zeros_like(m); best=None
    for i in range(1,n+1):
        c=lab==i; sz=c.sum()
        if sz<6 or mx[c].mean()<0.72: continue
        cy,cxx=ndi.center_of_mass(c)
        if egg:
            pass
        elif cy < top+0.25*H:
            keep|=c&m
            continue
        if abs(cxx-cx) < 0.25*max(W,1) and cy > top+0.3*H and (best is None or sz>best[0]): best=(sz,c)
    if best: keep|=ndi.binary_fill_holes(best[1])&(al>0.5)&(m|((mx>0.85))) | (best[1]&m)
    mask=ndi.gaussian_filter(keep.astype(np.float32),1.2)
    out=np.dstack([rgb, np.clip(mask*1.2,0,1)*al])
    return Image.fromarray((out*255).astype(np.uint8),'RGBA')
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
