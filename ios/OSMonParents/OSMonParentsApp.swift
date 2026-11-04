import SwiftUI

@main
struct OSMonParentsApp: App {
    var body: some Scene { WindowGroup { ParentsHome() } }
}
@MainActor
struct ParentsHome: View {
    @State private var connection = FamilyVault.load()
    @State private var server = ""
    @State private var code = ""
    @State private var children: [FamilyDevice] = []
    @State private var message = ""
    @State private var busy = false
    @State private var confirmDelete = false
    @Environment(\.scenePhase) private var phase

    func run(_ action: @escaping () async throws -> Void) {
        guard !busy else { return }; busy = true
        Task { do { try await action() } catch { message = error.localizedDescription }; busy = false }
    }
    func refresh() async throws {
        guard let connection else { return }
        let result: FamilyList = try await FamilyAPI(connection: connection).call("GET", "/v1/children")
        children = result.children
    }
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Aufgaben und Spielelimit für dein verbundenes OS-Mon-Gerät.")
                    Text("Es werden nur Aufgaben, Einstellungen und deren Übernahmestatus geteilt. Keine Bildschirmzeit- oder Gesundheitsdaten.").font(.footnote).foregroundStyle(.secondary)
                }
                if connection == nil {
                    Section("Elternzugang einrichten") {
                        TextField("HTTPS-Serveradresse", text: $server).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        Text("Verwende denselben Familienserver wie auf dem Kindergerät. Der Zugang wird sicher auf diesem Elternhandy gespeichert.").font(.footnote)
                        Button("Zugang erstellen") { run {
                            let base = try FamilyAPI.server(server)
                            let api = FamilyAPI(connection: .init(server: base, token: ""))
                            let result: FamilyToken = try await api.call("POST", "/v1/parents")
                            let c = FamilyConnection(server: base, token: result.token)
                            try FamilyVault.save(c); connection = c; try await refresh()
                        } }
                    }
                } else {
                    Section("Kindergerät verbinden") {
                        TextField("10-stelliger Kopplungscode", text: $code).textInputAutocapitalization(.characters).autocorrectionDisabled()
                        Text("Code im PIN-geschützten Eltern-Bereich auf dem Kindergerät erstellen. Er ist zehn Minuten gültig.").font(.footnote)
                        Button("Verbinden") { run {
                            guard let connection else { return }
                            let _: FamilyOK = try await FamilyAPI(connection: connection).call("POST", "/v1/claims", ["code": code])
                            code = ""; try await refresh(); message = "Gerät verbunden."
                        } }
                    }
                    Section("Deine Geräte") {
                        if children.isEmpty { Text("Noch kein Gerät verbunden.") }
                        ForEach(children) { child in
                            NavigationLink(child.name) { ParentDeviceEditor(device: child, connection: connection!, onSaved: { run { try await refresh() } }) }
                        }
                        Button("Aktualisieren") { run { try await refresh() } }
                    }
                    Section {
                        Button("Zugang und alle Verbindungen löschen", role: .destructive) { confirmDelete = true }
                        Text("Der Zugang gilt 90 Tage. Bei Verlust oder Ablauf am Kindergerät trennen und neu koppeln. Mehrere Elternkonten und Passwort-Wiederherstellung sind noch nicht enthalten.").font(.footnote)
                    }
                }
                if !message.isEmpty { Section { Text(message).accessibilityLabel(message) } }
                if busy { ProgressView() }
            }
            .navigationTitle("OS-Mon Eltern")
            .disabled(busy)
            .task { run { try await refresh() } }
            .onChange(of: phase) { _, p in if p == .active { run { try await refresh() } } }
            .confirmationDialog("Zugang wirklich löschen? Alle Geräte werden getrennt.", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Löschen", role: .destructive) { run {
                    guard let connection else { return }
                    let _: FamilyOK = try await FamilyAPI(connection: connection).call("DELETE", "/v1/parents")
                    FamilyVault.clear(); self.connection = nil; children = []
                } }
                Button("Nur Zugang auf diesem Handy entfernen", role: .destructive) {
                    FamilyVault.clear(); connection = nil; children = []; message = "Lokaler Zugang entfernt. Serverdaten und andere Geräte werden dadurch nicht gelöscht."
                }
            }
        }
    }
}

@MainActor
struct ParentDeviceEditor: View {
    @State var device: FamilyDevice
    let connection: FamilyConnection
    let onSaved: () -> Void
    @State private var title = ""
    @State private var rule = "daily"
    @State private var message = ""
    @State private var busy = false
    @State private var confirmUnpair = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Form {
            Section("Übertragung") {
                Text(device.applied == device.version ? "Letzter gespeicherter Stand übernommen" : "Änderungen warten auf das Kindergerät")
                if let seen = device.seen { Text("Letzte Rückmeldung: \(Date(timeIntervalSince1970: seen).formatted())").font(.footnote) }
                Text("Übernahme bei geöffneter Kinder-App. Ohne Internet gelten die zuletzt übernommenen Einstellungen.").font(.footnote)
            }
            Section("Mini-Spiele") {
                Picker("Runden pro Tag", selection: $device.config.limit) { Text("Aus").tag(0); ForEach(1...3, id: \.self) { Text("\($0)").tag($0) } }
            }
            Section("Aufgaben") {
                ForEach(device.config.tasks) { task in
                    VStack(alignment: .leading) {
                        Text(task.title)
                        Text((device.completed.contains(task.id) ? "Als erledigt gemeldet" : "Noch nicht als erledigt gemeldet")).font(.caption).foregroundStyle(.secondary)
                    }
                }.onDelete { device.config.tasks.remove(atOffsets: $0) }
                TextField("Neue Aufgabe (max. 40 Zeichen)", text: $title).onChange(of: title) { title = String(title.prefix(40)) }
                Picker("Wiederholen", selection: $rule) {
                    Text("Einmal").tag("once"); Text("Täglich").tag("daily"); Text("Mo–Fr").tag("weekdays"); Text("Wochenende").tag("weekend")
                }
                Button("Aufgabe hinzufügen") {
                    let t = title.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !t.isEmpty, device.config.tasks.count < 12 else { return }
                    device.config.tasks.append(.init(id: UUID().uuidString, title: t, `repeat`: rule)); title = ""
                }.disabled(device.config.tasks.count >= 12)
            }
            Section {
                Button("Änderungen senden") {
                    busy = true
                    Task {
                        do {
                            let _: FamilyOK = try await FamilyAPI(connection: connection).call("PUT", "/v1/children/\(device.id)/config", ["version": device.version, "config": try FamilyAPI.object(device.config)])
                            onSaved(); dismiss()
                        } catch { message = error.localizedDescription }
                        busy = false
                    }
                }
                Button("Gerät trennen", role: .destructive) { confirmUnpair = true }
            }
            if !message.isEmpty { Text(message) }
        }.navigationTitle(device.name).disabled(busy)
        .confirmationDialog("Gerät wirklich trennen? Die zuletzt übernommenen Einstellungen bleiben lokal erhalten.", isPresented: $confirmUnpair, titleVisibility: .visible) {
            Button("Trennen", role: .destructive) {
                busy = true; Task {
                    do { let _: FamilyOK = try await FamilyAPI(connection: connection).call("DELETE", "/v1/children/\(device.id)"); onSaved(); dismiss() }
                    catch { message = error.localizedDescription }; busy = false
                }
            }
        }
    }
}
