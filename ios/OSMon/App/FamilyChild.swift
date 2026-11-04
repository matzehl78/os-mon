import SwiftUI
import CoreLocation

@MainActor
enum FamilyChild {
    static var syncing = false
    static func sync() async throws {
        guard !syncing, let connection = FamilyVault.load() else { return }
        syncing = true; defer { syncing = false }
        let api = FamilyAPI(connection: connection)
        let device: FamilyDevice = try await api.call("GET", "/v1/device")
        guard device.paired else { return }
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
        let _: FamilyOK = try await api.call("POST", "/v1/device/ack", ["version": device.version, "completed": completed, "day": TaskStore.today])
        PhoneWatchBridge.shared.push()
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
            Text("Optional: Aufgaben, Spielelimit und Erledigt-Status werden über euren Familienserver geteilt. Standort nur nach ausdrücklichem Teilen; keine Gesundheitsdaten. Beide Apps benötigen denselben HTTPS-Server.").font(.footnote)
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
    @Published var message = "Kein automatisches Tracking. Ein geteilter Standort wird spätestens nach 24 Stunden gelöscht."
    @Published var busy = false
    private let manager = CLLocationManager()
    private var pending = false
    override init() { super.init(); manager.delegate = self; manager.desiredAccuracy = kCLLocationAccuracyHundredMeters }
    func share() {
        guard !busy else { return }; busy = true; pending = true
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        else { request() }
    }
    private func request() {
        guard pending else { return }
        guard manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways else {
            pending = false; busy = false; message = "Standortzugriff fehlt. Bei Bedarf in den iPhone-Einstellungen erlauben."; return
        }
        manager.requestLocation()
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { if manager.authorizationStatus != .notDetermined { request() } }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard pending, let location = locations.last else { return }; pending = false
        guard location.horizontalAccuracy >= 0, abs(location.timestamp.timeIntervalSinceNow) <= 120 else { busy = false; message = "Kein aktueller Standort verfügbar."; return }
        Task {
            defer { busy = false }
            do {
                guard let c = FamilyVault.load() else { throw FamilyError(text: "Kein Familienzugang.") }
                let _: FamilyOK = try await FamilyAPI(connection: c).call("POST", "/v1/device/location", ["latitude": location.coordinate.latitude, "longitude": location.coordinate.longitude, "accuracy": location.horizontalAccuracy, "measured": location.timestamp.timeIntervalSince1970])
                message = "Standort mit verbundenem Elternzugang geteilt. Keine laufende Ortung."
            } catch { message = error.localizedDescription }
        }
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { pending = false; busy = false; message = "Standort nicht verfügbar: " + error.localizedDescription }
    func remove() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do { if let c = FamilyVault.load() { let _: FamilyOK = try await FamilyAPI(connection: c).call("DELETE", "/v1/device/location") }; message = "Geteilter Standort vom Server gelöscht." }
        catch { message = "Löschen fehlgeschlagen: " + error.localizedDescription }
    }
}
struct FamilyLocationCard: View {
    @StateObject private var sender = FamilyLocationSender()
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Standort teilen").font(.headline)
            Text("Nur nach diesem Tippen wird der aktuelle Standort an euren verbundenen Elternzugang gesendet. App und Eltern-Webseite können ihn anzeigen.").font(.footnote)
            Button("Aktuellen Standort einmal teilen") { sender.share() }
            Button("Geteilten Standort löschen", role: .destructive) { Task { await sender.remove() } }
            Text(sender.message).font(.caption)
        }.disabled(sender.busy)
    }
}
