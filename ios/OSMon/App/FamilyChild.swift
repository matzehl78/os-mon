import SwiftUI

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
            Text("Optional: Aufgaben, Spielelimit und Erledigt-Status werden über euren Familienserver geteilt. Keine Sensor- oder Gesundheitsdaten. Beide Apps benötigen denselben HTTPS-Server.").font(.footnote)
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
