window.make3D = () => (() => {
  const LINE = 0x1b1e4b;
  const HEART = "M0 -0.35 C -0.05 -0.78 -0.78 -0.82 -0.92 -0.3 C -1.02 0.06 -0.62 0.46 0 0.9 C 0.62 0.46 1.02 0.06 0.92 -0.3 C 0.78 -0.82 0.05 -0.78 0 -0.35 Z";
  const reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  const U = 62, X3 = x => (x - 110) / U, Y3 = y => (210 - y) / U;    // 2D-Einheiten -> 3D
  const LINE_W = 0.05;
  const hexN = s => parseInt(s.slice(1), 16);

  let T, renderer, scene, camera, canvas, root, platform, platformMat, zz, badge, branchSprite, platformOn = true;
  let reactType = null, reactStart = 0, reactDur = 0;
  let C = null, P = null, pending = null;                       // Farben, Teile der aktuellen Figur
  let eyeCtx, eyeTex, heartCtx, heartTex;
  let target = null, cur = { open: 1, chubby: 0, tilt: 0, glow: 0.5 };
  let lastHeart = -1, lastEye = "", lastBadge = "", lastBranch = "";
  let ACC = null, GLS = null;                                   // Kopf-Zubehör, Brille (Kleiderschrank)
  // Wachstum (Growth.kt): Proportionen je Stufe; Ei mit Rissen
  const LOOK = { baby: [0.66, 0.60, 1.25, 0.62, 1.18], child: [0.78, 0.74, 1.14, 0.78, 1.10], teen: [0.90, 0.89, 1.05, 0.92, 1.04] };
  let STAGE = null, EGG = null, eggGroup = null, eggKey = "", eggShake = 0, eggShakeAt = 0;
  let rotY = 0, dragging = false, lastX = 0, blinkUntil = 0, nextBlink = 2, running = false;

  const phys = (color, extra = {}) => new T.MeshPhysicalMaterial(Object.assign({ color, roughness: 0.28, metalness: 0,
    clearcoat: 1, clearcoatRoughness: 0.12 }, extra));
  const flat = (color, extra = {}) => new T.MeshStandardMaterial(Object.assign({ color, roughness: 0.5 }, extra));
  const sphere = (r, mat, seg = 32) => new T.Mesh(new T.SphereGeometry(r, seg, Math.round(seg * 0.7)), mat);
  let lineHex = LINE;
  const lineMat = () => new T.MeshBasicMaterial({ color: lineHex, side: T.BackSide });
  function at(group, obj, x, y, z) { obj.position.set(x, y, z); group.add(obj); return obj; }

  /** Kontur im Comic-Stil mit gleicher Strichstärke: Rückseite um LINE_W größer (je Achse). */
  function outlineXYZ(mesh, ax, ay, az) {
    const o = new T.Mesh(mesh.geometry, lineMat());
    o.scale.set(1 + LINE_W / ax, 1 + LINE_W / ay, 1 + LINE_W / az);
    mesh.add(o);
    return o;
  }
  function outline(mesh) {
    const r = (mesh.geometry.parameters && mesh.geometry.parameters.radius) || 1, s = mesh.scale;
    return outlineXYZ(mesh, r * s.x, r * s.y, r * s.z);
  }

  function textSprite(size, color) {
    const c = document.createElement("canvas"); c.width = c.height = 128;
    const s = new T.Sprite(new T.SpriteMaterial({ map: new T.CanvasTexture(c), transparent: true, depthWrite: false }));
    s.userData = { canvas: c, size, color };
    return s;
  }
  function setSpriteText(s, text) {
    const g = s.userData.canvas.getContext("2d");
    g.clearRect(0, 0, 128, 128);
    g.font = `700 ${s.userData.size}px system-ui, "Segoe UI Emoji", "Apple Color Emoji", sans-serif`;
    g.textAlign = "center"; g.textBaseline = "middle"; g.fillStyle = s.userData.color; g.fillText(text, 64, 68);
    s.material.map.needsUpdate = true;
  }

  // ---------- Texturen: Herz und Augen ----------
  function drawHeart(fill) {
    const g = heartCtx, p = new Path2D(HEART);
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, 256, 256);
    g.setTransform(112, 0, 0, 112, 128, 122);
    g.fillStyle = C.line; g.fill(p);
    g.save(); g.clip(p);
    const top = 0.9 - fill * 1.72;
    g.fillStyle = C.heart; g.fillRect(-1.1, top, 2.2, 0.95 - top);
    g.restore();
    g.fillStyle = "#ffffff"; g.beginPath();
    [[0.12, -0.48], [-0.26, 0.08], [-0.02, 0.08], [-0.14, 0.56], [0.26, -0.04], [0.02, -0.04]].forEach(([x, y], k) => k ? g.lineTo(x, y) : g.moveTo(x, y));
    g.closePath(); g.fill();
    g.lineWidth = 0.06; g.strokeStyle = C.line; g.stroke(p);
    heartTex.needsUpdate = true;
  }
  function drawEye(open, lid, radiant) {
    const g = eyeCtx, cx = 128, cy = 128, R = 116;
    g.clearRect(0, 0, 256, 256);
    if (open < 0.08) {                     // geschlossen (Blinzeln, Winterschlaf): nur ein Bogen
      g.lineWidth = 14; g.lineCap = "round"; g.strokeStyle = C.line;
      g.beginPath(); g.moveTo(cx - 78, cy); g.quadraticCurveTo(cx, cy + 52, cx + 78, cy); g.stroke();
      eyeTex.needsUpdate = true; return;
    }
    g.save();
    g.beginPath(); g.arc(cx, cy, R, 0, Math.PI * 2); g.fillStyle = "#ffffff"; g.fill(); g.clip();
    g.fillStyle = C.irisDark; g.beginPath(); g.arc(cx, cy + 8, 84, 0, Math.PI * 2); g.fill();
    g.fillStyle = C.iris; g.beginPath(); g.arc(cx, cy + 8, 70, 0, Math.PI * 2); g.fill();
    g.fillStyle = C.line; g.beginPath(); g.arc(cx, cy + 10, 44, 0, Math.PI * 2); g.fill();
    g.fillStyle = "#ffffff"; g.fillRect(cx + 12, cy - 34, 30, 30);
    if (radiant) g.fillRect(cx - 42, cy + 28, 15, 15);
    if (open < 0.98) {                     // Lid von oben, leicht gewölbte Kante
      const edge = cy - R + 2 * R * (1 - open);
      g.fillStyle = lid;
      g.beginPath(); g.moveTo(0, 0); g.lineTo(256, 0); g.lineTo(256, edge - 6);
      g.quadraticCurveTo(cx, edge + 14, 0, edge - 6); g.closePath(); g.fill();
      g.strokeStyle = C.line; g.lineWidth = 8;
      g.beginPath(); g.moveTo(0, edge - 6); g.quadraticCurveTo(cx, edge + 14, 256, edge - 6); g.stroke();
    }
    g.restore();
    g.lineWidth = 10; g.strokeStyle = C.line;
    g.beginPath(); g.arc(cx, cy, R - 5, 0, Math.PI * 2); g.stroke();
    eyeTex.needsUpdate = true;
  }

  // ---------- Silhouette abtasten -> Profil für die Drehung ----------
  function sampleProfile(SH) {
    const ns = "http://www.w3.org/2000/svg", svg = document.createElementNS(ns, "svg");
    svg.setAttribute("width", "0"); svg.setAttribute("height", "0");
    svg.style.position = "absolute"; svg.style.visibility = "hidden";
    document.body.appendChild(svg);
    const ds = [SH.path];
    if (SH.head) { const [hx, hy, rx, ry] = SH.head; ds.push(`M${hx - rx} ${hy} a${rx} ${ry} 0 1 0 ${2 * rx} 0 a${rx} ${ry} 0 1 0 ${-2 * rx} 0`); }
    const pts = []; let top = 1e9, bot = -1e9;
    ds.forEach(d => {
      const el = document.createElementNS(ns, "path"); el.setAttribute("d", d); svg.appendChild(el);
      const len = el.getTotalLength();
      for (let i = 0; i <= 800; i++) { const q = el.getPointAtLength(len * i / 800); pts.push(q); top = Math.min(top, q.y); bot = Math.max(bot, q.y); }
    });
    svg.remove();
    const N = 56, rows = new Array(N + 1).fill(0);
    pts.forEach(q => { const k = Math.round((q.y - top) / (bot - top) * N); rows[k] = Math.max(rows[k], Math.abs(q.x - 110)); });
    for (let k = 1; k < N; k++) if (!rows[k]) rows[k] = (rows[k - 1] + (rows[k + 1] || rows[k - 1])) / 2;
    // leicht glätten
    const sm = rows.map((r, k) => k === 0 || k === N ? r : (rows[k - 1] + 2 * r + rows[k + 1]) / 4);
    return sm.map((r, k) => ({ y: Y3(top + (bot - top) * k / N), r: r / U }));   // von oben nach unten
  }
  function radiusAt(prof, y) {
    if (y >= prof[0].y || y <= prof[prof.length - 1].y) return 0;
    for (let k = 1; k < prof.length; k++) {
      if (y >= prof[k].y) { const a = prof[k - 1], b = prof[k], t = (y - b.y) / (a.y - b.y); return b.r + (a.r - b.r) * t; }
    }
    return 0;
  }

  // ---------- Figur bauen ----------
  function clearRoot() {
    while (root.children.length) {
      const o = root.children.pop();
      o.traverse(n => { if (n.geometry) n.geometry.dispose(); });
    }
  }

  function buildFigure(SH) {
    clearRoot();
    cur = { open: 1, chubby: 0, tilt: 0, glow: cur.glow };
    lastHeart = -1; lastEye = "";
    lineHex = hexN(C.line);
    const p = { arms: [], feet: [], beads: [], eyes: [], brows: { normal: [], worried: [], rings: [] }, mouths: {}, cheeks: [] };
    p.bodyMat = phys(hexN(C.body), { emissive: hexN(C.body), emissiveIntensity: 0.06 });
    const bodyMat = p.bodyMat;
    p.bodyGroup = new T.Group(); root.add(p.bodyGroup);
    const bg = p.bodyGroup;

    let face, fz, ant, heartPos, tailStart;
    if (SH.lumo) {
      // Lumo-Bauweise
      const torso = sphere(1, bodyMat, 48); torso.scale.set(0.66, 0.62, 0.58);
      at(bg, torso, 0, 0.66, 0); outline(torso);
      p.torso = torso; p.torsoBase = { x: 0.66, z: 0.58 };
      [-1, 1].forEach(sd => {
        const f = sphere(0.2, bodyMat); f.scale.set(1.15, 0.55, 1.35); at(bg, f, sd * 0.27, 0.1, 0.12); outline(f);
        p.feet.push({ m: f, y0: 0.1, side: sd });
        const a = sphere(1, bodyMat); a.scale.set(0.16, 0.34, 0.16); a.rotation.z = sd * 0.38;
        at(bg, a, sd * 0.68, 0.72, 0.08); outline(a); a.userData.base = { x: sd * 0.68, y: 0.72, spread: sd * 0.2, rz0: sd * 0.38, L: 0.34, side: sd };
        p.arms.push(a);
      });
      const hc = { y: 1.66, rx: 1.0, ry: 0.88, rz: 0.86 };
      fz = (x, y) => hc.rz * Math.sqrt(Math.max(0, 1 - (x / hc.rx) ** 2 - ((y - hc.y) / hc.ry) ** 2));
      p.headGroup = new T.Group(); p.headGroup.position.set(0, 1.05, 0); root.add(p.headGroup);
      const head = sphere(1, bodyMat, 56); head.scale.set(hc.rx, hc.ry, hc.rz);
      at(p.headGroup, head, 0, hc.y - 1.05, 0); outline(head);
      face = { eyeX: 0.42, eyeY: 1.64, eyeR: 0.34 };
      ant = { x: 0.42, y: 2.3, lean: 1 };
      heartPos = { x: 0, y: 0.66, z: 0.6, size: 0.62 };
      tailStart = null;
      p.pivotY = 1.05;
    } else {
      // Silhouette drehen
      const prof = sampleProfile(SH), depth = SH.depth || 0.85;
      const yTop = prof[0].y, yBot = prof[prof.length - 1].y, cy = (yTop + yBot) / 2;
      const pts = [new T.Vector2(0.001, yBot)];
      for (let k = prof.length - 1; k >= 0; k--) pts.push(new T.Vector2(Math.max(prof[k].r, 0.001), prof[k].y));
      pts.push(new T.Vector2(0.001, yTop));
      const geo = new T.LatheGeometry(pts, 64); geo.translate(0, -cy, 0);
      const bodyM = new T.Mesh(geo, bodyMat); bodyM.position.y = cy; bodyM.scale.z = depth; bg.add(bodyM);
      const rMax = Math.max(...prof.map(q => q.r));
      outlineXYZ(bodyM, rMax, (yTop - yBot) / 2, rMax * depth);
      p.torso = bodyM; p.torsoBase = { x: 1, z: depth };
      fz = (x, y) => depth * Math.sqrt(Math.max(0, radiusAt(prof, y) ** 2 - x * x));
      (SH.arms || []).forEach(([ax, ay, rx, ry, rot]) => {
        const a = sphere(1, bodyMat); a.scale.set(rx / U, ry / U, rx / U); a.rotation.z = -rot * Math.PI / 180;
        const x = X3(ax), y = Y3(ay);
        at(bg, a, x, y, Math.max(0.05, fz(x * 0.8, y) * 0.5)); outline(a);
        a.userData.base = { x, y, spread: Math.sign(x) * 0.18, rz0: -rot * Math.PI / 180, L: ry / U, side: Math.sign(x) || 1 };
        p.arms.push(a);
      });
      (SH.feet || []).forEach(([fx, fy, w, h]) => {
        const r = w / 2 / U, f = sphere(r, bodyMat); f.scale.set(1, (h / w) * 1.1, 1.3);
        at(bg, f, X3(fx + w / 2), Y3(fy + h / 2), 0.12); outline(f);
        p.feet.push({ m: f, y0: Y3(fy + h / 2), side: Math.sign(X3(fx + w / 2)) || 1 });
      });
      if (SH.head) {
        // eigener Kopf (z. B. "tall") liegt schon im Profil; nur der Drehpunkt für das Neigen
      }
      const { ey, dx, r } = SH.face;
      face = { eyeX: dx / U, eyeY: Y3(ey), eyeR: r / U * 1.05 };
      ant = { x: SH.ant.dx / U, y: Y3(SH.ant.y) - 0.12, lean: SH.ant.lean || 1,
        beads: SH.ant.beads && SH.ant.beads.map(([ox, oy, br]) => [ox / U, 0.12 - oy / U, br / U * 1.05]) };
      const [hx, hy, hs] = SH.heart;
      heartPos = { x: X3(hx), y: Y3(hy), z: fz(X3(hx), Y3(hy)) + 0.02, size: hs / 19 * 0.62 };
      tailStart = { x: X3(SH.tail[0]), y: Y3(SH.tail[1]) };
      p.pivotY = face.eyeY - 0.6;
      p.headGroup = new T.Group(); p.headGroup.position.set(0, p.pivotY, 0); root.add(p.headGroup);
    }
    const hg = p.headGroup, hy = y => y - p.pivotY;

    // Akku-Herz
    // Abstand zum Mund: das Herz darf den (gähnenden) Mund nie berühren → bei Bedarf tiefer und etwas kleiner
    {
      const mouthY = !SH.lumo && SH.face.my != null ? Y3(SH.face.my) : face.eyeY - face.eyeR - 0.03;
      const limit = mouthY - 0.08 - 0.14 * 1.05 - 0.1;           // Unterkante Gähn-Mund minus Luft
      const top = heartPos.y + heartPos.size * 0.5;
      if (!SH.lumo && top > limit) {
        // erst höchstens 0,1 tiefer (sonst rutscht es an die Bauchrundung), den Rest durch Verkleinern bei fester Unterkante
        const over = top - limit, down = Math.min(0.1, over);
        heartPos.y -= down;
        const rest = Math.min(over - down, heartPos.size * 0.35);
        if (rest > 0) { const bottom = heartPos.y - heartPos.size * 0.5; heartPos.size -= rest; heartPos.y = bottom + heartPos.size * 0.5; }
      }
    }
    // Als gewölbter Aufkleber: jeder Punkt folgt der Körperoberfläche (sonst steckt das Herz bei runden Figuren halb im Bauch)
    const bodyZ = SH.lumo ? (x, y) => 0.58 * Math.sqrt(Math.max(0, 1 - (x / 0.66) ** 2 - ((y - 0.66) / 0.62) ** 2)) : fz;
    heartPos.z = bodyZ(heartPos.x, heartPos.y) + 0.03;
    const hGeo = new T.PlaneGeometry(heartPos.size, heartPos.size, 24, 24), hp = hGeo.attributes.position;
    for (let i = 0; i < hp.count; i++) {
      const wx = heartPos.x + hp.getX(i), wy = heartPos.y + hp.getY(i);
      hp.setZ(i, Math.max(bodyZ(wx, wy), heartPos.z - 0.25) + 0.03 - heartPos.z);
    }
    hGeo.computeVertexNormals();
    p.heartMesh = new T.Mesh(hGeo, new T.MeshStandardMaterial({ map: heartTex, transparent: true, roughness: 0.3,
      emissive: 0xffffff, emissiveMap: heartTex, emissiveIntensity: 0.55,          // leuchtet selbst, auch an der Unterseite
      polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 }));
    p.heartMesh.renderOrder = 1;
    at(bg, p.heartMesh, heartPos.x, heartPos.y, heartPos.z);
    p.heartZ = heartPos.z;

    // Schwanz (normal / am Boden)
    const ts = tailStart;
    const upPts = ts ? [[ts.x, ts.y, -0.35], [ts.x + 0.55, ts.y - 0.15, -0.5], [ts.x + 0.88, ts.y + 0.02, -0.3], [ts.x + 0.95, ts.y + 0.28, -0.05]]
      : [[0.25, 0.4, -0.5], [0.8, 0.22, -0.6], [1.12, 0.32, -0.38], [1.2, 0.56, -0.1]];
    const downPts = ts ? [[ts.x, ts.y, -0.35], [ts.x + 0.55, 0.1, -0.45], [ts.x + 1.0, 0.07, -0.2], [ts.x + 1.12, 0.1, 0.05]]
      : [[0.25, 0.35, -0.5], [0.8, 0.08, -0.55], [1.25, 0.07, -0.25], [1.36, 0.1, 0.05]];
    const mkTail = arr => {
      const curve = new T.CatmullRomCurve3(arr.map(q => new T.Vector3(...q)));
      const m = new T.Mesh(new T.TubeGeometry(curve, 48, 0.055, 10, false), bodyMat);
      m.add(new T.Mesh(new T.TubeGeometry(curve, 48, 0.055 + LINE_W, 10, false), lineMat()));
      return m;
    };
    p.tailUp = mkTail(upPts); bg.add(p.tailUp);
    p.tailDown = mkTail(downPts); bg.add(p.tailDown);
    p.ballMat = phys(hexN(C.ant), { emissive: hexN(C.ant), emissiveIntensity: 0.3 });
    const endUp = upPts[3], endDown = downPts[3];
    p.ballUp = sphere(0.15, p.ballMat); at(bg, p.ballUp, endUp[0], endUp[1] + 0.02, endUp[2]); outline(p.ballUp);
    p.ballDown = sphere(0.15, p.ballMat); at(bg, p.ballDown, endDown[0], endDown[1] + 0.04, endDown[2]); outline(p.ballDown);

    // Antennen: je 3 Kugeln, Drehpunkt am Ansatz
    const mkAnt = sd => {
      const g = new T.Group(); g.position.set(sd * ant.x, hy(ant.y), 0); hg.add(g);
      const bd = ant.beads || [[0.04, 0.12, 0.13], [0.12, 0.38, 0.16], [0.24, 0.72, 0.22]];
      bd.forEach(([dx, dy, r], k) => {
        const m = sphere(r, phys(hexN(C.ant), { emissive: hexN(C.ant), emissiveIntensity: 0.3 }));
        at(g, m, sd * dx * ant.lean, dy, 0); outline(m);
        p.beads.push({ mesh: m, k });
      });
      return g;
    };
    p.antL = mkAnt(-1); p.antR = mkAnt(1);
    p.antL.userData.base = p.antL.position.clone(); p.antR.userData.base = p.antR.position.clone();
    p.lumo = !!SH.lumo;

    // Fest-Zubehör (für Werbe-Startbilder): auf dem Kopf zwischen den Antennen
    if (ACC) {
      const SC = { santa: 1.35, bunny: 1.5, crown: 1.85, flowers: 1.8, jester: 1.45, lantern: 1, beanie: 1.75, cap: 1.75, bow: 1.3, tophat: 1.45, wizard: 1.45, pirate: 1.45 };
      const g = new T.Group(); g.position.set(0, hy(ant.y) - 0.05, 0.02); g.scale.setScalar(SC[ACC] || 1.7); hg.add(g);
      const cone = (r, h, col) => { const m = new T.Mesh(new T.ConeGeometry(r, h, 40), phys(col)); return m; };
      const coneOutline = (m, r, h) => { const o = new T.Mesh(m.geometry, lineMat()); o.scale.set(1 + LINE_W / r, 1 + LINE_W / h, 1 + LINE_W / r); m.add(o); };
      if (ACC === "party") {
        const c = cone(0.3, 0.8, 0xffd54f); at(g, c, 0, 0.38, 0); c.rotation.z = -0.12; coneOutline(c, 0.3, 0.8);
        [0.16, 0.36, 0.56].forEach((y, i) => { const t = new T.Mesh(new T.TorusGeometry(0.3 * (1 - y / 0.8) + 0.01, 0.035, 10, 40), phys(i % 2 ? 0x4fc3f7 : 0xff4fa8));
          t.rotation.x = Math.PI / 2; t.position.set(-0.12 * (y), y, 0); g.add(t); });
        const b = sphere(0.1, phys(0xff4fa8)); at(g, b, -0.1, 0.8, 0); outline(b);
      } else if (ACC === "santa") {
        const c = cone(0.36, 0.78, 0xe53935); at(g, c, 0.05, 0.4, 0); c.rotation.z = -0.45; coneOutline(c, 0.36, 0.78);
        const brim = new T.Mesh(new T.TorusGeometry(0.36, 0.09, 16, 48), phys(0xffffff)); brim.rotation.x = Math.PI / 2; at(g, brim, 0, 0.06, 0);
        const b = sphere(0.12, phys(0xffffff)); at(g, b, 0.42, 0.66, 0); outline(b);
      } else if (ACC === "witch") {
        const brim = new T.Mesh(new T.CylinderGeometry(0.62, 0.62, 0.05, 48), phys(0x4a2c6e)); at(g, brim, 0, 0.04, 0);
        const c = cone(0.32, 0.9, 0x5e35b1); at(g, c, 0.03, 0.5, 0); c.rotation.z = -0.15; coneOutline(c, 0.32, 0.9);
        const band = new T.Mesh(new T.CylinderGeometry(0.3, 0.32, 0.1, 40), phys(0xff9800)); at(g, band, 0, 0.12, 0);
      } else if (ACC === "bunny") {
        // Hasenohren (Ostern): weiß, innen rosa, leicht nach außen geneigt
        [-1, 1].forEach(sd => {
          const ear = new T.Group(); ear.position.set(sd * 0.2, 0.02, 0); ear.rotation.z = -sd * 0.26; g.add(ear);
          const o = sphere(1, phys(0xffffff), 32); o.scale.set(0.12, 0.42, 0.08); at(ear, o, 0, 0.4, 0); outline(o);
          const i = sphere(1, phys(0xffa8c8, { roughness: 0.5 }), 24); i.scale.set(0.065, 0.32, 0.04); at(ear, i, 0, 0.4, 0.055);
        });
      } else if (ACC === "crown") {
        // Krone (Geburtstag): goldener Reif mit fünf Zacken, Edelsteine, Perlen an den Spitzen
        const gold = phys(0xffc107, { metalness: 0.55, roughness: 0.25, emissive: 0x7a5200, emissiveIntensity: 0.25 });
        const ring = new T.Mesh(new T.CylinderGeometry(0.32, 0.34, 0.2, 48, 1, true), gold); ring.material.side = T.DoubleSide; at(g, ring, 0, 0.1, 0);
        const rim = new T.Mesh(new T.TorusGeometry(0.335, 0.025, 8, 48), gold); rim.rotation.x = Math.PI / 2; at(g, rim, 0, 0.0, 0);
        for (let k = 0; k < 5; k++) {
          const a = k / 5 * Math.PI * 2 + Math.PI / 2, x = 0.32 * Math.cos(a), z = 0.32 * Math.sin(a), h = k === 0 ? 0.3 : 0.24;
          const sp = new T.Mesh(new T.ConeGeometry(0.075, h, 20), gold); at(g, sp, x, 0.2 + h / 2, z);
          const pearl = sphere(0.045, phys(0xfff59d)); at(g, pearl, x, 0.2 + h + 0.02, z); outline(pearl);
        }
        [[0, 0xe53935], [-0.65, 0x42a5f5], [0.65, 0x66bb6a]].forEach(([a, col]) => {
          const gem = sphere(0.055, phys(col, { roughness: 0.1 })); gem.scale.set(1, 1, 0.6);
          at(g, gem, 0.345 * Math.sin(a), 0.1, 0.345 * Math.cos(a)); gem.lookAt(0.9 * Math.sin(a), 0.1, 0.9 * Math.cos(a));
        });
      } else if (ACC === "flowers") {
        // Blumenkranz (Frühling, Muttertag): grüner Reif mit Blüten rundum
        const vine = new T.Mesh(new T.TorusGeometry(0.36, 0.04, 10, 56), phys(0x66bb6a, { roughness: 0.5 })); vine.rotation.x = Math.PI / 2; at(g, vine, 0, 0.04, 0);
        const cols = [0xff80ab, 0xffeb3b, 0xb388ff, 0xffffff, 0xff8a65, 0x81d4fa];
        cols.forEach((col, k) => {
          const a = k / cols.length * Math.PI * 2 + Math.PI / 2, x = 0.36 * Math.cos(a), z = 0.36 * Math.sin(a);
          const fl = new T.Group(); fl.position.set(x, 0.07, z); fl.lookAt(x * 3, 0.25, z * 3); g.add(fl);
          for (let i = 0; i < 5; i++) {
            const pa = i / 5 * Math.PI * 2, pt = sphere(0.055, phys(col, { roughness: 0.45 }), 16); pt.scale.set(1, 1, 0.45);
            at(fl, pt, 0.07 * Math.cos(pa), 0.07 * Math.sin(pa), 0);
          }
          const c = sphere(0.04, phys(k === 1 ? 0xff9800 : 0xffd54f), 16); at(fl, c, 0, 0, 0.02);
        });
      } else if (ACC === "jester") {
        // Narrenkappe (Fasching): zwei hängende Zipfel in Lila/Türkis mit goldenen Glöckchen
        const band = new T.Mesh(new T.CylinderGeometry(0.33, 0.35, 0.12, 40), phys(0xffd54f)); at(g, band, 0, 0.06, 0);
        [[-1, 0x7e57c2], [1, 0x26a69a]].forEach(([sd, col]) => {
          const curve = new T.CatmullRomCurve3([new T.Vector3(sd * 0.05, 0.12, 0), new T.Vector3(sd * 0.3, 0.42, 0), new T.Vector3(sd * 0.62, 0.42, 0.02), new T.Vector3(sd * 0.78, 0.18, 0.04)]);
          const N = 40, pos = [], idx = [];
          const geo = new T.TubeGeometry(curve, N, 0.16, 16, false);
          // Zipfel verjüngen: Radius entlang der Kurve von 0.2 auf 0.04
          const p3 = geo.attributes.position, v = new T.Vector3();
          for (let i = 0; i < p3.count; i++) {
            const seg = Math.floor(i / 17), u = seg / N, c = curve.getPointAt(Math.min(1, u));
            v.fromBufferAttribute(p3, i).sub(c).multiplyScalar((0.2 - 0.16 * u) / 0.16).add(c); p3.setXYZ(i, v.x, v.y, v.z);
          }
          geo.computeVertexNormals();
          const m = new T.Mesh(geo, phys(col)); g.add(m);
          const ol = new T.Mesh(geo, lineMat()); ol.scale.setScalar(1.03); m.add(ol);
          const bell = sphere(0.075, phys(0xffd54f, { metalness: 0.4, roughness: 0.25 })); at(g, bell, sd * 0.8, 0.12, 0.04); outline(bell);
        });
      } else if (ACC === "beanie") {
        // Wollmütze: Halbkugel mit Rippen, Umschlag, Bommel
        const dome = new T.Mesh(new T.SphereGeometry(0.34, 40, 20, 0, Math.PI * 2, 0, Math.PI / 2), phys(0xef5350, { roughness: 0.75, clearcoat: 0 }));
        dome.scale.set(1, 0.95, 1); at(g, dome, 0, 0.04, 0);
        const dOl = new T.Mesh(dome.geometry, lineMat()); dOl.scale.setScalar(1 + LINE_W / 0.34); dome.add(dOl);
        for (let k = 0; k < 12; k++) { const a = k / 12 * Math.PI * 2;
          const rib = new T.Mesh(new T.TorusGeometry(0.335, 0.012, 6, 24, Math.PI / 2), flat(0xc62828)); rib.rotation.set(0, -a, Math.PI / 2); rib.position.set(0, 0.04, 0); rib.scale.y = 0.95; g.add(rib); }
        const cuff = new T.Mesh(new T.CylinderGeometry(0.36, 0.36, 0.12, 40), phys(0xffcdd2, { roughness: 0.7, clearcoat: 0 })); at(g, cuff, 0, 0.05, 0);
        const pom = sphere(0.11, phys(0xffffff, { roughness: 0.9, clearcoat: 0 })); at(g, pom, 0, 0.4, 0); outline(pom);
      } else if (ACC === "cap") {
        // Kappe: Halbkugel, Schirm nach vorn, Knopf
        const dome = new T.Mesh(new T.SphereGeometry(0.32, 40, 20, 0, Math.PI * 2, 0, Math.PI / 2), phys(0x42a5f5)); dome.scale.set(1, 0.85, 1); at(g, dome, 0, 0.03, 0);
        const dOl = new T.Mesh(dome.geometry, lineMat()); dOl.scale.setScalar(1 + LINE_W / 0.32); dome.add(dOl);
        const brim = new T.Mesh(new T.CylinderGeometry(0.26, 0.26, 0.03, 40, 1, false, -Math.PI / 2, Math.PI), phys(0x1e88e5));
        brim.scale.set(1.15, 1, 1.25); brim.rotation.x = 0.12; at(g, brim, 0, 0.04, 0.22);
        const btn = sphere(0.04, phys(0x1e88e5)); at(g, btn, 0, 0.3, 0); outline(btn);
      } else if (ACC === "bow") {
        // Schleife: zwei Schlaufen und Knoten, seitlich oben am Kopf
        const B = new T.Group(); B.position.set(0.3, 0.02, 0.18); B.rotation.set(0.2, -0.4, -0.25); g.add(B);
        [-1, 1].forEach(sd => { const l = sphere(1, phys(0xff4fa8), 28); l.scale.set(0.2, 0.13, 0.08); at(B, l, sd * 0.17, 0, 0); l.rotation.z = sd * 0.25; outline(l); });
        const k = sphere(0.075, phys(0xff80c0)); at(B, k, 0, 0, 0.02); outline(k);
      } else if (ACC === "tophat") {
        // Zylinder: Krempe, hoher Hut, rotes Band
        const brim = new T.Mesh(new T.CylinderGeometry(0.46, 0.46, 0.04, 48), phys(0x263238)); at(g, brim, 0, 0.03, 0);
        const crown = new T.Mesh(new T.CylinderGeometry(0.27, 0.28, 0.56, 40), phys(0x37474f)); at(g, crown, 0, 0.32, 0);
        const cOl = new T.Mesh(crown.geometry, lineMat()); cOl.scale.set(1 + LINE_W / 0.28, 1 + LINE_W / 0.56, 1 + LINE_W / 0.28); crown.add(cOl);
        const band = new T.Mesh(new T.CylinderGeometry(0.285, 0.285, 0.09, 40), phys(0xe53935)); at(g, band, 0, 0.1, 0);
      } else if (ACC === "wizard") {
        // Zauberhut (Premium): blau, Sterne
        const brim = new T.Mesh(new T.CylinderGeometry(0.6, 0.6, 0.05, 48), phys(0x283593)); at(g, brim, 0, 0.04, 0);
        const c = cone(0.32, 0.95, 0x3949ab); at(g, c, -0.04, 0.52, 0); c.rotation.z = 0.18; coneOutline(c, 0.32, 0.95);
        [[0.05, 0.25, 0.27, 0.06], [-0.1, 0.5, 0.17, 0.05], [0.02, 0.72, 0.09, 0.04]].forEach(([x, y, z, r]) => {
          const st = new T.Mesh(new T.OctahedronGeometry(r), phys(0xffeb3b, { emissive: 0xffc107, emissiveIntensity: 0.4 })); at(g, st, x, y, z); });
      } else if (ACC === "pirate") {
        // Piratenhut (Premium): Dreispitz mit goldener Borte und Totenkopf
        const crown = sphere(1, phys(0x212121), 36); crown.scale.set(0.34, 0.26, 0.32); at(g, crown, 0, 0.08, 0); outline(crown);
        [0, 1, 2].forEach(k => { const a = k / 3 * Math.PI * 2 + Math.PI / 3;
          const flap = sphere(1, phys(0x212121), 28); flap.scale.set(0.32, 0.15, 0.06);
          flap.position.set(0.3 * Math.sin(a), 0.12, 0.3 * Math.cos(a)); flap.lookAt(0, 0.4, 0); g.add(flap); outline(flap); });
        const trim = new T.Mesh(new T.TorusGeometry(0.36, 0.022, 8, 48), phys(0xffc107, { metalness: 0.4 })); trim.rotation.x = Math.PI / 2; at(g, trim, 0, 0.06, 0);
        const skull = sphere(0.075, phys(0xffffff)); skull.scale.z = 0.5; at(g, skull, 0, 0.17, 0.33);
        [-1, 1].forEach(sd => { const e = sphere(0.018, flat(0x212121)); at(g, e, sd * 0.026, 0.18, 0.37); });
      } else if (ACC === "lantern") {
        // Laterne (St. Martin): an einem Stab neben der Figur, leuchtet warm
        const L = new T.Group(); L.position.set(1.18, 0, 0.2); root.add(L);
        const stick = new T.Mesh(new T.CylinderGeometry(0.03, 0.03, 2.2, 10), flat(0x6d4c41)); stick.rotation.z = 0.12; at(L, stick, 0.05, 1.2, 0);
        const hook = new T.Mesh(new T.CylinderGeometry(0.015, 0.015, 0.32, 8), flat(lineHex)); hook.rotation.z = -1.0; at(L, hook, -0.06, 2.22, 0);
        const lamp = new T.Group(); lamp.position.set(-0.18, 1.78, 0); L.add(lamp);
        const body = sphere(0.26, phys(0xffb300, { emissive: 0xff8f00, emissiveIntensity: 0.75, transparent: true, opacity: 0.95 }), 32);
        body.scale.set(1, 1.15, 1); lamp.add(body); outline(body);
        [-0.28, 0.28].forEach(y => { const cap = new T.Mesh(new T.CylinderGeometry(0.13, 0.15, 0.06, 24), flat(0xc62828)); at(lamp, cap, 0, y, 0); });
        const glow = new T.PointLight(0xffc04d, 0.9, 3); lamp.add(glow);
        const halo = sphere(0.42, new T.MeshBasicMaterial({ color: 0xffd54f, transparent: true, opacity: 0.18, depthWrite: false })); lamp.add(halo);
      }
    }

    // Augen (Textur auf einer Scheibe, folgt der Rundung)
    const eyeMat = new T.MeshBasicMaterial({ map: eyeTex, transparent: true, depthWrite: false });
    [-1, 1].forEach(sd => {
      const x = sd * face.eyeX, z = fz(x, face.eyeY);
      const m = new T.Mesh(new T.CircleGeometry(face.eyeR, 48), eyeMat);
      m.position.set(x, hy(face.eyeY), z + 0.06);
      m.rotation.y = sd * 0.8 * Math.atan2(face.eyeX, Math.max(z, 0.2));
      m.renderOrder = 2; hg.add(m); p.eyes.push(m);
    });
    // Brille (Kleiderschrank): vor den Augen, folgt der Gesichtsrundung
    if (GLS) {
      const GC = { round: [0x5d4037, 0xffffff, 0.15], sun: [0x1b1e4b, 0x263238, 0.92], heart: [0xd81b60, 0xff80ab, 0.4], star: [0xf9a825, 0xffeb3b, 0.35], monocle: [0xffb300, 0xffffff, 0.15] }[GLS] || [lineHex, 0xffffff, 0.15];
      const R = face.eyeR * 1.16, frameMat = phys(GC[0], { metalness: GLS === "monocle" || GLS === "star" ? 0.4 : 0 });
      const lensMat = new T.MeshBasicMaterial({ color: GC[1], transparent: true, opacity: GC[2], depthWrite: false, side: T.DoubleSide });
      const shapeOf = () => {                      // Umriss der Gläser (Einheitsgröße, mal R)
        if (GLS === "heart") return Array.from({ length: 40 }, (_, k) => { const t = k / 40 * Math.PI * 2;
          return [0.062 * (16 * Math.sin(t) ** 3), 0.062 * (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) + 0.1]; });
        if (GLS === "star") return Array.from({ length: 10 }, (_, k) => { const a = Math.PI / 2 + k * Math.PI / 5, r = k % 2 ? 0.55 : 1.22; return [r * Math.cos(a), r * Math.sin(a)]; });
        if (GLS === "sun") return Array.from({ length: 40 }, (_, k) => { const t = k / 40 * Math.PI * 2, c = Math.cos(t), s2 = Math.sin(t);
          return [1.08 * Math.sign(c) * Math.abs(c) ** 0.6, 0.82 * Math.sign(s2) * Math.abs(s2) ** 0.6 - 0.05]; });
        return Array.from({ length: 40 }, (_, k) => { const t = k / 40 * Math.PI * 2; return [Math.cos(t), Math.sin(t)]; });
      };
      const pts = shapeOf();
      const sides = GLS === "monocle" ? [1] : [-1, 1];
      sides.forEach(sd => {
        const x = sd * face.eyeX, z = fz(x, face.eyeY) + 0.12;
        const G = new T.Group(); G.position.set(x, hy(face.eyeY), z); G.rotation.y = sd * 0.8 * Math.atan2(face.eyeX, Math.max(z, 0.2)); hg.add(G);
        const curve = new T.CatmullRomCurve3(pts.map(([u, v]) => new T.Vector3(u * R, v * R, 0)), true, "catmullrom", GLS === "star" ? 0.02 : 0.5);
        const fr = new T.Mesh(new T.TubeGeometry(curve, 80, 0.034, 8, true), frameMat); fr.renderOrder = 3; G.add(fr);
        const sh = new T.Shape(pts.map(([u, v]) => new T.Vector2(u * R, v * R)));
        const lens = new T.Mesh(new T.ShapeGeometry(sh, 24), lensMat); lens.renderOrder = 3; G.add(lens);
        if (GLS === "sun") { const gl = new T.Mesh(new T.PlaneGeometry(R * 0.5, 0.04), new T.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.55 }));
          gl.rotation.z = 0.6; gl.position.set(-R * 0.3, R * 0.3, 0.01); G.add(gl); }
        // Bügel nach hinten
        const tx = sd * (face.eyeX + R * 1.02), tz = fz(tx, face.eyeY);
        const arm = new T.CatmullRomCurve3([new T.Vector3(sd * (face.eyeX + R), hy(face.eyeY) + 0.02, z - 0.02), new T.Vector3(tx + sd * 0.06, hy(face.eyeY) + 0.04, Math.max(0, tz - 0.1)), new T.Vector3(tx + sd * 0.02, hy(face.eyeY) + 0.04, Math.max(-0.2, tz - 0.4))]);
        if (GLS !== "monocle") hg.add(new T.Mesh(new T.TubeGeometry(arm, 16, 0.022, 6, false), frameMat));
        else { const ch = new T.CatmullRomCurve3([new T.Vector3(x + R * 0.3, hy(face.eyeY) - R * 0.95, z), new T.Vector3(x + R * 1.0, hy(face.eyeY) - R * 2.0, z - 0.05), new T.Vector3(x + R * 0.5, hy(face.eyeY) - R * 3.0, z - 0.15)]);
          hg.add(new T.Mesh(new T.TubeGeometry(ch, 20, 0.012, 6, false), frameMat)); }
      });
      if (sides.length === 2) {                    // Steg
        const zb = fz(0, face.eyeY + R * 0.2) + 0.12;
        const br = new T.CatmullRomCurve3([new T.Vector3(-face.eyeX + R * 0.85, hy(face.eyeY) + R * 0.15, zb - 0.02), new T.Vector3(0, hy(face.eyeY) + R * 0.35, zb + 0.02), new T.Vector3(face.eyeX - R * 0.85, hy(face.eyeY) + R * 0.15, zb - 0.02)]);
        hg.add(new T.Mesh(new T.TubeGeometry(br, 16, 0.026, 6, false), frameMat));
      }
    }
    // Brauen, Augenringe
    const browGeo = new T.TorusGeometry(0.12, 0.022, 6, 20, Math.PI * 0.7);
    const browMat = new T.MeshBasicMaterial({ color: lineHex });
    const ringMat = new T.MeshBasicMaterial({ color: hexN(C.lid), transparent: true, opacity: 0.45 });
    [-1, 1].forEach(sd => {
      const x = sd * face.eyeX, by = face.eyeY + face.eyeR + 0.06, z = fz(x, by) + 0.03;
      const n = new T.Mesh(browGeo, browMat); n.rotation.z = Math.PI * 0.15; at(hg, n, x, hy(by), z); p.brows.normal.push(n);
      const w = new T.Mesh(browGeo, browMat); w.rotation.z = Math.PI * 0.15 + (sd < 0 ? 0.4 : -0.4); at(hg, w, x, hy(by + 0.02), z); p.brows.worried.push(w);
      const ry = face.eyeY - face.eyeR - 0.02;
      const rg = sphere(0.1, ringMat); rg.scale.set(1.8 * face.eyeR / 0.34, 0.5, 0.3); at(hg, rg, x, hy(ry), fz(x, ry) + 0.03); p.brows.rings.push(rg);
    });
    // Münder (Gruppe am Mundpunkt)
    const mY = !SH.lumo && SH.face.my != null ? Y3(SH.face.my) : face.eyeY - face.eyeR - 0.03, mz = fz(0, mY) + 0.02;
    const mg = new T.Group(); mg.position.set(0, hy(mY), mz); hg.add(mg);
    const mLine = new T.MeshBasicMaterial({ color: lineHex });
    const mk = (geo, y, rz = 0) => { const m = new T.Mesh(geo, mLine); m.rotation.z = rz; m.position.y = y; mg.add(m); return m; };
    p.mouths.content = mk(new T.TorusGeometry(0.11, 0.024, 8, 24, Math.PI), 0, Math.PI);
    p.mouths.sick = mk(new T.TorusGeometry(0.1, 0.024, 8, 24, Math.PI), -0.1);
    p.mouths.tired = mk(new T.BoxGeometry(0.2, 0.034, 0.03), -0.05);
    p.mouths.hibernation = mk(new T.TorusGeometry(0.055, 0.02, 8, 16, Math.PI), -0.03, Math.PI);
    const openGroup = (y, sy) => {
      const g = new T.Group(); g.position.y = y; mg.add(g);
      const m = sphere(0.14, flat(lineHex, { roughness: 0.6 })); m.scale.set(1, sy, 0.35); g.add(m);
      const t = sphere(0.08, flat(hexN(C.tongue))); t.scale.set(1.1, 0.5, 0.3); t.position.set(0, -0.07, 0.03); g.add(t);
      return g;
    };
    p.mouths.radiant = openGroup(-0.05, 0.75);
    p.mouths.yawn = openGroup(-0.08, 1.05);
    const wave = new T.CatmullRomCurve3(Array.from({ length: 9 }, (_, k) => new T.Vector3(-0.16 + k * 0.04, (k % 2 ? 0.022 : -0.022), 0)));
    p.mouths.stress = mk(new T.TubeGeometry(wave, 40, 0.018, 6, false), -0.05);
    // Wangen
    const cheekMat = new T.MeshBasicMaterial({ color: hexN(C.cheek), transparent: true, opacity: 0.5 });
    [-1, 1].forEach(sd => {
      const x = sd * (face.eyeX + face.eyeR * 1.1), y = face.eyeY - face.eyeR * 1.1;   // neben/unter dem Auge, nicht darauf
      const c = sphere(0.1, cheekMat); c.scale.set(1.4, 0.75, 0.3); at(hg, c, x, hy(y), fz(x, y) + 0.02); p.cheeks.push(c);
    });
    // Schweißtropfen
    p.sweat = sphere(0.09, phys(hexN(C.sweat), { transparent: true, opacity: 0.9 })); p.sweat.scale.set(1, 1.5, 1);
    at(hg, p.sweat, face.eyeX + face.eyeR + 0.25, hy(face.eyeY + face.eyeR + 0.3), 0.45);
    p.zzBase = Math.max(face.eyeY + 0.9, 1.6);
    P = p;
  }

  /** 3D-Ei in den Farben der Figur: Körperfarbe, Tupfen in Antennenfarbe, Risse (0–4) vorne. */
  function buildEgg(cracks) {
    if (eggGroup) { scene.remove(eggGroup); eggGroup.traverse(n => { if (n.geometry) n.geometry.dispose(); }); }
    eggGroup = new T.Group(); scene.add(eggGroup);
    const H = 2.45, W = 0.92;
    const rOf = y => { const t = Math.min(1, Math.max(0, y / H)); const a = Math.acos(1 - 2 * t); return W * Math.sin(a) * (1.1 - 0.28 * t); };
    const pts = [];
    for (let i = 0; i <= 48; i++) { const t = i / 48, y = H * (1 - Math.cos(Math.PI * t)) / 2; pts.push(new T.Vector2(Math.max(0.001, rOf(y)), y)); }
    const geo = new T.LatheGeometry(pts, 72);
    const mat = phys(hexN(C.body), { emissive: hexN(C.body), emissiveIntensity: 0.05 });
    const egg = new T.Mesh(geo, mat); eggGroup.add(egg);
    const o = new T.Mesh(geo, lineMat()); o.scale.set(1 + LINE_W / W, 1 + LINE_W / H * 2, 1 + LINE_W / W); o.position.y = -LINE_W; egg.add(o);
    const spotMat = phys(hexN(C.ant), { emissive: hexN(C.ant), emissiveIntensity: 0.15 });
    const onSurf = (th, y, out = 0) => { const r = rOf(y) + out; return new T.Vector3(r * Math.sin(th), y, r * Math.cos(th)); };
    [[-0.5, 0.75, 0.2], [0.45, 1.3, 0.15], [0.15, 0.45, 0.17], [-0.25, 1.75, 0.1], [0.75, 0.6, 0.1], [-0.95, 1.2, 0.14], [1.2, 1.6, 0.12], [2.6, 1.0, 0.2], [-2.4, 0.7, 0.18], [3.1, 1.6, 0.14]]
      .forEach(([th, y, r]) => {
        const m = sphere(r, spotMat, 24); m.scale.set(1, 1, 0.28);
        m.position.copy(onSurf(th, y, -0.005)); m.lookAt(onSurf(th, y, 1)); eggGroup.add(m);
      });
    // Glanzlicht
    const gl = sphere(0.16, new T.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.35 }));
    gl.scale.set(0.8, 1.4, 0.2); gl.position.copy(onSurf(-0.55, 1.65, 0.01)); gl.lookAt(onSurf(-0.55, 1.65, 1)); eggGroup.add(gl);
    // Risse: Zickzack-Linien auf der Vorderseite (x = Winkel, y = Höhe)
    const C4 = [
      [[-0.18, 1.62], [-0.08, 1.48], [-0.2, 1.36], [-0.02, 1.22]],
      [[-0.02, 1.22], [0.14, 1.3], [0.24, 1.16], [0.42, 1.24]],
      [[-0.62, 1.12], [-0.46, 1.2], [-0.34, 1.06], [-0.2, 1.16], [-0.02, 1.22]],
      [[0.42, 1.24], [0.56, 1.08], [0.7, 1.2]]
    ];
    const crackMat = new T.MeshBasicMaterial({ color: lineHex });
    C4.slice(0, Math.max(0, Math.min(4, cracks))).forEach(line => {
      const curve = new T.CatmullRomCurve3(line.map(([th, y]) => onSurf(th, y, 0.012)), false, "catmullrom", 0.05);
      eggGroup.add(new T.Mesh(new T.TubeGeometry(curve, 40, 0.022, 6, false), crackMat));
    });
  }

  function build() {
    scene = new T.Scene();
    camera = new T.PerspectiveCamera(29, 1, 0.1, 50);
    // Bildausschnitt wie die 2D-Figur: Antennenspitze ca. 8 %, Füße ca. 92 % der Bühnenhöhe
    // gleicher Ausschnitt wie die 2D-viewBox (y ≈ -0.34 … 3.9), damit Antennen nicht abgeschnitten werden
    camera.position.set(0, 2.3, 8.3);
    camera.lookAt(0, 1.78, 0);
    scene.add(new T.HemisphereLight(0xffffff, 0x2a3466, 0.7));
    const key = new T.DirectionalLight(0xffffff, 0.95); key.position.set(2.5, 4, 5); scene.add(key);
    const rimP = new T.PointLight(0xff4fd8, 0.9, 10); rimP.position.set(-2.4, 2.4, -2); scene.add(rimP);
    const rimC = new T.PointLight(0x4fd9f7, 0.7, 10); rimC.position.set(2.4, 1, -2); scene.add(rimC);
    platformMat = new T.MeshStandardMaterial({ color: 0x1c9a9a, emissive: 0x1c9a9a, emissiveIntensity: 0.9, roughness: 0.4 });
    platform = new T.Mesh(new T.TorusGeometry(1.15, 0.035, 12, 80), platformMat);
    platform.rotation.x = Math.PI / 2; scene.add(platform);
    const disc = new T.Mesh(new T.CircleGeometry(1.15, 64), new T.MeshBasicMaterial({ color: 0x1c9a9a, transparent: true, opacity: 0.12 }));
    disc.rotation.x = -Math.PI / 2; disc.position.y = 0.002; scene.add(disc);
    platform.userData.disc = disc;
    platform.visible = disc.visible = platformOn;
    // Bodenschatten (nur mit Hintergrundbild, statt Leuchtring): weiche dunkle Ellipse unter den Füßen
    const sc = document.createElement("canvas"); sc.width = sc.height = 128;
    const sg = sc.getContext("2d"), grd = sg.createRadialGradient(64, 64, 4, 64, 64, 64);
    grd.addColorStop(0, "rgba(0,0,0,0.75)"); grd.addColorStop(0.55, "rgba(0,0,0,0.38)"); grd.addColorStop(1, "rgba(0,0,0,0)");
    sg.fillStyle = grd; sg.fillRect(0, 0, 128, 128);
    const shadow = new T.Mesh(new T.PlaneGeometry(2.0, 1.1), new T.MeshBasicMaterial({ map: new T.CanvasTexture(sc), transparent: true, depthWrite: false }));
    shadow.rotation.x = -Math.PI / 2; shadow.position.set(0, 0.004, 0.05); shadow.visible = !platformOn; scene.add(shadow);
    platform.userData.shadow = shadow;
    root = new T.Group(); scene.add(root);
    const ec = document.createElement("canvas"); ec.width = ec.height = 256;
    eyeCtx = ec.getContext("2d"); eyeTex = new T.CanvasTexture(ec);
    const hc = document.createElement("canvas"); hc.width = hc.height = 256;
    heartCtx = hc.getContext("2d"); heartTex = new T.CanvasTexture(hc);
    zz = textSprite(54, "#1b1e4b"); zz.scale.setScalar(0.7); setSpriteText(zz, "z Z"); scene.add(zz);
    badge = textSprite(80, "#000"); badge.scale.setScalar(0.6); badge.position.set(1.2, 2.95, 0.2); scene.add(badge);
    branchSprite = textSprite(70, "#000"); branchSprite.scale.setScalar(0.5); scene.add(branchSprite);
  }

  function resize() {
    const w = canvas.clientWidth || 260, h = canvas.clientHeight || 260;
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  }

  function frame(ms) {
    if (!running) return;
    if (pending) { const [F, SH, col] = pending; pending = null; C = col; buildFigure(SH); eggKey = ""; }
    if (EGG != null && C) {
      const k = EGG + "|" + C.body;
      if (k !== eggKey) { eggKey = k; buildEgg(EGG); }
      root.visible = false; zz.visible = badge.visible = branchSprite.visible = false;
      eggGroup.visible = true;
      const tt = ms / 1000;
      const sh = eggShake * Math.max(0, 1 - (performance.now() - eggShakeAt) / 500);   // klingt nach dem Antippen ab
      eggGroup.rotation.z = reduce ? 0 : 0.045 * Math.sin(tt * 2.4) + sh * 0.12 * Math.sin(tt * 38);
      eggGroup.rotation.y = reduce ? 0 : 0.15 * Math.sin(tt * 0.55);
      renderer.render(scene, camera);
      return;
    }
    if (eggGroup) eggGroup.visible = false;
    root.visible = true;
    if (!target || !P) return;
    const t = ms / 1000, p = target, ov = p.ov, Q = P;
    const yawn = ov.includes("yawning"), stress = ov.includes("stressed"), chubby = ov.includes("chubby"), slug = ov.includes("sluggish");
    const R = p.react || null;
    const HAPPY = ["giggle", "spin", "morning", "noon", "evening"].includes(R);
    const md = R === "giggle" || R === "spin" || R === "noon" ? "radiant" : R === "weak" || R === "evening" ? "content"
      : R === "startle" || R === "night" ? "tired" : p.mood;

    let open = 1;
    if (p.mood === "hibernation" && R !== "startle") open = 0; else if (yawn) open = 0.35; else if (md === "tired") open = 0.5; else if (slug) open = 0.6; else if (md === "sick") open = 0.7;
    if (HAPPY) open = 0.22; else if (R === "night") open = 0.45; else if (R === "surprise" || R === "startle") open = 1; else if (R === "sleepy") open = 0.45; else if (R === "dizzy") open = 0.6;
    if (!reduce && md !== "hibernation") {
      if (t > nextBlink) { blinkUntil = t + 0.13; nextBlink = t + 3 + Math.random() * 2.5; }
      if (t < blinkUntil) open = 0;
    }
    cur.open += (open - cur.open) * (open === 0 && t < blinkUntil ? 0.6 : 0.18);
    cur.chubby += ((chubby ? 1 : 0) - cur.chubby) * 0.1;
    cur.tilt += ((slug ? 0.13 : 0) - cur.tilt) * 0.08;
    const glow = p.energy == null ? 0 : p.energy / 100;
    cur.glow += (glow - cur.glow) * 0.08;

    // Körper
    const b = Q.torsoBase;
    if (Q.torso === undefined) return;
    Q.torso.scale.x = b.x * (1 + 0.3 * cur.chubby);
    Q.torso.scale.z = b.z * (1 + 0.18 * cur.chubby);
    const rr = reactType && !reduce ? (ms - reactStart) / reactDur : 2;
    const limb = rr >= 0 && rr < 1 && window.OSLimbs ? window.OSLimbs(reactType, rr) : null;
    Q.arms.forEach(a => {
      const u = a.userData.base, cx = u.x + u.spread * cur.chubby, cy = u.y - (slug ? 0.1 : 0);
      if (!limb) { a.position.x = cx; a.position.y = cy; a.rotation.z = u.rz0; return; }
      // Schulter = oberes Ende des Arms; um sie drehen (nach außen = von der Mitte weg)
      const sx = cx - u.L * Math.sin(u.rz0), sy = cy + u.L * Math.cos(u.rz0);
      const rz = u.rz0 + (u.side < 0 ? -1 : 1) * (u.side < 0 ? limb.l : limb.r) * Math.PI / 180;
      a.rotation.z = rz; a.position.x = sx + u.L * Math.sin(rz); a.position.y = sy - u.L * Math.cos(rz);
    });
    Q.feet.forEach(f => { f.m.position.y = f.y0 + (limb ? (f.side < 0 ? limb.fl : limb.fr) / U : 0); });
    Q.heartMesh.position.z = Q.heartZ + 0.1 * cur.chubby;
    const bodyHex = hexN(p.mood === "sick" ? C.bodySick : C.body);
    Q.bodyMat.color.setHex(bodyHex); Q.bodyMat.emissive.setHex(bodyHex);
    Q.bodyMat.emissiveIntensity = 0.02 + 0.12 * cur.glow;
    Q.tailUp.visible = Q.ballUp.visible = !slug; Q.tailDown.visible = Q.ballDown.visible = slug;
    Q.ballMat.emissiveIntensity = p.charging && !reduce ? 0.45 + 0.45 * Math.sin(t * 5) : (p.charging ? 0.8 : 0.25);

    // Gesicht
    Q.headGroup.rotation.z = cur.tilt;
    root.position.y = slug ? -0.05 : 0;
    const lid = open > 0.08 && open < 0.95 ? C.lid : (md === "sick" ? C.bodySick : C.body);
    const oq = Math.round(cur.open * 20) / 20;
    const eyeKey = oq + "|" + lid + "|" + (md === "radiant") + "|" + C.iris;
    if (eyeKey !== lastEye) { lastEye = eyeKey; drawEye(oq, lid, md === "radiant"); }
    const worried = stress || md === "sick";
    Q.brows.normal.forEach(x => x.visible = !worried && md !== "hibernation");
    Q.brows.worried.forEach(x => x.visible = worried);
    Q.brows.rings.forEach(x => x.visible = stress);
    const mouthKey = R === "surprise" || R === "startle" || R === "sleepy" || R === "morning" ? "yawn" : R === "dizzy" ? "stress"
      : R === "night" ? "hibernation"
      : R ? md : yawn ? "yawn" : stress ? "stress" : md;
    Object.entries(Q.mouths).forEach(([key, m]) => { m.visible = key === mouthKey; });
    const cheekOp = (R && R !== "dizzy") || p.petted ? 0.95 : md === "radiant" ? 0.75 : md === "content" ? 0.5 : 0.3;
    Q.cheeks.forEach(c => c.material.opacity = cheekOp);

    // Antennen = Ruhe
    const lit = p.rest == null ? 0 : p.rest >= 66 ? 3 : p.rest >= 33 ? 2 : 1;
    const on = hexN(C.ant), off = hexN(C.off);
    Q.beads.forEach(({ mesh, k: idx }) => {
      const isOn = idx < lit;
      mesh.material.color.setHex(isOn ? on : off);
      mesh.material.emissive.setHex(isOn ? on : 0x000000);
      mesh.material.emissiveIntensity = isOn ? 0.12 + 0.2 * cur.glow : 0;
      mesh.material.roughness = isOn ? 0.28 : 0.65;
    });
    const wob = stress && !reduce ? 0.09 * Math.sin(t * 14) : 0;
    Q.antL.rotation.z = 0.08 + wob; Q.antR.rotation.z = -0.08 - wob;

    // Herz = Energie
    const hf = Math.round(p.energy == null ? 0 : p.energy);
    if (hf !== lastHeart) { lastHeart = hf; drawHeart(hf / 100); }

    // Zusätze
    Q.sweat.visible = stress;
    zz.visible = yawn || md === "hibernation";
    const LZ = Array.isArray(STAGE) ? STAGE : LOOK[STAGE];              // „z Z“ wächst mit der Figur mit
    zz.position.set(-1.25 * (LZ ? LZ[0] : 1), Q.zzBase * (LZ ? LZ[1] : 1) + (zz.visible && !reduce ? 0.08 * Math.sin(t * 2.5) : 0), 0.3);
    const bText = p.form ? ({ chatterling: "💬", bingeon: "📺", pixelo: "🎮", zenfuchs: "🦊", wandero: "🍃" }[p.form]) : "";
    if (bText !== lastBadge) { lastBadge = bText; setSpriteText(badge, bText); }
    badge.visible = !!bText;
    const brText = p.branch === "radiant" ? "✨" : p.branch === "wild" ? "🍂" : "";
    if (brText !== lastBranch) { lastBranch = brText; setSpriteText(branchSprite, brText); }
    branchSprite.visible = !!brText;
    branchSprite.position.set(p.branch === "wild" ? -0.95 : -1.15, p.branch === "wild" ? 0.25 : 2.1, 0.4);

    // Leben: Atmen, leichtes Pendeln, Drehen per Finger
    const slow = slug || md === "sick" || md === "hibernation";
    const br = reduce ? 0 : Math.sin(t * (slow ? 1.7 : 2.8));
    Q.bodyGroup.scale.set(1 - 0.012 * br, 1 + 0.018 * br, 1);
    Q.headGroup.position.y = Q.pivotY + 0.02 * br;
    if (!dragging) rotY += (0 - rotY) * 0.06;
    root.rotation.y = rotY + (reduce ? 0 : 0.1 * Math.sin(t * 0.55));
    // Reaktion auf Antippen (Hüpfen, Drehen, Wackeln); Schatten bleibt am Boden
    root.rotation.z = 0; root.position.x = 0; root.scale.set(1, 1, 1);
    const ru = reactType && !reduce ? (ms - reactStart) / reactDur : 2;
    if (ru >= 0 && ru < 1) {
      const ease = u => u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
      if (reactType === "giggle") root.position.y += Math.abs(Math.sin(ru * Math.PI * 2)) * 0.32 * (1 - ru * 0.5);
      else if (reactType === "surprise") root.position.y += Math.sin(ru * Math.PI) * 0.45;
      else if (reactType === "spin") { root.rotation.y += ease(ru) * Math.PI * 2; root.position.y += Math.sin(ru * Math.PI) * 0.3; }
      else if (reactType === "dizzy") root.rotation.z = 0.28 * Math.sin(ru * Math.PI * 6) * (1 - ru);
      else if (reactType === "sleepy" || reactType === "night") root.scale.set(1 + 0.04 * Math.sin(ru * Math.PI), 1 - 0.07 * Math.sin(ru * Math.PI), 1);
      else if (reactType === "morning") root.scale.set(1 - 0.04 * Math.sin(ru * Math.PI), 1 + 0.12 * Math.sin(ru * Math.PI), 1);   // Strecken
      else if (reactType === "noon") root.position.y += Math.abs(Math.sin(ru * Math.PI * 2)) * 0.2;
      else if (reactType === "evening") root.rotation.z = 0.12 * Math.sin(ru * Math.PI * 2) * (1 - ru * 0.3);   // sanftes Wiegen
      else root.position.x = 0.05 * Math.sin(ru * Math.PI * 16) * (1 - ru);
    }
    // Wachstum: kleiner und gedrungener, Kopf/Gesicht größer, Antennen kürzer
    const L = Array.isArray(STAGE) ? STAGE : LOOK[STAGE];
    if (L) {
      const [sx, sy, fs, as, hs] = L;
      root.scale.x *= sx; root.scale.y *= sy; root.scale.z *= sx;
      if (Q.lumo) {
        Q.headGroup.scale.setScalar(hs);
        [Q.antL, Q.antR].forEach(a => a.scale.setScalar(as / hs * 1.05));
      } else {
        Q.headGroup.scale.set(fs, fs, 1);                // Gesicht größer, bleibt auf der Körperoberfläche
        [Q.antL, Q.antR].forEach(a => {                  // Antennenansatz bleibt auf dem Kopf, Antennen kürzer
          const b = a.userData.base; a.position.set(b.x / fs, b.y / fs, b.z); a.scale.set(as / fs, as / fs, as);
        });
      }
    } else {
      Q.headGroup.scale.setScalar(1);
      [Q.antL, Q.antR].forEach(a => { const b = a.userData.base; a.position.copy(b); a.scale.setScalar(1); });
    }
    renderer.render(scene, camera);
  }

  return {
    /** Wachstumsstufe: "baby" | "child" | "teen" | "adult"/null */
    setStage(st) { STAGE = st || null; },
    /** Ei zeigen (Anzahl Risse 0–4) oder null = Figur. shake 0–1 = Wackeln nach dem Antippen */
    setEgg(cracks, shake) { EGG = cracks == null ? null : cracks; if (shake) { eggShake = shake; eggShakeAt = performance.now(); } },
    /** true, wenn WebGL verfügbar ist */
    init(cv) {
      try {
        T = window.THREE; if (!T) return false;
        canvas = cv;
        renderer = new T.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
        renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
        // Farben 1:1 wie im Figurenblatt (ohne sRGB-Umrechnung, die sie blass macht)
        build(); resize();
        window.addEventListener("resize", resize);
        cv.addEventListener("pointerdown", e => { dragging = true; lastX = e.clientX; cv.setPointerCapture(e.pointerId); });
        cv.addEventListener("pointermove", e => { if (!dragging) return; rotY = Math.max(-1.2, Math.min(1.2, rotY + (e.clientX - lastX) * 0.012)); lastX = e.clientX; });
        const end = () => { dragging = false; };
        cv.addEventListener("pointerup", end); cv.addEventListener("pointercancel", end);
        return true;
      } catch (e) { return false; }
    },
    /** Figur wechseln (wird beim nächsten Bild neu aufgebaut). colors: wie figColors() plus irisDark. */
    setFigure(F, SH, colors, acc, glasses) { ACC = acc || null; GLS = glasses || null; pending = [F, SH, colors]; },
    /** Reaktion auf Antippen: Bewegung im 3D-Raum (Gesicht kommt über update(p.react)) */
    react(type, dur) { reactType = type; reactDur = dur; reactStart = performance.now(); },
    /** Leuchtring + Bodenscheibe ein/aus (aus, wenn ein Hintergrundbild eine eigene Plattform zeigt) */
    setPlatform(on) { platformOn = on; if (platform) { platform.visible = on; platform.userData.disc.visible = on; platform.userData.shadow.visible = !on; } },
    update(p, moodColor) {
      target = p;
      if (platformMat) {
        const c = new T.Color(moodColor);
        platformMat.color.copy(c); platformMat.emissive.copy(c);
        platform.userData.disc.material.color.copy(c);
      }
    },
    start() {
      if (running || !renderer) return;
      running = true; resize();
      const tick = ms => { if (!running) return; frame(ms); requestAnimationFrame(tick); };   // Dauerschleife (App)
      requestAnimationFrame(tick);
    },
    stop() { running = false; },
    /** Film: feste Größe und Bild für Zeitpunkt ms rendern (Reaktion mit Fortschritt ru) */
    setSize(px) { renderer.setPixelRatio(1); renderer.setSize(px, px, false); camera.aspect = 1; camera.updateProjectionMatrix(); },
    renderAt(ms, rType, ru) {
      if (rType && ru >= 0 && ru < 1) { reactType = rType; reactDur = 1000; reactStart = ms - ru * 1000; } else reactType = null;
      nextBlink = 1e9; running = true; frame(ms); running = false;
    },
  };
})();
