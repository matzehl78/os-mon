// Growth.swift: Wachsen vom Ei bis zum Erwachsenen. Gegenstück: Growth.kt (gleiche Regeln, gleiche Tests).
//
// Ei → (antippen bis es schlüpft) → Baby → Kind → Jugend → Erwachsen.
// Gezählt werden nur „gute Tage“: abgeschlossene Tage mit mittlerem Wohlbefinden >= 40 (nicht kränklich).
// Schlechte Tage und Tage ohne Messung bremsen, werfen aber nie zurück. Bei lauter guten Tagen ist das Wesen
// nach 12 Wochen erwachsen, realistisch nach etwa 3 Monaten.

import Foundation

enum GrowthStage: String, Codable, CaseIterable {
    case egg, baby, child, teen, adult

    var minDays: Int {
        switch self { case .egg: -1; case .baby: 0; case .child: 14; case .teen: 42; case .adult: 84 }
    }
    var title: String {
        switch self { case .egg: "Ei"; case .baby: "Baby"; case .child: "Kind"; case .teen: "Jugend"; case .adult: "Erwachsen" }
    }
    var next: GrowthStage? {
        let all = Self.allCases
        let i = all.firstIndex(of: self)!
        return i + 1 < all.count ? all[i + 1] : nil
    }
}

/// Proportionen fürs Zeichnen: sx/sy = ganze Figur (um die Füße), face = Gesicht, ant = Antennen, head = Kopf (Lumo-Bauweise).
struct GrowthLook: Equatable { let sx, sy, face, ant, head: CGFloat }

struct GrowthState: Codable, Equatable {
    var hatched: Bool
    var goodDays = 0
    var lastDay: Int
    var hatchedDay: Int? = nil
    var pendingStage: GrowthStage? = nil
}

enum Growth {
    static let goodMin: Float = 40
    static let eggTaps = 5
    static let maxCatchUp = 120

    static func stage(for goodDays: Int) -> GrowthStage {
        GrowthStage.allCases.last { $0 != .egg && goodDays >= $0.minDays } ?? .baby
    }

    static func stage(_ s: GrowthState) -> GrowthStage { s.hatched ? stage(for: s.goodDays) : .egg }

    static func isGood(_ avg: Float?) -> Bool { (avg ?? -1) >= goodMin }

    static func closeDay(_ s: GrowthState, day: Int, avgWellbeing: Float?) -> GrowthState {
        guard s.hatched, day > s.lastDay else { return s }
        let before = stage(for: s.goodDays)
        var c = s
        c.goodDays += isGood(avgWellbeing) ? 1 : 0
        c.lastDay = day
        let after = stage(for: c.goodDays)
        if after != before { c.pendingStage = after }
        return c
    }

    static func hatch(_ s: GrowthState, today: Int) -> GrowthState {
        guard !s.hatched else { return s }
        return GrowthState(hatched: true, goodDays: 0, lastDay: today, hatchedDay: today, pendingStage: .baby)
    }

    static func migrated(hatchDay: Int, today: Int) -> GrowthState {
        GrowthState(hatched: true, goodDays: max(0, today - hatchDay), lastDay: today - 1, hatchedDay: hatchDay)
    }

    static func progress(_ s: GrowthState) -> (fraction: Double, daysLeft: Int)? {
        guard s.hatched else { return nil }
        let st = stage(for: s.goodDays)
        guard let nx = st.next else { return nil }
        let span = Double(nx.minDays - st.minDays)
        return (min(1, max(0, Double(s.goodDays - st.minDays) / span)), nx.minDays - s.goodDays)
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
