// Growth.swift: Wachsen vom Ei bis zum Erwachsenen – und zurück bei schlechter Pflege.
// Gegenstück: Growth.kt (gleiche Regeln, gleiche Tests).
//
// Ei → (antippen bis es schlüpft) → Baby → Kind → Jugend → Erwachsen.
// Wachstumstage je abgeschlossenem Tag (mittleres Wohlbefinden): gut (>= 40) +1, kränklich (20–39) −1,
// Winterschlaf (< 20) −2, ohne Messung 0. Aufstieg, sobald die Grenze der nächsten Stufe erreicht ist.
// Rückfall um eine Stufe, wenn die Tage mehr als dropBuffer unter die Grenze der aktuellen Stufe fallen.
// Nie zurück ins Ei; Baby ist das Minimum.

import Foundation
import CoreGraphics

enum GrowthStage: String, Codable, CaseIterable {
    case egg, baby, child, teen, adult

    var minDays: Int {
        switch self { case .egg: -1; case .baby: 0; case .child: 14; case .teen: 42; case .adult: 84 }
    }
    var title: String {
        switch self { case .egg: "Ei"; case .baby: "Baby"; case .child: "Kind"; case .teen: "Jugend"; case .adult: "Erwachsen" }
    }
    private var index: Int { Self.allCases.firstIndex(of: self)! }
    var next: GrowthStage? { index + 1 < Self.allCases.count ? Self.allCases[index + 1] : nil }
    var previous: GrowthStage? { self == .egg || self == .baby ? nil : Self.allCases[index - 1] }
}

/// Proportionen fürs Zeichnen: sx/sy = ganze Figur (um die Füße), face = Gesicht, ant = Antennen, head = Kopf (Lumo-Bauweise).
struct GrowthLook: Equatable { let sx, sy, face, ant, head: CGFloat }

struct GrowthState: Codable, Equatable {
    var hatched: Bool
    var goodDays = 0                     // Wachstumstage (gute minus schlechte, nie unter 0)
    var lastDay: Int
    var hatchedDay: Int? = nil
    var pendingStage: GrowthStage? = nil // Wechsel, der noch gezeigt werden soll
    var stage: GrowthStage? = nil        // aktuelle Stufe (nil = aus goodDays ableiten, alte Stände)
    var pendingDown: Bool? = nil         // der Wechsel war ein Rückfall
}

/// Gefahr eines Rückfalls: so viele schlechte Tage noch bis zur Stufe darunter.
struct GrowthRisk: Equatable { let badDaysLeft: Int; let to: GrowthStage }

enum Growth {
    static let goodMin: Float = 40
    static let severeMax: Float = 20
    static let dropBuffer = 3
    static let eggTaps = 5
    static let maxCatchUp = 120

    static func stage(for goodDays: Int) -> GrowthStage {
        GrowthStage.allCases.last { $0 != .egg && goodDays >= $0.minDays } ?? .baby
    }

    static func stage(_ s: GrowthState) -> GrowthStage { s.hatched ? (s.stage ?? stage(for: s.goodDays)) : .egg }

    static func isGood(_ avg: Float?) -> Bool { (avg ?? -1) >= goodMin }

    static func delta(_ avg: Float?) -> Int {
        guard let avg else { return 0 }
        return avg >= goodMin ? 1 : avg < severeMax ? -2 : -1
    }

    static func closeDay(_ s: GrowthState, day: Int, avgWellbeing: Float?) -> GrowthState {
        guard s.hatched, day > s.lastDay else { return s }
        let cur = stage(s)
        var c = s
        c.goodDays = max(0, s.goodDays + delta(avgWellbeing))
        c.lastDay = day
        var new = cur
        if let nx = cur.next, c.goodDays >= nx.minDays { new = nx }
        else if let prev = cur.previous, c.goodDays < cur.minDays - dropBuffer { new = prev }
        c.stage = new
        if new != cur {
            c.pendingStage = new
            c.pendingDown = GrowthStage.allCases.firstIndex(of: new)! < GrowthStage.allCases.firstIndex(of: cur)!
        }
        return c
    }

    static func hatch(_ s: GrowthState, today: Int) -> GrowthState {
        guard !s.hatched else { return s }
        return GrowthState(hatched: true, goodDays: 0, lastDay: today, hatchedDay: today, pendingStage: .baby, stage: .baby)
    }

    static func migrated(hatchDay: Int, today: Int) -> GrowthState {
        let days = max(0, today - hatchDay)
        return GrowthState(hatched: true, goodDays: days, lastDay: today - 1, hatchedDay: hatchDay, stage: stage(for: days))
    }

    static func progress(_ s: GrowthState) -> (fraction: Double, daysLeft: Int)? {
        guard s.hatched else { return nil }
        let st = stage(s)
        guard let nx = st.next else { return nil }
        let span = Double(nx.minDays - st.minDays)
        return (min(1, max(0, Double(s.goodDays - st.minDays) / span)), nx.minDays - s.goodDays)
    }

    static func risk(_ s: GrowthState) -> GrowthRisk? {
        guard s.hatched else { return nil }
        let st = stage(s)
        guard let prev = st.previous, s.goodDays < st.minDays else { return nil }
        return GrowthRisk(badDaysLeft: s.goodDays - (st.minDays - dropBuffer) + 1, to: prev)
    }

    static func look(_ stage: GrowthStage?) -> GrowthLook {
        switch stage {
        case .baby: GrowthLook(sx: 0.66, sy: 0.60, face: 1.25, ant: 0.62, head: 1.18)
        case .child: GrowthLook(sx: 0.78, sy: 0.74, face: 1.14, ant: 0.78, head: 1.10)
        case .teen: GrowthLook(sx: 0.90, sy: 0.89, face: 1.05, ant: 0.92, head: 1.04)
        default: GrowthLook(sx: 1, sy: 1, face: 1, ant: 1, head: 1)
        }
    }

    static func cracks(_ taps: Int) -> Int { min(4, max(0, taps * 4 / eggTaps)) }
}

/// Speicher (App Group, damit Widget und Hintergrundbild die Stufe kennen). Nachtragen und Übernahme: GrowthStore (App).
enum GrowthStorage {
    static let key = "growth.state"
    static let tapsKey = "growth.eggTaps"
    private static var d: UserDefaults { STShared.defaults }

    static func load() -> GrowthState? { d.data(forKey: key).flatMap { try? JSONDecoder().decode(GrowthState.self, from: $0) } }
    static func save(_ s: GrowthState) { d.set(try? JSONEncoder().encode(s), forKey: key) }
    static var eggTaps: Int {
        get { d.integer(forKey: tapsKey) }
        set { d.set(newValue, forKey: tapsKey) }
    }
    /// Stufe zum Zeichnen; ohne gespeicherten Stand (ältere Installation) erwachsen.
    static var stage: GrowthStage { load().map { Growth.stage($0) } ?? .adult }
    static var cracks: Int { Growth.cracks(eggTaps) }
}
