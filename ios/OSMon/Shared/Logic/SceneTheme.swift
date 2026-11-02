// SceneTheme.swift: Jahreszeiten und Fest-Dekoration im Hintergrund (rein). Gegenstück: SceneTheme.kt, gleiche Tests.
// Jahreszeit meteorologisch (Nordhalbkugel). Requisiten: x = Anteil der Breite, size in pt, dy = Höhe über der Plattform.

import Foundation

enum Season: String, CaseIterable { case spring, summer, autumn, winter }

enum PropKind: String { case emoji, bunting, lightChain, lanterns, heartGarland, fireworks, snowGround, sunGlow, candles }

struct Prop: Equatable {
    var kind: PropKind
    var x: Double = 0.5
    var size: Double = 48
    var dy: Double = 0
    var text = ""
}

enum SceneTheme {
    static func season(month: Int) -> Season {
        switch month { case 3...5: return .spring; case 6...8: return .summer; case 9...11: return .autumn; default: return .winter }
    }
    static func season(day: Int) -> Season { season(month: Festivals.ymd(day).m) }

    static func title(_ s: Season) -> String {
        switch s { case .spring: return "Frühling"; case .summer: return "Sommer"; case .autumn: return "Herbst"; case .winter: return "Winter" }
    }

    /// Leichte Einfärbung (ARGB), gleiche Werte wie Android.
    static func tint(_ s: Season) -> UInt32 {
        switch s { case .spring: return 0x14FF8FC8; case .summer: return 0x14FFC94D; case .autumn: return 0x1FFF8A3D; case .winter: return 0x24A8D8FF }
    }

    static func particles(_ s: Season) -> Decor {
        switch s { case .spring: return .petals; case .summer: return .butterflies; case .autumn: return .leaves; case .winter: return .snow }
    }

    static func seasonProps(_ s: Season) -> [Prop] {
        switch s {
        case .spring: return [Prop(kind: .emoji, x: 0.06, size: 30, text: "🌷"), Prop(kind: .emoji, x: 0.94, size: 28, text: "🌼")]
        case .summer: return [Prop(kind: .sunGlow), Prop(kind: .emoji, x: 0.94, size: 34, text: "🌻")]
        case .autumn: return [Prop(kind: .emoji, x: 0.06, size: 30, text: "🍂"), Prop(kind: .emoji, x: 0.94, size: 28, text: "🍁")]
        case .winter: return [Prop(kind: .snowGround)]
        }
    }

    static func festivalProps(_ f: FestivalID, adventCandles: Int = 1) -> [Prop] {
        func e(_ x: Double, _ size: Double, _ t: String, dy: Double = 0) -> Prop { Prop(kind: .emoji, x: x, size: size, dy: dy, text: t) }
        switch f {
        case .christmas: return [e(0.13, 120, "🎄"), e(0.84, 52, "🎁"), e(0.93, 38, "🎁"), Prop(kind: .lightChain)]
        case .nikolaus: return [e(0.13, 100, "🎄"), e(0.87, 50, "🥾"), Prop(kind: .lightChain)]
        case .advent: return [e(0.12, 90, "🌲"), Prop(kind: .candles, x: 0.86, size: 30, text: String(min(4, max(1, adventCandles)))), Prop(kind: .lightChain)]
        case .birthday, .hatchday: return [e(0.12, 70, "🎈", dy: 170), e(0.9, 64, "🎈", dy: 200), e(0.87, 56, "🎂"), e(0.14, 44, "🎁"), Prop(kind: .bunting)]
        case .milestone: return [e(0.12, 64, "🎈", dy: 180), e(0.9, 60, "🎈", dy: 200), Prop(kind: .bunting)]
        case .silvester, .newYear: return [Prop(kind: .fireworks), e(0.88, 44, "🥂"), e(0.12, 44, "🍀")]
        case .carnival: return [Prop(kind: .bunting), e(0.12, 50, "🎭"), e(0.88, 48, "🎊")]
        case .easter: return [e(0.1, 40, "🥚"), e(0.17, 34, "🐣"), e(0.85, 36, "🥚"), e(0.91, 48, "🐰")]
        case .halloween: return [e(0.12, 64, "🎃"), e(0.89, 50, "🎃"), e(0.82, 34, "🦇", dy: 240), e(0.1, 40, "🕸️", dy: 280)]
        case .stMartin: return [Prop(kind: .lanterns), e(0.88, 44, "🐴")]
        case .valentine: return [Prop(kind: .heartGarland), e(0.88, 46, "🌹")]
        case .mothersDay: return [Prop(kind: .heartGarland), e(0.88, 54, "💐")]
        case .fathersDay: return [Prop(kind: .bunting), e(0.88, 46, "🚲")]
        }
    }

    static func props(festival: Festival?, season: Season, seasons: Bool, adventCandles: Int = 1) -> [Prop] {
        (festival.map { festivalProps($0.id, adventCandles: adventCandles) } ?? []) + (seasons ? seasonProps(season) : [])
    }

    static func particles(festival: Festival?, season: Season, seasons: Bool) -> Decor? {
        festival?.decor ?? (seasons ? particles(season) : nil)
    }
}
