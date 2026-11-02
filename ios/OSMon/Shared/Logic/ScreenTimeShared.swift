// ScreenTimeShared.swift
// Target-Mitgliedschaft: Haupt-App, DeviceActivityMonitor-Extension und Widget-Extension.
// (Diese Datei importiert bewusst kein FamilyControls, damit sie überall einbindbar ist.)

import Foundation

enum UsageGroup: String, CaseIterable, Codable {
    case social, entertainment, games, productivity, other

    var title: String {
        switch self {
        case .social:        "Social & Messaging"
        case .entertainment: "Video & Streaming"
        case .games:         "Games"
        case .productivity:  "Produktivität & Lernen"
        case .other:         "Sonstiges"
        }
    }
}

enum STShared {
    static let appGroup = "group.com.example.osmon"      // gleiche App Group wie das Widget
    static let widgetKind = "OSMonWidget"

    // Auflösung der Schwellen. Das System begrenzt Anzahl/Kombination von Schedules und
    // Events: Werte im Spike auf echten Geräten testen und ggf. reduzieren.
    static let stepMinutes = 10
    static let maxEventsPerGroup = 36                    // bis 6 h pro Gruppe und Tag
    static let nightStepMinutes = 15
    static let maxNightEvents = 24                       // bis 6 h

    static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    static func dayKey(_ date: Date = .now) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    private static func key(_ name: String, _ day: String) -> String { "st.\(day).\(name)" }

    static func minutes(_ name: String, day: String = dayKey()) -> Int {
        defaults.integer(forKey: key(name, day))
    }

    /// Schwellen sind kumulativ ("jetzt 30 Min erreicht"): Maximum statt Addition, damit
    /// doppelte oder verspätete Callbacks nichts verfälschen (idempotent).
    static func raise(_ name: String, to value: Int, day: String = dayKey()) {
        let k = key(name, day)
        if value > defaults.integer(forKey: k) { defaults.set(value, forKey: k) }
    }

    static func totalMinutes(day: String = dayKey()) -> Int {
        UsageGroup.allCases.reduce(0) { $0 + minutes($1.rawValue, day: day) }
    }

    /// Tagesanteile je Gruppe (Grundlage für die Evolutions-Dominanz).
    static func shares(day: String = dayKey()) -> [UsageGroup: Double] {
        let total = Double(totalMinutes(day: day))
        guard total > 0 else { return [:] }
        return Dictionary(uniqueKeysWithValues: UsageGroup.allCases.map {
            ($0, Double(minutes($0.rawValue, day: day)) / total)
        })
    }

    /// Stress S (0-100), iOS-Variante ohne Pickups: Gewichte 0,72 / 0,28.
    static func stress(goalMinutes z: Double = 180, day: String = dayKey()) -> Double {
        let t = Double(totalMinutes(day: day))
        let n = Double(minutes("night", day: day))
        return min(100, max(0, 100 * (0.72 * t / (2 * z) + 0.28 * n / 45)))
    }
}

// MARK: - Ziele (in allen Targets lesbar: App, Widget, Monitor)

/// Vom Nutzer einstellbare Ziele. Ohne Eintrag gelten die PRD-Startwerte (180 Min, 7.000 Schritte).
enum PetGoals {
    static let screenRange = 90...240          // Minuten, Schritt 15 (PRD: Z begrenzt auf 90..240)
    static let stepsRange = 3000...15000       // Schritt 500

    static var screenMinutes: Int {
        get { STShared.defaults.object(forKey: "goal.screenMin") as? Int ?? 180 }
        set { STShared.defaults.set(min(max(newValue, screenRange.lowerBound), screenRange.upperBound), forKey: "goal.screenMin") }
    }

    static var steps: Int {
        get { STShared.defaults.object(forKey: "goal.steps") as? Int ?? 7000 }
        set { STShared.defaults.set(min(max(newValue, stepsRange.lowerBound), stepsRange.upperBound), forKey: "goal.steps") }
    }
}
