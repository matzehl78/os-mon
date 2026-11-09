/**
 * 3D-Szene „Ei schlüpft, Figur wächst“ für Three.js r150+ (hier r187).
 * Rein zeitgesteuert: update(t) setzt jedes Objekt allein aus der Zeit t (Sekunden).
 * Dadurch kann Remotion jedes Bild einzeln und parallel rendern (keine Zustände
 * von Bild zu Bild, keine Zufallszahlen zur Laufzeit).
 *
 * Herkunft: tools/schlupf3d/schlupf-body.html (r128), hier auf aktuelles Three.js portiert.
 */
import * as T from "three";
import { DUR } from "../timeline";

const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const ss = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const lerp = (a, b, t) => a + (b - a) * t;
const V3 = (x, y, z) => new T.Vector3(x, y, z);
const UP = V3(0, 1, 0);
const PI = Math.PI;

/** Deterministischer Zufall (gleich in jedem Render-Tab) */
function mulberry(a) {
  return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}

/**
 * @param {T.WebGLRenderer} renderer
 * @param {{ reduceMotion?: boolean }} [opts]
 */
export function createScene(renderer, opts = {}) {
  const reduce = !!opts.reduceMotion;
  const disposables = [];
  const track = (x) => { disposables.push(x); return x; };
  const world = new T.Group();

  // ---------------- Licht, Umgebung ----------------
  const envTex = (() => {
    const env = new T.Scene();
    env.add(new T.Mesh(new T.SphereGeometry(20, 32, 16), new T.MeshBasicMaterial({ color: 0x0a0c10, side: T.BackSide })));
    const box = (c, w, h, x, y, z) => { const m = new T.Mesh(new T.PlaneGeometry(w, h), new T.MeshBasicMaterial({ color: c, side: T.DoubleSide })); m.position.set(x, y, z); m.lookAt(0, 0, 0); env.add(m); };
    box(0xffffff, 10, 6, 4, 9, 8); box(0xffe2c8, 6, 8, -10, 3, 4); box(0x3ff0c8, 3, 10, 10, 1, -6); box(0xffffff, 14, 2, 0, -2, 12);
    const pm = new T.PMREMGenerator(renderer);
    const rt = pm.fromScene(env, 0.02); pm.dispose();
    return track(rt).texture;
  })();
  // Physikalische Lichteinheiten (r155+): Werte gegenüber r128 etwa mal π
  world.add(new T.HemisphereLight(0xfff3e6, 0x1a2a28, 0.55 * PI));
  const key = new T.DirectionalLight(0xfff0e0, 1.6 * PI); key.position.set(3, 6, 5); world.add(key);
  const rim = new T.DirectionalLight(0x8ff5e0, 1.0 * PI); rim.position.set(-4, 3, -5); world.add(rim);
  const heartLight = new T.PointLight(0x35e6bd, 0, 4, 2); world.add(heartLight);

  // ---------------- Texturen ----------------
  function canvasTex(w, h, draw) {
    const c = document.createElement("canvas"); c.width = w; c.height = h;
    draw(/** @type {CanvasRenderingContext2D} */ (c.getContext("2d")), w, h);
    const t = new T.CanvasTexture(c); t.colorSpace = T.SRGBColorSpace; t.anisotropy = 4; return track(t);
  }
  const glowTex = canvasTex(128, 128, (g) => { const r = g.createRadialGradient(64, 64, 0, 64, 64, 64); r.addColorStop(0, "rgba(255,255,255,1)"); r.addColorStop(0.25, "rgba(255,255,255,.55)"); r.addColorStop(1, "rgba(255,255,255,0)"); g.fillStyle = r; g.fillRect(0, 0, 128, 128); });
  const glow = (color, size, op = 1) => { const s = new T.Sprite(new T.SpriteMaterial({ map: glowTex, color, transparent: true, opacity: op, blending: T.AdditiveBlending, depthWrite: false })); s.scale.setScalar(size); return s; };
  const HEART = "M0 -0.35 C -0.05 -0.78 -0.78 -0.82 -0.92 -0.3 C -1.02 0.06 -0.62 0.46 0 0.9 C 0.62 0.46 1.02 0.06 0.92 -0.3 C 0.78 -0.82 0.05 -0.78 0 -0.35 Z";
  const BOLT = [[0.16, -0.62], [-0.3, 0.1], [-0.02, 0.1], [-0.18, 0.66], [0.3, -0.06], [0.02, -0.06]];
  const drawBolt = (g) => { g.beginPath(); BOLT.forEach(([x, y], i) => (i ? g.lineTo(x, y) : g.moveTo(x, y))); g.closePath(); g.fill(); };

  // ---------------- Boden ----------------
  const shadow = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({ map: canvasTex(128, 128, (g) => { const r = g.createRadialGradient(64, 64, 0, 64, 64, 64); r.addColorStop(0, "rgba(0,0,0,.85)"); r.addColorStop(1, "rgba(0,0,0,0)"); g.fillStyle = r; g.fillRect(0, 0, 128, 128); }), transparent: true, depthWrite: false }));
  shadow.rotation.x = -PI / 2; shadow.position.y = 0.002; world.add(shadow);
  const puddle = new T.Mesh(new T.CircleGeometry(1, 48), new T.MeshPhysicalMaterial({ color: 0xbfe9ff, transparent: true, opacity: 0, roughness: 0.02, clearcoat: 1, envMapIntensity: 0.6, depthWrite: false }));
  puddle.rotation.x = -PI / 2; puddle.position.y = 0.004; world.add(puddle);

  // ================= DAS EI =================
  const EGG_H = 1.35, EGG_R = 1.0;
  const eggR = (y) => { const u = clamp(y / EGG_H, -1, 1); return EGG_R * Math.sqrt(Math.max(0, 1 - u * u)) * (1 - 0.16 * u); };
  const splitY = (th) => 0.12 + 0.13 * Math.abs(((th * 9) / PI) % 2 - 1) * 2 - 0.13 + 0.04 * Math.sin(th * 3);
  function drawEggHeart(g, W, H, emissiveOnly) {
    g.save(); g.translate(W / 2, H * 0.47); g.scale(105, 120);
    if (emissiveOnly) { g.shadowColor = "rgba(80,255,210,1)"; g.shadowBlur = 40; }
    g.fillStyle = emissiveOnly ? "rgba(60,240,200,.85)" : "rgba(80,225,195,.95)"; g.fill(new Path2D(HEART));
    g.fillStyle = emissiveOnly ? "#ffffff" : "#eafff9"; g.scale(0.9, 0.9); drawBolt(g);
    g.restore();
  }
  const eggMap = canvasTex(1024, 512, (g, W, H) => {
    const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, "#f9dcc4"); gr.addColorStop(0.5, "#f6c9a6"); gr.addColorStop(1, "#efb894");
    g.fillStyle = gr; g.fillRect(0, 0, W, H);
    const rp = mulberry(21);
    for (let i = 0; i < 9000; i++) { g.fillStyle = `rgba(255,255,255,${rp() * 0.08})`; g.fillRect(rp() * W, rp() * H, 1.5, 1.5); }
    const rnd = mulberry(7);
    for (let i = 0; i < 260; i++) {
      const x = rnd() * W, y = rnd() * H, r = Math.pow(rnd(), 3) * 16 + 2;
      if (Math.abs(x - W / 2) < 120 && Math.abs(y - H * 0.47) < 95) continue;
      g.save(); g.translate(x, y); g.fillStyle = "rgba(20,190,160,.92)";
      g.beginPath(); g.arc(0, 0, r * 0.62, 0, PI * 2); g.fill();
      if (r > 6) {
        const n = 9 + ((rnd() * 6) | 0); g.lineWidth = Math.max(1, r * 0.12); g.strokeStyle = "rgba(20,190,160,.85)";
        for (let k = 0; k < n; k++) { const a = (k / n) * PI * 2 + rnd(); g.beginPath(); g.moveTo(0, 0); g.lineTo(Math.cos(a) * r * 1.35, Math.sin(a) * r * 1.1); g.stroke(); }
      }
      g.restore();
    }
    drawEggHeart(g, W, H, false);
  });
  const eggEmi = canvasTex(1024, 512, (g, W, H) => { g.fillStyle = "#000"; g.fillRect(0, 0, W, H); drawEggHeart(g, W, H, true); });

  /** Eierschale bis/ab Zickzack-Linie: part "bottom" oder "top" */
  function shellGeo(part, seg = 96, rows = 40) {
    const pos = [], uv = [], idx = [];
    for (let j = 0; j <= seg; j++) {
      const u = j / seg, th = u * PI * 2, ys = splitY(th);
      for (let i = 0; i <= rows; i++) {
        const f = i / rows, y = part === "bottom" ? lerp(-EGG_H, ys, f) : lerp(ys, EGG_H, f), r = eggR(y);
        pos.push(r * Math.sin(th), y, -r * Math.cos(th)); uv.push(u, (y + EGG_H) / (2 * EGG_H));
      }
    }
    for (let j = 0; j < seg; j++) for (let i = 0; i < rows; i++) { const a = j * (rows + 1) + i, b = a + rows + 1; idx.push(a, a + 1, b, b, a + 1, b + 1); }
    const g = new T.BufferGeometry(); g.setAttribute("position", new T.Float32BufferAttribute(pos, 3)); g.setAttribute("uv", new T.Float32BufferAttribute(uv, 2)); g.setIndex(idx); g.computeVertexNormals();
    return g;
  }
  const shellMat = new T.MeshPhysicalMaterial({ map: eggMap, emissiveMap: eggEmi, emissive: 0xffffff, emissiveIntensity: 0.6, roughness: 0.38, clearcoat: 0.9, clearcoatRoughness: 0.18 });
  const innerMat = new T.MeshStandardMaterial({ color: 0xf2c4bc, roughness: 0.55, side: T.BackSide });
  const egg = new T.Group(); world.add(egg);
  const eggBottom = new T.Group(), eggTop = new T.Group(), eggTopPivot = new T.Group();
  egg.add(eggBottom, eggTopPivot); eggTopPivot.add(eggTop);
  const gB = shellGeo("bottom"), gT = shellGeo("top");
  eggBottom.add(new T.Mesh(gB, shellMat), new T.Mesh(gB, innerMat));
  eggTop.add(new T.Mesh(gT, shellMat), new T.Mesh(gT, innerMat));
  eggTopPivot.position.set(0, splitY(0), -eggR(0.12)); eggTop.position.set(0, -splitY(0), eggR(0.12));
  const crackCurve = new T.CatmullRomCurve3(Array.from({ length: 145 }, (_, i) => { const th = (i / 144) * PI * 2, y = splitY(th), r = eggR(y) + 0.004; return V3(r * Math.sin(th), y, -r * Math.cos(th)); }), true);
  const crackGeo = new T.TubeGeometry(crackCurve, 400, 0.022, 6, true);
  const crackMat = new T.MeshStandardMaterial({ color: 0xf6dcc8, emissive: 0x3ff0c8, emissiveIntensity: 2, roughness: 0.5 });
  eggBottom.add(new T.Mesh(crackGeo, crackMat)); eggTop.add(new T.Mesh(crackGeo, crackMat));
  const sideCracks = [];
  for (let k = 0; k < 6; k++) {
    const th0 = (k / 6) * PI * 2 + 0.4, pts = []; let th = th0, y = splitY(th0);
    for (let s = 0; s < 6; s++) { const r = eggR(y) + 0.004; pts.push(V3(r * Math.sin(th), y, -r * Math.cos(th))); th += s % 2 ? 0.07 : -0.07; y += k % 2 ? 0.09 : -0.11; }
    const m = new T.Mesh(new T.TubeGeometry(new T.CatmullRomCurve3(pts), 30, 0.014, 5), crackMat); (k % 2 ? eggTop : eggBottom).add(m); sideCracks.push(m);
  }
  const dropMat = new T.MeshPhysicalMaterial({ color: 0xffffff, transparent: true, opacity: 0.45, roughness: 0, clearcoat: 1, envMapIntensity: 3, ior: 1.33 });
  const dropGeo = new T.SphereGeometry(1, 16, 12);
  {
    const rnd = mulberry(3);
    for (let i = 0; i < 26; i++) {
      const th = rnd() * PI * 2, y = lerp(-0.9, 1.15, rnd()), r = eggR(y), s = 0.03 + rnd() * 0.05;
      const d = new T.Mesh(dropGeo, dropMat), n = V3(Math.sin(th), 0.25 * y, -Math.cos(th)).normalize();
      d.position.set(r * Math.sin(th), y, -r * Math.cos(th)).addScaledVector(n, s * 0.25); d.lookAt(d.position.clone().add(n));
      d.scale.set(s, s * 1.35, s * 0.5); (y > splitY(th) ? eggTop : eggBottom).add(d);
    }
  }
  const eggGlow = glow(0x35e6bd, 2.2, 0.35); eggGlow.position.set(0, 0.1, 1.1); egg.add(eggGlow);
  const shards = [];
  for (let i = 0; i < 7; i++) {
    const th = i * 0.9 + 0.3, w = 0.28 + (i % 3) * 0.06;
    const g = new T.SphereGeometry(EGG_R, 8, 6, th, w, PI * 0.45, 0.32);
    const m = new T.Group(); m.add(new T.Mesh(g, shellMat), new T.Mesh(g, innerMat));
    m.userData = { v: V3(Math.sin(th) * (1.1 + (i % 2) * 0.5), 2.6 + (i % 3) * 0.6, -Math.cos(th) * 1.2 + 0.9), spin: (i % 2 ? 1 : -1) * (3 + i) };
    m.visible = false; world.add(m); shards.push(m);
  }

  // ================= FIGUR: Fell per Schalen-Shader =================
  const LAYERS = 26;
  const furU = { uTime: { value: 0 }, uFurLen: { value: 0.12 }, uWet: { value: 0 }, uDens: { value: 70 }, uLightDir: { value: V3(0.45, 0.8, 0.55).normalize() } };
  const furVS = /* glsl */ `
    attribute float aFur; uniform float uLayer, uFurLen, uTime, uWet; uniform vec3 uPS;
    varying vec3 vN, vCol, vP, vW; varying float vFur;
    void main(){
      vec4 wp = modelMatrix * vec4(position, 1.0);
      vec3 wn = normalize(mat3(modelMatrix) * (normal / (uPS * uPS)));
      float L = uFurLen * aFur * (1.0 - 0.5 * uWet);
      float h = uLayer;
      wp.xyz += wn * L * h;
      wp.y -= L * h * h * (0.35 + 0.9 * uWet);
      wp.x += sin(uTime * 1.7 + wp.y * 4.0) * L * h * h * 0.12;
      vP = position * uPS; vN = wn; vCol = color; vFur = aFur; vW = wp.xyz;
      gl_Position = projectionMatrix * viewMatrix * wp;
    }`;
  const furFS = /* glsl */ `
    uniform float uLayer, uWet, uDens; uniform vec3 uLightDir;
    varying vec3 vN, vCol, vP, vW; varying float vFur;
    float h3(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
    void main(){
      float dens = uDens * mix(1.0, 0.33, uWet) * mix(1.6, 1.0, clamp(vFur, 0.0, 1.0));
      if (uLayer > 0.0) {
        vec3 q = vP * dens; vec3 c = floor(q); vec3 f = fract(q) - 0.5;
        float len = mix(0.35, 1.0, h3(c));
        if (uLayer > len) discard;
        float th = (1.0 - uLayer / len) * mix(0.62, 0.8, uWet);
        if (length(f) > th) discard;
      }
      vec3 N = normalize(vN); vec3 V = normalize(cameraPosition - vW);
      float dif = max(dot(N, uLightDir), 0.0) * 0.7 + 0.3;
      float back = pow(1.0 - max(dot(N, V), 0.0), 2.2);
      float ao = mix(0.5, 1.08, pow(uLayer, 0.7));
      vec3 col = vCol * (0.38 + dif * 0.85) * ao;
      col += back * (0.25 + 0.6 * uLayer) * vec3(1.0, 0.93, 0.86) * 0.6;
      col *= mix(1.0, 0.82, uWet);
      vec3 Hh = normalize(uLightDir + V);
      col += pow(max(dot(N, Hh), 0.0), 40.0) * uWet * 0.7;
      gl_FragColor = vec4(col, 1.0);
      #include <tonemapping_fragment>
      #include <colorspace_fragment>
    }`;

  /** Fell-Teil: Einheitskugel, Farbe und Fell-Länge je Ecke aus fn(normal) -> [Color, fur] */
  function furPart(fn, seg = 40) {
    const geo = new T.SphereGeometry(1, seg, Math.round(seg * 0.75));
    const n = geo.attributes.normal, cnt = n.count, col = new Float32Array(cnt * 3), fur = new Float32Array(cnt), v = new T.Vector3();
    for (let i = 0; i < cnt; i++) { v.fromBufferAttribute(n, i); const [c, f] = fn(v); col.set([c.r, c.g, c.b], i * 3); fur[i] = f; }
    geo.setAttribute("color", new T.BufferAttribute(col, 3)); geo.setAttribute("aFur", new T.BufferAttribute(fur, 1));
    const ps = { value: new T.Vector3(1, 1, 1) };
    const mk = (l) => new T.ShaderMaterial({ uniforms: Object.assign({ uLayer: { value: l }, uPS: ps }, furU), vertexShader: furVS, fragmentShader: furFS, vertexColors: true });
    const mesh = new T.Mesh(geo, mk(0)); mesh.frustumCulled = false;
    for (let l = 1; l <= LAYERS; l++) { const s = new T.Mesh(geo, mk(l / LAYERS)); s.frustumCulled = false; s.renderOrder = l; mesh.add(s); }
    mesh.userData.ps = ps;
    return mesh;
  }
  const C = (hex) => new T.Color(hex);               // ColorManagement: Hex (sRGB) -> linear
  const PEACH = C("#f7c48e"), CREAM = C("#fde6cc"), WHITE = C("#fffaf5"), PINK = C("#f59aa4"), TEAL = C("#22e0b0");
  const mixC = (a, b, t) => a.clone().lerp(b, clamp(t));

  const pet = new T.Group(); world.add(pet);
  const root = new T.Group(); pet.add(root);
  /** @type {Record<string, T.Mesh>} */
  const P = {};
  P.body = furPart((n) => [mixC(PEACH, CREAM, ss(0.1, 0.75, n.z) * ss(-0.7, 0.3, n.y)), 1.0]);
  P.head = furPart((n) => { const face = ss(0.2, 0.8, n.z) * ss(0.55, -0.15, n.y); return [mixC(PEACH, WHITE, face * 1.1 + ss(0.4, 0.95, n.z) * 0.5), lerp(1.15, 0.35, ss(0.45, 0.95, n.z) * ss(0.7, 0.0, n.y))]; });
  P.earL = furPart((n) => [mixC(PEACH, PINK, ss(-0.2, -0.75, n.x) * 0.9), lerp(1.2, 0.5, ss(-0.2, -0.7, n.x))], 28);
  P.earR = furPart((n) => [mixC(PEACH, PINK, ss(0.2, 0.75, n.x) * 0.9), lerp(1.2, 0.5, ss(0.2, 0.7, n.x))], 28);
  for (const s of ["FL", "FR", "HL", "HR"]) {
    P["leg" + s] = furPart((n) => [mixC(PEACH, CREAM, 0.3 + 0.5 * ss(0, 0.8, n.z)), 0.75], 24);
    P["paw" + s] = furPart(() => [mixC(CREAM, WHITE, 0.6), 0.55], 24);
  }
  P.thighL = furPart(() => [mixC(PEACH, CREAM, 0.2), 1.0], 28);
  P.thighR = furPart(() => [mixC(PEACH, CREAM, 0.2), 1.0], 28);
  P.pom = furPart((n) => [mixC(TEAL, C("#7ff7da"), ss(0.2, 1, n.y) * 0.3), 2.2], 28);
  Object.values(P).forEach((m) => root.add(m));
  // Schwanz: Kurve wird je Bild neu gebaut (Wedeln)
  const tail = new T.Mesh(new T.BufferGeometry(), new T.MeshStandardMaterial({ color: 0xf2cfa8, roughness: 0.85 })); root.add(tail);

  // Augen
  const eyeTex = canvasTex(512, 256, (g, W, H) => {
    g.fillStyle = "#f7fbfa"; g.fillRect(0, 0, W, H);
    const cx = W * 0.25, cy = H * 0.5, k = 81.5;
    const ir = g.createRadialGradient(cx, cy + 6, 4, cx, cy, 0.8 * k);
    ir.addColorStop(0, "#0b3b2c"); ir.addColorStop(0.35, "#13a46d"); ir.addColorStop(0.75, "#1fcf8c"); ir.addColorStop(0.93, "#0e7a52"); ir.addColorStop(1, "#063622");
    g.fillStyle = ir; g.beginPath(); g.arc(cx, cy, 0.8 * k, 0, PI * 2); g.fill();
    for (let i = 0; i < 60; i++) { const a = (i / 60) * PI * 2; g.strokeStyle = "rgba(170,255,215,.18)"; g.lineWidth = 1.5; g.beginPath(); g.moveTo(cx + Math.cos(a) * 18, cy + Math.sin(a) * 18); g.lineTo(cx + Math.cos(a) * 46, cy + Math.sin(a) * 46); g.stroke(); }
    g.fillStyle = "#020807"; g.beginPath(); g.arc(cx, cy, 0.33 * k, 0, PI * 2); g.fill();
    g.fillStyle = "#ffffff"; g.beginPath(); g.ellipse(cx + 13, cy - 14, 9, 8, 0, 0, PI * 2); g.fill();
    g.globalAlpha = 0.8; g.beginPath(); g.arc(cx - 15, cy + 17, 4.5, 0, PI * 2); g.fill(); g.globalAlpha = 1;
  });
  const eyeMat = new T.MeshPhysicalMaterial({ map: eyeTex, roughness: 0.08, clearcoat: 1, clearcoatRoughness: 0.03, envMapIntensity: 1.6 });
  const lidMat = new T.MeshStandardMaterial({ color: 0xf6dcc0, roughness: 0.9 });
  const lashMat = new T.MeshStandardMaterial({ color: 0x2a1a17, roughness: 0.6 });
  const eyes = [-1, 1].map((side) => {
    const g = new T.Group(); const ball = new T.Mesh(new T.SphereGeometry(1, 40, 28), eyeMat); g.add(ball);
    const lid = new T.Group(); lid.add(new T.Mesh(new T.SphereGeometry(1.05, 32, 16, 0, PI * 2, 0, PI / 2), lidMat));
    const lash = new T.Mesh(new T.TorusGeometry(1.05, 0.07, 8, 48), lashMat); lash.rotation.x = PI / 2; lid.add(lash);
    g.add(lid); g.userData = { side, lid, ball }; root.add(g); return g;
  });
  const nose = new T.Mesh(new T.SphereGeometry(1, 24, 16), new T.MeshPhysicalMaterial({ color: 0xf28a96, roughness: 0.3, clearcoat: 0.8 })); root.add(nose);
  const mouth = new T.Mesh(new T.TubeGeometry(new T.CatmullRomCurve3([V3(-1, 0.3, 0), V3(-0.5, -0.15, 0.1), V3(0, 0.1, 0.15), V3(0.5, -0.15, 0.1), V3(1, 0.3, 0)]), 24, 0.09, 6), new T.MeshStandardMaterial({ color: 0xc8606c, roughness: 0.6 })); root.add(mouth);
  const whisk = new T.LineSegments(new T.BufferGeometry(), new T.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.55 }));
  { const p = []; for (const s of [-1, 1]) for (let i = 0; i < 3; i++) p.push(s * 0.25, -0.05 * i, 0, s * (1.3 + 0.1 * i), 0.15 - 0.22 * i, -0.25); whisk.geometry.setAttribute("position", new T.Float32BufferAttribute(p, 3)); }
  root.add(whisk);
  // Fühler mit Leuchtperlen
  const beadMat = new T.MeshPhysicalMaterial({ color: 0x5ff5cf, emissive: 0x19d9a3, emissiveIntensity: 0.9, roughness: 0.05, clearcoat: 1, clearcoatRoughness: 0.02, envMapIntensity: 2 });
  const stalkMat = new T.MeshStandardMaterial({ color: 0xf0cda6, roughness: 0.7 });
  const ants = [-1, 1].map((side) => {
    const g = new T.Group(); const stalk = new T.Mesh(new T.CylinderGeometry(0.035, 0.05, 1, 10), stalkMat); stalk.position.y = 0.5; g.add(stalk);
    const beads = [[0.09, 1.02, 0.0], [0.13, 1.18, 0.07], [0.2, 1.42, 0.16]].map(([r, y, x]) => { const b = new T.Mesh(new T.SphereGeometry(r, 24, 16), beadMat); b.position.set(x * side, y, 0); g.add(b); const gl = glow(0x3ff0c8, r * 6, 0.32); gl.position.copy(b.position); g.add(gl); return b; });
    g.userData = { side, beads }; root.add(g); return g;
  });
  // Brust-Herz
  const heartTex = canvasTex(256, 256, (g) => {
    g.translate(128, 122); g.scale(105, 105);
    g.shadowColor = "rgba(53,230,189,1)"; g.shadowBlur = 18;
    const gr = g.createRadialGradient(0, 0.05, 0.1, 0, 0.05, 1.0); gr.addColorStop(0, "#5ff7d2"); gr.addColorStop(1, "#14b98f");
    g.fillStyle = gr; g.fill(new Path2D(HEART)); g.shadowBlur = 0;
    g.fillStyle = "#ffffff"; g.shadowColor = "#bfffee"; g.shadowBlur = 12; g.scale(0.75, 0.75); drawBolt(g);
  });
  const heart = new T.Mesh(new T.PlaneGeometry(1, 1), new T.MeshBasicMaterial({ map: heartTex, transparent: true, depthWrite: false, toneMapped: false }));
  heart.renderOrder = 100; root.add(heart);
  const heartGlow = glow(0x35e6bd, 1, 0.5); heartGlow.renderOrder = 101; root.add(heartGlow);
  // Tropfen am nassen Fell
  const wetDrops = [];
  { const rnd = mulberry(11); for (let i = 0; i < 18; i++) { const d = new T.Mesh(dropGeo, dropMat); d.userData = { th: rnd() * PI * 2, part: i % 3, sp: 0.4 + rnd() * 0.6 }; root.add(d); wetDrops.push(d); } }
  // Funken
  const SPARK_N = 160;
  const sparkGeo = new T.BufferGeometry(); sparkGeo.setAttribute("position", new T.BufferAttribute(new Float32Array(SPARK_N * 3), 3));
  const sparks = new T.Points(sparkGeo, new T.PointsMaterial({ map: glowTex, color: 0x7ff7da, size: 0.16, transparent: true, depthWrite: false, blending: T.AdditiveBlending }));
  world.add(sparks);
  const sparkSeed = Array.from({ length: SPARK_N }, (_, i) => { const r = mulberry(100 + i); return [r() * PI * 2, r() * 2 - 1, 0.6 + r() * 1.4, r()]; });

  // ---------------- Proportionen je Stufe ----------------
  const LOOK = {
    baby: { S: 0.78, bw: 0.82, bh: 0.78, bd: 0.75, bodyY: 0.78, bodyZ: 0, tilt: 0, headR: 0.98, headY: 1.95, headZ: 0.12, eye: 0.27, eyeX: 0.37, eyeY: 0.02, earL: 1.25, earW: 0.5,
      fSh: [0.34, 0.75, 0.38], fFt: [0.32, 0.6], hHip: [0.55, 0.42, 0.0], hFt: [0.62, 0.66], thigh: 0.48, legR: 0.21, paw: 0.24, sit: 1, raise: 0, tailL: 1.2, antL: 0.62, bead: 0.85, fur: 0.13, heartS: 0.42, look: 0.25 },
    child: { S: 0.92, bw: 0.78, bh: 0.78, bd: 0.95, bodyY: 0.95, bodyZ: -0.05, tilt: 0.2, headR: 0.86, headY: 2.0, headZ: 0.35, eye: 0.24, eyeX: 0.35, eyeY: 0.04, earL: 1.25, earW: 0.48,
      fSh: [0.33, 0.95, 0.45], fFt: [0.35, 0.72], hHip: [0.45, 0.65, -0.45], hFt: [0.5, -0.15], thigh: 0.42, legR: 0.2, paw: 0.22, sit: 0.35, raise: 1, tailL: 1.35, antL: 0.7, bead: 0.9, fur: 0.13, heartS: 0.42, look: 0.2 },
    teen: { S: 1.05, bw: 0.62, bh: 0.72, bd: 0.92, bodyY: 1.55, bodyZ: -0.05, tilt: 0.35, headR: 0.72, headY: 2.75, headZ: 0.42, eye: 0.21, eyeX: 0.31, eyeY: 0.05, earL: 1.2, earW: 0.44,
      fSh: [0.3, 1.45, 0.42], fFt: [0.33, 0.58], hHip: [0.36, 1.4, -0.55], hFt: [0.4, -0.45], thigh: 0.36, legR: 0.17, paw: 0.2, sit: 0, raise: 0, tailL: 1.5, antL: 0.8, bead: 1.0, fur: 0.14, heartS: 0.46, look: 0.3 },
    adult: { S: 1.12, bw: 1.08, bh: 1.12, bd: 0.95, bodyY: 1.2, bodyZ: 0, tilt: -0.05, headR: 0.84, headY: 2.75, headZ: 0.22, eye: 0.21, eyeX: 0.32, eyeY: 0.06, earL: 1.3, earW: 0.52,
      fSh: [0.38, 1.0, 0.45], fFt: [0.36, 0.72], hHip: [0.72, 0.55, 0.0], hFt: [0.82, 0.7], thigh: 0.55, legR: 0.24, paw: 0.27, sit: 1, raise: 0, tailL: 1.6, antL: 0.85, bead: 1.15, fur: 0.17, heartS: 0.62, look: 0.3 },
  };
  LOOK.hatch = Object.assign({}, LOOK.baby, { S: 0.66 });
  const mixLook = (a, b, t) => { const o = {}; for (const k in a) o[k] = Array.isArray(a[k]) ? a[k].map((v, i) => lerp(v, b[k][i], t)) : lerp(a[k], b[k], t); return o; };

  function limb(mesh, a, b, r) {
    const d = b.clone().sub(a), L = d.length();
    mesh.position.copy(a).add(b).multiplyScalar(0.5);
    mesh.quaternion.setFromUnitVectors(UP, d.normalize());
    mesh.scale.set(r, L / 2 + r * 0.6, r); mesh.userData.ps.value.copy(mesh.scale);
  }
  function setScale(m, x, y, z) { m.scale.set(x, y, z); m.userData.ps.value.set(x, y, z); }

  /** Pose aus Proportionen L und Animationszustand A */
  function pose(L, A) {
    const t = A.time;
    root.scale.setScalar(L.S);
    const br = 1 + 0.025 * Math.sin(t * 2.2);
    setScale(P.body, L.bw * br, L.bh * br, L.bd);
    P.body.position.set(0, L.bodyY, L.bodyZ); P.body.rotation.set(L.tilt, 0, 0);
    const head = P.head; setScale(head, L.headR * 1.06, L.headR * 0.94, L.headR * 0.96);
    head.position.set(0, L.headY + A.hop * 0.05 + 0.02 * Math.sin(t * 2.2 + 0.6), L.headZ);
    head.rotation.set(-L.look + A.nod, A.turn, A.tiltZ);
    head.updateMatrix();
    const HM = head.matrix.clone().multiply(new T.Matrix4().makeScale(1 / head.scale.x, 1 / head.scale.y, 1 / head.scale.z));
    const onHead = (x, y, z) => V3(x, y, z).applyMatrix4(HM);
    const hr = L.headR;
    eyes.forEach((e) => {
      const s = e.userData.side;
      e.position.copy(onHead(s * L.eyeX * hr, L.eyeY * hr - 0.02, hr * 0.84));
      e.quaternion.copy(head.quaternion); e.rotateY(s * 0.22); e.rotateX(-0.1);
      e.scale.setScalar(L.eye);
      e.userData.ball.rotation.set(-A.lookUp, -s * 0.05 + A.lookX, 0);
      e.userData.lid.rotation.x = lerp(-0.62, 1.95, A.closed);
    });
    nose.position.copy(onHead(0, -0.2 * hr, hr * 0.99)); nose.scale.set(0.085 * hr, 0.06 * hr, 0.07 * hr); nose.quaternion.copy(head.quaternion);
    mouth.position.copy(onHead(0, -0.36 * hr, hr * 0.93)); mouth.scale.setScalar(0.075 * hr); mouth.quaternion.copy(head.quaternion);
    whisk.position.copy(onHead(0, -0.28 * hr, hr * 0.9)); whisk.scale.setScalar(hr); whisk.quaternion.copy(head.quaternion);
    [[P.earL, -1], [P.earR, 1]].forEach(([ear, s]) => {
      const flap = A.earFlap * Math.sin(t * 22 + s) + 0.06 * Math.sin(t * 1.9 + s);
      ear.position.copy(onHead(s * hr * 0.78, hr * 0.42, -hr * 0.05)); ear.quaternion.copy(head.quaternion);
      ear.rotateZ(s * (0.32 + flap)); ear.rotateY(s * -0.25);
      ear.translateY(-L.earL * hr * 0.52); ear.translateX(s * 0.08 * hr);
      setScale(ear, L.earW * hr * 0.42, L.earL * hr * 0.6, L.earW * hr * 0.95);
    });
    ants.forEach((a) => {
      const s = a.userData.side; a.position.copy(onHead(s * hr * 0.25, hr * 0.9, hr * 0.05)); a.quaternion.copy(head.quaternion);
      a.rotateZ(-s * 0.2 + 0.08 * Math.sin(t * 2.6 + s) + A.antWob * Math.sin(t * 15 + s));
      a.rotateX(-0.12 + 0.05 * Math.sin(t * 1.8)); a.scale.setScalar((L.antL * hr) / 0.85);
      a.userData.beads.forEach((b) => b.scale.setScalar(L.bead * (1 + 0.06 * Math.sin(t * 3 + b.position.y * 5))));
    });
    const bodyQ = P.body.quaternion;
    const onBody = (x, y, z) => V3(x, y, z).applyQuaternion(bodyQ).add(P.body.position);
    for (const s of [-1, 1]) {
      const nm = s < 0 ? "L" : "R";
      const sh = V3(s * L.fSh[0], L.fSh[1], L.fSh[2]);
      const ft = V3(s * L.fFt[0], L.paw * 0.75, L.fFt[1]);
      const raise = s > 0 ? L.raise : 0;
      if (raise > 0) ft.lerp(V3(s * 0.42, L.fSh[1] + 0.05 + 0.08 * Math.sin(t * 5), L.fSh[2] + 0.55), raise);
      if (A.jump > 0) ft.y += A.jump * 0.2;
      limb(P["legF" + nm], sh, ft, L.legR);
      const pf = P["pawF" + nm]; pf.position.copy(ft).add(V3(0, -L.paw * 0.15, L.paw * 0.3)); setScale(pf, L.paw * 1.05, L.paw * 0.75, L.paw * 1.3); pf.rotation.set(-raise * 0.9, 0, 0);
      const hip = V3(s * L.hHip[0], L.hHip[1], L.hHip[2]);
      const th = P["thigh" + nm]; th.position.copy(hip); setScale(th, L.thigh * 0.85, L.thigh, L.thigh * lerp(1.15, 0.9, 1 - L.sit)); th.rotation.set(0, s * 0.2, 0);
      const hf = V3(s * L.hFt[0], L.paw * 0.75, L.hFt[1]);
      limb(P["legH" + nm], hip, hf, L.legR * lerp(1.15, 1.0, L.sit));
      const ph = P["pawH" + nm]; ph.position.copy(hf).add(V3(0, -L.paw * 0.1, L.paw * 0.35 * L.sit)); setScale(ph, L.paw * 1.05, L.paw * lerp(0.8, 1.0, L.sit), L.paw * lerp(1.3, 1.05, L.sit));
      ph.rotation.set(-L.sit * 1.2, 0, 0);
    }
    const tb = onBody(0, -0.3 * L.bh, -L.bd * 0.85);
    const sw = 0.18 * Math.sin(t * 2.4) + A.tailWag * Math.sin(t * 12), tl = L.tailL;
    const c = new T.CatmullRomCurve3([tb, tb.clone().add(V3(-0.25 + sw * 0.3, -0.05, -0.35 * tl)), tb.clone().add(V3(-0.55 + sw * 0.6, 0.45 * tl, -0.45 * tl)), tb.clone().add(V3(-0.6 + sw, 1.0 * tl, -0.25 * tl))]);
    tail.geometry.dispose(); tail.geometry = new T.TubeGeometry(c, 24, 0.07, 8);
    P.pom.position.copy(c.getPoint(1)).add(V3(-0.05, 0.12, 0)); setScale(P.pom, 0.24, 0.24, 0.24);
    const hp = onBody(0, 0.18 * L.bh, L.bd + (furU.uFurLen.value * 0.55) / L.S + 0.02);
    heart.position.copy(hp); heart.quaternion.copy(bodyQ); heart.rotateX(-0.25); heart.scale.setScalar(L.heartS * (1 + 0.08 * A.pulse));
    heartGlow.position.copy(hp).add(V3(0, 0, 0.05)); heartGlow.scale.setScalar(L.heartS * (1.9 + 0.7 * A.pulse)); heartGlow.material.opacity = 0.25 + 0.3 * A.pulse;
    wetDrops.forEach((d) => {
      const u = d.userData; d.visible = A.wet > 0.05;
      if (!d.visible) return;
      const fall = (t * u.sp + u.th) % 1;
      const base = u.part === 0 ? onHead(Math.sin(u.th) * hr * 0.9, -hr * 0.35, Math.cos(u.th) * hr * 0.6 + hr * 0.2) : (u.part === 1 ? P.earL : P.earR).position.clone().add(V3(0, -L.earL * hr * 0.5, 0));
      d.position.copy(base).add(V3(0, -fall * 0.5, 0.05)); d.scale.set(0.045, 0.07 + fall * 0.03, 0.045).multiplyScalar(A.wet);
    });
  }

  // ---------------- Zeitachse → Zustand ----------------
  function stateAt(t) {
    const A = { time: t, closed: 0, wet: 0, earFlap: 0, antWob: 0, tailWag: 0, hop: 0, jump: 0, nod: 0, turn: 0, tiltZ: 0, lookUp: 0, lookX: 0, pulse: 0 };
    const petPos = V3(0, 0, 0); let petRotY = 0;
    const grow = (a, b, t0) => mixLook(LOOK[a], LOOK[b], ss(t0, t0 + 2.2, t));
    let L = LOOK.hatch;
    if (t >= 11) L = grow("hatch", "baby", 11);
    if (t >= 20) L = grow("baby", "child", 20);
    if (t >= 26) L = grow("child", "teen", 26);
    if (t >= 32) L = grow("teen", "adult", 32);
    A.pulse = 0.5 + 0.5 * Math.sin(t * 3.2);
    const petVis = t >= 7.6;
    petPos.y = lerp(-1.05, -0.62, ss(7.5, 8.6, t));
    A.wet = 1 - ss(11.2, 12.6, t);
    A.closed = 1 - ss(10.0, 10.6, t);
    if (t > 9 && t < 10) A.nod = 0.06 * Math.sin((t - 9) * 6);
    if (t > 11 && t < 12.8) { const k = Math.sin((PI * (t - 11)) / 1.8); A.earFlap = 0.35 * k; A.tiltZ = 0.22 * k * Math.sin(t * 24); A.antWob = 0.25 * k; A.tailWag = 0.4 * k; }
    if (t >= 12.8) { const j = ss(12.8, 13.8, t), arc = Math.sin(PI * clamp(t - 12.8)); petPos.y = lerp(-0.62, 0, j) + arc * 1.1; petPos.z = lerp(0, 1.5, j); A.jump = arc; }
    if (t >= 13.8) { petPos.y = 0; petPos.z = lerp(1.5, 0.3, ss(13.8, 15.5, t)); }
    if (t > 12.6) {
      const bl = (t * 0.37) % 1; if (bl < 0.04) A.closed = Math.max(A.closed, Math.sin((PI * bl) / 0.04));
      A.turn = 0.18 * Math.sin(t * 0.7); A.lookX = 0.1 * Math.sin(t * 0.9); A.lookUp = 0.08;
      A.tailWag = Math.max(A.tailWag, 0.12);
    }
    let burst = 0;
    for (const g of [8.0, 20, 26, 32]) { const k = t - g; if (k > 0 && k < 2.4) burst = Math.max(burst, Math.sin((PI * k) / 2.4)); }
    // Schlussleuchten (Übergang zurück zum Ei, nahtlose Schleife)
    const outro = ss(DUR - 2.2, DUR - 0.2, t);
    burst = Math.max(burst, outro);
    A.pulse = Math.max(A.pulse, burst);
    if (t > 22.5 && t < 26) { const k = (t - 22.5) % 1.6; A.hop = k < 0.5 ? Math.sin((PI * k) / 0.5) : 0; petPos.y += A.hop * 0.25; }
    if (t > 28.5 && t < 32) petRotY = 0.5 * Math.sin((t - 28.5) * 0.9);
    if (t > 35.6 && t < 37.6) A.closed = Math.max(A.closed, Math.sin((PI * (t - 35.6)) / 2));
    return { L, A, petPos, petVis, petRotY, burst, outro };
  }

  const tmp = new T.Vector3();
  /**
   * Bild für Zeit t setzen und Kamera ausrichten.
   * @param {number} t Sekunden
   * @param {T.PerspectiveCamera} camera
   * @param {{ framing?: number }} [o] framing: Zielhöhe der Figur im Bild (0..1, vertikal etwas tiefer wegen Texten oben)
   */
  function update(t, camera, o = {}) {
    const { L, A, petPos, petVis, petRotY, burst, outro } = stateAt(t);
    furU.uTime.value = t; furU.uWet.value = A.wet; furU.uFurLen.value = L.fur * L.S;
    // Ei
    egg.visible = t < 16.5;
    if (egg.visible) {
      let rotZ = 0.035 * Math.sin(t * 2.0), rotX = 0;
      const shake = ss(3.8, 7.2, t) * (1 - ss(7.4, 7.6, t));
      if (!reduce) { rotZ += shake * 0.12 * Math.sin(t * 31) * (0.6 + 0.4 * Math.sin(t * 5)); rotX += shake * 0.05 * Math.sin(t * 23); }
      egg.rotation.set(rotX, 0.35 * Math.sin(t * 0.4) * (1 - ss(7, 8, t)), rotZ * (1 - ss(7.5, 8, t)));
      const cr = ss(4.0, 7.3, t);
      crackGeo.setDrawRange(0, Math.floor((crackGeo.index.count * cr) / 6) * 6);
      sideCracks.forEach((m, i) => { m.visible = t > 5 + i * 0.3; });
      crackMat.emissiveIntensity = t < 7.5 ? 1.2 + 2.5 * cr * (0.6 + 0.4 * Math.sin(t * 18)) : lerp(2.5, 0, ss(7.5, 9, t));
      const open = ss(7.5, 8.2, t), bounce = t > 8.2 ? 0.08 * Math.exp(-(t - 8.2) * 3) * Math.sin((t - 8.2) * 14) : 0;
      eggTopPivot.rotation.x = -(open * 1.25 + bounce);
      eggTopPivot.position.y = splitY(0) + open * 0.05;
      const lit = 1 - ss(7.6, 8.4, t);
      shellMat.emissiveIntensity = (0.35 + 0.5 * (0.5 + 0.5 * Math.sin(t * 3.2)) + cr * 0.8) * lit;
      eggGlow.material.opacity = (0.25 + 0.25 * Math.sin(t * 3.2) + cr * 0.5) * lit;
      const fade = 1 - ss(14.5, 16.4, t);
      shellMat.transparent = innerMat.transparent = fade < 0.999; shellMat.opacity = innerMat.opacity = fade;
      dropMat.opacity = 0.45 * Math.max(fade, A.wet);
      egg.position.y = EGG_H * 0.92 - ss(14.5, 16.4, t) * 0.4;
    } else dropMat.opacity = 0.45;
    shards.forEach((s) => {
      const k = t - 7.55; s.visible = k > 0 && t < 16.4;
      if (!s.visible) return;
      const u = s.userData, kk = Math.min(k, 0.95);
      s.position.set(u.v.x * kk, Math.max(0.05, EGG_H * 0.95 + u.v.y * kk - 4.9 * kk * kk), u.v.z * kk);
      s.rotation.set(u.spin * kk, u.spin * 0.5 * kk, k > 0.95 ? 1.4 : u.spin * 0.3 * kk);
    });
    puddle.material.opacity = 0.16 * ss(7.6, 9, t) * (1 - ss(15, 18, t)); puddle.scale.set(1.2 + ss(7.6, 10, t) * 0.8, 0.9 + ss(7.6, 10, t) * 0.5, 1);
    // Figur
    const inEgg = t < 12.8;
    pet.visible = petVis; pet.position.copy(petPos);
    if (inEgg) pet.position.y += EGG_H * 0.92 - 0.55;
    pet.rotation.set(0, petRotY + (t > 12.8 && t < 13.8 ? Math.sin(PI * (t - 12.8)) * 0.3 : 0), 0);
    pose(L, A);
    heart.getWorldPosition(tmp);
    heartLight.position.copy(tmp).add(V3(0, 0, 0.4)); heartLight.intensity = petVis ? (0.12 + 0.3 * A.pulse + 2 * outro) * PI : 0;
    ["legFL", "legFR", "legHL", "legHR", "pawFL", "pawFR", "pawHL", "pawHR", "thighL", "thighR", "pom"].forEach((k) => (P[k].visible = !inEgg));
    tail.visible = !inEgg;
    const onGround = petVis && !inEgg;
    const sc = onGround ? 2.2 * L.S + 0.4 : 2.6; shadow.scale.set(sc, sc * 0.7, 1); shadow.position.z = onGround ? pet.position.z : 0;
    shadow.material.opacity = 0.8 - 0.4 * clamp(pet.position.y);
    // Funken
    const pos = sparkGeo.attributes.position.array, center = petVis ? tmp.clone() : V3(0, 1.3, 0.5);
    const sk = Math.max(burst, t > 3.8 && t < 7.6 ? ss(3.8, 7.5, t) * 0.5 : 0);
    sparks.material.opacity = sk; sparks.visible = sk > 0.01;
    sparkSeed.forEach(([a, y, r, ph], i) => { const k = (t * 0.6 + ph) % 1, rr = r * (0.4 + k * 1.2); pos[i * 3] = center.x + Math.cos(a + t * 0.8) * rr; pos[i * 3 + 1] = center.y + y * rr * 0.8 + k * 0.6; pos[i * 3 + 2] = center.z + Math.sin(a + t * 0.8) * rr * 0.6; });
    sparkGeo.attributes.position.needsUpdate = true;
    // Kamera: Figur passend einrahmen (Höhe und Breite), langsamer Schwenk, Schub bei Höhepunkten
    const h = onGround ? L.S * (L.headY + L.headR + L.antL + 0.6) : 3.4;
    const w = onGround ? L.S * 2.6 : 2.6;
    const fov = T.MathUtils.degToRad(camera.fov / 2);
    const fitH = (h * 0.5 + 0.5) / Math.tan(fov), fitW = (w * 0.5 + 0.4) / (Math.tan(fov) * camera.aspect);
    let dist = Math.max(fitH, fitW) + 0.4;
    const punch = (reduce ? 0 : 1) * (ss(3.8, 7.4, t) * (1 - ss(7.4, 8.4, t)) * 0.18 + burst * 0.06);
    dist *= 1 - punch;
    const shakeCam = reduce ? 0 : ss(5.5, 7.4, t) * (1 - ss(7.4, 7.8, t)) * 0.03;
    const fy = (onGround ? h * 0.5 : 1.55) + (o.framing ?? 0) * h;
    const orb = reduce ? 0 : 0.32 * Math.sin(t * 0.21 + 0.4) - 0.12;
    const cz = onGround ? pet.position.z : 0;
    camera.position.set(Math.sin(orb) * dist + shakeCam * Math.sin(t * 47), fy + dist * 0.12 + shakeCam * Math.cos(t * 39), Math.cos(orb) * dist + cz);
    camera.lookAt(0, fy - 0.05, cz);
    camera.updateMatrixWorld();
  }

  function dispose() {
    world.traverse((o) => {
      const m = /** @type {any} */ (o);
      if (m.geometry) m.geometry.dispose();
      if (m.material) (Array.isArray(m.material) ? m.material : [m.material]).forEach((x) => x.dispose());
    });
    disposables.forEach((d) => d.dispose && d.dispose());
  }

  return { world, environment: envTex, update, dispose };
}
