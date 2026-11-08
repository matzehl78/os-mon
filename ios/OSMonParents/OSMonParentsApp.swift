import SwiftUI
import MapKit

@main
struct OSMonParentsApp: App {
    var body: some Scene { WindowGroup { ParentsHome() } }
}
@MainActor
struct ParentsHome: View {
    @State private var connection = FamilyVault.load()
    @State private var server = "https://parents.ulmatti-studios.com"
    @State private var email = ""
    @State private var password = ""
    @State private var code = ""
    @State private var webCode = ""
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
                    Text("Geteilt werden Aufgaben, Einstellungen, Übernahmestatus und auf Wunsch ein bewusst geteilter Standort. Keine Bildschirmzeit- oder Gesundheitsdaten.").font(.footnote).foregroundStyle(.secondary)
                }
                if connection == nil {
                    Section("Elternzugang einrichten") {
                        TextField("HTTPS-Serveradresse", text: $server).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                        Text("Verwende denselben Familienserver wie auf dem Kindergerät. Der Zugang wird sicher auf diesem Elternhandy gespeichert.").font(.footnote)
                        TextField("E-Mail", text: $email).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress).textContentType(.username)
                        SecureField("Passwort", text: $password).textContentType(.password)
                        Button("Anmelden") { let pass = password; password = ""; run {
                            let base = try FamilyAPI.server(server)
                            let result: FamilyToken = try await FamilyAPI(connection: .init(server: base, token: "")).call("POST", "/v1/account/login", ["email": email, "password": pass])
                            let c = FamilyConnection(server: base, token: result.token)
                            try FamilyVault.save(c); connection = c; try await refresh(); message = "Angemeldet."
                        } }
                        Button("Mit E-Mail registrieren") { run {
                            let base = try FamilyAPI.server(server)
                            let _: FamilyOK = try await FamilyAPI(connection: .init(server: base, token: "")).call("POST", "/v1/account/register", ["email": email])
                            message = "Falls möglich wurde eine Bestätigung gesendet. Im Postfach den Link öffnen und Passwort festlegen. Bestehende Familie zuerst über ihren Web-Anmeldecode im Portal mit einem Konto verknüpfen."
                        } }
                        Button("Passwort vergessen") { run {
                            let base = try FamilyAPI.server(server)
                            let _: FamilyOK = try await FamilyAPI(connection: .init(server: base, token: "")).call("POST", "/v1/account/forgot", ["email": email])
                            message = "Falls möglich erhältst du einen Link zum Zurücksetzen. Bitte auch Spam prüfen."
                        } }
                        Button("Bestätigung erneut senden") { run {
                            let base = try FamilyAPI.server(server)
                            let _: FamilyOK = try await FamilyAPI(connection: .init(server: base, token: "")).call("POST", "/v1/account/resend", ["email": email])
                            message = "Falls möglich erhältst du eine neue Bestätigung. Bitte auch Spam prüfen."
                        } }
                        Text("Anmeldung gilt höchstens sieben Tage. Passwörter werden nicht gespeichert. E-Mail-Bestätigung und Passwortwahl erfolgen im Webportal.").font(.footnote)
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
                    Section("Elternkonto für bestehende Familie") {
                        TextField("E-Mail zur Kontoverknüpfung", text: $email).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
                        Button("Diese Familie mit E-Mail absichern") { run {
                            guard let connection else { return }
                            let _: FamilyOK = try await FamilyAPI(connection: connection).call("POST", "/v1/account/register", ["email": email])
                            message = "Falls möglich erhältst du eine Bestätigung. Dort Passwort festlegen und anschließend hier neu anmelden. Geräte und Aufgaben bleiben erhalten. Bei bestehendem Konto: Webportal verwenden."
                        } }
                        Button("Abmelden / neu anmelden") { run {
                            guard let old = connection else { return }
                            defer { server = old.server; password = ""; FamilyVault.clear(); connection = nil; children = []; webCode = "" }
                            let _: FamilyOK = try await FamilyAPI(connection: old).call("DELETE", "/v1/browser-session")
                        } }
                    }
                    Section("Auch im Browser") {
                        Button("Web-Anmeldecode erstellen") { run {
                            guard let connection else { return }
                            let result: FamilyWebCode = try await FamilyAPI(connection: connection).call("POST", "/v1/web-codes")
                            webCode = result.code
                        } }
                        if !webCode.isEmpty { Text(webCode).font(.title2.monospaced()).textSelection(.enabled) }
                        Text("Fünf Minuten gültig, einmal verwendbar. Nur auf eurer eigenen Eltern-Webseite eingeben. App und Browser verwenden danach dieselben Geräte.").font(.footnote)
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
                        Text("Bestehende Zugänge ohne E-Mail-Konto gelten weiterhin 90 Tage. Verknüpfe sie vor Ablauf mit einer E-Mail. Ein Konto je Familie; mehrere Anmeldungen sind möglich.").font(.footnote)
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
    private func refreshLocation() async throws {
        let result: FamilyList = try await FamilyAPI(connection: connection).call("GET", "/v1/children")
        if let d = result.children.first(where: { $0.id == device.id }) {
            device.location = d.location; device.locationSharing = d.locationSharing; device.locationRequest = d.locationRequest
        }
    }
    private func locationRequestText(_ req: FamilyLocationRequest) -> String {
        switch req.state {
        case "pending": return req.expires > Date().timeIntervalSince1970 ? "Anfrage läuft …" : "Keine Antwort innerhalb von 90 Sekunden. Gerät möglicherweise nicht erreichbar."
        case "received": return "Standort für die letzte Anfrage empfangen."
        case "permissionDenied": return "Standortberechtigung am Kindergerät fehlt."
        case "unavailable": return "Kindergerät konnte keinen aktuellen Standort ermitteln."
        default: return "Anfrage abgelaufen. Gerät möglicherweise nicht erreichbar."
        }
    }
    var body: some View {
        Form {
            Section("Übertragung") {
                Text(device.applied == device.version ? "Letzter gespeicherter Stand übernommen" : "Änderungen warten auf das Kindergerät")
                if let seen = device.seen { Text("Letzte Rückmeldung: \(Date(timeIntervalSince1970: seen).formatted())").font(.footnote) }
                Label(FamilyMeasuring.text(device.measuring), systemImage: device.measuring?.isOff == true ? "exclamationmark.triangle.fill" : "waveform.path.ecg")
                    .foregroundStyle(device.measuring?.isOff == true ? Color.orange : Color.primary)
                Text("Übernahme bei geöffneter Kinder-App. Ohne Internet gelten die zuletzt übernommenen Einstellungen.").font(.footnote)
            }
            Section("Zuletzt geteilter Standort") {
                Text(device.locationSharing == true ? "Standortanfragen am Kindergerät freigegeben" : "Standortanfragen am Kindergerät nicht freigegeben")
                if let req = device.locationRequest {
                    Text(locationRequestText(req))
                }
                Button("Neuen Standort anfragen") {
                    busy = true
                    Task {
                        do {
                            let _: FamilyRequestResult = try await FamilyAPI(connection: connection).call("POST", "/v1/children/\(device.id)/location-request", [:])
                            try await refreshLocation()
                        } catch { message = error.localizedDescription }
                        busy = false
                    }
                }.disabled(device.locationSharing != true)
                Text("Kinder-App muss geöffnet sein. Die Anfrage läuft nach 90 Sekunden ab. Der unten angezeigte Standort kann von einer früheren Messung stammen.").font(.footnote)
                if let l = device.location, Date().timeIntervalSince1970 - l.received < 86400 {
                    Map(initialPosition: .region(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude), span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)))) {
                        Marker("Geteilter Standort", coordinate: CLLocationCoordinate2D(latitude: l.latitude, longitude: l.longitude))
                    }.frame(height: 220).id(l.measured)
                    Text("Gemessen: \(Date(timeIntervalSince1970: l.measured).formatted())")
                    Text("Genauigkeit ungefähr \(Int(l.accuracy.rounded())) m. Keine Live-Ortung.").font(.footnote)
                } else { Text("Kein geteilter Standort. Auf dem Kindergerät ausdrücklich teilen.") }
                Button("Anzeige neu laden") {
                    busy = true
                    Task {
                        do {
                            let result: FamilyList = try await FamilyAPI(connection: connection).call("GET", "/v1/children")
                            device.location = result.children.first(where: { $0.id == device.id })?.location
                        } catch { message = error.localizedDescription }
                        busy = false
                    }
                }
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
        .task {
            while !Task.isCancelled {
                do { try await refreshLocation() } catch { message = error.localizedDescription }
                do { try await Task.sleep(for: .seconds(5)) } catch { break }
            }
        }
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
