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

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let d = session.receivedApplicationContext[WatchPayload.key] as? Data, let p = WatchPayload.decode(d) {
            DispatchQueue.main.async { self.apply(p) }
        }
        if activationState == .activated { requestUpdate() }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let d = applicationContext[WatchPayload.key] as? Data, let p = WatchPayload.decode(d) else { return }
        DispatchQueue.main.async { self.apply(p) }
    }
}
