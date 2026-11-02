// PushResponder.swift: Reaktion auf Benachrichtigungen + Hintergrund-Aktualisierung (iOS 17+, nur App-Target)
//
// Abhängigkeiten: PushDelivery.swift, PushPolicy.swift, PetSync.swift, PetCore.swift, ScreenTimeShared.swift.
//
// Einrichtung:
//   1. Beim App-Start (App.init bzw. application(_:didFinishLaunchingWithOptions:)), VOR dem Ende des Starts:
//        PushResponder.shared.setUp()          // Delegate + Kategorien + Hintergrund-Aufgabe registrieren
//   2. Wenn die App in den Hintergrund geht (scenePhase == .background):
//        PetBackgroundRefresh.schedule()
//   3. Info.plist der App:
//        BGTaskSchedulerPermittedIdentifiers = [ "com.example.osmon.refresh" ]
//        UIBackgroundModes = [ "fetch" ]
//
// Antworten auf eine Meldung:
//   Tipp auf die Meldung oder "Später"  -> engagiert (Ignorier-Zähler zurück)
//   "Fokus 15 Min"                      -> engagiert + Fokus-Session starten
//   Wegwischen (.customDismissAction)   -> ignoriert

import BackgroundTasks
import Foundation
import UserNotifications

final class PushResponder: NSObject, UNUserNotificationCenterDelegate {
    static let shared = PushResponder()

    func setUp() {
        UNUserNotificationCenter.current().delegate = self
        PushDelivery.registerCategories()
        PetBackgroundRefresh.register()
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        guard response.notification.request.content.categoryIdentifier == PushDelivery.category else {
            completionHandler(); return
        }
        switch response.actionIdentifier {
        case UNNotificationDismissActionIdentifier:
            PushStore.update { PushPolicy.onIgnored($0) }
        case PushDelivery.actionFocus:
            PushStore.update { PushPolicy.onEngaged($0) }
            PetSync.startFocus(minutes: 15)
            PetSync.reloadWidgetIfNeeded(PetSync.tick().snapshot)
        default:                                   // Tipp auf die Meldung oder "Später"
            PushStore.update { PushPolicy.onEngaged($0) }
        }
        completionHandler()
    }

    /// Im Vordergrund ist das Wesen ohnehin sichtbar; trotzdem zeigen, damit die Zählung ehrlich bleibt.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }
}

// MARK: - Gemeinsamer Ablauf "ticken und zustellen" (App-Prozess)

enum PetPushRun {
    /// Rechnet einen Tick, lädt bei Bedarf das Widget neu und prüft die Push-Regeln mit allen Signalen,
    /// die der App-Prozess kennt (Akku, Speicher, Bildschirmzeit, Schritte).
    /// @MainActor, weil PetSync.tick UIDevice liest (UIKit ist Main-Actor-gebunden). Aufrufer aus
    /// Hintergrund-Kontexten (HealthKit, BGTask, App Intent) springen per await automatisch dorthin.
    @MainActor @discardableResult
    static func tickAndDeliver(now: Date = .now) async -> PushType? {
        let t = PetSync.tick(now: now)
        PetSync.reloadWidgetIfNeeded(t.snapshot)
        var sig = PushDelivery.baseSignals(now: now, screenGoalMin: PetSync.config.screenGoalMin,
                                           stepGoal: PetSync.config.stepGoal)
        sig.batteryPct = t.inputs.batteryPct
        sig.charging = t.inputs.charging
        sig.freeStorageRatio = t.inputs.freeStorageRatio
        // Nachtnutzung meldet nur die Monitor-Extension (sie weiß, dass gerade jemand aktiv ist).
        return await PushDelivery.evaluateAndDeliver(sig, now: now)
    }
}

// MARK: - Hintergrund-Aktualisierung (BGAppRefreshTask)

enum PetBackgroundRefresh {
    static let identifier = "com.example.osmon.refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            schedule()                                   // nächste Runde gleich wieder anmelden
            let work = Task {
                await PetPushRun.tickAndDeliver()
                task.setTaskCompleted(success: true)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    /// Frühestens in 30 Minuten; wann es wirklich läuft, entscheidet iOS (abhängig von der Nutzung der App).
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}
