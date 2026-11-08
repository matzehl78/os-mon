import SwiftUI
import CoreLocation
import FamilyControls

@MainActor
enum FamilyChild {
    static var syncing = false
    static func sync() async throws {
        guard !syncing, let connection = FamilyVault.load() else { return }
        syncing = true; defer { syncing = false }
        let api = FamilyAPI(connection: connection)
        let device: FamilyDevice = try await api.call("GET", "/v1/device")
        guard device.paired else { return }
        FamilyLocationSender.shared.handle(device, connection: connection)
        let old = TaskStore.load()
        let local = old.filter { !$0.id.hasPrefix("family:") }
        guard local.count + device.config.tasks.count <= DayTasks.maxTasks else { throw FamilyError(text: "Zu viele Aufgaben. Lokal und aus der Eltern-App zusammen sind höchstens 12 möglich.") }
        let remote: [DayTask] = device.config.tasks.map { task in
            let id = "family:" + task.id
            var t = old.first { $0.id == id } ?? DayTask(id: id, title: task.title, emoji: "👪", createdDay: TaskStore.today)
            t.title = task.title; t.repeatRule = TaskRepeat(rawValue: task.repeat) ?? .daily; t.fromParent = true
            return t
        }
        if old != local + remote { TaskStore.save(local + remote); NotificationCenter.default.post(name: .osmonTasksChanged, object: nil) }
        if GameStore.limit != device.config.limit { GameStore.setLimit(device.config.limit) }
        let completed = remote.filter { DayTasks.isDone($0, TaskStore.today) }.map { String($0.id.dropFirst(7)) }
        let _: FamilyOK = try await api.call("POST", "/v1/device/ack", ["version": device.version, "completed": completed, "day": TaskStore.today,
                                                                         "measuring": measuring()])
        PhoneWatchBridge.shared.push()
    }

    /// Ob Bildschirmzeit und Schritte gerade gemessen werden (nur an/aus, keine Werte), damit Eltern ein Abschalten sehen.
    /// Schritte: iOS verrät nicht, ob die Health-Leseerlaubnis besteht, daher nur der Schalter in OS-Mon.
    static func measuring() -> [String: Bool] {
        let d = STShared.defaults
        return ["screen": d.bool(forKey: "feature.screentime") && AuthorizationCenter.shared.authorizationStatus == .approved,
                "steps": d.bool(forKey: "feature.steps")]
    }
}

struct FamilyChildCard: View {
    @State private var server = FamilyVault.load()?.server ?? ""
    @State private var name = "Mein OS-Mon"
    @State private var code = ""
    @State private var message = ""
    @State private var busy = false
    @State private var linked = FamilyVault.load() != nil
    @State private var confirm = false
    private func run(_ action: @escaping () async throws -> Void) {
        guard ParentStore.isUnlocked else { message = "Eltern-Bereich erneut mit PIN entsperren."; return }
        busy = true; Task { do { try await action() } catch { message = error.localizedDescription }; busy = false }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Mit OS-Mon Eltern verbinden").font(.headline)
            Text("Optional: Aufgaben, Spielelimit, Erledigt-Status und ob Bildschirmzeit und Schritte gemessen werden (nur an/aus) werden über euren Familienserver geteilt. Standort nur nach ausdrücklichem Teilen; keine Gesundheitsdaten. Beide Apps benötigen denselben HTTPS-Server.").font(.footnote)
            if !linked {
                TextField("HTTPS-Serveradresse", text: $server).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("Gerätename (ohne echten Kindernamen)", text: $name).onChange(of: name) { name = String(name.prefix(40)) }
                Button("Freigeben und Kopplungscode erstellen") { run {
                    let base = try FamilyAPI.server(server)
                    let api = FamilyAPI(connection: .init(server: base, token: ""))
                    let config = FamilyConfig(limit: GameStore.limit, tasks: [])
                    let pair: FamilyPair = try await api.call("POST", "/v1/pairings", ["name": name, "config": try FamilyAPI.object(config)])
                    try FamilyVault.save(.init(server: base, token: pair.token)); code = pair.code; linked = true
                    message = "Code zehn Minuten gültig. Nur in eurer Eltern-App eingeben."
                } }
            } else {
                if !code.isEmpty { Text(code).font(.title2.monospaced().bold()).textSelection(.enabled) }
                Button("Verbindung prüfen und synchronisieren") { run { try await FamilyChild.sync(); message = "Verbindung abgefragt. Einstellungen werden übernommen, sobald gekoppelt." } }
                FamilyLocationCard()
                Button("Verbindung entfernen", role: .destructive) { confirm = true }
                Text("Änderungen werden bei geöffneter OS-Mon-App abgeholt. Nach Trennen bleiben zuletzt übernommene Aufgaben und Limits lokal erhalten.").font(.caption)
            }
            if busy { ProgressView() }
            if !message.isEmpty { Text(message).font(.footnote) }
        }.disabled(busy)
        .confirmationDialog("Verbindung serverseitig entfernen?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Online trennen", role: .destructive) { run {
                if let c = FamilyVault.load() { let _: FamilyOK = try await FamilyAPI(connection: c).call("DELETE", "/v1/device") }
                FamilyVault.clear(); linked = false; code = ""; message = "Verbindung entfernt."
            } }
            Button("Nur lokalen Zugang entfernen", role: .destructive) {
                guard ParentStore.isUnlocked else { return }
                FamilyVault.clear(); linked = false; code = ""; message = "Lokal getrennt. Daten auf dem Familienserver bitte in der Eltern-App löschen."
            }
        }
    }
}

// A location is requested only after a visible, explicit action on this device.
@MainActor
final class FamilyLocationSender: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = FamilyLocationSender()
    @Published var sharing = false
    private var requestID: String?
    private var connection: FamilyConnection?
    private var timeout: Task<Void, Never>?
    @Published var message = "Kein automatisches Tracking. Ein geteilter Standort wird nach 24 Stunden ausgeblendet."
    @Published var busy = false
    private let manager = CLLocationManager()
    private var pending = false
    override init() { super.init(); manager.delegate = self; manager.desiredAccuracy = kCLLocationAccuracyHundredMeters }
    func handle(_ device: FamilyDevice, connection: FamilyConnection) {
        sharing = device.locationSharing == true
        guard sharing, let req = device.locationRequest, req.state == "pending", req.expires > Date().timeIntervalSince1970, !busy else { return }
        requestID = req.id; self.connection = connection
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            Task { let _: FamilyOK? = try? await FamilyAPI(connection: connection).call("POST", "/v1/device/location-result", ["requestId": req.id, "state": "permissionDenied"]) }; requestID = nil; self.connection = nil; return
        }
        share()
    }
    func setSharing(_ enabled: Bool) async {
        guard !busy, let c = FamilyVault.load() else { return }
        if enabled && manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization(); message = "Bitte Berechtigung erlauben und danach Freigabe erneut aktivieren."; return }
        if enabled && manager.authorizationStatus != .authorizedWhenInUse && manager.authorizationStatus != .authorizedAlways { message = "Standortberechtigung in den iPhone-Einstellungen erlauben."; return }
        do {
            let _: FamilyOK = try await FamilyAPI(connection: c).call("PUT", "/v1/device/location-sharing", ["enabled": enabled])
            sharing = enabled; message = enabled ? "Eltern dürfen einen Standort anfragen, solange OS-Mon geöffnet ist." : "Standortanfragen deaktiviert und Serverstandort gelöscht."
        } catch { message = error.localizedDescription }
    }
    func share() {
        guard !busy else { return }; busy = true; pending = true
        if connection == nil { connection = FamilyVault.load() }
        timeout?.cancel()
        timeout = Task { try? await Task.sleep(for: .seconds(30)); if !Task.isCancelled && pending { fail("Kein Standort innerhalb von 30 Sekunden verfügbar.") } }
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else { request() }
    }
    private func request() {
        guard pending else { return }
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            fail("Standortzugriff fehlt. Bei Bedarf in den iPhone-Einstellungen erlauben."); return
        }
        manager.requestLocation()
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { if manager.authorizationStatus != .notDetermined { request() } }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard pending, let location = locations.last else { return }; pending = false
        guard location.horizontalAccuracy >= 0, abs(location.timestamp.timeIntervalSinceNow) <= 120 else { fail("Kein aktueller Standort verfügbar."); return }
        timeout?.cancel()
        let capturedID = requestID; let capturedConnection = connection
        Task {
            defer { busy = false; requestID = nil; connection = nil }
            do {
                guard UIApplication.shared.applicationState == .active else { throw FamilyError(text: "Standortanfrage nur bei geöffneter App möglich.") }
                guard let c = capturedConnection else { throw FamilyError(text: "Kein Familienzugang.") }
                var body: [String: Any] = ["latitude": location.coordinate.latitude, "longitude": location.coordinate.longitude, "accuracy": location.horizontalAccuracy, "measured": location.timestamp.timeIntervalSince1970]
                if let capturedID { body["requestId"] = capturedID }
                let _: FamilyOK = try await FamilyAPI(connection: c).call("POST", "/v1/device/location", body)
                message = "Standort mit verbundenem Elternzugang geteilt. Keine laufende Ortung."
            } catch { message = error.localizedDescription }
        }
    }
    private func fail(_ text: String) {
        pending = false; busy = false; message = text; timeout?.cancel()
        let id = requestID; let c = connection; requestID = nil; connection = nil
        if let id, let c { Task { let _: FamilyOK? = try? await FamilyAPI(connection: c).call("POST", "/v1/device/location-result", ["requestId": id, "state": "unavailable"]) } }
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { fail("Standort nicht verfügbar: " + error.localizedDescription) }
    func remove() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do { if let c = FamilyVault.load() { let _: FamilyOK = try await FamilyAPI(connection: c).call("DELETE", "/v1/device/location") }; message = "Geteilter Standort vom Server gelöscht." }
        catch { message = "Löschen fehlgeschlagen: " + error.localizedDescription }
    }
}
struct FamilyLocationCard: View {
    @ObservedObject private var sender = FamilyLocationSender.shared
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Standort teilen").font(.headline)
            Text("Einmalig Standortanfragen erlauben: Danach darf der verbundene Elternzugang Messungen anfordern, solange OS-Mon geöffnet ist. Keine Hintergrundortung.").font(.footnote)
            Text(sender.sharing ? "Standortanfragen: freigegeben" : "Standortanfragen: aus")
            Button(sender.sharing ? "Standortanfragen deaktivieren" : "Standortanfragen erlauben") { Task { await sender.setSharing(!sender.sharing) } }
            Button("Aktuellen Standort einmal teilen") { sender.share() }
            Button("Geteilten Standort löschen", role: .destructive) { Task { await sender.remove() } }
            Text(sender.message).font(.caption)
        }.disabled(sender.busy)
        .task { if let c = FamilyVault.load(), let d: FamilyDevice = try? await FamilyAPI(connection: c).call("GET", "/v1/device") { sender.sharing = d.locationSharing == true } }
    }
}
