// PushPolicy.swift: Swift-Port von PushPolicy.kt (Regel-Engine für die 5 Haustier-Benachrichtigungen)
//
// Targets: Haupt-App, DeviceActivity-Monitor-Extension, Test-Target. Rein, keine Plattform-Imports.
// Regeln und Texte: siehe Kopf von PushPolicy.kt. Parität: golden_push.json (erzeugt von golden_push.py),
// geprüft in PushGoldenTests.swift und PushGoldenTest.kt. Änderungen immer in allen drei Fassungen.
//
// lastSentMs ist [String: Int64] (Schlüssel = PushType.rawValue), damit PushState ohne Sonderbehandlung
// Codable ist und als JSON-Objekt im App-Group-Speicher liegt.

import Foundation

/// Reihenfolge = Priorität (batteryLow zuerst).
enum PushType: String, Codable, CaseIterable {
    case batteryLow = "BATTERY_LOW"
    case nightUse = "NIGHT_USE"
    case storageFull = "STORAGE_FULL"
    case screenGoal = "SCREEN_GOAL"
    case stepsPraise = "STEPS_PRAISE"
}

struct PushSignals: Codable, Equatable {
    var batteryPct: Float? = nil
    var charging: Bool = false
    var freeStorageRatio: Float? = nil
    var screenMinutes: Float? = nil
    var screenGoalMin: Float = 180
    var nightActiveMinutes: Float? = nil      // aktive Bildschirmzeit seit 23:30
    var steps: Int? = nil
    var stepGoal: Int = 7000
}

/// Lokale Uhrzeit liefert die Plattform (Engine bleibt zeitzonenfrei). dayKey z. B. "2026-11-02".
struct PushClock: Codable, Equatable {
    var nowMs: Int64
    var minuteOfDay: Int
    var dayKey: String
}

struct PushState: Codable, Equatable {
    var dayKey: String = ""
    var sentToday: Int = 0
    var lastSentMs: [String: Int64] = [:]
    var lastAnyMs: Int64? = nil
    var ignoredStreak: Int = 0                // aufeinanderfolgend ignorierte Pushes
}

struct PushMessage: Codable, Equatable {
    var type: PushType
    var title: String
    var body: String
    var actions: [String] = ["Fokus 15 Min", "Später"]
}

enum PushPolicy {
    static let baseCap = 3
    static let minGapMs: Int64 = 2 * 3_600_000
    private static let hourMs: Int64 = 3_600_000
    private static let dayMs: Int64 = 24 * hourMs

    private static let cooldownMs: [PushType: Int64] = [
        .batteryLow: 6 * hourMs,
        .nightUse: 20 * hourMs,                // einmal pro Nacht
        .storageFull: 7 * dayMs,               // Speicher voll nervt sonst schnell
        .screenGoal: 20 * hourMs,              // einmal pro Tag
        .stepsPraise: 20 * hourMs,
    ]

    /// 3 Pushes/Tag; je 3 ignorierte in Folge sinkt das Limit um 1 (Minimum 1).
    static func dailyCap(_ s: PushState) -> Int { baseCap - min(2, s.ignoredStreak / 3) }

    static func isQuiet(_ minuteOfDay: Int) -> Bool { minuteOfDay >= 22 * 60 || minuteOfDay < 8 * 60 }

    private static func rollover(_ s: PushState, _ dayKey: String) -> PushState {
        guard s.dayKey != dayKey else { return s }
        var r = s
        r.dayKey = dayKey
        r.sentToday = 0
        return r
    }

    private static func eligible(_ type: PushType, _ sig: PushSignals, _ clock: PushClock) -> Bool {
        switch type {
        case .batteryLow:
            return (sig.batteryPct ?? 100) <= 15 && !sig.charging
        case .nightUse:
            return (clock.minuteOfDay >= 23 * 60 + 30 || clock.minuteOfDay < 3 * 60)
                && (sig.nightActiveMinutes ?? 0) >= 10
        case .storageFull:
            return (sig.freeStorageRatio ?? 1) < 0.08
        case .screenGoal:
            return (sig.screenMinutes ?? 0) >= sig.screenGoalMin
        case .stepsPraise:
            // 80 % des Ziels erreicht (ganzzahlig gerechnet, kein Float-Vergleich)
            guard let steps = sig.steps else { return false }
            return steps * 5 >= sig.stepGoal * 4
        }
    }

    /// Liefert die Push, die jetzt gesendet werden soll, oder nil.
    static func evaluate(_ state: PushState, _ sig: PushSignals, _ clock: PushClock) -> PushMessage? {
        let s = rollover(state, clock.dayKey)
        if s.sentToday >= dailyCap(s) { return nil }
        if let last = s.lastAnyMs, clock.nowMs - last < minGapMs { return nil }

        let quiet = isQuiet(clock.minuteOfDay)
        for type in PushType.allCases {
            if quiet && type != .nightUse { continue }
            if let last = s.lastSentMs[type.rawValue], clock.nowMs - last < cooldownMs[type]! { continue }
            if eligible(type, sig, clock) { return message(type, sig, clock) }
        }
        return nil
    }

    // MARK: Zustandsübergänge

    static func onSent(_ state: PushState, _ type: PushType, _ clock: PushClock) -> PushState {
        var s = rollover(state, clock.dayKey)
        s.sentToday += 1
        s.lastSentMs[type.rawValue] = clock.nowMs
        s.lastAnyMs = clock.nowMs
        return s
    }

    /// Push wurde weggewischt oder verfiel unbeachtet.
    static func onIgnored(_ state: PushState) -> PushState {
        var s = state
        s.ignoredStreak += 1
        return s
    }

    /// Nutzer hat getippt oder eine Aktion gewählt.
    static func onEngaged(_ state: PushState) -> PushState {
        var s = state
        s.ignoredStreak = 0
        return s
    }

    // MARK: Texte (identisch zu Kotlin; Rundung "halbe aufwärts" wie roundToInt)

    private static func roundInt(_ x: Float) -> Int { Int(x.rounded(.toNearestOrAwayFromZero)) }

    private static func pad2(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }

    private static func thousands(_ n: Int) -> String {
        let digits = Array(String(n))
        var out = ""
        for (i, ch) in digits.enumerated() {
            if i > 0 && (digits.count - i) % 3 == 0 { out.append(".") }
            out.append(ch)
        }
        return out
    }

    private static func duration(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        if h == 0 { return "\(m) Min." }
        if m == 0 { return "\(h) Std." }
        return "\(h) Std. \(m) Min."
    }

    static func message(_ type: PushType, _ sig: PushSignals, _ clock: PushClock) -> PushMessage {
        switch type {
        case .batteryLow:
            return PushMessage(type: type, title: "Gähn … 🪫",
                               body: "Ich hab nur noch \(roundInt(sig.batteryPct ?? 0)) % Energie. "
                                   + "Gibst du mir Strom, bevor ich einschlafe?")
        case .storageFull:
            return PushMessage(type: type, title: "Die Haut spannt! 🍔",
                               body: "Nur noch \(roundInt((sig.freeStorageRatio ?? 0) * 100)) % Platz, "
                                   + "ich passe kaum noch in mich rein. Misten wir 1 GB alte Videos aus?")
        case .screenGoal:
            return PushMessage(type: type, title: "Meine Augen werden eckig 👀",
                               body: "\(duration(roundInt(sig.screenMinutes ?? 0))) Bildschirm heute. "
                                   + "Eine 10-Minuten-Pause für uns beide?")
        case .nightUse:
            return PushMessage(type: type, title: "Psst … 🌙",
                               body: "Es ist \(pad2(clock.minuteOfDay / 60)):\(pad2(clock.minuteOfDay % 60)) "
                                   + "und ich kann nicht schlafen. Legst du uns schlafen?")
        case .stepsPraise:
            let steps = sig.steps ?? 0
            let body = steps >= sig.stepGoal
                ? "\(thousands(steps)) Schritte, Ziel geschafft! Ich bin ganz stolz auf uns."
                : "\(thousands(steps)) Schritte, wow. Noch eine Runde frische Luft, dann sind wir strahlend?"
            return PushMessage(type: type, title: "Ich hab Muckis! 💪", body: body)
        }
    }
}
