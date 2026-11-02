// PushDelivery.swift: Zustellung der Haustier-Benachrichtigungen (iOS 17+)
//
// Targets: Haupt-App UND DeviceActivity-Monitor-Extension. Dateien in beiden: PushPolicy.swift,
// ScreenTimeShared.swift. Kein UIKit, kein PetSync: die Monitor-Extension hat ein knappes Speicherlimit.
// Die Reaktion auf Tipps und Aktionen (Delegate) liegt in PushResponder.swift (nur App).
//
// Wer zustellt (Aufrufer):
//   - Monitor-Extension bei Bildschirmzeit-Schwellen: Bildschirmziel und Nachtnutzung (OSMonMonitor.swift)
//   - Kurzbefehl "OS-Mon aktualisieren" (Akku < 20 %): Akku, Speicher, Schritte (OnboardingView.swift)
//   - HealthKit-Wake-up (ca. stündlich): Schritte, Akku, Speicher (StepsHealthKit.swift)
//   - Hintergrund-Aktualisierung (BGAppRefreshTask, vom System getaktet): alles (PushResponder.swift)
// Das Widget stellt bewusst nicht zu: Widget-Extensions sind dafür nicht gedacht und würden bei
// jedem Timeline-Aufruf Pushes auslösen können.
//
// Alle Wege teilen sich einen PushState im App-Group-Speicher ("push.state"); Limit, Abstand und
// Cooldowns gelten deshalb über Prozessgrenzen hinweg. Zwei Prozesse im selben Moment können im
// ungünstigen Fall beide zustellen; der 2-h-Mindestabstand macht das in der Praxis sehr selten.
//
// "Ignoriert" erkennt iOS nicht direkt: Eine zugestellte Meldung, die nach 2 Stunden noch in der
// Mitteilungszentrale liegt, gilt als ignoriert (sweepUnattended). Wegwischen meldet iOS nur bei
// Kategorien mit .customDismissAction an die App (PushResponder) und das auch nicht immer.

import Foundation
import UserNotifications

// MARK: - Uhr der Plattform

extension PushClock {
    static func now(_ date: Date = .now) -> PushClock {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return PushClock(nowMs: Int64((date.timeIntervalSince1970 * 1000).rounded()),
                         minuteOfDay: (c.hour ?? 0) * 60 + (c.minute ?? 0),
                         dayKey: STShared.dayKey(date))
    }
}

// MARK: - Speicher (App Group)

enum PushStore {
    private static let key = "push.state"

    static func load() -> PushState {
        STShared.defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(PushState.self, from: $0) } ?? PushState()
    }

    static func save(_ s: PushState) {
        if let data = try? JSONEncoder().encode(s) { STShared.defaults.set(data, forKey: key) }
    }

    /// Lesen-Ändern-Schreiben unter einer Sperre innerhalb des Prozesses.
    static func update(_ change: (PushState) -> PushState) {
        lock.lock(); defer { lock.unlock() }
        save(change(load()))
    }

    static let lock = NSLock()
}

// MARK: - Zustellung

enum PushDelivery {
    static let category = "OSMON_PET"
    static let actionFocus = "OSMON_FOCUS_15"
    static let actionLater = "OSMON_LATER"
    static let typeKey = "osmonType"

    /// Schalter "Meldungen vom Wesen" in den Einstellungen (Opt-out, Standard: an).
    static var optedOut: Bool {
        get { STShared.defaults.bool(forKey: "push.optOut") }
        set { STShared.defaults.set(newValue, forKey: "push.optOut") }
    }
    private static let unattendedAfter: TimeInterval = 2 * 3600

    /// Einmal beim App-Start registrieren (PushResponder.setUp ruft das auf). Die Monitor-Extension
    /// braucht keine Registrierung, die Kategorie gilt systemweit für die App.
    static func registerCategories() {
        let focus = UNNotificationAction(identifier: actionFocus, title: "Fokus 15 Min", options: [])
        let later = UNNotificationAction(identifier: actionLater, title: "Später", options: [])
        let cat = UNNotificationCategory(identifier: category, actions: [focus, later], intentIdentifiers: [],
                                         options: [.customDismissAction])
        UNUserNotificationCenter.current().setNotificationCategories([cat])
    }

    /// Signale aus den Speicherständen, die jeder Prozess lesen kann (Bildschirmzeit, Schritte).
    /// Akku und Speicher gibt nur die App mit (siehe Aufrufer), die Monitor-Extension lässt sie leer.
    static func baseSignals(now: Date = .now, screenGoalMin: Float = Float(PetGoals.screenMinutes),
                            stepGoal: Int = PetGoals.steps) -> PushSignals {
        let d = STShared.defaults
        let day = STShared.dayKey(now)
        var s = PushSignals(screenGoalMin: screenGoalMin, stepGoal: stepGoal)
        if d.bool(forKey: "feature.screentime") { s.screenMinutes = Float(STShared.totalMinutes(day: day)) }
        if d.bool(forKey: "feature.steps") { s.steps = d.integer(forKey: "steps.\(day)") }
        return s
    }

    /// Prüft die Regeln und stellt höchstens eine Meldung zu. Ruft completion mit dem zugestellten Typ.
    static func evaluateAndDeliver(_ signals: PushSignals, now: Date = .now,
                                   completion: @escaping (PushType?) -> Void = { _ in }) {
        let center = UNUserNotificationCenter.current()
        // In den Einstellungen abgeschaltet: nichts zustellen (die iOS-Erlaubnis bleibt davon unberührt).
        guard !optedOut else { completion(nil); return }
        center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else {
                completion(nil); return
            }
            sweepUnattended(now: now) {
                let clock = PushClock.now(now)
                var chosen: PushMessage?
                PushStore.lock.lock()
                let state = PushStore.load()
                if let msg = PushPolicy.evaluate(state, signals, clock) {
                    chosen = msg
                    // Vor dem Zustellen als gesendet markieren: Ein paralleler Aufruf sieht dann sofort den Abstand.
                    PushStore.save(PushPolicy.onSent(state, msg.type, clock))
                }
                PushStore.lock.unlock()

                guard let msg = chosen else { completion(nil); return }
                let content = UNMutableNotificationContent()
                content.title = msg.title
                content.body = msg.body
                content.sound = .default
                content.categoryIdentifier = category
                content.threadIdentifier = "osmon"
                content.userInfo = [typeKey: msg.type.rawValue]
                // Eine Meldung je Typ: eine neuere ersetzt eine ältere desselben Typs.
                let request = UNNotificationRequest(identifier: "osmon.\(msg.type.rawValue)", content: content, trigger: nil)
                center.add(request) { error in completion(error == nil ? msg.type : nil) }
            }
        }
    }

    /// async-Variante für App Intents und HealthKit.
    @discardableResult
    static func evaluateAndDeliver(_ signals: PushSignals, now: Date = .now) async -> PushType? {
        await withCheckedContinuation { cont in
            evaluateAndDeliver(signals, now: now) { cont.resume(returning: $0) }
        }
    }

    /// Für kurzlebige Extensions (Monitor): wartet höchstens `timeout` Sekunden, damit der Prozess
    /// nicht endet, bevor die Meldung beim System angekommen ist. Nicht auf dem Main-Thread aufrufen.
    static func evaluateAndDeliverBlocking(_ signals: PushSignals, now: Date = .now, timeout: TimeInterval = 3) {
        let done = DispatchSemaphore(value: 0)
        evaluateAndDeliver(signals, now: now) { _ in done.signal() }
        _ = done.wait(timeout: .now() + timeout)
    }

    /// Meldungen, die seit über 2 Stunden unbeachtet zugestellt sind, zählen als ignoriert und werden entfernt.
    static func sweepUnattended(now: Date = .now, completion: @escaping () -> Void = {}) {
        let center = UNUserNotificationCenter.current()
        center.getDeliveredNotifications { delivered in
            let stale = delivered.filter {
                $0.request.content.categoryIdentifier == category && now.timeIntervalSince($0.date) > unattendedAfter
            }
            if !stale.isEmpty {
                center.removeDeliveredNotifications(withIdentifiers: stale.map(\.request.identifier))
                PushStore.update { s in stale.reduce(s) { acc, _ in PushPolicy.onIgnored(acc) } }
            }
            completion()
        }
    }
}
