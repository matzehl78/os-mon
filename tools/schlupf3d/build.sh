#!/bin/sh
# Erzeugt aus schlupf-body.html zwei Fassungen:
#  schlupf.html          – offline, nutzt ../pet3d/three.min.js (r128)
#  schlupf-artifact.html – lädt three.js r128 vom CDN (zum Teilen)
cd "$(dirname "$0")"
{ printf '<!doctype html>\n<html lang="de"><head><meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n</head><body>\n'
  sed 's#<!--THREE-->#<script src="../pet3d/three.min.js"></script>#' schlupf-body.html
  printf '</body></html>\n'; } > schlupf.html
sed 's#<!--THREE-->#<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>#' schlupf-body.html > "${1:-schlupf-artifact.html}"
