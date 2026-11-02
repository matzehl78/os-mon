// EvolutionTracker.swift: Swift-Port von EvolutionTracker.kt (tägliche Evolutions-Logik)
//
// Targets: Haupt-App (und Test-Target). Abhängigkeiten: PetCore.swift, ScreenTimeShared.swift (UsageGroup).
// Rein und ohne Plattformzugriffe. Die Regeln stehen im Kopf von EvolutionTracker.kt.
// Parität: golden_evolution.json (erzeugt von golden_evolution.py), geprüft in EvolutionGoldenTests.swift.
//
// Anteile werden als [String: Float] geführt (Schlüssel = UsageGroup.rawValue), damit der Zustand
// ohne Sonderbehandlung Codable ist und im App-Group-Speicher abgelegt werden kann.

import Foundation

typealias GroupShares = [String: Float]

enum PetEvoKind: String, Codable { case evolved, metamorphosis }

struct PetEvoEvent: Codable, Equatable {
    var kind: PetEvoKind
    var from: PetForm?
    var to: PetForm
    var epochDay: Int
}

/// Ein abgeschlossener Tag. epochDay = Tage seit 1970-01-01 (lokales Datum).
/// shares nil = keine Nutzungsdaten; avgWellbeing nil = keine Messung.
struct PetDayRecord: Codable, Equatable {
    var epochDay: Int
    var shares: GroupShares?
    var totalMinutes: Float?
    var avgWellbeing: Float?
}

struct PetEvoState: Codable, Equatable {
    var hatchDay: Int
    var history: [PetDayRecord] = []
    var blended: GroupShares? = nil
    var current: PetEvolution? = nil
    var lastEvolutionDay: Int? = nil
    var challenger: PetForm? = nil
    var challengerSince: Int? = nil
    var pendingEvent: PetEvoEvent? = nil
}

enum EvolutionTracker {
    static let evolveAge = 11
    static let minUsableDays = 5
    static let fallbackAge = 21
    static let window = 14
    static let historyMax = 28
    static let metaInterval = 21
    static let challengerDays = 14
    static let margin: Float = 0.10

    private static let keep: Float = powf(0.5, 1.0 / 5.0)     // Gewicht der Vergangenheit pro Tag

    private static let formGroup: [PetForm: UsageGroup] = [
        .chatterling: .social,
        .bingeon: .entertainment,
        .pixelo: .games,
        .zenfuchs: .productivity,
    ]                                                          // wandero hat keine Kategorie

    static func isUsable(_ r: PetDayRecord) -> Bool {
        r.shares != nil && (r.totalMinutes ?? 0) > 0
    }

    static func blend(prev: GroupShares?, today: GroupShares) -> GroupShares {
        var out: GroupShares = [:]
        for g in UsageGroup.allCases {
            let t = today[g.rawValue] ?? 0
            if let prev {
                out[g.rawValue] = keep * (prev[g.rawValue] ?? 0) + (1 - keep) * t
            } else {
                out[g.rawValue] = t
            }
        }
        return out
    }

    private static func groupMargin(_ blended: GroupShares?, from: PetForm, to: PetForm) -> Float? {
        guard let fg = formGroup[from], let tg = formGroup[to], let blended else { return nil }
        return (blended[tg.rawValue] ?? 0) - (blended[fg.rawValue] ?? 0)
    }

    private static func groupShares(_ s: GroupShares?) -> [UsageGroup: Float] {
        var out: [UsageGroup: Float] = [:]
        for (k, v) in s ?? [:] { if let g = UsageGroup(rawValue: k) { out[g] = v } }
        return out
    }

    /// Tage müssen in aufsteigender Reihenfolge abgeschlossen werden; ein bereits abgeschlossener Tag wird ignoriert.
    static func closeDay(_ state: PetEvoState, _ r: PetDayRecord, config: PetConfig) -> PetEvoState {
        var s = state
        if let last = s.history.last?.epochDay, r.epochDay <= last { return s }

        s.history = Array((s.history + [r]).suffix(historyMax))
        if isUsable(r), let today = r.shares { s.blended = blend(prev: s.blended, today: today) }

        let win = Array(s.history.suffix(window))
        let usableWindow = win.filter { isUsable($0) }
        let avgDaily: Float = usableWindow.isEmpty
            ? 0 : usableWindow.reduce(0) { $0 + ($1.totalMinutes ?? 0) } / Float(usableWindow.count)
        let wellbeing = win.compactMap { $0.avgWellbeing }
        let avgW: Float = wellbeing.isEmpty ? 50 : wellbeing.reduce(0, +) / Float(wellbeing.count)

        let cand = PetCore.evolve(shares: groupShares(s.blended), avgDailyMinutes: avgDaily,
                                  avgWellbeing14: avgW, config: config)
        let age = r.epochDay - s.hatchDay

        guard let cur = s.current else {
            let ready = age >= evolveAge && (usableWindow.count >= minUsableDays || age >= fallbackAge)
            if ready {
                s.current = cand
                s.lastEvolutionDay = r.epochDay
                s.pendingEvent = PetEvoEvent(kind: .evolved, from: nil, to: cand.form, epochDay: r.epochDay)
            }
            return s
        }

        if cand.form == cur.form {
            s.current = cand                                   // Hybrid und Branch nachziehen
            s.challenger = nil
            s.challengerSince = nil
            return s
        }

        var updated = cur
        updated.branch = cand.branch                           // nur den Gesundheits-Zweig aktualisieren
        s.current = updated
        if s.challenger != cand.form {
            s.challenger = cand.form
            s.challengerSince = r.epochDay
        }
        let since = s.challengerSince ?? r.epochDay
        let stable = r.epochDay - since >= challengerDays - 1
        let intervalOk = s.lastEvolutionDay.map { r.epochDay - $0 >= metaInterval } ?? true
        let m = groupMargin(s.blended, from: cur.form, to: cand.form)
        let marginOk = m.map { $0 >= margin } ?? true
        if stable && intervalOk && marginOk {
            s.pendingEvent = PetEvoEvent(kind: .metamorphosis, from: cur.form, to: cand.form, epochDay: r.epochDay)
            s.current = cand
            s.lastEvolutionDay = r.epochDay
            s.challenger = nil
            s.challengerSince = nil
        }
        return s
    }

    /// Die Oberfläche hat das Ereignis gezeigt.
    static func acknowledge(_ state: PetEvoState) -> PetEvoState {
        var s = state
        s.pendingEvent = nil
        return s
    }
}
