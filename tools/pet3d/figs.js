const clamp=(x,a,b)=>Math.min(b,Math.max(a,x)); const FORMS={};
window.OSLimbs = function (type, u) {
  const env = Math.sin(Math.PI * u), quick = u < 0.2 ? u / 0.2 : (1 - u) / 0.8, hold = Math.min(1, u * 4, (1 - u) * 4);
  const alt = Math.sin(u * Math.PI * 4);
  switch (type) {
    case "giggle":   { const f = env * (40 + 25 * Math.sin(u * Math.PI * 6)); return { l: f, r: f, fl: 6 * Math.max(0, alt), fr: 6 * Math.max(0, -alt) }; }
    case "spin":     return { l: 85 * env, r: 85 * env, fl: 4 * env, fr: 4 * env };
    case "surprise": return { l: 140 * quick, r: 140 * quick, fl: 3 * quick, fr: 3 * quick };
    case "dizzy":    return { l: (20 + 25 * Math.sin(u * Math.PI * 5)) * (1 - u), r: (20 + 25 * Math.sin(u * Math.PI * 5 + Math.PI)) * (1 - u),
                              fl: 3 * Math.max(0, alt) * (1 - u), fr: 3 * Math.max(0, -alt) * (1 - u) };
    case "sleepy":
    case "night":    return { l: 10 * env, r: 135 * hold, fl: 0, fr: 0 };            // Hand vor den gähnenden Mund
    case "morning":  return { l: 165 * env, r: 165 * env, fl: 3 * env, fr: 3 * env };  // Strecken auf Zehenspitzen
    case "noon":     return { l: 30 * env + 15 * alt * env, r: 30 * env - 15 * alt * env, fl: 5 * Math.max(0, alt), fr: 5 * Math.max(0, -alt) };
    case "evening":  return { l: 10 * env, r: 140 * hold + 20 * Math.sin(u * Math.PI * 8) * hold, fl: 0, fr: 0 };   // Winken
    case "weak":     return { l: -8 * env, r: -8 * env, fl: 0, fr: 0 };
    case "startle":  return { l: 45 * quick, r: 45 * quick, fl: 0, fr: 0 };
    case "shy":      return { l: -14 * hold, r: -14 * hold, fl: 3 * Math.max(0, alt) * hold, fr: 3 * Math.max(0, -alt) * hold };   // verlegen: Arme an den Körper, Füße scharren
    default:         return null;
  }
};


// ======================= Figuren (Katalog) =======================
// Jede Figur = Farben + Körperform. Alle teilen dieselben Bausteine: 2 Antennen mit je 3 Kugeln (Ruhe),
// Akku-Herz (Energie), große Augen, Mund, Wangen, Schwanz mit Kugel. Koordinaten: 2D-Zeichenfläche,
// Mitte x = 110, Füße unten bei y ≈ 210. Die 3D-Ansicht dreht dieselbe Silhouette zu einem Körper.
const FIGS = [
  { id: "lumo",     name: "Lumo",     shape: "lumo",    body: "#4fd9f7", ant: "#ff4fd8", heart: "#ff4fd8", iris: "#8a5cff" },
  { id: "minto",    name: "Minto",    shape: "ball",    body: "#ACFDEC", ant: "#FE9588", heart: "#FE9588", iris: "#03A294", line: "#080D30", cheek: "#FE9588", lid: "#ACFDEC" },   // Farben vom Teileblatt "Minto"
  { id: "lila",     name: "Lila",     shape: "tall",    body: "#DBBDFD", ant: "#FEF55A", heart: "#FBE93E", iris: "#7138C8", line: "#080D30", cheek: "#FE9588", lid: "#DBBDFD" },   // Farben komplett vom Teileblatt
  { id: "glut",     name: "Glut",     shape: "blob",    body: "#FD827B", ant: "#01E3F3", heart: "#01E3F3", iris: "#087DCE", line: "#080D30", cheek: "#E8456B", lid: "#FD827B" },   // Farben vom Teileblatt "Glut"
  { id: "kasto",    name: "Kasto",    shape: "block",   body: "#7DD6FE", ant: "#FEB52D", heart: "#FDB532", iris: "#3240D5", line: "#080D30", cheek: "#FE9588", lid: "#7DD6FE" },   // Farben vom Teileblatt
  { id: "tropfi",   name: "Tropfi",   shape: "drop",    body: "#FEB5E2", ant: "#C475FB", heart: "#BF74F1", iris: "#9035BD", line: "#080D30", cheek: "#FE9588", lid: "#FEB5E2" },   // Farben vom Teileblatt
  { id: "wolki",    name: "Wolki",    shape: "cloud",   body: "#FEF8E3", ant: "#01DDA0", heart: "#03D89E", iris: "#04927D", line: "#080D30", cheek: "#FE9588", lid: "#FEF8E3" },   // Farben vom Teileblatt "Figur 6"
  { id: "rauti",    name: "Rauti",    shape: "diamond", body: "#A165F9", ant: "#0BDDFE", heart: "#06F1F7", iris: "#0596C8", line: "#080D30", cheek: "#FE9588", lid: "#A165F9" },   // Farben vom Teileblatt "Rauti"
  { id: "sunni",    name: "Sunni",    shape: "bean",    body: "#FDD845", ant: "#FD4DB1", heart: "#FD5CC1", iris: "#9636C5", line: "#080D30", cheek: "#FE9588", lid: "#FDD845" },   // Farben vom Teileblatt "Sunni"
  { id: "orbo",     name: "Orbo",     shape: "eight",   body: "#FF985D", ant: "#356AFD", heart: "#286BF8", iris: "#087DCE", line: "#080D30", cheek: "#FE9588", lid: "#FF985D" },   // Farben vom Teileblatt "Orbo"
  { id: "schnecki", name: "Schnecki", shape: "slug",    body: "#5E6076", ant: "#98E33C", heart: "#98E33C", iris: "#4E8A12", line: "#080D30", cheek: "#FF7A9A", lid: "#5E6076" },   // Farben vom Teileblatt "Schnecki"
];

/** Körperformen. face: Augenhöhe ey, halber Augenabstand dx, Augenradius r. ant: unterste Kugel bei 110±dx, y.
 *  arms/feet: vollständige Listen (beide Seiten). heart: [x, y, Größe]. tail: Ansatz [x, y]. */
const SHAPES = {
  lumo: { lumo: true, face: { ey: 100, dx: 26, r: 21 }, ant: { dx: 24, y: 40 }, heart: [110, 172, 19], tail: [140, 190],
    glint: [78, 62, -28], sweat: [174, 54], depth: 0.86 },
  jelly: { path: "M44 132 C44 88 76 62 110 62 C144 62 176 88 176 132 L176 176 Q168 196 154 182 Q140 200 126 184 Q110 202 94 184 Q80 200 66 182 Q52 196 44 176 Z",
    face: { ey: 124, dx: 30, r: 21 }, ant: { dx: 40, y: 72 }, heart: [110, 170, 13], tail: [170, 178], arms: [], feet: [],
    glint: [74, 86, -30], sweat: [176, 92], depth: 0.9 },
  ball: { path: "M37 120 A73 73 0 1 1 183 120 A73 73 0 1 1 37 120 Z",   // nach Figurenblatt "Minto": runde Kugel
    face: { ey: 111, dx: 39, r: 22 }, ant: { dx: 33, y: 47, beads: [[0, 0, 11], [12, -17, 13], [31, -39, 20]] }, heart: [110, 168, 20],
    tail: [152, 186], arms: [[40, 158, 10, 13, 30], [180, 158, 10, 13, -30]], feet: [[54, 193, 43, 17], [124, 193, 43, 17]],
    glint: [75, 64, -30], sweat: [172, 66], depth: 0.92 },
  tall: { head: [110, 70, 43, 39],   // nach Figurenblatt "Lila": großer runder Kopf, schmaler Hals, Flaschenkörper
    path: "M92 100 C95 112 90 122 86 134 C80 150 78 168 84 182 C90 196 104 199 110 199 C116 199 130 196 136 182 C142 168 140 150 134 134 C130 122 125 112 128 100 Z",
    face: { ey: 75, dx: 20, r: 16 }, ant: { dx: 27, y: 36, beads: [[0, 0, 8], [7, -17, 10], [-4, -38, 13]] }, heart: [110, 128, 14], tail: [130, 192],
    arms: [[74, 140, 9, 33, 20], [146, 140, 9, 33, -20]], feet: [[72, 197, 35, 13], [113, 197, 35, 13]],
    glint: [88, 48, -30], sweat: [152, 42], depth: 0.85 },
  blob: { path: "M110 52 C150 52 178 80 190 112 C200 140 198 172 184 190 L36 190 C22 172 20 140 30 112 C42 80 70 52 110 52 Z",   // nach Figurenblatt "Glut": breite Kuppel mit Flossen-Armen
    face: { ey: 115, dx: 41, r: 25, my: 130 }, ant: { dx: 38, y: 52, beads: [[0, 0, 10], [4, -19, 12], [19, -45, 20]] }, heart: [110, 163, 21],
    tail: [178, 186], arms: [[12, 142, 15, 32, 18], [208, 142, 15, 32, -18]], feet: [[38, 190, 66, 20], [116, 190, 66, 20]],
    glint: [62, 88, -35], sweat: [194, 78], depth: 0.82 },
  block: { path: "M88 55 H132 Q157 55 157 80 V164 Q157 186 135 186 H85 Q63 186 63 164 V80 Q63 55 88 55 Z",   // nach Figurenblatt "Kasto": hoher Quader mit runden Ecken
    face: { ey: 102, dx: 25, r: 18, my: 116 }, ant: { dx: 22, y: 52, beads: [[0, 0, 9], [6, -13, 11], [13, -34, 17]] }, heart: [110, 145, 16], tail: [146, 177],
    arms: [[59, 140, 10, 18, 15], [161, 140, 10, 18, -15]], feet: [[70, 183, 32, 19], [118, 183, 32, 19]],
    glint: [76, 72, -20], sweat: [170, 56], depth: 0.78 },
  drop: { path: "M110 43 C122 43 150 70 158 92 C164 110 152 120 141 125 C148 140 146 160 144 170 C140 186 120 187 110 187 C100 187 80 186 76 170 C74 160 72 140 79 125 C68 120 56 110 62 92 C70 70 98 43 110 43 Z",   // nach Figurenblatt "Tropfi": Tropfen-Kopf, Taille, runder Bauch
    face: { ey: 96, dx: 25, r: 15, my: 110 }, ant: { dx: 13, y: 42, beads: [[0, 0, 8], [5, -13, 10], [15, -30, 15]] }, heart: [110, 146, 16], tail: [138, 178],
    arms: [[70, 151, 10, 19, 18], [150, 151, 10, 19, -18]], feet: [[73, 183, 34, 19], [113, 183, 34, 19]],
    glint: [80, 78, -35], sweat: [160, 58], depth: 0.85 },
  cloud: { path: "M110 50 C138 50 159 70 159 95 C159 113 148 124 134 127 C141 140 144 155 144 168 C144 180 138 185 128 185 L92 185 C82 185 76 180 76 168 C76 155 79 140 86 127 C72 124 61 113 61 95 C61 70 82 50 110 50 Z",   // nach Figurenblatt "Wolki": großer runder Kopf, schmaler Bauch
    face: { ey: 98, dx: 24, r: 17, my: 112 }, ant: { dx: 14, y: 46, beads: [[0, 0, 8], [7, -13, 10], [17, -32, 16]] }, heart: [110, 146, 17], tail: [137, 176],
    arms: [[67, 152, 11, 20, 20], [153, 152, 11, 20, -20]], feet: [[70, 182, 37, 19], [112, 182, 37, 19]],
    glint: [80, 70, -30], sweat: [165, 56], depth: 0.85 },
  diamond: { path: "M110 49 C116 49 120 52 126 58 L162 98 C168 105 168 115 162 122 L149 135 C150 150 150 162 146 172 C140 184 124 188 110 188 C96 188 80 184 74 172 C70 162 70 150 71 135 L58 122 C52 115 52 105 58 98 L94 58 C100 52 104 49 110 49 Z",   // nach Figurenblatt "Rauti": Rauten-Kopf, runder Bauch
    face: { ey: 109, dx: 27, r: 15, my: 121 }, ant: { dx: 16, y: 50, beads: [[0, 0, 9], [7, -14, 10], [16, -33, 15]] }, heart: [110, 155, 17], tail: [130, 172],
    arms: [[64, 152, 10, 16, 15], [156, 152, 10, 16, -15]], feet: [[77, 182, 32, 17], [113, 182, 32, 17]],
    glint: [84, 76, -45], sweat: [166, 70], depth: 0.8 },
  bean: { path: "M110 50 C136 50 154 70 154 95 C154 108 146 116 143 123 C148 135 150 145 148 160 C146 176 136 185 122 185 L98 185 C84 185 74 176 72 160 C70 145 72 135 77 123 C74 116 66 108 66 95 C66 70 84 50 110 50 Z",   // nach Figurenblatt "Sunni": runder Kopf, leichte Taille, Bauch
    face: { ey: 97, dx: 21, r: 14, my: 110 }, ant: { dx: 14, y: 46, beads: [[0, 0, 8], [7, -15, 10], [15, -34, 16]] }, heart: [110, 145, 17], tail: [138, 176],
    arms: [[62, 146, 11, 18, 20], [158, 146, 11, 18, -20]], feet: [[70, 183, 35, 17], [111, 183, 35, 17]],
    glint: [82, 70, -30], sweat: [160, 56], depth: 0.84 },
  eight: { path: "M110 49 C134 49 153 64 153 85 C153 101 144 111 134 116 C145 124 149 138 149 152 C149 171 133 183 110 183 C87 183 71 171 71 152 C71 138 75 124 86 116 C76 111 67 101 67 85 C67 64 86 49 110 49 Z",   // nach Figurenblatt "Orbo" (neu): Achter aus zwei Kugeln
    face: { ey: 90, dx: 20, r: 14, my: 104 }, ant: { dx: 16, y: 45, beads: [[0, 0, 8], [7, -13, 10], [15, -32, 15]] }, heart: [110, 146, 17], tail: [140, 176],
    arms: [[61, 149, 11, 19, 18], [159, 149, 11, 19, -18]], feet: [[66, 180, 40, 18], [114, 180, 40, 18]],
    glint: [82, 66, -30], sweat: [160, 54], depth: 0.86 },
  slug: { path: "M110 65 C138 65 158 76 159 98 C160 104 160 106 161 112 C163 122 158 128 153 133 C157 143 157 152 156 162 C155 178 140 187 122 187 L98 187 C80 187 65 178 64 162 C63 152 63 143 67 133 C62 128 57 122 59 112 C60 106 60 104 61 98 C62 76 82 65 110 65 Z",   // nach Figurenblatt "Schnecki": breiter Kopf mit Backen, Bauch
    face: { ey: 110, dx: 26, r: 14, my: 122 }, ant: { dx: 19, y: 64, beads: [[0, 0, 8], [7, -15, 9], [14, -34, 14]] }, heart: [110, 158, 17], tail: [149, 177],
    arms: [[58, 158, 12, 18, 25], [162, 158, 12, 18, -25]], feet: [[66, 182, 38, 17], [113, 182, 38, 17]],
    glint: [76, 80, -25], sweat: [170, 74], depth: 0.82 },
};

let FIG_ID = "lumo";
try { const v = localStorage.getItem("osmon.fig"); if (FIGS.some(f => f.id === v)) FIG_ID = v; } catch (e) {}
const curFig = () => FIGS.find(f => f.id === FIG_ID) || FIGS[0];

function mixHex(a, b, t) {
  const pa = parseInt(a.slice(1), 16), pb = parseInt(b.slice(1), 16);
  const ch = s => [(pa >> s) & 255, (pb >> s) & 255];
  const v = [16, 8, 0].map(s => { const [x, y] = ch(s); return Math.round(x + (y - x) * t); });
  return "#" + v.map(n => n.toString(16).padStart(2, "0")).join("");
}
/** Abgeleitete Farben einer Figur (gleiche Rollen wie bei Lumo). */
function figColors(F) {
  return { body: F.body, bodySick: mixHex(F.body, "#c9d2d6", 0.5), ant: F.ant, off: mixHex(F.ant, "#1b1e4b", 0.6),
    heart: F.heart, iris: F.iris, lid: F.lid || mixHex(F.iris, "#ffffff", 0.3), line: F.line || "#1b1e4b", cheek: F.cheek || "#ff7fc8",
    tongue: "#ff8fd6", sweat: "#9ff0ff" };
}
const HEART = "M0 -0.35 C -0.05 -0.78 -0.78 -0.82 -0.92 -0.3 C -1.02 0.06 -0.62 0.46 0 0.9 C 0.62 0.46 1.02 0.06 0.92 -0.3 C 0.78 -0.82 0.05 -0.78 0 -0.35 Z";

// ======================= Figur in 2D =======================
/** p: { mood, ov, form, branch, energy, rest, charging, petted }  id: eindeutiger Präfix  F: Figur (sonst aktuelle) */
function lumoSVG(p, id, F) {
  F = F || curFig();
  const SH = SHAPES[F.shape], L = figColors(F);
  const md = p.mood, ov = p.ov, R = p.react || null;     // R: kurze Reaktion auf Antippen (überdeckt das Gesicht)
  const yawn = ov.includes("yawning"), stress = ov.includes("stressed");
  const chubby = ov.includes("chubby"), slug = ov.includes("sluggish");
  const body = md === "sick" ? L.bodySick : L.body;
  const lit = p.rest == null ? 0 : p.rest >= 66 ? 3 : p.rest >= 33 ? 2 : 1;
  const fill = p.energy == null ? 0 : clamp(p.energy, 0, 100) / 100;
  const sw = 3, cx = 110;
  const { ey, dx, r } = SH.face, my = SH.face.my != null ? SH.face.my : ey + r + 2;
  const stroke = `stroke="${L.line}" stroke-width="${sw}"`;

  // Schwanz (hinter allem), liegt am Boden, wenn träge
  const [tx, ty] = SH.tail;
  const tailPath = slug ? `M${tx} ${ty + 8} Q${tx + 30} ${ty + 24} ${tx + 58} ${Math.min(ty + 22, 212)}`
    : `M${tx} ${ty} Q${tx + 36} ${ty + 12} ${tx + 48} ${ty - 12}`;
  const bx = slug ? tx + 62 : tx + 52, by = slug ? Math.min(ty + 20, 210) : ty - 18;
  const tail = `<path d="${tailPath}" stroke="${L.line}" stroke-width="10" fill="none" stroke-linecap="round"/>
    <path d="${tailPath}" stroke="${body}" stroke-width="5" fill="none" stroke-linecap="round"/>
    <circle class="${p.charging ? "charge" : ""}" cx="${bx}" cy="${by}" r="10" fill="${L.ant}" ${stroke}/>
    <circle cx="${bx - 3}" cy="${by - 4}" r="2.5" fill="#fff" opacity="0.8"/>`;

  // Körper: Lumo-Bauweise (Kopf + Rumpf) oder eine Silhouette
  let feet, bodyParts, headPart;
  if (SH.lumo) {
    const w = chubby ? 1.3 : 1, x0 = 110 - 46 * w, x1 = 110 + 46 * w;
    feet = [80, 116].map(x => `<g class="limb" data-limb="${x < 100 ? "fl" : "fr"}"><rect x="${x}" y="192" width="24" height="18" rx="8" fill="${body}" ${stroke}/></g>`).join("");
    const torso = `<path d="M${x0 + 8} 138 Q${x0 - 12} 168 ${x0 + 2} 194 Q110 208 ${x1 - 2} 194 Q${x1 + 12} 168 ${x1 - 8} 138 Z" fill="${body}" ${stroke}/>`;
    const arm = sd => { const ax = 110 + sd * (44 * w + 4), ay = slug ? 170 : 164, a = sd * 12 * Math.PI / 180;
      return `<g class="limb" data-limb="${sd < 0 ? "l" : "r"}" style="transform-origin:${(ax + 20 * Math.sin(a)).toFixed(1)}px ${(ay - 20 * Math.cos(a)).toFixed(1)}px">`
        + `<ellipse cx="${ax}" cy="${ay}" rx="10" ry="20" transform="rotate(${sd * 12} ${ax} ${ay})" fill="${body}" ${stroke}/></g>`; };
    bodyParts = torso + arm(-1) + arm(1);
    headPart = `<ellipse cx="110" cy="96" rx="64" ry="58" fill="${body}" ${stroke}/>`;
  } else {
    feet = (SH.feet || []).map(([x, y, w, h]) => `<g class="limb" data-limb="${x + w / 2 < 110 ? "fl" : "fr"}"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${Math.min(8, h / 2)}" fill="${body}" ${stroke}/></g>`).join("");
    // Arm als Gruppe mit Drehpunkt an der Schulter (oberes Ende der Ellipse), damit er sich bei Reaktionen heben lässt
    const arms = (SH.arms || []).map(([ax, ay, rx, ry, rot]) => { const a = rot * Math.PI / 180, cy = ay + (slug ? 6 : 0);
      return `<g class="limb" data-limb="${ax < 110 ? "l" : "r"}" style="transform-origin:${(ax + ry * Math.sin(a)).toFixed(1)}px ${(cy - ry * Math.cos(a)).toFixed(1)}px">`
        + `<ellipse cx="${ax}" cy="${cy}" rx="${rx}" ry="${ry}" transform="rotate(${rot} ${ax} ${ay})" fill="${body}" ${stroke}/></g>`; }).join("");
    const sil = `<path d="${SH.path}" fill="${body}" ${stroke}/>`;
    const fat = chubby ? `transform="translate(110 0) scale(1.18 1) translate(-110 0)"` : "";
    bodyParts = `<g ${fat}>${sil}${arms}</g>`;
    headPart = SH.head ? `<ellipse cx="${SH.head[0]}" cy="${SH.head[1]}" rx="${SH.head[2]}" ry="${SH.head[3]}" fill="${body}" ${stroke}/>` : "";
  }
  const [gx, gy, gr] = SH.glint;
  const glint = `<ellipse cx="${gx}" cy="${gy}" rx="${Math.max(9, r * 0.7)}" ry="${Math.max(5, r * 0.38)}" transform="rotate(${gr} ${gx} ${gy})" fill="#fff" opacity="0.45"/>`;

  // Akku-Herz: Füllung von unten = Energie
  const [hx, hy, hs] = SH.heart;
  const fillTop = 0.9 - fill * 1.72;
  const heart = `<g transform="translate(${hx} ${hy}) scale(${hs})">
      <clipPath id="${id}h"><path d="${HEART}"/></clipPath>
      <path d="${HEART}" fill="${L.line}"/>
      <rect x="-1.1" y="${fillTop}" width="2.2" height="${0.9 - fillTop + 0.05}" fill="${L.heart}" clip-path="url(#${id}h)"/>
      <polygon points="0.12,-0.48 -0.26,0.08 -0.02,0.08 -0.14,0.56 0.26,-0.04 0.02,-0.04" fill="#fff"/>
      <path d="${HEART}" fill="none" stroke="${L.line}" stroke-width="${sw / hs}"/>
    </g>`;

  // Antennen: unterste Kugel zuerst; leuchtend = Antennenfarbe, aus = abgedunkelt
  const A = SH.ant, lean = A.lean || 1;
  const beads = A.beads || [[0, 0, 8], [6, -16, 10], [14, -37, 14]];
  const ant = sd => `<g class="ant${stress ? " wob" : ""}" style="transform-origin:${110 + sd * A.dx}px ${A.y + 10}px">
      ${beads.map(([ox, oy, br], k) => {
        const on = k < lit, bxx = 110 + sd * (A.dx + ox * lean), byy = A.y + oy;
        return `<circle cx="${bxx}" cy="${byy}" r="${br}" fill="${on ? L.ant : L.off}" ${stroke}/>`
          + (on ? `<circle cx="${bxx - br * 0.35}" cy="${byy - br * 0.4}" r="${br * 0.28}" fill="#fff" opacity="0.8"/>` : "");
      }).join("")}</g>`;

  // Wangen
  const cheekOp = (R && R !== "dizzy") || p.petted ? 0.95 : md === "radiant" ? 0.75 : md === "content" ? 0.5 : 0.3;
  const cheeks = [-1, 1].map(sd => `<ellipse cx="${cx + sd * (dx + r + 1)}" cy="${ey + r + 1}" rx="${r * 0.43}" ry="${r * 0.24}" fill="${L.cheek}" opacity="${cheekOp}"/>`).join("");

  // Augen
  let open = 1;
  if (yawn) open = 0.35; else if (md === "tired") open = 0.5; else if (slug) open = 0.6; else if (md === "sick") open = 0.7;
  if (R === "surprise" || R === "startle") open = 1; else if (R === "sleepy") open = 0.75; else if (R === "weak") open = 0.7; else if (R === "night") open = 0.45;
  const HAPPY = ["giggle", "spin", "morning", "noon", "evening"].includes(R);
  const eye = (ex, k) => {
    if (HAPPY)                            // fröhlich zugekniffen: ^ ^
      return `<path d="M${ex - r * 0.72} ${ey + r * 0.25} Q${ex} ${ey - r * 0.8} ${ex + r * 0.72} ${ey + r * 0.25}" stroke="${L.line}" stroke-width="4" fill="none" stroke-linecap="round"/>`;
    if (R === "dizzy")                    // Spiralaugen
      return `<circle cx="${ex}" cy="${ey}" r="${r}" fill="#fff" stroke="${L.line}" stroke-width="2.5"/>
        <path d="M${ex} ${ey} m${-r * 0.08} 0 a${r * 0.1} ${r * 0.1} 0 1 1 ${r * 0.2} 0 a${r * 0.3} ${r * 0.3} 0 1 1 ${-r * 0.5} 0 a${r * 0.5} ${r * 0.5} 0 1 1 ${r * 0.85} 0" stroke="${L.line}" stroke-width="2.2" fill="none" stroke-linecap="round"/>`;
    if (md === "hibernation" && R !== "startle") return `<path d="M${ex - r * 0.67} ${ey} Q${ex} ${ey + r * 0.43} ${ex + r * 0.67} ${ey}" stroke="${L.line}" stroke-width="3.5" fill="none" stroke-linecap="round"/>`;
    const rings = stress ? `<ellipse cx="${ex}" cy="${ey + r - 1}" rx="${r * 0.8}" ry="${r * 0.28}" fill="${L.lid}" opacity="0.45"/>` : "";
    const hl = r * 0.29;
    return `${rings}<clipPath id="${id}e${k}"><circle cx="${ex}" cy="${ey}" r="${r}"/></clipPath>
      <circle cx="${ex}" cy="${ey}" r="${r}" fill="#fff"/>
      <g clip-path="url(#${id}e${k})">
        <circle cx="${ex}" cy="${ey + 1}" r="${r * 0.67}" fill="${L.iris}"/>
        <circle cx="${ex}" cy="${ey + 1}" r="${r * 0.38}" fill="${L.line}"/>
        <rect x="${ex + r * 0.05}" y="${ey - r * 0.38}" width="${hl}" height="${hl}" fill="#fff"/>
        ${md === "radiant" ? `<rect x="${ex - r * 0.33}" y="${ey + r * 0.19}" width="${hl / 2}" height="${hl / 2}" fill="#fff"/>` : ""}
        <rect x="${ex - r}" y="${ey - r}" width="${2 * r}" height="${2 * r * (1 - open)}" fill="${L.lid}"/>
        <rect class="blink" x="${ex - r}" y="${ey - r}" width="${2 * r}" height="${2 * r}" fill="${body}"/>
      </g>
      <circle cx="${ex}" cy="${ey}" r="${r}" fill="none" stroke="${L.line}" stroke-width="2.5"/>`;
  };
  const worried = (stress || md === "sick" || R === "startle") && !HAPPY;
  const brow = (ex, sd) => {
    const inner = ex - sd * 10, outer = ex + sd * 10, b = ey - r - (R === "surprise" ? 4 : 0);
    return worried
      ? `<path d="M${outer} ${b - 5} Q${ex} ${b - 9} ${inner} ${b - 13}" stroke="${L.line}" stroke-width="2.5" fill="none" stroke-linecap="round"/>`
      : `<path d="M${ex - 10} ${b - 7} Q${ex} ${b - 13} ${ex + 10} ${b - 7}" stroke="${L.line}" stroke-width="2.5" fill="none" stroke-linecap="round"/>`;
  };

  // Mund (relativ zur Mundhöhe my)
  let mouth;
  const ln = `stroke="${L.line}" stroke-width="3" fill="none" stroke-linecap="round"`;
  const happy = R === "giggle" || R === "spin" || R === "noon";
  if (R === "evening") mouth = `<path d="M${cx - 10} ${my} Q${cx} ${my + 9} ${cx + 10} ${my}" ${ln}/>`;
  else if (R === "night") mouth = `<path d="M${cx - 5} ${my + 4} Q${cx} ${my + 8} ${cx + 5} ${my + 4}" ${ln}/>`;
  else if (R === "surprise" || R === "startle") mouth = `<ellipse cx="${cx}" cy="${my + 6}" rx="5" ry="6.5" fill="${L.line}"/>`;
  else if (R === "weak") mouth = `<path d="M${cx - 7} ${my + 2} Q${cx} ${my + 7} ${cx + 7} ${my + 2}" ${ln}/>`;
  else if (happy) mouth = `<path d="M${cx - 14} ${my - 1} Q${cx} ${my + 19} ${cx + 14} ${my - 1} Z" fill="${L.line}" stroke="${L.line}" stroke-width="2" stroke-linejoin="round"/><ellipse cx="${cx}" cy="${my + 9}" rx="6" ry="3.5" fill="${L.tongue}"/>`;
  else if (yawn || R === "sleepy" || R === "morning") mouth = `<ellipse cx="${cx}" cy="${my + 7}" rx="12" ry="15" fill="${L.line}"/><ellipse cx="${cx}" cy="${my + 15}" rx="8" ry="5" fill="${L.tongue}"/>`;
  else if (stress || R === "dizzy") mouth = `<path d="M${cx - 14} ${my + 5} q3.5 -4 7 0 t7 0 t7 0 t7 0" stroke="${L.line}" stroke-width="2.5" fill="none" stroke-linecap="round"/>`;
  else mouth = {
    radiant: `<path d="M${cx - 14} ${my - 1} Q${cx} ${my + 19} ${cx + 14} ${my - 1} Z" fill="${L.line}" stroke="${L.line}" stroke-width="2" stroke-linejoin="round"/><ellipse cx="${cx}" cy="${my + 9}" rx="6" ry="3.5" fill="${L.tongue}"/>`,
    content: `<path d="M${cx - 10} ${my} Q${cx} ${my + 9} ${cx + 10} ${my}" ${ln}/>`,
    tired: `<path d="M${cx - 8} ${my + 5} L${cx + 8} ${my + 5}" ${ln}/>`,
    sick: `<path d="M${cx - 10} ${my + 9} Q${cx} ${my} ${cx + 10} ${my + 9}" ${ln}/>`,
    hibernation: `<path d="M${cx - 5} ${my + 4} Q${cx} ${my + 8} ${cx + 5} ${my + 4}" ${ln}/>`,
  }[md];

  const [swx, swy] = SH.sweat;
  const sweat = stress ? `<path d="M${swx} ${swy} Q${swx - 8} ${swy + 12} ${swx} ${swy + 18} Q${swx + 8} ${swy + 12} ${swx} ${swy} Z" fill="${L.sweat}" stroke="${L.line}" stroke-width="2"/>` : "";
  const [zx, zy] = SH.zz || [14, 44];
  const zz = yawn || md === "hibernation" ? `<text class="zz" x="${zx}" y="${zy}" font-size="20" font-weight="700" fill="${L.line}">z Z</text>` : "";
  const badge = p.form ? `<text x="182" y="22" font-size="28">${FORMS[p.form].badge}</text>` : "";
  const branch = p.branch === "radiant" ? `<text x="4" y="78" font-size="20">✨</text>` : p.branch === "wild" ? `<text x="18" y="212" font-size="20">🍂</text>` : "";

  const slow = slug || md === "sick" || md === "hibernation";
  const headTilt = slug ? `rotate(-7 110 ${ey + 50})` : "";
  const face = `${cheeks}${eye(cx - dx, 0)}${eye(cx + dx, 1)}${brow(cx - dx, -1)}${brow(cx + dx, 1)}${mouth}`;
  // Lumo-Bauweise: Kopf über dem Rumpf. Silhouetten: Gesicht liegt auf dem Körper, Herz darüber.
  const inner = SH.lumo
    ? `${tail}${feet}${bodyParts}${heart}<g transform="${headTilt}">${ant(-1)}${ant(1)}${headPart}${glint}${face}</g>`
    : `${tail}${feet}<g transform="${headTilt}">${ant(-1)}${ant(1)}</g>${bodyParts}${headPart}${glint}${heart}<g transform="${headTilt}">${face}</g>`;
  return `<g transform="translate(0 ${slug ? 4 : 0})"><g class="pet-body${slow ? " slow" : ""}">${inner}</g></g>${sweat}${zz}${badge}${branch}`;
}

