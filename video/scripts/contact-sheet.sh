#!/usr/bin/env bash
# Rendert einzelne Frames als Kontaktbogen zur Sichtprüfung.
# Aufruf: scripts/contact-sheet.sh out/sheet.png 0 115 250 599
# Ohne GPU (Server/CI): GL=swangle BROWSER=/pfad/zu/chrome-headless-shell scripts/contact-sheet.sh ...
set -euo pipefail
OUT=$1; shift
TMP=$(mktemp -d)
EXTRA=()
[ -n "${GL:-}" ] && EXTRA+=(--gl="$GL")
[ -n "${BROWSER:-}" ] && EXTRA+=(--browser-executable="$BROWSER")
for f in "$@"; do
  npx remotion still src/index.ts OsMonEvolution "$TMP/$f.png" --frame="$f" --scale=0.4 "${EXTRA[@]}" --log=error
done
python3 - "$OUT" "$TMP" "$@" <<'PY'
import sys
from PIL import Image, ImageDraw
out, tmp, frames = sys.argv[1], sys.argv[2], sys.argv[3:]
ims = [Image.open(f"{tmp}/{f}.png").convert("RGB") for f in frames]
w, h = ims[0].size
sheet = Image.new("RGB", (w * len(ims), h))
for i, (f, im) in enumerate(zip(frames, ims)):
    ImageDraw.Draw(im).text((10, 10), f"#{f}", fill="yellow")
    sheet.paste(im, (i * w, 0))
sheet.save(out)
PY
rm -rf "$TMP"
