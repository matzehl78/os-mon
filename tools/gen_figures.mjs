// gen_figures.mjs: erzeugt die Figuren-Daten für iOS und Android aus dem Prototyp (eine Quelle für alles).
//
// Aufruf:  node gen_figures.mjs [osmon-prototyp.html]
// Ergebnis: figures.json, PetFigureData.swift, PetFigureData.kt (im selben Ordner)
//
// Der Prototyp ist die Referenz für Formen und Farben. Neue oder geänderte Figuren dort eintragen,
// dann dieses Skript laufen lassen und die beiden Daten-Dateien in die Projekte kopieren.
// Die Zeichenlogik (PetFigure.swift / PetFigure.kt) ist eine 1:1-Übertragung von lumoSVG() im Prototyp.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const here = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(process.argv[2] || join(here, "osmon-prototyp.html"), "utf8");
const a = src.indexOf("const FIGS = [");
const b = src.indexOf("let FIG_ID");
if (a < 0 || b < 0) throw new Error("FIGS/SHAPES im Prototyp nicht gefunden");
const { FIGS, SHAPES } = new Function(src.slice(a, b) + "\nreturn { FIGS, SHAPES };")();

function mixHex(x, y, t) {
  const pa = parseInt(x.slice(1), 16), pb = parseInt(y.slice(1), 16);
  return "#" + [16, 8, 0].map(s => {
    const u = (pa >> s) & 255, v = (pb >> s) & 255;
    return Math.round(u + (v - u) * t).toString(16).padStart(2, "0");
  }).join("").toUpperCase();
}
const up = h => h.toUpperCase();

// Lumo wird im Prototyp aus Kopf + Rumpf gebaut. Für die Apps als normale Form beschreiben (gleiche Maße).
function lumoShape(S) {
  return {
    ...S,
    path: "M72 138 Q52 168 66 194 Q110 208 154 194 Q168 168 148 138 Z",
    head: [110, 96, 64, 58],
    arms: [[62, 164, 10, 20, -12], [158, 164, 10, 20, 12]],
    feet: [[80, 192, 24, 18], [116, 192, 24, 18]],
  };
}

const figures = FIGS.map(F => {
  let S = SHAPES[F.shape];
  if (!S) throw new Error(`Form ${F.shape} fehlt`);
  if (S.lumo) S = lumoShape(S);
  const colors = {
    body: up(F.body), bodySick: mixHex(F.body, "#c9d2d6", 0.5), ant: up(F.ant), off: mixHex(F.ant, "#1b1e4b", 0.6),
    heart: up(F.heart), iris: up(F.iris), lid: up(F.lid || mixHex(F.iris, "#ffffff", 0.3)),
    line: up(F.line || "#1b1e4b"), cheek: up(F.cheek || "#ff7fc8"), tongue: "#FF8FD6", sweat: "#9FF0FF",
  };
  const shape = {
    lumo: !!S.lumo, path: S.path, head: S.head || null,
    face: { ey: S.face.ey, dx: S.face.dx, r: S.face.r, my: S.face.my ?? S.face.ey + S.face.r + 2 },
    ant: { dx: S.ant.dx, y: S.ant.y, lean: S.ant.lean || 1, beads: S.ant.beads || [[0, 0, 8], [6, -16, 10], [14, -37, 14]] },
    heart: S.heart, tail: S.tail, arms: S.arms || [], feet: S.feet || [],
    glint: S.glint, sweat: S.sweat, zz: S.zz || [14, 44],
  };
  return { id: F.id, name: F.name, colors, shape };
});

// Referenz-Umriss je Körperpfad (für die Parser-Tests in Swift/Kotlin), gemessen im Browser per getBBox().
// Ohne Playwright bleibt "bbox" weg und die Tests überspringen den Vergleich.
try {
  let pw;
  try { pw = await import("playwright"); } catch { pw = createRequire(import.meta.url)("playwright"); }   // auch über NODE_PATH
  const { chromium } = pw;
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const boxes = await page.evaluate(paths => paths.map(d => {
    const ns = "http://www.w3.org/2000/svg", svg = document.createElementNS(ns, "svg"), el = document.createElementNS(ns, "path");
    el.setAttribute("d", d); svg.appendChild(el); document.body.appendChild(svg);
    const b = el.getBBox(); return [b.x, b.y, b.width, b.height].map(v => Math.round(v * 100) / 100);
  }), figures.map(f => f.shape.path));
  await browser.close();
  figures.forEach((f, i) => { f.shape.bbox = boxes[i]; });
} catch (e) {
  console.warn("Hinweis: ohne Playwright keine bbox-Referenz (" + e.message.split("\n")[0] + ")");
}

writeFileSync(join(here, "figures.json"), JSON.stringify(figures, null, 2) + "\n");

// ---------- Swift ----------
const n = v => (Number.isInteger(v) ? v.toFixed(0) : String(v));
const hexS = h => "0x" + h.slice(1);
const swiftList = (arr, f) => "[" + arr.map(f).join(", ") + "]";
const sw = figures.map(f => {
  const c = f.colors, s = f.shape;
  return `        PetFigure(
            id: "${f.id}", name: "${f.name}",
            colors: .init(body: ${hexS(c.body)}, bodySick: ${hexS(c.bodySick)}, ant: ${hexS(c.ant)}, off: ${hexS(c.off)},
                          heart: ${hexS(c.heart)}, iris: ${hexS(c.iris)}, lid: ${hexS(c.lid)}, line: ${hexS(c.line)},
                          cheek: ${hexS(c.cheek)}, tongue: ${hexS(c.tongue)}, sweat: ${hexS(c.sweat)}),
            shape: .init(
                lumo: ${s.lumo},
                path: "${s.path}",
                head: ${s.head ? `.init(cx: ${n(s.head[0])}, cy: ${n(s.head[1])}, rx: ${n(s.head[2])}, ry: ${n(s.head[3])})` : "nil"},
                face: .init(ey: ${n(s.face.ey)}, dx: ${n(s.face.dx)}, r: ${n(s.face.r)}, my: ${n(s.face.my)}),
                ant: .init(dx: ${n(s.ant.dx)}, y: ${n(s.ant.y)}, lean: ${n(s.ant.lean)}, beads: ${swiftList(s.ant.beads, q => `.init(ox: ${n(q[0])}, oy: ${n(q[1])}, r: ${n(q[2])})`)}),
                heart: .init(x: ${n(s.heart[0])}, y: ${n(s.heart[1])}, size: ${n(s.heart[2])}),
                tail: CGPoint(x: ${n(s.tail[0])}, y: ${n(s.tail[1])}),
                arms: ${swiftList(s.arms, q => `.init(cx: ${n(q[0])}, cy: ${n(q[1])}, rx: ${n(q[2])}, ry: ${n(q[3])}, rot: ${n(q[4])})`)},
                feet: ${swiftList(s.feet, q => `.init(x: ${n(q[0])}, y: ${n(q[1])}, w: ${n(q[2])}, h: ${n(q[3])})`)},
                glint: .init(x: ${n(s.glint[0])}, y: ${n(s.glint[1])}, rot: ${n(s.glint[2])}),
                sweat: CGPoint(x: ${n(s.sweat[0])}, y: ${n(s.sweat[1])}),
                zz: CGPoint(x: ${n(s.zz[0])}, y: ${n(s.zz[1])}))),`;
}).join("\n");

writeFileSync(join(here, "PetFigureData.swift"), `// PetFigureData.swift: GENERIERT von gen_figures.mjs aus osmon-prototyp.html. Nicht von Hand ändern.
// Targets: App und Widget-Extension.

import CoreGraphics

extension PetFigure {
    static let all: [PetFigure] = [
${sw}
    ]
}
`);

// ---------- Kotlin ----------
const hexK = h => "0xFF" + h.slice(1);
const f2 = v => n(v) + "f";
const kList = (arr, f) => "listOf(" + arr.map(f).join(", ") + ")";
const kt = figures.map(f => {
  const c = f.colors, s = f.shape;
  return `    PetFigure(
        id = "${f.id}", name = "${f.name}",
        colors = FigureColors(
            body = ${hexK(c.body)}.toInt(), bodySick = ${hexK(c.bodySick)}.toInt(), ant = ${hexK(c.ant)}.toInt(), off = ${hexK(c.off)}.toInt(),
            heart = ${hexK(c.heart)}.toInt(), iris = ${hexK(c.iris)}.toInt(), lid = ${hexK(c.lid)}.toInt(), line = ${hexK(c.line)}.toInt(),
            cheek = ${hexK(c.cheek)}.toInt(), tongue = ${hexK(c.tongue)}.toInt(), sweat = ${hexK(c.sweat)}.toInt()
        ),
        shape = FigureShape(
            lumo = ${s.lumo},
            path = "${s.path}",
            head = ${s.head ? `Ellipse(${f2(s.head[0])}, ${f2(s.head[1])}, ${f2(s.head[2])}, ${f2(s.head[3])})` : "null"},
            face = Face(${f2(s.face.ey)}, ${f2(s.face.dx)}, ${f2(s.face.r)}, ${f2(s.face.my)}),
            ant = Antenna(${f2(s.ant.dx)}, ${f2(s.ant.y)}, ${f2(s.ant.lean)}, ${kList(s.ant.beads, q => `Bead(${f2(q[0])}, ${f2(q[1])}, ${f2(q[2])})`)}),
            heart = HeartSpot(${f2(s.heart[0])}, ${f2(s.heart[1])}, ${f2(s.heart[2])}),
            tail = Pt(${f2(s.tail[0])}, ${f2(s.tail[1])}),
            arms = ${kList(s.arms, q => `Arm(${f2(q[0])}, ${f2(q[1])}, ${f2(q[2])}, ${f2(q[3])}, ${f2(q[4])})`)},
            feet = ${kList(s.feet, q => `Foot(${f2(q[0])}, ${f2(q[1])}, ${f2(q[2])}, ${f2(q[3])})`)},
            glint = Glint(${f2(s.glint[0])}, ${f2(s.glint[1])}, ${f2(s.glint[2])}),
            sweat = Pt(${f2(s.sweat[0])}, ${f2(s.sweat[1])}),
            zz = Pt(${f2(s.zz[0])}, ${f2(s.zz[1])})
        )
    ),`;
}).join("\n");

writeFileSync(join(here, "PetFigureData.kt"), `// PetFigureData.kt: GENERIERT von gen_figures.mjs aus osmon-prototyp.html. Nicht von Hand ändern.

package com.example.osmon.figure

val ALL_FIGURES: List<PetFigure> = listOf(
${kt}
)
`);

console.log(`${figures.length} Figuren geschrieben: figures.json, PetFigureData.swift, PetFigureData.kt`);
