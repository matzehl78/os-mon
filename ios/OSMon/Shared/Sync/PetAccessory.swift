// PetAccessory.swift: Fest-Zubehör auf der Figur. Gegenstück: PetAccessory.kt (gleiche Formen und Maße).
// Zeichen-Einheiten wie die Figur; Anker: Mitte x = 110, Oberkante Kopf = top (FigureShape.ant.y).

import SwiftUI

enum PetAccessory {
    private static func c(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static func draw(_ g: GraphicsContext, _ a: Accessory, top: CGFloat, line: Color) {
        let cx: CGFloat = 110, b = top + 8
        let ln = StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round)
        func P(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }
        func dot(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ fill: Color, _ w: CGFloat = 2.5) {
            let e = Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
            g.fill(e, with: .color(fill)); if w > 0 { g.stroke(e, with: .color(line), lineWidth: w) }
        }
        switch a {
        case .partyHat:
            var hat = Path(); hat.move(to: P(cx - 24, b)); hat.addLine(to: P(cx + 4, b - 60)); hat.addLine(to: P(cx + 26, b)); hat.closeSubpath()
            g.fill(hat, with: .color(c(0xFFD54F)))
            var cl = g; cl.clip(to: hat)
            for k in 0...4 {
                let y = b - 8 - CGFloat(k) * 13
                var st = Path(); st.move(to: P(cx - 40, y + 6)); st.addLine(to: P(cx + 40, y - 10)); st.addLine(to: P(cx + 40, y - 4)); st.addLine(to: P(cx - 40, y + 12)); st.closeSubpath()
                cl.fill(st, with: .color(k % 2 == 0 ? c(0xFF4FA8) : c(0x4FC3F7)))
            }
            g.stroke(hat, with: .color(line), style: ln)
            dot(cx + 4, b - 62, 7, c(0xFF4FA8))
        case .santaHat:
            var cap = Path(); cap.move(to: P(cx - 28, b - 6))
            cap.addCurve(to: P(cx + 44, b - 40), control1: P(cx - 24, b - 44), control2: P(cx + 10, b - 62))
            cap.addCurve(to: P(cx + 28, b - 6), control1: P(cx + 30, b - 40), control2: P(cx + 24, b - 30)); cap.closeSubpath()
            g.fill(cap, with: .color(c(0xE53935))); g.stroke(cap, with: .color(line), style: ln)
            let band = Path(roundedRect: CGRect(x: cx - 34, y: b - 12, width: 68, height: 16), cornerRadius: 8)
            g.fill(band, with: .color(.white)); g.stroke(band, with: .color(line), style: ln)
            dot(cx + 46, b - 40, 8, .white)
        case .bunnyEars:
            for side: CGFloat in [-1, 1] {
                var e = g
                e.translateBy(x: cx + side * 14, y: b); e.rotate(by: .degrees(Double(side * 14))); e.translateBy(x: -(cx + side * 14), y: -b)
                let outer = Path(ellipseIn: CGRect(x: cx + side * 14 - 10, y: b - 58, width: 20, height: 60))
                e.fill(outer, with: .color(.white)); e.stroke(outer, with: .color(line), style: ln)
                e.fill(Path(ellipseIn: CGRect(x: cx + side * 14 - 5, y: b - 50, width: 10, height: 42)), with: .color(c(0xFFA8C8)))
            }
        case .witchHat:
            let brim = Path(ellipseIn: CGRect(x: cx - 44, y: b - 8, width: 88, height: 14))
            var cone = Path(); cone.move(to: P(cx - 24, b - 2))
            cone.addCurve(to: P(cx + 30, b - 70), control1: P(cx - 14, b - 40), control2: P(cx + 2, b - 58))
            cone.addCurve(to: P(cx + 24, b - 2), control1: P(cx + 14, b - 52), control2: P(cx + 20, b - 26)); cone.closeSubpath()
            g.fill(brim, with: .color(c(0x4A2C6E))); g.stroke(brim, with: .color(line), style: ln)
            g.fill(cone, with: .color(c(0x5E35B1))); g.stroke(cone, with: .color(line), style: ln)
            let band = Path(CGRect(x: cx - 22, y: b - 14, width: 45, height: 8))
            g.fill(band, with: .color(c(0xFF9800))); g.stroke(band, with: .color(line), lineWidth: 2)
        case .crown:
            var cr = Path(); cr.move(to: P(cx - 26, b))
            for (x, y) in [(cx - 30, b - 30), (cx - 14, b - 16), (cx, b - 36), (cx + 14, b - 16), (cx + 30, b - 30), (cx + 26, b)] { cr.addLine(to: P(x, y)) }
            cr.closeSubpath()
            g.fill(cr, with: .color(c(0xFFC107))); g.stroke(cr, with: .color(line), style: ln)
            dot(cx, b - 8, 4.5, c(0xE53935), 0); dot(cx - 16, b - 7, 3.5, c(0x42A5F5), 0); dot(cx + 16, b - 7, 3.5, c(0x66BB6A), 0)
            dot(cx - 30, b - 30, 3.5, c(0xFFF59D), 2); dot(cx, b - 36, 3.5, c(0xFFF59D), 2); dot(cx + 30, b - 30, 3.5, c(0xFFF59D), 2)
        case .flowers:
            let cols: [UInt32] = [0xFF80AB, 0xFFEB3B, 0xB388FF]
            for (i, dx) in [CGFloat(-24), 0, 24].enumerated() {
                let x = cx + dx, y = b - 4 - (i == 1 ? 6 : 0)
                for k in 0..<5 {
                    let ang = Double(k) * 72 * .pi / 180
                    dot(x + 7 * CGFloat(cos(ang)), y + 7 * CGFloat(sin(ang)), 5.5, c(cols[i]), 1.8)
                }
                dot(x, y, 4.5, .white, 1.8)
            }
        case .lantern:
            var stick = Path(); stick.move(to: P(196, 168)); stick.addLine(to: P(186, 70))
            g.stroke(stick, with: .color(c(0x6D4C41)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
            var str = Path(); str.move(to: P(186, 70)); str.addLine(to: P(200, 82)); g.stroke(str, with: .color(line), lineWidth: 2)
            g.fill(Path(ellipseIn: CGRect(x: 178, y: 74, width: 44, height: 50)), with: .color(c(0xFFD54F, 0.33)))
            let lan = Path(roundedRect: CGRect(x: 186, y: 82, width: 28, height: 34), cornerRadius: 10)
            g.fill(lan, with: .color(c(0xFFB300))); g.stroke(lan, with: .color(line), style: ln)
            dot(200, 99, 6, c(0xFFF59D), 0)
        case .jester:
            var cap = Path(); cap.move(to: P(cx - 30, b))
            cap.addCurve(to: P(cx - 50, b - 52), control1: P(cx - 40, b - 30), control2: P(cx - 54, b - 44))
            cap.addCurve(to: P(cx, b - 26), control1: P(cx - 30, b - 44), control2: P(cx - 10, b - 30))
            cap.addCurve(to: P(cx + 50, b - 52), control1: P(cx + 10, b - 30), control2: P(cx + 30, b - 44))
            cap.addCurve(to: P(cx + 30, b), control1: P(cx + 54, b - 44), control2: P(cx + 40, b - 30)); cap.closeSubpath()
            g.fill(cap, with: .color(c(0x7E57C2)))
            var cl = g; cl.clip(to: cap); cl.fill(Path(CGRect(x: cx, y: b - 60, width: 60, height: 64)), with: .color(c(0x26A69A)))
            g.stroke(cap, with: .color(line), style: ln)
            dot(cx - 50, b - 52, 6, c(0xFFD54F), 2); dot(cx + 50, b - 52, 6, c(0xFFD54F), 2)
        // ---------- Kleiderschrank (Wardrobe.swift) ----------
        case .beanie:
            var dome = Path(); dome.move(to: P(cx - 34, b)); dome.addCurve(to: P(cx + 34, b), control1: P(cx - 34, b - 44), control2: P(cx + 34, b - 44)); dome.closeSubpath()
            g.fill(dome, with: .color(c(0xEF5350)))
            var cl = g; cl.clip(to: dome)
            for k in -3...3 { var l = Path(); l.move(to: P(cx + CGFloat(k) * 9, b)); l.addLine(to: P(cx + CGFloat(k) * 6, b - 40)); cl.stroke(l, with: .color(c(0xC62828)), lineWidth: 2.5) }
            g.stroke(dome, with: .color(line), style: ln)
            let band = Path(roundedRect: CGRect(x: cx - 37, y: b - 10, width: 74, height: 14), cornerRadius: 7)
            g.fill(band, with: .color(c(0xFFCDD2))); g.stroke(band, with: .color(line), style: ln)
            dot(cx, b - 38, 9, .white)
        case .cap:
            var dome = Path(); dome.move(to: P(cx - 31, b)); dome.addCurve(to: P(cx + 31, b), control1: P(cx - 31, b - 38), control2: P(cx + 31, b - 38)); dome.closeSubpath()
            var brim = Path(); brim.move(to: P(cx + 18, b - 6)); brim.addQuadCurve(to: P(cx + 60, b + 2), control: P(cx + 54, b - 10))
            brim.addQuadCurve(to: P(cx + 18, b + 2), control: P(cx + 40, b + 6)); brim.closeSubpath()
            g.fill(brim, with: .color(c(0x1E88E5))); g.stroke(brim, with: .color(line), style: ln)
            g.fill(dome, with: .color(c(0x42A5F5))); g.stroke(dome, with: .color(line), style: ln)
            var seam = Path(); seam.move(to: P(cx, b - 28)); seam.addLine(to: P(cx, b)); g.stroke(seam, with: .color(c(0x1E88E5)), lineWidth: 2.5)
            dot(cx, b - 29, 4, c(0x1E88E5), 2)
        case .bow:
            let x = cx + 24, y = b - 6
            for sd: CGFloat in [-1, 1] {
                var loop = Path(); loop.move(to: P(x, y))
                loop.addCurve(to: P(x + sd * 26, y), control1: P(x + sd * 10, y - 20), control2: P(x + sd * 30, y - 16))
                loop.addCurve(to: P(x, y), control1: P(x + sd * 30, y + 16), control2: P(x + sd * 10, y + 20)); loop.closeSubpath()
                g.fill(loop, with: .color(c(0xFF4FA8))); g.stroke(loop, with: .color(line), style: ln)
            }
            dot(x, y, 7, c(0xFF80C0))
        case .topHat:
            let brim = Path(ellipseIn: CGRect(x: cx - 40, y: b - 9, width: 80, height: 14))
            g.fill(brim, with: .color(c(0x263238))); g.stroke(brim, with: .color(line), style: ln)
            let crown = Path(roundedRect: CGRect(x: cx - 24, y: b - 52, width: 48, height: 50), cornerRadius: 5)
            g.fill(crown, with: .color(c(0x37474F))); g.stroke(crown, with: .color(line), style: ln)
            let band = Path(CGRect(x: cx - 24, y: b - 16, width: 48, height: 8))
            g.fill(band, with: .color(c(0xE53935))); g.stroke(band, with: .color(line), lineWidth: 2)
            var sh = Path(); sh.move(to: P(cx - 15, b - 46)); sh.addLine(to: P(cx - 15, b - 22))
            g.stroke(sh, with: .color(.white.opacity(0.33)), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        case .wizardHat:
            let brim = Path(ellipseIn: CGRect(x: cx - 44, y: b - 8, width: 88, height: 14))
            var cone = Path(); cone.move(to: P(cx - 26, b - 2))
            cone.addCurve(to: P(cx - 26, b - 80), control1: P(cx - 16, b - 44), control2: P(cx - 4, b - 66))
            cone.addCurve(to: P(cx + 26, b - 2), control1: P(cx + 4, b - 70), control2: P(cx + 18, b - 40)); cone.closeSubpath()
            g.fill(brim, with: .color(c(0x283593))); g.stroke(brim, with: .color(line), style: ln)
            g.fill(cone, with: .color(c(0x3949AB))); g.stroke(cone, with: .color(line), style: ln)
            for (x, y, r) in [(cx - 6, b - 22, CGFloat(5)), (cx + 10, b - 40, 4), (cx - 10, b - 52, 3.5)] { g.fill(star(x, y, r), with: .color(c(0xFFEB3B))) }
        case .pirateHat:
            var hat = Path(); hat.move(to: P(cx - 46, b - 2))
            hat.addCurve(to: P(cx, b - 44), control1: P(cx - 40, b - 26), control2: P(cx - 24, b - 44))
            hat.addCurve(to: P(cx + 46, b - 2), control1: P(cx + 24, b - 44), control2: P(cx + 40, b - 26))
            hat.addQuadCurve(to: P(cx - 46, b - 2), control: P(cx, b - 14)); hat.closeSubpath()
            g.fill(hat, with: .color(c(0x212121))); g.stroke(hat, with: .color(line), style: ln)
            var trim = Path(); trim.move(to: P(cx - 44, b - 4)); trim.addQuadCurve(to: P(cx + 44, b - 4), control: P(cx, b - 16))
            g.stroke(trim, with: .color(c(0xFFC107)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            dot(cx, b - 30, 7, .white, 0); dot(cx - 2.5, b - 31, 1.8, c(0x212121), 0); dot(cx + 2.5, b - 31, 1.8, c(0x212121), 0)
            var bones = Path(); bones.move(to: P(cx - 8, b - 20)); bones.addLine(to: P(cx + 8, b - 16)); bones.move(to: P(cx - 8, b - 16)); bones.addLine(to: P(cx + 8, b - 20))
            g.stroke(bones, with: .color(.white), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        }
    }

    private static func star(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        var p = Path()
        for k in 0..<10 {
            let a = (-90 + Double(k) * 36) * .pi / 180, rr = k % 2 == 0 ? r : r * 0.45
            let pt = CGPoint(x: x + rr * CGFloat(cos(a)), y: y + rr * CGFloat(sin(a)))
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath(); return p
    }

    private static func heartPath(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> Path {
        var p = Path(); p.move(to: CGPoint(x: x, y: y + r * 0.95))
        p.addCurve(to: CGPoint(x: x, y: y - r * 0.45), control1: CGPoint(x: x - r * 1.5, y: y - r * 0.05), control2: CGPoint(x: x - r * 0.75, y: y - r * 1.25))
        p.addCurve(to: CGPoint(x: x, y: y + r * 0.95), control1: CGPoint(x: x + r * 0.75, y: y - r * 1.25), control2: CGPoint(x: x + r * 1.5, y: y - r * 0.05))
        p.closeSubpath(); return p
    }

    /// Brille über den Augen (Kleiderschrank). Augen bei (cx ± dx, ey), Radius r. Gegenstück: PetAccessory.drawGlasses (Kotlin).
    static func drawGlasses(_ g: GraphicsContext, _ gl: Glasses, cx: CGFloat, ey: CGFloat, dx: CGFloat, r: CGFloat, line: Color) {
        let R = r + 4
        let eyes = [cx - dx, cx + dx]
        func st(_ w: CGFloat) -> StrokeStyle { StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round) }
        func temples(_ col: Color) {
            for sd: CGFloat in [-1, 1] { var t = Path(); t.move(to: CGPoint(x: cx + sd * (dx + R), y: ey - 2)); t.addLine(to: CGPoint(x: cx + sd * (dx + R + 10), y: ey - 6)); g.stroke(t, with: .color(col), style: st(3)) }
        }
        func bridge(_ col: Color) {
            var b = Path(); b.move(to: CGPoint(x: cx - dx + R * 0.85, y: ey - 3)); b.addQuadCurve(to: CGPoint(x: cx + dx - R * 0.85, y: ey - 3), control: CGPoint(x: cx, y: ey - 10))
            g.stroke(b, with: .color(col), style: st(3))
        }
        func circ(_ x: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x - R, y: ey - R, width: 2 * R, height: 2 * R)) }
        switch gl {
        case .round:
            temples(line); bridge(line)
            for x in eyes { g.fill(circ(x), with: .color(.white.opacity(0.15))); g.stroke(circ(x), with: .color(c(0x5D4037)), style: st(3.5)) }
        case .sun:
            temples(line); bridge(line)
            for x in eyes {
                let rc = Path(roundedRect: CGRect(x: x - R - 1, y: ey - R * 0.8, width: 2 * R + 2, height: R * 1.55), cornerRadius: R * 0.5)
                g.fill(rc, with: .color(c(0x263238, 0.94))); g.stroke(rc, with: .color(line), style: st(3))
                var glare = Path(); glare.move(to: CGPoint(x: x - R * 0.5, y: ey - R * 0.35)); glare.addLine(to: CGPoint(x: x - R * 0.1, y: ey - R * 0.6))
                g.stroke(glare, with: .color(.white.opacity(0.6)), style: st(3))
            }
        case .heart:
            temples(c(0xD81B60)); bridge(c(0xD81B60))
            for x in eyes { let h = heartPath(x, ey, R * 0.95); g.fill(h, with: .color(c(0xFF80AB, 0.4))); g.stroke(h, with: .color(c(0xD81B60)), style: st(3.5)) }
        case .star:
            temples(c(0xF9A825)); bridge(c(0xF9A825))
            for x in eyes { let s = star(x, ey + 1, R * 1.25); g.fill(s, with: .color(c(0xFFEB3B, 0.33))); g.stroke(s, with: .color(c(0xF9A825)), style: st(3.5)) }
        case .monocle:
            let x = cx + dx
            var ch = Path(); ch.move(to: CGPoint(x: x + R * 0.3, y: ey + R)); ch.addQuadCurve(to: CGPoint(x: x + R * 0.6, y: ey + R * 3.2), control: CGPoint(x: x + R * 1.2, y: ey + R * 2.2))
            g.stroke(ch, with: .color(c(0xFFB300)), style: st(2))
            g.fill(circ(x), with: .color(.white.opacity(0.15))); g.stroke(circ(x), with: .color(c(0xFFB300)), style: st(3.5))
        }
    }
}
