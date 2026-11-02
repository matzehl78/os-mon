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
        }
    }
}
