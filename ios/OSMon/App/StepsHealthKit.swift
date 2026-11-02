// StepsHealthKit.swift
// Target: Haupt-App (zusätzlich ScreenTimeShared.swift, PetSync.swift und PushResponder.swift im Target).
// Capabilities: HealthKit + "Background Delivery" (HealthKit-Capability, Option dafür aktivieren),
//               App Groups.
// Info.plist: NSHealthShareUsageDescription = "OS-Mon liest deine Schritte, um die Vitalität
//             deines Wesens zu berechnen. Die Daten bleiben auf deinem Gerät."
//
// Start (MUSS bei jedem Prozessstart laufen, auch bei Hintergrund-Starts durch HealthKit,
// z. B. in App.init oder application(_:didFinishLaunchingWithOptions:)):
//   Task { try? await StepsProvider.shared.startBackgroundDelivery() }

import Foundation
import HealthKit
import WidgetKit

// StepsStore (Speicher in der App Group) liegt in PetSync.swift, damit auch das Widget ihn liest.

// MARK: - HealthKit

final class StepsProvider {
    static let shared = StepsProvider()

    private let store = HKHealthStore()
    private let type = HKQuantityType(.stepCount)
    private var observer: HKObserverQuery?

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    /// Nur Leserechte. Hinweis: iOS verrät nicht, ob der Nutzer das Lesen erlaubt hat.
    /// Verweigerung und "keine Daten" sehen identisch aus (Ergebnis 0 bzw. nil).
    func requestAuthorization() async throws {
        guard isAvailable else { return }
        try await store.requestAuthorization(toShare: [], read: [type])
    }

    /// Schritte im Zeitraum. Die Statistik-Query dedupliziert überlappende Quellen
    /// (iPhone + Apple Watch). Manuell eingetragene Werte werden ausgeschlossen.
    func steps(from start: Date, to end: Date = .now) async throws -> Int {
        let time = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        // "!= YES" schließt auch Samples ohne dieses Metadatenfeld ein (Standardfall).
        let notManual = NSPredicate(format: "metadata.%K != YES", HKMetadataKeyWasUserEntered)
        let predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [time, notManual])

        let descriptor = HKStatisticsQueryDescriptor(
            predicate: .quantitySample(type: type, predicate: predicate),
            options: .cumulativeSum
        )
        let stats = try await descriptor.result(for: store)
        return Int(stats?.sumQuantity()?.doubleValue(for: .count()) ?? 0)
    }

    /// Hintergrund-Zustellung: Das System weckt die App, wenn neue Schritte vorliegen.
    /// Für stepCount liefert iOS höchstens etwa stündlich, nicht in Echtzeit.
    func startBackgroundDelivery() async throws {
        guard isAvailable else { return }
        try await store.enableBackgroundDelivery(for: type, frequency: .hourly)

        guard observer == nil else { return }
        let query = HKObserverQuery(sampleType: type, predicate: nil) { [weak self] _, completion, error in
            // completion() MUSS immer aufgerufen werden, sonst drosselt iOS die weiteren Wake-ups.
            guard error == nil, let self else { completion(); return }
            Task {
                await self.refresh()
                completion()
            }
        }
        store.execute(query)
        observer = query
    }

    /// Hintergrund-Zustellung beenden (Einstellungen: Schritte aus, Daten löschen). Die Leseerlaubnis selbst
    /// kann nur die Person in den iOS-Einstellungen (Datenschutz › Health) zurücknehmen.
    func stopBackgroundDelivery() async {
        if let observer { store.stop(observer) }
        observer = nil
        try? await store.disableAllBackgroundDelivery()
    }

    /// Liest die heutigen Schritte, speichert sie und lässt danach den gemeinsamen Ablauf laufen
    /// (Tick, Widget bei Bedarf, Push-Regeln). Der HealthKit-Wake-up ist damit einer der wenigen
    /// regelmäßigen Hintergrund-Zeitpunkte, an denen iOS auch Akku- und Speicher-Meldungen erlaubt.
    func refresh() async {
        let start = Calendar.current.startOfDay(for: .now)
        // Bei gesperrtem Gerät sind Gesundheitsdaten verschlüsselt: Query wirft dann einen Fehler.
        // Dann alten Wert behalten.
        guard let n = try? await steps(from: start) else { return }

        let old = StepsStore.today
        guard n >= old else { return }            // Schritte steigen am selben Tag nur: kleinere Werte ignorieren
        StepsStore.today = n
        await PetPushRun.tickAndDeliver()
    }
}
