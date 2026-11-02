// ScreenTimeApp.swift
// Target: nur Haupt-App. Capabilities: Family Controls (Entitlement bei Apple beantragen!),
// App Groups. Mindestversion iOS 17 (zusammen mit dem Widget).

import SwiftUI
import FamilyControls
import DeviceActivity

@MainActor
final class ScreenTimeManager: ObservableObject {
    @Published var selections: [UsageGroup: FamilyActivitySelection] = [:]
    private let center = DeviceActivityCenter()

    init() { load() }

    // MARK: Berechtigung

    /// Zeigt den System-Dialog. Für Einzelnutzer: .individual.
    func requestAuthorization() async throws {
        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
    }

    // MARK: Auswahl speichern/laden (Tokens sind opak, aber Codable)

    func save() {
        for (group, sel) in selections {
            if let data = try? JSONEncoder().encode(sel) {
                STShared.defaults.set(data, forKey: "sel.\(group.rawValue)")
            }
        }
    }

    private func load() {
        for group in UsageGroup.allCases {
            if let data = STShared.defaults.data(forKey: "sel.\(group.rawValue)"),
               let sel = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) {
                selections[group] = sel
            }
        }
    }

    // MARK: Monitoring

    /// Registriert Schwellen-Events. Die Monitor-Extension zählt die Callbacks im App-Group-Speicher.
    /// Wichtig: Gruppen sollten sich nicht überschneiden, sonst werden Minuten doppelt gezählt.
    func startMonitoring() throws {
        save()
        center.stopMonitoring()

        // Tagesfenster 00:00-23:59, täglich wiederholt
        let day = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )

        var dayEvents: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        for (group, sel) in selections where !isEmpty(sel) {
            for i in 1...STShared.maxEventsPerGroup {
                let minutes = i * STShared.stepMinutes
                dayEvents[DeviceActivityEvent.Name("\(group.rawValue).\(minutes)")] =
                    DeviceActivityEvent(
                        applications: sel.applicationTokens,
                        categories: sel.categoryTokens,
                        webDomains: sel.webDomainTokens,
                        threshold: DateComponents(hour: minutes / 60, minute: minutes % 60)
                    )
            }
        }
        try center.startMonitoring(DeviceActivityName("osmon.day"), during: day, events: dayEvents)

        // Nachtfenster 22:30-06:00 über alle Gruppen. Einmal pro Nacht gezählt;
        // Callbacks nach Mitternacht landen im Zähler des neuen Kalendertags.
        let night = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 22, minute: 30),
            intervalEnd: DateComponents(hour: 6, minute: 0),
            repeats: true
        )
        let all = union(of: Array(selections.values))
        var nightEvents: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        for i in 1...STShared.maxNightEvents {
            let minutes = i * STShared.nightStepMinutes
            nightEvents[DeviceActivityEvent.Name("night.\(minutes)")] = DeviceActivityEvent(
                applications: all.apps, categories: all.cats, webDomains: all.domains,
                threshold: DateComponents(hour: minutes / 60, minute: minutes % 60)
            )
        }
        try center.startMonitoring(DeviceActivityName("osmon.night"), during: night, events: nightEvents)
    }

    /// Beendet alle Zeitpläne (Einstellungen: Bildschirmzeit aus, Daten löschen).
    func stopMonitoring() {
        center.stopMonitoring()
    }

    /// Autorisierung zurückgeben (nur beim vollständigen Löschen). Fehler sind unkritisch.
    func revokeAuthorization() async {
        await withCheckedContinuation { cont in
            AuthorizationCenter.shared.revokeAuthorization { _ in cont.resume() }
        }
    }

    var authorizationStatus: AuthorizationStatus { AuthorizationCenter.shared.authorizationStatus }

    var hasSelection: Bool { selections.values.contains { !isEmpty($0) } }

    // MARK: Helfer

    private func isEmpty(_ s: FamilyActivitySelection) -> Bool {
        s.applicationTokens.isEmpty && s.categoryTokens.isEmpty && s.webDomainTokens.isEmpty
    }

    private func union(of selections: [FamilyActivitySelection])
        -> (apps: Set<ApplicationToken>, cats: Set<ActivityCategoryToken>, domains: Set<WebDomainToken>) {
        var apps = Set<ApplicationToken>(), cats = Set<ActivityCategoryToken>(), domains = Set<WebDomainToken>()
        for s in selections {
            apps.formUnion(s.applicationTokens)
            cats.formUnion(s.categoryTokens)
            domains.formUnion(s.webDomainTokens)
        }
        return (apps, cats, domains)
    }
}

// Die Gruppen-Auswahl liegt im Onboarding (OnboardingView.swift) und in den Einstellungen (SettingsView.swift).
