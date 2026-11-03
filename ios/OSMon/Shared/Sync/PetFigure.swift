// PetFigure.swift: Figuren-Katalog, Auswahl und Zeichnung des Wesens (iOS 17+)
//
// Targets: App UND Widget-Extension (zusammen mit PetFigureData.swift, PetCore.swift, PetSync.swift,
// ScreenTimeShared.swift).
//
// Die Zeichnung ist eine 1:1-Übertragung von lumoSVG() aus dem Prototyp: gleiche Zeichenfläche
// (viewBox -21.5 -31.8 263 263), gleiche Bausteine, gleiche Reihenfolge, gleiche Farben.
// Die Daten (Formen, Farben) erzeugt gen_figures.mjs aus dem Prototyp in PetFigureData.swift.
//
// Bedeutung der Bausteine (für alle Figuren gleich):
//   Herz-Füllung = Energie (Akku) · leuchtende Antennenkugeln = Ruhe (0–3) · Augen/Mund = Stimmung
//   Overlays: gähnt (Augen halb zu, offener Mund, "z Z"), gestresst (Augenringe, Wellenmund, Schweiß,
//   wackelnde Antennen), pummelig (breiter), träge (Kopf geneigt, Schwanz am Boden).
//
// Auswahl: App-Group-Schlüssel "pet.figure" (STShared.defaults), damit App und Widget dieselbe Figur zeigen.

import SwiftUI
import WidgetKit

// MARK: - Modell

struct PetFigure: Identifiable, Hashable {
    struct Colors {
        let body, bodySick, ant, off, heart, iris, lid, line, cheek, tongue, sweat: UInt32
    }
    struct Head { let cx, cy, rx, ry: CGFloat }
    struct Face { let ey, dx, r, my: CGFloat }
    struct Bead { let ox, oy, r: CGFloat }
    struct Antenna { let dx, y, lean: CGFloat; let beads: [Bead] }
    struct HeartSpot { let x, y, size: CGFloat }
    struct Arm { let cx, cy, rx, ry, rot: CGFloat }
    struct Foot { let x, y, w, h: CGFloat }
    struct Glint { let x, y, rot: CGFloat }
    struct FigureShape {
        let lumo: Bool            // Lumo-Bauweise: Kopf über dem Rumpf (Kopf neigt sich mit)
        let path: String          // Körper-Silhouette (SVG-Pfad)
        let head: Head?
        let face: Face
        let ant: Antenna
        let heart: HeartSpot
        let tail: CGPoint
        let arms: [Arm]
        let feet: [Foot]
        let glint: Glint
        let sweat: CGPoint
        let zz: CGPoint
    }

    let id: String
    let name: String
    let colors: Colors
    let shape: FigureShape

    static func == (a: PetFigure, b: PetFigure) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }

    static let defaultID = "lumo"
    static func byID(_ id: String?) -> PetFigure { all.first { $0.id == id } ?? all[0] }
}

// MARK: - Auswahl (App + Widget)

enum PetFigureStore {
    static let key = "pet.figure"

    static var current: PetFigure { PetFigure.byID(STShared.defaults.string(forKey: key)) }

    static func select(_ id: String) {
        guard PetFigure.all.contains(where: { $0.id == id }) else { return }
        STShared.defaults.set(id, forKey: key)
        WidgetCenter.shared.reloadTimelines(ofKind: STShared.widgetKind)
    }
}

// MARK: - Zustand, der gezeichnet wird

struct PetPose {
    var mood: PetMood
    var overlays: Set<PetOverlay> = []
    var form: PetForm? = nil
    var branch: PetBranch? = nil
    var energy: Float? = 100          // Herz-Füllung
    var rest: Float? = 100            // leuchtende Antennenkugeln
    var charging = false              // Schwanzkugel pulsiert
    var petted = false                // Wangen leuchten
    var reaction: ReactionKind? = nil  // Antippen: Gesicht, Arme/Beine, Körperbewegung
    var reactU: Double = -1            // Fortschritt 0..1 (außerhalb = keine Reaktion)
    var accessory: Accessory? = nil    // Kopf: Fest-Zubehör oder Mütze aus dem Kleiderschrank, PetAccessory.swift
    var stage: GrowthStage? = nil      // Wachstumsstufe (Baby … Erwachsen); nil = erwachsen. Ei: drawEgg()
    var glasses: Glasses? = nil        // Brille aus dem Kleiderschrank

    init(mood: PetMood, overlays: Set<PetOverlay> = [], form: PetForm? = nil, branch: PetBranch? = nil,
         energy: Float? = 100, rest: Float? = 100, charging: Bool = false, petted: Bool = false,
         reaction: ReactionKind? = nil, reactU: Double = -1, accessory: Accessory? = nil) {
        self.mood = mood; self.overlays = overlays; self.form = form; self.branch = branch
        self.energy = energy; self.rest = rest; self.charging = charging; self.petted = petted
        self.reaction = reaction; self.reactU = reactU; self.accessory = accessory
    }

    init(snapshot s: PetSnapshot, evolution: PetEvolution? = nil, charging: Bool = false, petted: Bool = false) {
        self.init(mood: s.mood, overlays: s.overlays, form: evolution?.form, branch: evolution?.branch,
                  energy: s.stats.e, rest: s.stats.r, charging: charging, petted: petted)
    }

    /// Für Auswahl-Kacheln und Vorschauen.
    static let preview = PetPose(mood: .content, energy: 80, rest: 80)
}

// MARK: - Views

/// Das Wesen in der gewählten (oder einer festen) Figur. Quadratisch; Größe setzt der Aufrufer.
struct PetFigureView: View {
    var figure: PetFigure? = nil                 // nil = aktuelle Auswahl (reagiert auf Änderungen)
    let pose: PetPose
    var animated = true                          // im Widget immer false
    var reaction: ActiveReaction? = nil          // Antippen-Reaktion (Gesicht, Arme/Beine, Bewegung)

    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let f = figure ?? PetFigure.byID(figureID)
        Group {
            if animated && !reduceMotion {
                TimelineView(.animation(minimumInterval: 1.0 / 30)) { ctx in
                    canvas(f, t: ctx.date.timeIntervalSinceReferenceDate, now: ctx.date)
                }
            } else {
                canvas(f, t: nil, now: .now)         // bei reduzierter Bewegung: nur Gesicht, keine Bewegung
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func canvas(_ f: PetFigure, t: Double?, now: Date) -> some View {
        var p = pose
        if let r = reaction {
            p.reaction = r.kind
            // Ohne Animation (Bewegung reduzieren): u ≈ 0 -> nur das Gesicht, Arme/Beine/Körper in Ruhe
            p.reactU = t == nil ? (r.isActive(now) ? 0.001 : -1) : r.progress(now)
        }
        return Canvas { context, size in
            PetFigureRenderer.draw(context, size: size, figure: f, pose: p, t: t)
        }
    }
}

/// Auswahl-Raster für die Einstellungen (und das Onboarding).
struct PetFigurePicker: View {
    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID

    private let columns = [GridItem(.adaptive(minimum: 78), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(PetFigure.all) { f in
                let selected = f.id == figureID
                Button {
                    PetFigureStore.select(f.id)
                    figureID = f.id
                } label: {
                    VStack(spacing: 2) {
                        PetFigureView(figure: f, pose: .preview, animated: false).frame(width: 64, height: 64)
                        Text(f.name).font(.caption).lineLimit(1)
                    }
                    .padding(6)
                    .frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 12)
                        .fill(selected ? Color.accentColor.opacity(0.15) : Color.clear))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(selected ? Color.accentColor : Color.secondary.opacity(0.25), lineWidth: selected ? 2 : 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(f.name)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

/// Das Ei vor dem Schlüpfen (schaukelt leicht; shakeAt = Zeitpunkt des letzten Antippens → kräftiges Wackeln).
struct PetEggView: View {
    var figure: PetFigure? = nil
    let cracks: Int
    var shakeAt: Date? = nil
    var animated = true
    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let f = figure ?? PetFigure.byID(figureID)
        Group {
            if animated && !reduceMotion {
                TimelineView(.animation(minimumInterval: 1.0 / 30)) { ctx in
                    let since = shakeAt.map { ctx.date.timeIntervalSince($0) } ?? 99
                    Canvas { g, size in
                        PetFigureRenderer.drawEgg(g, size: size, figure: f, cracks: cracks,
                                                  t: ctx.date.timeIntervalSinceReferenceDate, shake: max(0, 1 - since / 0.5))
                    }
                }
            } else {
                Canvas { g, size in PetFigureRenderer.drawEgg(g, size: size, figure: f, cracks: cracks, t: nil) }
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

// MARK: - Zeichnung (Port von lumoSVG)

enum PetFigureRenderer {
    static let viewBox = CGRect(x: -21.5, y: -31.8, width: 263, height: 263)
    private static let heartD = "M0 -0.35 C -0.05 -0.78 -0.78 -0.82 -0.92 -0.3 C -1.02 0.06 -0.62 0.46 0 0.9 C 0.62 0.46 1.02 0.06 0.92 -0.3 C 0.78 -0.82 0.05 -0.78 0 -0.35 Z"
    private static let white = Color.white

    /// t = Zeit in Sekunden für Atmen/Blinzeln/Wackeln; nil = Standbild (Widget, reduzierte Bewegung).
    static func draw(_ ctx: GraphicsContext, size: CGSize, figure F: PetFigure, pose p: PetPose, t: Double?) {
        var g = ctx
        let s = min(size.width, size.height) / viewBox.width
        g.translateBy(x: (size.width - viewBox.width * s) / 2, y: (size.height - viewBox.height * s) / 2)
        g.scaleBy(x: s, y: s)
        g.translateBy(x: -viewBox.minX, y: -viewBox.minY)

        let S = F.shape, C = F.colors
        let md = p.mood, ov = p.overlays
        let yawn = ov.contains(.yawning), stress = ov.contains(.stressed)
        let chubby = ov.contains(.chubby), slug = ov.contains(.sluggish)
        let body = col(md == .sick ? C.bodySick : C.body), line = col(C.line)
        let lit: Int = p.rest.map { $0 >= 66 ? 3 : $0 >= 33 ? 2 : 1 } ?? 0
        let level = CGFloat(max(0, min(100, p.energy ?? 0)) / 100)      // Herz-Füllung
        let sw: CGFloat = 3, cx: CGFloat = 110
        let ey = S.face.ey, dx = S.face.dx, r = S.face.r, my = S.face.my
        let slow = slug || md == .sick || md == .hibernation
        let R: ReactionKind? = (p.reactU >= 0 && p.reactU < 1) ? p.reaction : nil
        let limbs = R.map { ReactionMotion.limbs($0, CGFloat(p.reactU)) }
        if let R {                                        // Reaktion bewegt die ganze Figur (Drehpunkt Mitte unten)
            let bm = ReactionMotion.body(R, CGFloat(p.reactU))
            g.translateBy(x: 110 + bm.dx, y: 210 + bm.dy)
            g.rotate(by: .degrees(Double(bm.rot)))
            g.scaleBy(x: bm.sx, y: bm.sy)
            g.translateBy(x: -110, y: -210)
        }
        // Wachstum: kleiner und gedrungener, Kopf/Gesicht größer, Antennen kürzer (um die Füße skaliert)
        let L = Growth.look(p.stage)
        if L.sx != 1 || L.sy != 1 { g.concatenate(scale(L.sx, L.sy, about: CGPoint(x: 110, y: 212))) }

        // Animation (entspricht den CSS-Keyframes im Prototyp)
        var breathe: CGFloat = 0, blink: CGFloat = 0, wobble: CGFloat = 0, pulse: Double = 1, zzOff: CGFloat = 0
        if let t {
            let period = slow ? 3.6 : 2.2
            breathe = 0.02 * CGFloat(1 - cos(2 * .pi * t / period)) / 2
            let ph = t.truncatingRemainder(dividingBy: 4.2) / 4.2
            blink = CGFloat(ph < 0.96 ? 0 : ph < 0.975 ? (ph - 0.96) / 0.015 : (1 - ph) / 0.025)
            if stress { wobble = -4 * CGFloat(cos(2 * .pi * t / 0.5)) }
            if p.charging { pulse = 0.725 + 0.275 * cos(2 * .pi * t / 1.2) }
            zzOff = -4 * CGFloat(1 - cos(2 * .pi * t / 2)) / 2
        }

        let tilt: CGAffineTransform = slug ? rotate(-7, about: CGPoint(x: 110, y: ey + 50)) : .identity

        // ---- Bausteine
        func tail(_ g: GraphicsContext) {
            let tx = S.tail.x, ty = S.tail.y
            var path = Path()
            if slug {
                path.move(to: CGPoint(x: tx, y: ty + 8))
                path.addQuadCurve(to: CGPoint(x: tx + 58, y: min(ty + 22, 212)), control: CGPoint(x: tx + 30, y: ty + 24))
            } else {
                path.move(to: CGPoint(x: tx, y: ty))
                path.addQuadCurve(to: CGPoint(x: tx + 48, y: ty - 12), control: CGPoint(x: tx + 36, y: ty + 12))
            }
            g.stroke(path, with: .color(line), style: StrokeStyle(lineWidth: 10, lineCap: .round))
            g.stroke(path, with: .color(body), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            let bx = slug ? tx + 62 : tx + 52, by = slug ? min(ty + 20, 210) : ty - 18
            var b = g; b.opacity = pulse
            filled(b, circle(bx, by, 10), col(C.ant), stroke: line, sw)
            var gl = g; gl.opacity = 0.8
            gl.fill(circle(bx - 3, by - 4, 2.5), with: .color(white))
        }

        func feet(_ g: GraphicsContext) {
            for f in S.feet {
                let lift = limbs.map { f.x + f.w / 2 < 110 ? $0.fl : $0.fr } ?? 0
                let rect = CGRect(x: f.x, y: f.y - lift, width: f.w, height: f.h)
                filled(g, Path(roundedRect: rect, cornerRadius: min(8, f.h / 2)), body, stroke: line, sw)
            }
        }

        func antennas(_ g: GraphicsContext) {
            let A = S.ant
            for sd in [-1.0, 1.0] as [CGFloat] {
                var a = g
                if wobble != 0 { a.concatenate(rotate(wobble, about: CGPoint(x: 110 + sd * A.dx, y: A.y + 10))) }
                for (k, bd) in A.beads.enumerated() {
                    let on = k < lit
                    let bx = 110 + sd * (A.dx + bd.ox * A.lean), by = A.y + bd.oy
                    filled(a, circle(bx, by, bd.r), col(on ? C.ant : C.off), stroke: line, sw)
                    if on {
                        var gl = a; gl.opacity = 0.8
                        gl.fill(circle(bx - bd.r * 0.35, by - bd.r * 0.4, bd.r * 0.28), with: .color(white))
                    }
                }
            }
        }

        func bodyParts(_ g: GraphicsContext) {
            var b = g
            if chubby { b.concatenate(scale(1.18, 1, about: CGPoint(x: 110, y: 0))) }
            filled(b, SVGPath.parse(S.path), body, stroke: line, sw)
            for a in S.arms {
                let cy = a.cy + (slug ? 6 : 0)
                var e = ellipse(a.cx, cy, a.rx, a.ry)
                    .applying(rotate(a.rot, about: CGPoint(x: a.cx, y: a.cy)))
                if let limbs {                                // Arm um die Schulter (oberes Ende) drehen
                    let rad = a.rot * .pi / 180
                    let shoulder = CGPoint(x: a.cx + a.ry * sin(rad), y: cy - a.ry * cos(rad))
                    e = e.applying(rotate(a.cx < 110 ? limbs.l : -limbs.r, about: shoulder))
                }
                filled(b, e, body, stroke: line, sw)
            }
        }

        func head(_ g: GraphicsContext) {
            guard let h = S.head else { return }
            filled(g, ellipse(h.cx, h.cy, h.rx, h.ry), body, stroke: line, sw)
        }

        func glint(_ g: GraphicsContext) {
            let G = S.glint
            var gl = g; gl.opacity = 0.45
            gl.fill(ellipse(G.x, G.y, max(9, r * 0.7), max(5, r * 0.38)).applying(rotate(G.rot, about: CGPoint(x: G.x, y: G.y))),
                    with: .color(white))
        }

        // Herz mit Abstand zum Mund (auch zum gähnenden, auch wenn Kopf/Gesicht beim Wachsen größer sind):
        // erst höchstens 10 tiefer, den Rest durch Verkleinern bei fester Unterkante. Gleiche Regel wie PetFigureCore.kt.
        let heartXYS: (x: CGFloat, y: CGFloat, s: CGFloat) = {
            let H = S.heart
            var mb = ey + (my + 18 - ey) * L.face
            if S.lumo { let hb = S.head.map { $0.cy + $0.ry } ?? (ey + 40); mb = hb + (mb - hb) * L.head }
            let need = mb + 3 - (H.y - 0.82 * H.size)
            guard need > 0 else { return (H.x, H.y, H.size) }
            let down = min(10, need)
            var y = H.y + down, s = H.size
            if need > down { let bottom = y + 0.9 * s; s = max(H.size * 0.55, (bottom - (mb + 3)) / 1.72); y = bottom - 0.9 * s }
            return (H.x, y, s)
        }()

        func heart(_ g: GraphicsContext) {
            let H = heartXYS
            var h = g
            h.translateBy(x: H.x, y: H.y)
            h.scaleBy(x: H.s, y: H.s)
            let hp = SVGPath.parse(heartD)
            h.fill(hp, with: .color(line))
            var c = h; c.clip(to: hp)
            let top = 0.9 - level * 1.72
            c.fill(Path(CGRect(x: -1.1, y: top, width: 2.2, height: 0.9 - top + 0.05)), with: .color(col(C.heart)))
            var bolt = Path()
            bolt.addLines([CGPoint(x: 0.12, y: -0.48), CGPoint(x: -0.26, y: 0.08), CGPoint(x: -0.02, y: 0.08),
                           CGPoint(x: -0.14, y: 0.56), CGPoint(x: 0.26, y: -0.04), CGPoint(x: 0.02, y: -0.04)])
            bolt.closeSubpath()
            h.fill(bolt, with: .color(white))
            h.stroke(hp, with: .color(line), lineWidth: sw / H.s)
        }

        func face(_ g: GraphicsContext) {
            // Wangen
            var ck = g
            ck.opacity = p.petted || (R != nil && R != .dizzy) ? 0.95 : md == .radiant ? 0.75 : md == .content ? 0.5 : 0.3
            for sd in [-1.0, 1.0] as [CGFloat] {
                ck.fill(ellipse(cx + sd * (dx + r + 1), ey + r + 1, r * 0.43, r * 0.24), with: .color(col(C.cheek)))
            }
            // Augen
            var open: CGFloat = yawn ? 0.35 : md == .tired ? 0.5 : slug ? 0.6 : md == .sick ? 0.7 : 1
            switch R {
            case .surprise?, .startle?: open = 1
            case .sleepy?: open = 0.75
            case .weak?: open = 0.7
            case .night?: open = 0.45
            default: break
            }
            for ex in [cx - dx, cx + dx] {
                if R?.happy == true {                         // fröhlich zugekniffen: ^ ^
                    var arc = Path()
                    arc.move(to: CGPoint(x: ex - r * 0.72, y: ey + r * 0.25))
                    arc.addQuadCurve(to: CGPoint(x: ex + r * 0.72, y: ey + r * 0.25), control: CGPoint(x: ex, y: ey - r * 0.8))
                    g.stroke(arc, with: .color(line), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                    continue
                }
                if R == .dizzy {                              // Spiralaugen
                    filled(g, circle(ex, ey, r), white, stroke: line, 2.5)
                    let sp = SVGPath.parse("M\(ex) \(ey) m\(-r * 0.08) 0 a\(r * 0.1) \(r * 0.1) 0 1 1 \(r * 0.2) 0 a\(r * 0.3) \(r * 0.3) 0 1 1 \(-r * 0.5) 0 a\(r * 0.5) \(r * 0.5) 0 1 1 \(r * 0.85) 0")
                    g.stroke(sp, with: .color(line), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                    continue
                }
                if md == .hibernation && R != .startle {
                    var arc = Path()
                    arc.move(to: CGPoint(x: ex - r * 0.67, y: ey))
                    arc.addQuadCurve(to: CGPoint(x: ex + r * 0.67, y: ey), control: CGPoint(x: ex, y: ey + r * 0.43))
                    g.stroke(arc, with: .color(line), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                    continue
                }
                if stress {
                    var rg = g; rg.opacity = 0.45
                    rg.fill(ellipse(ex, ey + r - 1, r * 0.8, r * 0.28), with: .color(col(C.lid)))
                }
                let eye = circle(ex, ey, r)
                g.fill(eye, with: .color(white))
                var e = g; e.clip(to: eye)
                e.fill(circle(ex, ey + 1, r * 0.67), with: .color(col(C.iris)))
                e.fill(circle(ex, ey + 1, r * 0.38), with: .color(line))
                let hl = r * 0.29
                e.fill(Path(CGRect(x: ex + r * 0.05, y: ey - r * 0.38, width: hl, height: hl)), with: .color(white))
                if md == .radiant {
                    e.fill(Path(CGRect(x: ex - r * 0.33, y: ey + r * 0.19, width: hl / 2, height: hl / 2)), with: .color(white))
                }
                e.fill(Path(CGRect(x: ex - r, y: ey - r, width: 2 * r, height: 2 * r * (1 - open))), with: .color(col(C.lid)))
                if blink > 0 { e.fill(Path(CGRect(x: ex - r, y: ey - r, width: 2 * r, height: 2 * r * blink)), with: .color(body)) }
                g.stroke(eye, with: .color(line), lineWidth: 2.5)
            }
            // Brauen
            let worried = (stress || md == .sick || R == .startle) && R?.happy != true
            for (ex, sd) in [(cx - dx, CGFloat(-1)), (cx + dx, CGFloat(1))] {
                let b = ey - r - (R == .surprise ? 4 : 0)
                var br = Path()
                if worried {
                    br.move(to: CGPoint(x: ex + sd * 10, y: b - 5))
                    br.addQuadCurve(to: CGPoint(x: ex - sd * 10, y: b - 13), control: CGPoint(x: ex, y: b - 9))
                } else {
                    br.move(to: CGPoint(x: ex - 10, y: b - 7))
                    br.addQuadCurve(to: CGPoint(x: ex + 10, y: b - 7), control: CGPoint(x: ex, y: b - 13))
                }
                g.stroke(br, with: .color(line), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            }
            // Mund
            let ln = StrokeStyle(lineWidth: 3, lineCap: .round)
            var m = Path()
            if R == .evening {
                m.move(to: CGPoint(x: cx - 10, y: my))
                m.addQuadCurve(to: CGPoint(x: cx + 10, y: my), control: CGPoint(x: cx, y: my + 9))
                g.stroke(m, with: .color(line), style: ln)
            } else if R == .night {
                m.move(to: CGPoint(x: cx - 5, y: my + 4))
                m.addQuadCurve(to: CGPoint(x: cx + 5, y: my + 4), control: CGPoint(x: cx, y: my + 8))
                g.stroke(m, with: .color(line), style: ln)
            } else if R == .surprise || R == .startle {
                g.fill(ellipse(cx, my + 6, 5, 6.5), with: .color(line))
            } else if R == .weak {
                m.move(to: CGPoint(x: cx - 7, y: my + 2))
                m.addQuadCurve(to: CGPoint(x: cx + 7, y: my + 2), control: CGPoint(x: cx, y: my + 7))
                g.stroke(m, with: .color(line), style: ln)
            } else if R == .giggle || R == .spin || R == .noon {
                m.move(to: CGPoint(x: cx - 14, y: my - 1))
                m.addQuadCurve(to: CGPoint(x: cx + 14, y: my - 1), control: CGPoint(x: cx, y: my + 19))
                m.closeSubpath()
                g.fill(m, with: .color(line))
                g.stroke(m, with: .color(line), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                g.fill(ellipse(cx, my + 9, 6, 3.5), with: .color(col(C.tongue)))
            } else if yawn || R == .sleepy || R == .morning {
                g.fill(ellipse(cx, my + 6, 10, 12), with: .color(line))       // Gähn-Mund (Unterkante my + 18)
                g.fill(ellipse(cx, my + 12.5, 6.5, 4), with: .color(col(C.tongue)))
            } else if stress || R == .dizzy {
                m.move(to: CGPoint(x: cx - 14, y: my + 5))
                m.addQuadCurve(to: CGPoint(x: cx - 7, y: my + 5), control: CGPoint(x: cx - 10.5, y: my + 1))
                m.addQuadCurve(to: CGPoint(x: cx, y: my + 5), control: CGPoint(x: cx - 3.5, y: my + 9))
                m.addQuadCurve(to: CGPoint(x: cx + 7, y: my + 5), control: CGPoint(x: cx + 3.5, y: my + 1))
                m.addQuadCurve(to: CGPoint(x: cx + 14, y: my + 5), control: CGPoint(x: cx + 10.5, y: my + 9))
                g.stroke(m, with: .color(line), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            } else {
                switch md {
                case .radiant:
                    m.move(to: CGPoint(x: cx - 14, y: my - 1))
                    m.addQuadCurve(to: CGPoint(x: cx + 14, y: my - 1), control: CGPoint(x: cx, y: my + 19))
                    m.closeSubpath()
                    g.fill(m, with: .color(line))
                    g.stroke(m, with: .color(line), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                    g.fill(ellipse(cx, my + 9, 6, 3.5), with: .color(col(C.tongue)))
                case .content:
                    m.move(to: CGPoint(x: cx - 10, y: my))
                    m.addQuadCurve(to: CGPoint(x: cx + 10, y: my), control: CGPoint(x: cx, y: my + 9))
                    g.stroke(m, with: .color(line), style: ln)
                case .tired:
                    m.move(to: CGPoint(x: cx - 8, y: my + 5))
                    m.addLine(to: CGPoint(x: cx + 8, y: my + 5))
                    g.stroke(m, with: .color(line), style: ln)
                case .sick:
                    m.move(to: CGPoint(x: cx - 10, y: my + 9))
                    m.addQuadCurve(to: CGPoint(x: cx + 10, y: my + 9), control: CGPoint(x: cx, y: my))
                    g.stroke(m, with: .color(line), style: ln)
                case .hibernation:
                    m.move(to: CGPoint(x: cx - 5, y: my + 4))
                    m.addQuadCurve(to: CGPoint(x: cx + 5, y: my + 4), control: CGPoint(x: cx, y: my + 8))
                    g.stroke(m, with: .color(line), style: ln)
                }
            }
        }

        // ---- Zusammensetzen (Reihenfolge wie im Prototyp)
        var pet = g
        pet.translateBy(x: 0, y: slug ? 4 : 0)
        pet.concatenate(scale(1 + breathe, 1 - breathe, about: CGPoint(x: 110, y: 212)))
        var tilted = pet; tilted.concatenate(tilt)
        let headBase = S.head.map { $0.cy + $0.ry } ?? (ey + 40)
        var headG = tilted
        if S.lumo && L.head != 1 { headG.concatenate(scale(L.head, L.head, about: CGPoint(x: 110, y: headBase))) }
        var antG = headG; antG.concatenate(scale(L.ant, L.ant, about: CGPoint(x: 110, y: S.ant.y + 8)))
        var faceG = headG; faceG.concatenate(scale(L.face, L.face, about: CGPoint(x: cx, y: ey)))

        tail(pet)
        feet(pet)
        if S.lumo {
            bodyParts(pet); heart(pet)
            antennas(antG); head(headG); glint(headG); face(faceG)
            if let gl = p.glasses { PetAccessory.drawGlasses(faceG, gl, cx: cx, ey: ey, dx: dx, r: r, line: line) }
        } else {
            antennas(antG)
            bodyParts(pet); head(pet); glint(pet); heart(pet)
            face(faceG)
            if let gl = p.glasses { PetAccessory.drawGlasses(faceG, gl, cx: cx, ey: ey, dx: dx, r: r, line: line) }
        }
        if let a = p.accessory { PetAccessory.draw(headG, a, top: S.ant.y, line: line) }

        // Außerhalb des atmenden Körpers
        if stress {
            let x = S.sweat.x, y = S.sweat.y
            var d = Path()
            d.move(to: CGPoint(x: x, y: y))
            d.addQuadCurve(to: CGPoint(x: x, y: y + 18), control: CGPoint(x: x - 8, y: y + 12))
            d.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: x + 8, y: y + 12))
            d.closeSubpath()
            filled(g, d, col(C.sweat), stroke: line, 2)
        }
        if yawn || md == .hibernation {
            text(g, "z Z", size: 20, bold: true, color: line, at: CGPoint(x: S.zz.x, y: S.zz.y + zzOff))
        }
        if let form = p.form { text(g, form.badge, size: 28, at: CGPoint(x: 182, y: 22)) }
        if p.branch == .radiant { text(g, "✨", size: 20, at: CGPoint(x: 4, y: 78)) }
        if p.branch == .wild { text(g, "🍂", size: 20, at: CGPoint(x: 18, y: 212)) }
    }

    /// Das Ei in den Farben der Figur: Grundfarbe = Körper, Tupfen = Antennenfarbe. cracks 0...4 (Growth.cracks),
    /// shake 0...1 = Wackeln nach dem Antippen, t = Zeit (leichtes Schaukeln); nil = Standbild. Gleiche Maße wie drawEgg() in Kotlin.
    static func drawEgg(_ ctx: GraphicsContext, size: CGSize, figure F: PetFigure, cracks: Int, t: Double?, shake: Double = 0) {
        var g = ctx
        let s = min(size.width, size.height) / viewBox.width
        g.translateBy(x: (size.width - viewBox.width * s) / 2, y: (size.height - viewBox.height * s) / 2)
        g.scaleBy(x: s, y: s)
        g.translateBy(x: -viewBox.minX, y: -viewBox.minY)
        let C = F.colors, line = col(C.line)
        var sh = g; sh.opacity = 0.16
        sh.fill(Path(ellipseIn: CGRect(x: 60, y: 206, width: 100, height: 14)), with: .color(.black))
        let tt = t ?? 0
        let rock = 2 * sin(tt * 2.4) + shake * 9 * sin(tt * 38)
        g.concatenate(rotate(CGFloat(rock), about: CGPoint(x: 110, y: 212)))
        var egg = Path()
        egg.move(to: CGPoint(x: 110, y: 52))
        egg.addCurve(to: CGPoint(x: 170, y: 152), control1: CGPoint(x: 146, y: 52), control2: CGPoint(x: 170, y: 112))
        egg.addCurve(to: CGPoint(x: 110, y: 212), control1: CGPoint(x: 170, y: 190), control2: CGPoint(x: 144, y: 212))
        egg.addCurve(to: CGPoint(x: 50, y: 152), control1: CGPoint(x: 76, y: 212), control2: CGPoint(x: 50, y: 190))
        egg.addCurve(to: CGPoint(x: 110, y: 52), control1: CGPoint(x: 50, y: 112), control2: CGPoint(x: 74, y: 52))
        egg.closeSubpath()
        g.fill(egg, with: .color(col(C.body)))
        var inner = g
        inner.clip(to: egg)
        var spots = inner; spots.opacity = 0.85
        for (x, y, r) in [(84.0, 150.0, 14.0), (132, 120, 10), (122, 180, 12), (98, 92, 7), (146, 162, 6)] as [(CGFloat, CGFloat, CGFloat)] {
            spots.fill(circle(x, y, r), with: .color(col(C.ant)))
        }
        var gl = inner; gl.opacity = 0.38
        gl.fill(Path(ellipseIn: CGRect(x: 72, y: 84, width: 28, height: 42)), with: .color(white))
        g.stroke(egg, with: .color(line), lineWidth: 4)
        let crackPts: [[CGFloat]] = [
            [96, 102, 104, 112, 98, 120, 108, 130],
            [108, 130, 120, 124, 126, 136, 138, 130],
            [72, 136, 82, 130, 90, 140, 98, 134, 108, 130],
            [138, 130, 146, 140, 152, 132]
        ]
        for pts in crackPts.prefix(max(0, min(4, cracks))) {
            var p = Path()
            p.move(to: CGPoint(x: pts[0], y: pts[1]))
            stride(from: 2, to: pts.count, by: 2).forEach { p.addLine(to: CGPoint(x: pts[$0], y: pts[$0 + 1])) }
            g.stroke(p, with: .color(line), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        }
    }

    // MARK: Hilfen

    static func col(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }

    private static func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
    }

    private static func ellipse(_ x: CGFloat, _ y: CGFloat, _ rx: CGFloat, _ ry: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: x - rx, y: y - ry, width: 2 * rx, height: 2 * ry))
    }

    private static func filled(_ g: GraphicsContext, _ p: Path, _ fill: Color, stroke: Color, _ width: CGFloat) {
        g.fill(p, with: .color(fill))
        g.stroke(p, with: .color(stroke), style: StrokeStyle(lineWidth: width, lineJoin: .round))
    }

    /// Drehung um einen Punkt, Winkel in Grad (wie SVG rotate(a cx cy)).
    private static func rotate(_ deg: CGFloat, about c: CGPoint) -> CGAffineTransform {
        CGAffineTransform(translationX: c.x, y: c.y).rotated(by: deg * .pi / 180).translatedBy(x: -c.x, y: -c.y)
    }

    private static func scale(_ sx: CGFloat, _ sy: CGFloat, about c: CGPoint) -> CGAffineTransform {
        CGAffineTransform(translationX: c.x, y: c.y).scaledBy(x: sx, y: sy).translatedBy(x: -c.x, y: -c.y)
    }

    /// SVG-Text: y ist die Grundlinie.
    private static func text(_ g: GraphicsContext, _ s: String, size: CGFloat, bold: Bool = false,
                             color: Color = .primary, at p: CGPoint) {
        let t = Text(s).font(.system(size: size, weight: bold ? .bold : .regular)).foregroundColor(color)
        g.draw(t, at: CGPoint(x: p.x, y: p.y + size * 0.22), anchor: .bottomLeading)
    }
}

// MARK: - SVG-Pfad -> SwiftUI.Path

/// Kleiner Parser für SVG-Pfaddaten (M L H V C S Q T A Z, absolut und relativ).
enum SVGPath {
    static func parse(_ d: String) -> Path {
        var path = Path()
        var it = Scanner(d)
        var cur = CGPoint.zero, start = CGPoint.zero
        var lastCtrl: CGPoint? = nil, lastCmd: Character = " "
        var cmd: Character = " "

        while let c = it.nextCommand(default: cmd) {
            cmd = c
            let rel = c.isLowercase
            let C = Character(c.uppercased())
            func pt() -> CGPoint? {
                guard let x = it.number(), let y = it.number() else { return nil }
                return rel ? CGPoint(x: cur.x + x, y: cur.y + y) : CGPoint(x: x, y: y)
            }
            switch C {
            case "M":
                guard let p = pt() else { return path }
                path.move(to: p); cur = p; start = p; lastCtrl = nil
                cmd = rel ? "l" : "L"                       // weitere Paare = Linien
            case "L":
                guard let p = pt() else { return path }
                path.addLine(to: p); cur = p; lastCtrl = nil
            case "H":
                guard let x = it.number() else { return path }
                cur = CGPoint(x: rel ? cur.x + x : x, y: cur.y); path.addLine(to: cur); lastCtrl = nil
            case "V":
                guard let y = it.number() else { return path }
                cur = CGPoint(x: cur.x, y: rel ? cur.y + y : y); path.addLine(to: cur); lastCtrl = nil
            case "C":
                guard let c1 = pt(), let c2 = pt(), let p = pt() else { return path }
                path.addCurve(to: p, control1: c1, control2: c2); lastCtrl = c2; cur = p
            case "S":
                let c1 = (lastCmd == "C" || lastCmd == "S") ? reflect(lastCtrl, cur) : cur
                guard let c2 = pt(), let p = pt() else { return path }
                path.addCurve(to: p, control1: c1, control2: c2); lastCtrl = c2; cur = p
            case "Q":
                guard let q = pt(), let p = pt() else { return path }
                path.addQuadCurve(to: p, control: q); lastCtrl = q; cur = p
            case "T":
                let q = (lastCmd == "Q" || lastCmd == "T") ? reflect(lastCtrl, cur) : cur
                guard let p = pt() else { return path }
                path.addQuadCurve(to: p, control: q); lastCtrl = q; cur = p
            case "A":
                guard let rx = it.number(), let ry = it.number(), let rot = it.number(),
                      let large = it.number(), let sweep = it.number(), let p = pt() else { return path }
                addArc(&path, from: cur, to: p, rx: rx, ry: ry, rotDeg: rot, large: large != 0, sweep: sweep != 0)
                cur = p; lastCtrl = nil
            case "Z":
                path.closeSubpath(); cur = start; lastCtrl = nil
                cmd = " "                                      // nach Z muss ein neuer Befehl kommen
            default:
                return path
            }
            lastCmd = C
        }
        return path
    }

    private static func reflect(_ c: CGPoint?, _ p: CGPoint) -> CGPoint {
        guard let c else { return p }
        return CGPoint(x: 2 * p.x - c.x, y: 2 * p.y - c.y)
    }

    /// SVG-Bogen (Endpunkt-Form) als Bézier-Kurven, höchstens 90° je Stück.
    private static func addArc(_ path: inout Path, from p0: CGPoint, to p1: CGPoint, rx rxIn: CGFloat, ry ryIn: CGFloat,
                               rotDeg: CGFloat, large: Bool, sweep: Bool) {
        if p0 == p1 { return }
        var rx = abs(rxIn), ry = abs(ryIn)
        if rx == 0 || ry == 0 { path.addLine(to: p1); return }
        let phi = rotDeg * .pi / 180, cosP = cos(phi), sinP = sin(phi)
        let dx2 = (p0.x - p1.x) / 2, dy2 = (p0.y - p1.y) / 2
        let x1 = cosP * dx2 + sinP * dy2, y1 = -sinP * dx2 + cosP * dy2
        let lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var coef = sqrt(max(0, num / den))
        if large == sweep { coef = -coef }
        let cxp = coef * rx * y1 / ry, cyp = -coef * ry * x1 / rx
        let cx = cosP * cxp - sinP * cyp + (p0.x + p1.x) / 2
        let cy = sinP * cxp + cosP * cyp + (p0.y + p1.y) / 2
        func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let a = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            return a
        }
        let th1 = angle(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry)
        var dth = angle((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
        if !sweep && dth > 0 { dth -= 2 * .pi }
        if sweep && dth < 0 { dth += 2 * .pi }
        let n = max(1, Int(ceil(abs(dth) / (.pi / 2))))
        let step = dth / CGFloat(n)
        let k = 4.0 / 3.0 * tan(step / 4)
        func onEllipse(_ t: CGFloat) -> (CGPoint, CGPoint) {      // Punkt + Ableitung
            let ct = cos(t), st = sin(t)
            let p = CGPoint(x: cx + rx * ct * cosP - ry * st * sinP, y: cy + rx * ct * sinP + ry * st * cosP)
            let d = CGPoint(x: -rx * st * cosP - ry * ct * sinP, y: -rx * st * sinP + ry * ct * cosP)
            return (p, d)
        }
        var t = th1
        for _ in 0..<n {
            let (a, da) = onEllipse(t), (b, db) = onEllipse(t + step)
            path.addCurve(to: b,
                          control1: CGPoint(x: a.x + k * da.x, y: a.y + k * da.y),
                          control2: CGPoint(x: b.x - k * db.x, y: b.y - k * db.y))
            t += step
        }
    }

    /// Zerlegt Pfaddaten in Befehle und Zahlen (auch "1-2", ".5.5", "1e-3").
    private struct Scanner {
        let chars: [Character]
        var i = 0
        init(_ s: String) { chars = Array(s) }

        mutating func skip() { while i < chars.count, chars[i] == " " || chars[i] == "," || chars[i].isNewline || chars[i] == "\t" { i += 1 } }

        /// Nächster Befehl; steht eine Zahl an, gilt der vorige Befehl weiter.
        mutating func nextCommand(default d: Character) -> Character? {
            skip()
            guard i < chars.count else { return nil }
            let c = chars[i]
            if c.isLetter, c != "e", c != "E" { i += 1; return c }
            return d == " " ? nil : d
        }

        mutating func number() -> CGFloat? {
            skip()
            let s = i
            if i < chars.count, chars[i] == "-" || chars[i] == "+" { i += 1 }
            var dot = false, digits = false
            while i < chars.count {
                let c = chars[i]
                if c.isNumber { digits = true; i += 1 }
                else if c == ".", !dot { dot = true; i += 1 }
                else if (c == "e" || c == "E"), digits {
                    i += 1
                    if i < chars.count, chars[i] == "-" || chars[i] == "+" { i += 1 }
                } else { break }
            }
            guard digits, let v = Double(String(chars[s..<i])) else { i = s; return nil }
            return CGFloat(v)
        }
    }
}

// MARK: - Vorschau

#Preview("Alle Figuren") {
    ScrollView {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110))]) {
            ForEach(PetFigure.all) { f in
                VStack {
                    PetFigureView(figure: f, pose: PetPose(mood: .radiant, energy: 70, rest: 70)).frame(width: 110, height: 110)
                    Text(f.name).font(.caption)
                }
            }
        }
        .padding()
    }
}

#Preview("Zustände") {
    let f = PetFigure.byID("glut")
    return VStack {
        HStack {
            PetFigureView(figure: f, pose: PetPose(mood: .tired, overlays: [.yawning], energy: 10, rest: 50))
            PetFigureView(figure: f, pose: PetPose(mood: .tired, overlays: [.stressed, .chubby], energy: 40, rest: 20))
        }
        HStack {
            PetFigureView(figure: f, pose: PetPose(mood: .sick, overlays: [.sluggish], energy: 25, rest: 30))
            PetFigureView(figure: f, pose: PetPose(mood: .hibernation, form: .zenfuchs, branch: .wild, energy: 5, rest: 5))
        }
    }
    .padding()
}
