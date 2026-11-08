// SettingsView.swift: Einstellungen mit Berechtigungs-Dashboard (iOS 17+, nur App-Target)
//
// PRD 1.4: je Sensor sehen, was gelesen wird, und es an- oder abschalten; sehen, was gespeichert wird;
// alles exportieren; alles in einem Schritt löschen. Dazu: Meldungen abschalten und Ziele einstellen.
//
// Abhängigkeiten: ScreenTimeApp.swift (ScreenTimeManager), StepsHealthKit.swift (StepsProvider),
// PushDelivery.swift / PushPolicy.swift / PushResponder.swift, PetSync.swift, ScreenTimeShared.swift (PetGoals).
//
// Einstieg: HomeScreen zeigt sie als Sheet (Zahnrad oben rechts). Nach "Alle Daten löschen" setzt sie
// "onboarding.done" zurück; das App-Root (@AppStorage, siehe OnboardingView.swift) zeigt dann das Onboarding.

import BackgroundTasks
import FamilyControls
import SwiftUI
import UIKit
import UserNotifications
import WidgetKit

// MARK: - Löschen und Exportieren

enum PetData {
    /// Alle Schlüssel, die OS-Mon in der App Group anlegt (Regeln: Shared/Logic/OwnDataKeys.swift).
    static func ownKeys() -> [String] { OwnDataKeys.keys(in: STShared.defaults, suite: STShared.appGroup) }

    /// Lesbarer JSON-Export aller eigenen Daten. Codierte Werte (Snapshot, Evolution, Push-Zustand) werden
    /// aufgelöst; die Bildschirmzeit-Auswahl ist ein von Apple verschlüsseltes Token und bleibt unlesbar.
    static func exportJSON(now: Date = .now) throws -> URL {
        let d = STShared.defaults
        var entries: [String: Any] = [:]
        for key in ownKeys() {
            guard let value = d.object(forKey: key) else { continue }
            switch value {
            case let data as Data where key.hasPrefix("sel."):
                entries[key] = "<von Apple verschlüsselte App-Auswahl, \(data.count) Bytes>"
            case let data as Data:
                entries[key] = (try? JSONSerialization.jsonObject(with: data)) ?? "<\(data.count) Bytes>"
            case let date as Date:
                entries[key] = ISO8601DateFormatter().string(from: date)
            default:
                entries[key] = value
            }
        }
        let doc: [String: Any] = [
            "app": "OS-Mon",
            "exportiertAm": ISO8601DateFormatter().string(from: now),
            "hinweis": "Alle Daten, die OS-Mon auf diesem Gerät gespeichert hat. Nichts davon wurde hochgeladen.",
            "daten": entries,
        ]
        let json = try JSONSerialization.data(withJSONObject: doc, options: [.prettyPrinted, .sortedKeys])
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("OS-Mon-Export-\(STShared.dayKey(now)).json")
        try json.write(to: url, options: .atomic)
        return url
    }

    /// Löscht alles, was OS-Mon gespeichert oder eingeplant hat. Schritte in Apple Health gehören nicht
    /// OS-Mon und bleiben unberührt; die Health-Leseerlaubnis kann nur die Person selbst zurücknehmen.
    @MainActor
    static func deleteAll(screen: ScreenTimeManager) async {
        if let connection = FamilyVault.load() {
            let _: FamilyOK? = try? await FamilyAPI(connection: connection).call("DELETE", "/v1/device")
        }
        FamilyVault.clear()
        screen.stopMonitoring()
        await screen.revokeAuthorization()
        await StepsProvider.shared.stopBackgroundDelivery()
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
        BGTaskScheduler.shared.cancelAllTaskRequests()
        for key in ownKeys() { STShared.defaults.removeObject(forKey: key) }
        ParentStore.lockNow()                       // Eltern-PIN ist mit gelöscht
        try? FileManager.default.contentsOfDirectory(at: FileManager.default.temporaryDirectory,
                                                     includingPropertiesForKeys: nil)
            .filter { $0.lastPathComponent.hasPrefix("OS-Mon-Export") }
            .forEach { try? FileManager.default.removeItem(at: $0) }
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Anzahl Tage mit Tageslog (für "Was gespeichert wird").
    static func loggedDays() -> Int { ownKeys().filter { $0.hasPrefix("daylog.n.") }.count }
}

// MARK: - Screen

struct SettingsView: View {
    @StateObject private var screen = ScreenTimeManager()
    @Environment(\.dismiss) private var dismiss
    @State private var showIntro = false
    @State private var showParent = false
    @State private var parentVersion = 0
    private var locked: Bool { _ = parentVersion; return ParentStore.settingsLocked }
    @AppStorage(Pet3D.key, store: STShared.defaults) private var use3D = true
    @AppStorage(SoundPlayer.key, store: STShared.defaults) private var sound = true
    @State private var showWallpaper = false
    @Environment(\.scenePhase) private var phase
    var onDataDeleted: () -> Void = {}

    @State private var screenOn = STShared.defaults.bool(forKey: "feature.screentime")
    @State private var stepsOn = STShared.defaults.bool(forKey: "feature.steps")
    @State private var pushOn = !PushDelivery.optedOut
    @State private var screenGoal = PetGoals.screenMinutes
    @State private var stepGoal = PetGoals.steps

    @State private var notifStatus: UNAuthorizationStatus = .notDetermined
    @State private var pushState = PushStore.load()
    @State private var selectionDirty = false
    @State private var busy = false
    @State private var errorText: String?
    @State private var exportURL: URL?
    @State private var confirmDelete = false

    private let defaults = STShared.defaults

    var body: some View {
        NavigationStack {
            Form {
                parentSection
                figureSection
                sceneSection
                introSection
                lockScreenSection
                if locked { Section("Abend und Morgen") { LockedByParentRow(title: "Abend und Morgen") { showParent = true } } }
                else { SleepSettingsSection() }
                FestivalSettingsSection()
                sensorSection
                // Messungen, auf denen Elternregeln und Wesen beruhen: mit PIN gesperrt (Meldungen bleiben frei)
                if locked { Section("Bildschirmzeit und Schritte") { LockedByParentRow(title: "Bildschirmzeit und Schritte") { showParent = true } } }
                else {
                    screenTimeSection
                    stepsSection
                }
                pushSection
                if locked { Section("Ziele") { LockedByParentRow(title: "Ziele") { showParent = true } } } else { goalSection }
                storageSection
                dataSection
            }
            .navigationTitle("Einstellungen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
            .disabled(busy)
            .task { await refreshStatus() }
            .onChange(of: phase) { _, p in if p == .active { Task { await refreshStatus() } } }
            .confirmationDialog("Alle Daten löschen?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Alles löschen", role: .destructive) { Task { await deleteAll() } }
            } message: {
                Text("Dein Wesen, seine Entwicklung, alle Tageswerte und Einstellungen werden von diesem Gerät entfernt. Das lässt sich nicht rückgängig machen.")
            }
        }
    }

    // MARK: Abschnitte

    private var figureSection: some View {
        Section {
            PetFigurePicker().padding(.vertical, 4)
            Toggle("3D-Wesen", isOn: $use3D)
                .onChange(of: use3D) { _, on in if on { STShared.defaults.set(false, forKey: Pet3D.failedKey) } }
            Toggle("Töne & Stimme", isOn: $sound)
                .onChange(of: sound) { _, on in if on { SoundPlayer.shared.play(.giggle) } }
        } header: {
            Text("Figur")
        } footer: {
            Text("Alle Figuren zeigen dasselbe: Herz = Energie, leuchtende Antennen = Ruhe. Das Widget wechselt mit. 3D: Mit dem Finger drehen; Widget, Sperrbildschirm und Uhr bleiben 2D.")
        }
    }

    /// Eltern-Bereich (PIN) – sperrt Ziele, Schlafenszeit, Bildschirmzeit/Schritte und Daten löschen.
    private var parentSection: some View {
        Section {
            Button { showParent = true } label: {
                Label(ParentStore.hasPin ? "Eltern-Bereich (mit PIN)" : "Eltern-Bereich einrichten", systemImage: "figure.2.and.child.holdinghands")
            }
        } footer: {
            Text("PIN festlegen, Aufgaben fürs Kind stellen, die Woche ansehen und Einstellungen sperren.")
        }
        .sheet(isPresented: $showParent, onDismiss: { parentVersion += 1 }) { ParentView() }
    }

    private var introSection: some View {
        Section {
            Button { showIntro = true } label: { Label("Einführungsfilm ansehen", systemImage: "play.rectangle") }
        }
        .fullScreenCover(isPresented: $showIntro) { IntroVideoView { showIntro = false } }
    }

    private var lockScreenSection: some View {
        Section {
            Button { showWallpaper = true } label: { Label("Hintergrundbild mit deinem Wesen", systemImage: "iphone") }
        } header: {
            Text("Sperrbildschirm")
        } footer: {
            Text("Erstellt ein Bild mit deiner Figur, ihrem Hintergrund und ihrer aktuellen Stimmung. iOS lässt Apps das Hintergrundbild nicht selbst setzen: Bild sichern und in Fotos als Hintergrund wählen. Live auf dem Sperrbildschirm: Sperrbildschirm lange drücken → Anpassen → Widgets → OS-Mon.")
        }
        .fullScreenCover(isPresented: $showWallpaper) { WallpaperPreview { showWallpaper = false } }
    }

    private var sceneSection: some View {
        Section {
            ScenePicker(status: ProgressData.refresh().status).padding(.vertical, 4)
        } header: {
            Text("Hintergrund")
        } footer: {
            Text("Weitere Hintergründe schaltest du mit gesunden Gewohnheiten frei: Bewegung, Fokus-Pausen, weniger Bildschirm. Freigeschaltet bleibt freigeschaltet.")
        }
    }

    private var sensorSection: some View {
        Section {
            InfoRow(symbol: "battery.75", title: "Akku", detail: "Ladestand und Laden. Keine Erlaubnis nötig.")
            InfoRow(symbol: "internaldrive", title: "Speicher", detail: "Nur der Anteil freien Speichers. Keine Inhalte.")
        } header: {
            Text("Was OS-Mon liest")
        } footer: {
            Text("Alles wird auf diesem Gerät berechnet. Es gibt kein Konto und keinen Upload.")
        }
    }

    private var screenTimeSection: some View {
        Section {
            Toggle(isOn: Binding(get: { screenOn }, set: { on in Task { await setScreenTime(on) } })) {
                Label("Bildschirmzeit", systemImage: "moon.fill")
            }
            StatusLine(ok: screenOn && screen.authorizationStatus == .approved,
                       text: screenStatusText)
            if screenOn {
                ForEach(UsageGroup.allCases.filter { $0 != .other }, id: \.self) { group in
                    SettingsGroupRow(screen: screen, group: group) { selectionDirty = true }
                }
                if selectionDirty {
                    Button("Auswahl übernehmen") { Task { await setScreenTime(true) } }
                }
            }
        } footer: {
            Text("OS-Mon erfährt nur Minuten pro Gruppe, nie welche Apps. Ohne Bildschirmzeit bleibt „Ruhe“ leer und es gibt keine Bildschirmzeit- und Nacht-Meldungen.")
        }
    }

    private var stepsSection: some View {
        Section {
            Toggle(isOn: Binding(get: { stepsOn }, set: { on in Task { await setSteps(on) } })) {
                Label("Schritte (Apple Health)", systemImage: "figure.walk")
            }
            if stepsOn {
                Button("Health-Freigabe prüfen") { openSystemSettings() }
            }
        } footer: {
            Text("Nur die Tagessumme wird gelesen, nichts geschrieben. Die Leseerlaubnis nimmst du in den iOS-Einstellungen unter Datenschutz › Health zurück; iOS verrät OS-Mon nicht, ob sie erteilt ist.")
        }
    }

    private var pushSection: some View {
        Section {
            Toggle(isOn: Binding(get: { pushOn }, set: { on in
                pushOn = on
                PushDelivery.optedOut = !on
                if on && notifStatus == .notDetermined { Task { await requestNotifications() } }
            })) {
                Label("Meldungen vom Wesen", systemImage: "bell.fill")
            }
            switch notifStatus {
            case .denied:
                StatusLine(ok: false, text: "In iOS ausgeschaltet")
                Button("iOS-Einstellungen öffnen") { openSystemSettings() }
            case .notDetermined:
                StatusLine(ok: false, text: "Noch nicht erlaubt")
            default:
                StatusLine(ok: pushOn, text: pushOn ? "Erlaubt" : "In OS-Mon ausgeschaltet")
            }
            if pushOn {
                let cap = PushPolicy.dailyCap(pushState)
                Text("Derzeit höchstens \(cap) \(cap == 1 ? "Meldung" : "Meldungen") am Tag")
                    .font(.footnote).foregroundStyle(.secondary)
                if cap < PushPolicy.baseCap {
                    Button("Wieder bis zu \(PushPolicy.baseCap) Meldungen erlauben") {
                        PushStore.update { PushPolicy.onEngaged($0) }
                        pushState = PushStore.load()
                    }
                }
            }
        } header: {
            Text("Meldungen")
        } footer: {
            Text("Nie zwischen 22 und 8 Uhr (außer wenn du nachts am Handy bist). Wenn du Meldungen ignorierst, meldet sich dein Wesen seltener.")
        }
    }

    private var goalSection: some View {
        Section {
            Stepper(value: Binding(get: { screenGoal }, set: { v in
                screenGoal = v
                PetGoals.screenMinutes = v
                refreshPet()
            }), in: PetGoals.screenRange, step: 15) {
                LabeledContent("Bildschirmzeit am Tag", value: hoursText(screenGoal))
            }
            Stepper(value: Binding(get: { stepGoal }, set: { v in
                stepGoal = v
                PetGoals.steps = v
                refreshPet()
            }), in: PetGoals.stepsRange, step: 500) {
                LabeledContent("Schritte am Tag", value: stepGoal.formatted(.number.locale(Locale(identifier: "de_DE"))))
            }
        } header: {
            Text("Ziele")
        } footer: {
            Text("Bestimmen, ab wann dein Wesen gestresst wird bzw. voller Energie ist.")
        }
    }

    private var storageSection: some View {
        Section {
            InfoRow(symbol: "pawprint.fill", title: "Zustand deines Wesens", detail: "Letzte Werte und Stimmung.")
            InfoRow(symbol: "calendar", title: "Tageswerte",
                    detail: "Wohlbefinden-Mittel für \(PetData.loggedDays()) Tage, Streichel- und Fokus-Zähler.")
            InfoRow(symbol: "sparkles", title: "Entwicklung", detail: "Schlüpftag, Form und die letzten 28 Tage als Anteile pro Gruppe.")
            InfoRow(symbol: "moon", title: "Bildschirmzeit", detail: "Minuten pro Gruppe und Tag. Keine App-Namen.")
            InfoRow(symbol: "figure.walk", title: "Schritte", detail: "Nur die Tagessumme.")
            InfoRow(symbol: "bell", title: "Meldungen", detail: "Wann welche Meldung kam und ob du reagiert hast.")
        } header: {
            Text("Was gespeichert wird")
        } footer: {
            Text("\(PetData.ownKeys().count) Einträge, ausschließlich auf diesem Gerät.")
        }
    }

    private var dataSection: some View {
        Section {
            if let exportURL {
                ShareLink(item: exportURL) { Label("Daten exportieren (JSON)", systemImage: "square.and.arrow.up") }
            }
            if !locked {
                Button(role: .destructive) { confirmDelete = true } label: {
                    Label("Alle Daten löschen", systemImage: "trash")
                }
            }
            if let errorText { Text(errorText).font(.footnote).foregroundStyle(.red) }
        } footer: {
            Text("Der Export enthält alles, was OS-Mon über dich gespeichert hat, in lesbarer Form.")
        }
    }

    // MARK: Aktionen

    private var screenStatusText: String {
        guard screenOn else { return "Aus" }
        switch screen.authorizationStatus {
        case .approved: return screen.hasSelection ? "Aktiv" : "Aktiv, aber keine Apps zugeordnet"
        case .denied: return "Von iOS verweigert"
        default: return "Noch nicht erlaubt"
        }
    }

    private func setScreenTime(_ on: Bool) async {
        errorText = nil
        if on {
            guard screen.hasSelection else {
                screenOn = true                          // Gruppen-Zeilen zeigen, damit man zuordnen kann
                selectionDirty = true
                return
            }
            do {
                try await screen.requestAuthorization()
                try screen.startMonitoring()
                defaults.set(true, forKey: "feature.screentime")
                screenOn = true
                selectionDirty = false
            } catch {
                errorText = "Bildschirmzeit konnte nicht gestartet werden (\(error.localizedDescription))."
            }
        } else {
            screen.stopMonitoring()
            defaults.set(false, forKey: "feature.screentime")
            screenOn = false
            selectionDirty = false
        }
        refreshPet()
    }

    private func setSteps(_ on: Bool) async {
        if on {
            do {
                try await StepsProvider.shared.requestAuthorization()
                try await StepsProvider.shared.startBackgroundDelivery()
                await StepsProvider.shared.refresh()
                defaults.set(true, forKey: "feature.steps")
                stepsOn = true
            } catch {
                errorText = "Schritte konnten nicht aktiviert werden (\(error.localizedDescription))."
            }
        } else {
            await StepsProvider.shared.stopBackgroundDelivery()
            defaults.set(false, forKey: "feature.steps")
            stepsOn = false
        }
        refreshPet()
    }

    private func requestNotifications() async {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
        defaults.set(granted, forKey: "feature.notifications")
        await refreshStatus()
    }

    private func refreshStatus() async {
        notifStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        pushState = PushStore.load()
        exportURL = try? PetData.exportJSON()
    }

    /// Ziele oder Sensoren geändert: Wesen und Widget sofort nachziehen.
    private func refreshPet() {
        PetSync.reloadWidgetIfNeeded(PetSync.tick().snapshot, force: true)
        exportURL = try? PetData.exportJSON()
    }

    private func deleteAll() async {
        busy = true
        await PetData.deleteAll(screen: screen)
        busy = false
        dismiss()
        onDataDeleted()
    }

    private func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    private func hoursText(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        return m == 0 ? "\(h) Std." : "\(h) Std. \(m) Min."
    }
}

// MARK: - Bausteine

private struct InfoRow: View {
    let symbol: String
    let title: String
    let detail: String

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
        } icon: { Image(systemName: symbol) }
        .accessibilityElement(children: .combine)
    }
}

private struct StatusLine: View {
    let ok: Bool
    let text: String

    var body: some View {
        Label(text, systemImage: ok ? "checkmark.circle.fill" : "exclamationmark.circle")
            .font(.footnote)
            .foregroundStyle(ok ? .green : .secondary)
    }
}

private struct SettingsGroupRow: View {
    @ObservedObject var screen: ScreenTimeManager
    let group: UsageGroup
    var onChange: () -> Void
    @State private var presenting = false

    private var count: Int {
        let s = screen.selections[group]
        return (s?.applicationTokens.count ?? 0) + (s?.categoryTokens.count ?? 0) + (s?.webDomainTokens.count ?? 0)
    }

    var body: some View {
        Button { presenting = true } label: {
            LabeledContent(group.title, value: count == 0 ? "Auswählen" : "\(count) gewählt")
        }
        .familyActivityPicker(
            isPresented: $presenting,
            selection: Binding(
                get: { screen.selections[group] ?? FamilyActivitySelection() },
                set: { screen.selections[group] = $0; onChange() }))
    }
}

#Preview { SettingsView() }
