// WatchStore.swift: Uhr-Seite der Verbindung (WatchConnectivity). Hält das letzte Paket vom iPhone,
// speichert es in der App Group (für die Komplikation) und schickt Aktionen ans iPhone.
// Abhaken wirkt sofort auf der Uhr; das iPhone bestätigt mit einem neuen Paket.

import Foundation
import Observation
import WatchConnectivity
import WidgetKit

@Observable
final class WatchStore: NSObject, WCSessionDelegate {
    static let shared = WatchStore()
    static let widgetKind = "OSMonWatchWidget"

    private(set) var payload: WatchPayload? = WatchPayload.decode(STShared.defaults.data(forKey: WatchPayload.key))
    var cheer: String? = nil

    func start() {
        #if DEBUG
        if let demo = WatchDemo.payload() { apply(demo, save: false); return }
        #endif
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    private func apply(_ p: WatchPayload, save: Bool = true) {
        payload = p
        if save {
            STShared.defaults.set(p.encoded(), forKey: WatchPayload.key)
            WidgetCenter.shared.reloadTimelines(ofKind: Self.widgetKind)
        }
    }

    // MARK: Aktionen

    func toggle(_ t: DayTask) {
        guard var p = payload else { return }
        let nowDone = !DayTasks.isDone(t, p.day)
        p.tasks = p.tasks.map { $0.id == t.id ? DayTasks.toggle($0, p.day) : $0 }
        cheer = nowDone ? (p.progress.stage == .all ? "Alles geschafft! 🎉" : "Super, erledigt!") : nil
        apply(p)
        send([WatchAction.kind: WatchAction.toggleTask, "id": t.id])
    }

    func kindDone() {
        guard var p = payload, !p.kindDone else { return }
        p.kindDone = true
        apply(p)
        send([WatchAction.kind: WatchAction.kindDone])
    }

    func requestUpdate() { send([WatchAction.kind: WatchAction.requestUpdate]) }

    private func send(_ msg: [String: Any]) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
        let s = WCSession.default
        if s.isReachable {
            s.sendMessage(msg, replyHandler: nil) { _ in s.transferUserInfo(msg) }
        } else {
            s.transferUserInfo(msg)            // wird nachgeliefert, sobald das iPhone erreichbar ist
        }
    }

    // MARK: WCSessionDelegate

    /// iPhone hat „Alle Daten löschen“ ausgeführt: nichts Altes mehr zeigen (App und Widget).
    private func clear() {
        payload = nil; cheer = nil
        STShared.defaults.removeObject(forKey: WatchPayload.key)
        WidgetCenter.shared.reloadTimelines(ofKind: Self.widgetKind)
    }

    private func receive(_ context: [String: Any]) {
        if context[WatchReset.key] != nil { DispatchQueue.main.async { self.clear() }; return }
        guard let d = context[WatchPayload.key] as? Data, let p = WatchPayload.decode(d) else { return }
        DispatchQueue.main.async { self.apply(p) }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        receive(session.receivedApplicationContext)
        if activationState == .activated { requestUpdate() }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        receive(applicationContext)
    }
}
