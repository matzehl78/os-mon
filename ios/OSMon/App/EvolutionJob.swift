// EvolutionJob.swift: iOS-Anbindung des EvolutionTracker (Speicher, Tageslog, Tagesabschluss)
//
// Targets: Haupt-App. Abhängigkeiten: EvolutionTracker.swift, PetCore.swift, ScreenTimeShared.swift,
// PetSync.swift (EpochDay, DayLog).
//
// Anders als auf Android braucht iOS keinen Hintergrundjob: Die DeviceActivity-Zähler stehen pro Kalendertag
// im App-Group-Speicher (st.<Datum>.<Gruppe>) und bleiben erhalten. Beim nächsten Öffnen der App holt
// closePastDays alle verpassten Tage nach (idempotent). Der Mittelwert des Wohlbefindens (DayLog) wird nur
// gemessen, wenn App, Widget oder Kurzbefehl einen Tick gerechnet haben (PetSync.tick schreibt es).
// Das Widget tickt bei jedem Timeline-Aufruf, die Datenbasis hängt also nicht mehr am App-Öffnen.

import Foundation

// EpochDay und DayLog liegen in PetSync.swift (App und Widget schreiben das Tageslog).

// MARK: - Speicher (App Group)

enum EvolutionStore {
    private static let key = "pet.evostate"
    private static var defaults: UserDefaults { STShared.defaults }

    static func load() -> PetEvoState? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(PetEvoState.self, from: $0) }
    }

    static func save(_ s: PetEvoState) {
        if let data = try? JSONEncoder().encode(s) { defaults.set(data, forKey: key) }
    }

    /// Schlüpftag festlegen (einmalig, beim Onboarding-Abschluss bzw. beim ersten Aufruf).
    @discardableResult
    static func ensureHatched(today: Int = EpochDay.of()) -> PetEvoState {
        if let s = load() { return s }
        let s = PetEvoState(hatchDay: today)
        save(s)
        return s
    }

    static func acknowledge() {
        if let s = load() { save(EvolutionTracker.acknowledge(s)) }
    }
}

// MARK: - Tagesabschluss

enum EvolutionJob {
    private static let maxCatchUpDays = 14                   // die Zähler bleiben erhalten, großzügiger als Android

    private static func record(day: Int, screenOn: Bool) -> PetDayRecord {
        var shares: GroupShares? = nil
        var total: Float? = nil
        if screenOn {
            let key = STShared.dayKey(EpochDay.date(day))
            let minutes = Dictionary(uniqueKeysWithValues:
                UsageGroup.allCases.map { ($0.rawValue, Float(STShared.minutes($0.rawValue, day: key))) })
            let sum = minutes.values.reduce(0, +)
            if sum > 0 {
                shares = minutes.mapValues { $0 / sum }
                total = sum
            }
        }
        return PetDayRecord(epochDay: day, shares: shares, totalMinutes: total, avgWellbeing: DayLog.average(day: day))
    }

    /// Schließt alle noch offenen Tage vor heute (idempotent) und speichert den Zustand nur bei Änderung.
    @discardableResult
    static func closePastDays(config: PetConfig = PetSync.config, today: Int = EpochDay.of()) -> PetEvoState {
        var s = EvolutionStore.ensureHatched(today: today)
        let lastClosed = s.history.last?.epochDay ?? (s.hatchDay - 1)
        let screenOn = STShared.defaults.bool(forKey: "feature.screentime")

        let first = max(lastClosed + 1, today - maxCatchUpDays)
        guard first < today else { return s }
        for day in first..<today {
            s = EvolutionTracker.closeDay(s, record(day: day, screenOn: screenOn), config: config)
        }
        EvolutionStore.save(s)
        return s
    }
}
