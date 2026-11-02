// OnboardingView.swift: Onboarding (iOS 17+, nur Haupt-App-Target)
//
// Ablauf (PRD 4.2): Willkommen -> Ei schlüpfen lassen -> Akku/Speicher (ohne Erlaubnis)
//   -> Bildschirmzeit (Gruppen zuordnen) -> Schritte (optional) -> Extras (Hinweise, Kurzbefehl).
// Jede Erlaubnis wird einzeln, just-in-time und mit Klartext angefragt. "Später" ist immer
// gleichwertig sichtbar; die App läuft ohne jede einzelne Erlaubnis degradiert weiter.
//
// Setzt die Flags im App-Group-Speicher:
//   "feature.screentime", "feature.steps", "feature.notifications", "onboarding.done"
// Hinweis: "feature.steps" heißt "Nutzer hat zugestimmt". iOS verrät nicht, ob HealthKit das Lesen
// wirklich erlaubt wurde (siehe StepsHealthKit.swift).
//
// Abhängigkeiten: PetFigure.swift (Figurenauswahl beim Schlüpfen), ScreenTimeApp.swift (ScreenTimeManager), StepsHealthKit.swift (StepsProvider),
// ScreenTimeShared.swift (STShared, UsageGroup), HomeView.swift (PetAvatarView), PetSync.swift.
// Die frühere ScreenTimeOnboardingView aus ScreenTimeApp.swift wird durch diese Datei ersetzt.
//
// Einstieg im App-Root (inkl. Benachrichtigungen und Hintergrund-Aktualisierung, siehe PushResponder.swift):
//   @main struct OSMonApp: App {
//       @AppStorage("onboarding.done", store: STShared.defaults) private var done = false
//       @Environment(\.scenePhase) private var phase
//       init() { PushResponder.shared.setUp() }
//       var body: some Scene {
//           WindowGroup { if done { HomeScreen() } else { OnboardingView { done = true } } }
//               .onChange(of: phase) { _, p in if p == .background { PetBackgroundRefresh.schedule() } }
//       }
//   }

import SwiftUI
import UIKit
import FamilyControls
import UserNotifications
import AppIntents
import WidgetKit

// MARK: - Kurzbefehl-Aktion (Automation "Akkustand fällt unter 20 %")

struct RefreshPetIntent: AppIntent {
    static let title: LocalizedStringResource = "OS-Mon aktualisieren"
    static let description = IntentDescription("Liest Akku, Speicher und Bildschirmzeit und aktualisiert dein Wesen.")

    func perform() async throws -> some IntentResult {
        await PetPushRun.tickAndDeliver()         // ticken, Widget bei Bedarf neu laden, Push-Regeln prüfen
        return .result()
    }
}

// MARK: - Zustand

enum OnboardingStep: Int, CaseIterable { case welcome, egg, basics, screenTime, steps, extras }

@MainActor
final class OnboardingModel: ObservableObject {
    @Published var step: OnboardingStep = .welcome
    @Published var eggTaps = 0
    @Published var screenTimeDone = false
    @Published var screenTimeError: String?
    @Published var stepsDone = false
    @Published var notificationsAsked = false
    @Published var notificationsGranted = false

    let screen = ScreenTimeManager()
    private let defaults = STShared.defaults

    var hatched: Bool { eggTaps >= 3 }

    var hasScreenSelection: Bool {
        screen.selections.values.contains {
            !($0.applicationTokens.isEmpty && $0.categoryTokens.isEmpty && $0.webDomainTokens.isEmpty)
        }
    }

    func next() {
        if let n = OnboardingStep(rawValue: step.rawValue + 1) { step = n }
    }

    func back() {
        if let p = OnboardingStep(rawValue: step.rawValue - 1) { step = p }
    }

    func tapEgg() { eggTaps += 1 }

    func enableScreenTime() async {
        screenTimeError = nil
        do {
            try await screen.requestAuthorization()      // System-Dialog (braucht Family-Controls-Entitlement)
            try screen.startMonitoring()
            defaults.set(true, forKey: "feature.screentime")
            screenTimeDone = true
        } catch {
            screenTimeError = "Das hat nicht geklappt (\(error.localizedDescription)). Du kannst es später in den Einstellungen erneut versuchen."
        }
    }

    func enableSteps() async {
        do {
            try await StepsProvider.shared.requestAuthorization()
            try await StepsProvider.shared.startBackgroundDelivery()
            await StepsProvider.shared.refresh()
            defaults.set(true, forKey: "feature.steps")
            stepsDone = true
        } catch {
            stepsDone = false
        }
    }

    func enableNotifications() async {
        let granted = (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound])) ?? false
        defaults.set(granted, forKey: "feature.notifications")
        notificationsGranted = granted
        notificationsAsked = true
    }

    func finish() {
        defaults.set(true, forKey: "onboarding.done")
        EvolutionStore.ensureHatched()                       // Schlüpftag festlegen (Evolution ab Tag 11)
    }
}

// MARK: - Screen

struct OnboardingView: View {
    @StateObject private var model = OnboardingModel()
    var onFinished: () -> Void = {}

    var body: some View {
        VStack(spacing: 16) {
            ProgressDots(count: OnboardingStep.allCases.count, index: model.step.rawValue)
                .padding(.top, 8)

            Group {
                switch model.step {
                case .welcome:    WelcomeStep()
                case .egg:        EggStep(model: model)
                case .basics:     BasicsStep()
                case .screenTime: ScreenTimeStep(model: model, screen: model.screen)
                case .steps:      StepsStep(model: model)
                case .extras:     ExtrasStep(model: model)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .id(model.step)
            .transition(.opacity)

            footer
        }
        .padding()
        .animation(.easeInOut(duration: 0.25), value: model.step)
    }

    // MARK: Fußleiste: Primäraktion + gleichwertig sichtbares "Später"

    @ViewBuilder
    private var footer: some View {
        VStack(spacing: 8) {
            switch model.step {
            case .welcome:
                primary("Los geht’s") { model.next() }

            case .egg:
                primary("Weiter", enabled: model.hatched) { model.next() }

            case .basics:
                primary("Weiter") { model.next() }

            case .screenTime:
                if model.screenTimeDone {
                    primary("Weiter") { model.next() }
                } else {
                    primary("Bildschirmzeit aktivieren", enabled: model.hasScreenSelection) {
                        Task { await model.enableScreenTime() }
                    }
                    secondary("Später") { model.next() }
                }

            case .steps:
                if model.stepsDone {
                    primary("Weiter") { model.next() }
                } else {
                    primary("Schritte erlauben") { Task { await model.enableSteps() } }
                    secondary("Später") { model.next() }
                }

            case .extras:
                primary("Wesen kennenlernen") {
                    model.finish()
                    onFinished()
                }
            }

            if model.step != .welcome {
                secondary("Zurück") { model.back() }.font(.footnote)
            }
        }
    }

    private func primary(_ title: String, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(title).frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).controlSize(.large).disabled(!enabled)
    }

    private func secondary(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action).buttonStyle(.bordered).controlSize(.regular)
    }
}

// MARK: - Schritte

private struct WelcomeStep: View {
    var body: some View {
        StepPage(symbol: "heart.circle.fill", title: "Dein Handy ist das Haustier",
                 text: "OS-Mon ist ein Wesen, das sich freut, wenn es deinem Handy und dir gut geht.") {
            VStack(alignment: .leading, spacing: 10) {
                Label("Akku wird zu Energie", systemImage: "bolt.fill")
                Label("Freier Speicher wird zu Fitness", systemImage: "internaldrive.fill")
                Label("Bildschirmzeit wird zu Ruhe", systemImage: "moon.fill")
                Label("Schritte werden zu Vitalität", systemImage: "figure.walk")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            PrivacyNote("Alles wird auf deinem Gerät berechnet. Nichts wird hochgeladen. Das Wesen stirbt nie, es schläft höchstens.")
        }
    }
}

private struct EggStep: View {
    @ObservedObject var model: OnboardingModel
    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID

    var body: some View {
        StepPage(symbol: nil, title: model.hatched ? "Hallo, \(PetFigure.byID(figureID).name)!" : "Tippe auf das Ei",
                 text: model.hatched ? "Dein Wesen ist geschlüpft. Such dir aus, wie es aussieht. Das geht später jederzeit in den Einstellungen."
                                     : "Noch \(max(0, 3 - model.eggTaps))× tippen …") {
            if model.hatched {
                PetAvatarView(mood: .radiant, overlays: [], form: nil, branch: nil)
                    .scaleEffect(0.85)
                    .frame(height: 190)
                    .transition(.scale.combined(with: .opacity))
                PetFigurePicker()
                    .transition(.opacity)
            } else {
                Text("🥚").font(.system(size: 120))
                    .rotationEffect(.degrees(model.eggTaps.isMultiple(of: 2) ? -6 : 6))
                    .animation(.spring(response: 0.2, dampingFraction: 0.4), value: model.eggTaps)
                    .onTapGesture { model.tapEgg() }
                    .accessibilityLabel("Ei")
                    .accessibilityHint("Aktivieren, damit es schlüpft. Dreimal nötig.")
                    .accessibilityAddTraits(.isButton)
            }
        }
        .animation(.easeInOut, value: model.hatched)
    }
}

private struct BasicsStep: View {
    var body: some View {
        StepPage(symbol: "battery.100.bolt", title: "Das geht ohne Erlaubnis",
                 text: "Diese beiden Werte liest OS-Mon direkt vom Gerät:") {
            VStack(alignment: .leading, spacing: 10) {
                Label("Akkustand und Ladezustand", systemImage: "battery.75")
                Label("Freier Speicherplatz (nur als Anteil)", systemImage: "internaldrive")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            PrivacyNote("OS-Mon sieht keine Dateien, Fotos oder Inhalte, nur die beiden Zahlen.")
        }
    }
}

private struct ScreenTimeStep: View {
    @ObservedObject var model: OnboardingModel
    @ObservedObject var screen: ScreenTimeManager

    var body: some View {
        StepPage(symbol: "moon.fill", title: "Bildschirmzeit",
                 text: "Apple lässt OS-Mon nicht sehen, welche Apps du nutzt, nur zählen. Ordne deine Apps Gruppen zu: OS-Mon erfährt dann nur die Minuten pro Gruppe. Eine App sollte nur in einer Gruppe stehen.") {
            VStack(spacing: 0) {
                ForEach(UsageGroup.allCases.filter { $0 != .other }, id: \.self) { group in
                    GroupRow(screen: screen, group: group)
                    Divider()
                }
            }
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))

            if model.screenTimeDone {
                Label("Aktiv", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
            }
            if let e = model.screenTimeError {
                Text(e).font(.footnote).foregroundStyle(.red)
            }
            PrivacyNote("Ohne Bildschirmzeit bleibt der Ring „Ruhe“ leer, alles andere funktioniert.")
        }
    }
}

private struct GroupRow: View {
    @ObservedObject var screen: ScreenTimeManager
    let group: UsageGroup
    @State private var presenting = false

    private var count: Int {
        let s = screen.selections[group]
        return (s?.applicationTokens.count ?? 0) + (s?.categoryTokens.count ?? 0) + (s?.webDomainTokens.count ?? 0)
    }

    var body: some View {
        Button { presenting = true } label: {
            HStack {
                Text(group.title)
                Spacer()
                Text(count == 0 ? "Auswählen" : "\(count) gewählt").foregroundStyle(.secondary)
                Image(systemName: "chevron.right").font(.footnote).foregroundStyle(.tertiary)
            }
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .familyActivityPicker(
            isPresented: $presenting,
            selection: Binding(
                get: { screen.selections[group] ?? FamilyActivitySelection() },
                set: { screen.selections[group] = $0 }))
    }
}

private struct StepsStep: View {
    @ObservedObject var model: OnboardingModel

    var body: some View {
        StepPage(symbol: "figure.walk", title: "Schritte (optional)",
                 text: "OS-Mon liest nur deine Tagesschritte aus Apple Health, nichts wird geschrieben. Manuell eingetragene Werte zählen nicht.") {
            if model.stepsDone {
                Label("Aktiv", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
            }
            PrivacyNote("Schritte sind Gesundheitsdaten: Sie bleiben auf dem Gerät und du kannst die Freigabe jederzeit in den iOS-Einstellungen unter Datenschutz › Health widerrufen. Falls später keine Schritte erscheinen, prüfe dort die Freigabe, denn iOS meldet einer App nicht, ob sie lesen darf.")
        }
    }
}

private struct ExtrasStep: View {
    @ObservedObject var model: OnboardingModel

    var body: some View {
        StepPage(symbol: "bell.badge.fill", title: "Fast geschafft",
                 text: "Dein Wesen meldet sich höchstens dreimal am Tag, nie in der Nachtruhe und lernt, wenn du Meldungen ignorierst.") {
            Button {
                Task { await model.enableNotifications() }
            } label: {
                Label(model.notificationsAsked
                      ? (model.notificationsGranted ? "Meldungen erlaubt" : "Meldungen nicht erlaubt")
                      : "Meldungen erlauben",
                      systemImage: model.notificationsGranted ? "checkmark.circle.fill" : "bell")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered).controlSize(.large)
            .disabled(model.notificationsAsked)

            VStack(alignment: .leading, spacing: 6) {
                Text("Tipp: Akku-Warnung im Hintergrund").font(.subheadline.bold())
                Text("iOS meldet App-Änderungen des Akkus nicht von selbst. Mit einer Kurzbefehle-Automation „Akkustand fällt unter 20 %“ und der Aktion „OS-Mon aktualisieren“ merkt sich dein Wesen auch dann, wenn die App geschlossen ist.")
                    .font(.footnote).foregroundStyle(.secondary)
                Button("Kurzbefehle öffnen") {
                    if let url = URL(string: "shortcuts://") { UIApplication.shared.open(url) }
                }
                .font(.footnote)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Bausteine

private struct StepPage<Content: View>: View {
    let symbol: String?
    let title: String
    let text: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                if let symbol {
                    Image(systemName: symbol).font(.system(size: 54)).foregroundStyle(.tint)
                        .accessibilityHidden(true)
                }
                Text(title).font(.title.bold()).multilineTextAlignment(.center)
                Text(text).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
                content()
            }
            .padding(.vertical, 8)
        }
        .scrollBounceBehavior(.basedOnSize)
    }
}

private struct PrivacyNote: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Label(text, systemImage: "lock.fill")
            .font(.footnote).foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ProgressDots: View {
    let count: Int
    let index: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { i in
                Capsule()
                    .fill(i == index ? Color.accentColor : Color.secondary.opacity(0.3))
                    .frame(width: i == index ? 22 : 8, height: 8)
            }
        }
        .animation(.easeInOut, value: index)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Schritt \(index + 1) von \(count)")
    }
}

#Preview("Willkommen") { OnboardingView() }
