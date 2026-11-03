// PhoneWatchBridge.swift: iPhone-Seite der Apple-Watch-Verbindung (WatchConnectivity).
//
// Schickt nach jedem Update (HomeViewModel.refresh, Aufgabe geändert) das aktuelle Paket als
// applicationContext (nur der neueste Stand zählt, kommt auch an, wenn die Uhr gerade nicht erreichbar ist).
// Nimmt Aktionen der Uhr an (Aufgabe abhaken, Miteinander-Tipp ausprobiert) und schickt danach den neuen Stand.

import Foundation
import WatchConnectivity

extension Notification.Name {
    /// Aufgaben wurden außerhalb der Oberfläche geändert (z. B. auf der Uhr abgehakt).
    static let osmonTasksChanged = Notification.Name("osmon.tasksChanged")
}

final class PhoneWatchBridge: NSObject, WCSessionDelegate {
    static let shared = PhoneWatchBridge()

    private var accessory: Accessory?
    private var form: PetForm?
    private var supported: Bool { WCSession.isSupported() }

    func start() {
        guard supported else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Aktuellen Stand an die Uhr. Ohne Snapshot wird der zuletzt gespeicherte genommen.
    func push(snapshot: PetSnapshot? = nil, accessory: Accessory? = nil, form: PetForm? = nil, updateExtras: Bool = false) {
        if updateExtras { self.accessory = accessory; self.form = form }
        guard supported else { return }
        let s = WCSession.default
        guard s.activationState == .activated, s.isPaired, s.isWatchAppInstalled else { return }
        guard let data = Self.payload(snapshot: snapshot, accessory: self.accessory, form: self.form)?.encoded() else { return }
        try? s.updateApplicationContext([WatchPayload.key: data])
    }

    static func payload(snapshot: PetSnapshot?, accessory: Accessory?, form: PetForm?, now: Date = .now) -> WatchPayload? {
        guard let snap = snapshot ?? PetSync.lastSnapshot()?.snapshot else { return nil }
        let day = EpochDay.of(now)
        let tasks = TaskStore.load()
        let content = TaskContent.shared
        let figure = PetFigureStore.current
        let tip = content.tipOfTheDay(day)
        return WatchPayload(
            snapshot: snap, updatedAt: now, figureID: figure.id, petName: figure.name,
            accessory: accessory?.rawValue, form: form?.rawValue, day: day,
            tasks: DayTasks.forDay(tasks, day),
            motivation: content.motivation(DayTasks.progress(tasks, day), day: day, pet: figure.name),
            kindTip: tip?.1.tip, kindCategory: tip.map { "\($0.0.emoji) \($0.0.title)" },
            kindDone: TaskStore.kindDone(day), stage: GrowthStorage.stage.rawValue)
    }

    // MARK: Aktionen der Uhr

    private func handle(_ msg: [String: Any]) {
        DispatchQueue.main.async {
            let day = EpochDay.of(.now)
            switch msg[WatchAction.kind] as? String {
            case WatchAction.toggleTask:
                guard let id = msg["id"] as? String else { return }
                if let t = TaskStore.load().first(where: { $0.id == id }), !DayTasks.isDone(t, day) { AchievementStore.addTask() }
                let l = TaskStore.load().map { $0.id == id ? DayTasks.toggle($0, day) : $0 }
                TaskStore.save(l)
                NotificationCenter.default.post(name: .osmonTasksChanged, object: nil)
            case WatchAction.kindDone:
                TaskStore.markKind(day)
                NotificationCenter.default.post(name: .osmonTasksChanged, object: nil)
            default: break
            }
            self.push()
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) { handle(message) }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        handle(message)
        replyHandler([:])
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) { handle(userInfo) }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if activationState == .activated { DispatchQueue.main.async { self.push() } }
    }

    func sessionWatchStateDidChange(_ session: WCSession) { DispatchQueue.main.async { self.push() } }
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { WCSession.default.activate() }
}
