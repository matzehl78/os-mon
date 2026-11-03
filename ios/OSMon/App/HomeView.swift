// HomeView.swift: Home-Screen (iOS 17+, nur Haupt-App-Target)
//
// Abhängigkeiten im Target: PetFigure.swift + PetFigureData.swift (Figur), PetSync.swift (Kern, Anzeige-Zuordnungen, PetCopy), PetCore.swift,
// ScreenTimeShared.swift, EvolutionJob.swift, PetScene.swift (Hintergrund, Wochenrückblick), PetProgress.swift,
// PetReaction.swift (Antippen). Sensoren, Snapshot, Streicheln und Fokus laufen über
// PetSync, damit App und Widget denselben Zustand und dieselbe Formel nutzen.
//
// Aufbau (PRD 4.2): Wohlbefinden-Ring + Stimmung oben, Wesen mit Sprechblase, 4 Statusringe
// (Energie, Ruhe, Fitness, Vitalität), Aktionen "Fokus starten" / "Streicheln", Quest-Karten,
// Fußzeile "zuletzt aktualisiert". Die Ringe stehen in einer Reihe unter dem Wesen (statt rundherum),
// damit der Screen auf kleinen Geräten nicht überladen wirkt.
//
// Das Wesen zeichnet PetFigure.swift in der gewählten Figur (kein Asset nötig). Es hängt nur an
// (mood, overlays, form, branch, Energie, Ruhe), also lässt es sich später durch eine Rive-Animation
// ersetzen, ohne den Rest des Screens anzufassen.
//
// Mit Hintergrund-Szene: Bild von oben bis knapp unter die Statusringe, Plattform unter den Füßen der Figur,
// Kopfzeile und Ringe auf Glas-Karten. Antippen der Figur löst eine Reaktion aus (Gesicht, Arme/Beine, Hüpfen,
// Emoji-Partikel, Sprechblase, Haptik). Antippen ändert keine Werte. Onboarding nutzt weiter PetAvatarView ohne Szene.
//
// Flags im App-Group-Speicher, die das Onboarding setzen muss:
//   "feature.screentime" (Bool), "feature.steps" (Bool)
// Ohne Flag zeigt der Ring "–" und ein Tipp öffnet über onMissingPermission den Berechtigungsfluss.
// Das hält die App im degradierten Betrieb ehrlich (iOS verrät eine verweigerte Schritte-Erlaubnis nicht).

import SwiftUI
import UIKit
import WidgetKit

// MARK: - Zustand für die Oberfläche

enum PetSensor { case screenTime, steps }

struct Quest: Identifiable {
    let id: String
    let title: String
    let detail: String
    let progress: Double          // 0...1
    var isLimit = false           // "unter Ziel bleiben": voller Balken ist schlecht
    var isDone: Bool { !isLimit && progress >= 1 }
}

struct HomeState {
    var snapshot: PetSnapshot
    var evolution: PetEvolution?          // nil = noch Ei/Baby (vor Tag 11)
    var quests: [Quest]
    var updatedAt: Date
    var canPat: Bool
    var patReadyAt: Date?
    var focusEndsAt: Date?
    var screenTimeEnabled: Bool
    var stepsEnabled: Bool
    var evolutionEvent: PetEvoEvent? = nil     // Entwicklung/Metamorphose, bis der Nutzer sie bestätigt
    var review: WeeklyReview? = nil            // Wochenrückblick, bis weggeklickt
    var sceneStatus: [SceneUnlock] = []
    var newScenes: [PetScene] = []             // neu freigeschaltete Hintergründe, bis bestätigt
    var sleepPhase: DayPhase = .day            // Abend/Nacht: Einladung zur Gute-Nacht-Routine
    var morning: MorningInfo? = nil            // Guten-Morgen-Karte, bis weggeklickt
    var festival: Festival? = nil              // Fest heute (Zubehör, Deko, Gruß)
    var festivalCard = false                   // Karte „Feiern“ sichtbar
    var season: Season = .autumn               // Jahreszeit (Tönung, Deko, Teilchen)
    var seasonsOn = true

    static let empty = HomeState(
        snapshot: PetSnapshot(stats: PetStats(e: 100, r: 100, f: 100, v: 100, a: 70),
                              wellbeing: 90, mood: .radiant, overlays: []),
        evolution: nil, quests: [], updatedAt: .now, canPat: true, patReadyAt: nil,
        focusEndsAt: nil, screenTimeEnabled: false, stepsEnabled: false)
}

// MARK: - ViewModel (PetSync -> Anzeige)

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state = HomeState.empty

    private let defaults = STShared.defaults
    private let stepQuestTarget = 5000
    private let focusQuestTargetMin = 45

    /// Ein Update-Zyklus: verpasste Tage abschließen, ticken, Widget bei spürbarer Änderung neu laden.
    func refresh(now: Date = .now) {
        let evo = EvolutionJob.closePastDays()                     // idempotent, holt verpasste Tage nach
        let t = PetSync.tick(now: now)                             // speichert Snapshot + DayLog
        PetSync.reloadWidgetIfNeeded(t.snapshot)

        let readyAt = PetSync.patReadyAt(now: now)
        let prog = ProgressData.refresh(today: EpochDay.of(now))
        var newScenes = state.newScenes
        for s in prog.newlyUnlocked where !newScenes.contains(s) { newScenes.append(s) }
        state = HomeState(
            snapshot: t.snapshot,
            evolution: evo.current,
            quests: quests(for: t.inputs, now: now),
            updatedAt: now,
            canPat: readyAt == nil,
            patReadyAt: readyAt,
            focusEndsAt: PetSync.focusEndsAt,
            screenTimeEnabled: defaults.bool(forKey: "feature.screentime"),
            stepsEnabled: defaults.bool(forKey: "feature.steps"),
            evolutionEvent: evo.pendingEvent,
            review: prog.review,
            sceneStatus: prog.status,
            newScenes: newScenes,
            sleepPhase: SleepStore.phase(now),
            morning: SleepStore.morning(now: now, screenTimeOn: defaults.bool(forKey: "feature.screentime")),
            festival: FestivalStore.today(now),
            festivalCard: FestivalStore.today(now) != nil && !FestivalStore.cardHidden(EpochDay.of(now)),
            season: SceneTheme.season(day: EpochDay.of(now)),
            seasonsOn: FestivalStore.seasons)
    }

    func hideFestivalCard() {
        FestivalStore.hideCard(EpochDay.of(.now))
        state.festivalCard = false
    }

    func dismissMorning() {
        guard let m = state.morning else { return }
        SleepStore.markMorningSeen(m.day)
        state.morning = nil
    }

    func dismissReview() {
        guard let r = state.review else { return }
        SceneStore.markReviewSeen(r.weekStart)
        state.review = nil
    }

    func useScene(_ s: PetScene) {
        SceneStore.select(s.id, status: state.sceneStatus)
        state.newScenes = []
    }

    func ackScenes() { state.newScenes = [] }

    func ackEvolution() {
        EvolutionStore.acknowledge()
        refresh()
    }

    func pat(now: Date = .now) {
        guard PetSync.pat(now: now) else { return }
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        refresh(now: now)
        PetSync.reloadWidgetIfNeeded(state.snapshot, force: true)   // Widget-Button ändert sich immer
    }

    /// Eine Fokus-Session zählt erst, wenn sie bis zum Ende durchläuft (nicht beim Starten).
    /// Live Activity / App-Shield folgen als eigener Schritt.
    func startFocus(minutes: Int, now: Date = .now) {
        PetSync.startFocus(minutes: minutes, now: now)
        refresh(now: now)
    }

    func cancelFocus() {
        PetSync.cancelFocus()
        refresh()
    }

    private func quests(for i: PetInputs, now: Date) -> [Quest] {
        var out: [Quest] = []
        let focus = PetSync.counter("focusMinutes", now: now)
        out.append(Quest(id: "focus", title: "Handy \(focusQuestTargetMin) Min weglegen",
                         detail: "\(focus) / \(focusQuestTargetMin) Min",
                         progress: min(1, Double(focus) / Double(focusQuestTargetMin))))
        if let steps = i.steps {
            out.append(Quest(id: "steps", title: "5.000 Schritte gehen",
                             detail: "\(steps) / \(stepQuestTarget)",
                             progress: min(1, Double(steps) / Double(stepQuestTarget))))
        }
        if let m = i.screenMinutes {
            let goal = PetSync.config.screenGoalMin
            out.append(Quest(id: "screen", title: "Bildschirmzeit unter Ziel bleiben",
                             detail: "\(Int(m)) / \(Int(goal)) Min",
                             progress: min(1, Double(m / goal)), isLimit: true))
        }
        return out
    }
}

// MARK: - Screen mit Lebenszyklus

struct HomeScreen: View {
    @StateObject private var model = HomeViewModel()
    @Environment(\.scenePhase) private var phase
    @State private var showSettings = false
    @State private var showBedtime = false
    /// Ohne eigene Behandlung öffnet ein Tipp auf einen leeren Ring die Einstellungen.
    var onMissingPermission: ((PetSensor) -> Void)? = nil

    var body: some View {
        HomeContent(
            state: model.state,
            onPat: { model.pat() },
            onStartFocus: { model.startFocus(minutes: $0) },
            onCancelFocus: { model.cancelFocus() },
            onMissing: { sensor in
                if let handler = onMissingPermission { handler(sensor) } else { showSettings = true }
            },
            onAckEvolution: { model.ackEvolution() },
            onOpenSettings: { showSettings = true },
            onDismissReview: { model.dismissReview() },
            onUseScene: { model.useScene($0) },
            onAckScenes: { model.ackScenes() },
            onOpenBedtime: { showBedtime = true },
            onDismissMorning: { model.dismissMorning() },
            onHideFestival: { model.hideFestivalCard() })
            .fullScreenCover(isPresented: $showBedtime, onDismiss: { model.refresh() }) { BedtimeView { showBedtime = false } }
            .sheet(isPresented: $showSettings, onDismiss: { model.refresh() }) {
                SettingsView()          // "Alle Daten löschen" setzt onboarding.done zurück -> App-Root wechselt
            }
            .onAppear { SleepStore.noteOpen(); model.refresh() }
            .onChange(of: phase) { _, new in if new == .active { SleepStore.noteOpen(); model.refresh() } }
            .onReceive(Timer.publish(every: 60, on: .main, in: .common).autoconnect()) { _ in model.refresh() }
    }
}

// MARK: - Reine Darstellung (gut für Previews)

struct HomeContent: View {
    let state: HomeState
    var onPat: () -> Void = {}
    var onStartFocus: (Int) -> Void = { _ in }
    var onCancelFocus: () -> Void = {}
    var onMissing: (PetSensor) -> Void = { _ in }
    var onAckEvolution: () -> Void = {}
    var onOpenSettings: (() -> Void)? = nil
    var onDismissReview: () -> Void = {}
    var onUseScene: (PetScene) -> Void = { _ in }
    var onAckScenes: () -> Void = {}
    var onOpenBedtime: () -> Void = {}
    var onDismissMorning: () -> Void = {}
    var onHideFestival: () -> Void = {}
    var demoScrollTo: String? = nil               // nur Screenshots: z. B. "review"

    private var snap: PetSnapshot { state.snapshot }
    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID
    @AppStorage(SceneStore.key, store: STShared.defaults) private var sceneID = PetScene.defaultID
    private var scene: PetScene { PetScene.byID(sceneID) }
    private var hasScene: Bool { scene.asset != nil }

    // Lage von Figur und Ringen im Inhalt (für die Bildlage)
    @State private var stageFrame: CGRect = .zero
    @State private var headerFrame: CGRect = .zero
    @State private var topInset: CGFloat = 0
    @State private var ringsFrame: CGRect = .zero
    // Antippen
    @State private var picker = ReactionPicker()
    @State private var reaction: ActiveReaction? = nil
    @State private var celebrateAt: Date? = nil        // „Feiern“: Konfetti-Schub

    var body: some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(spacing: hasScene ? 14 : 20) {
                glass(header)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("home")) } action: { headerFrame = $0 }
                VStack(spacing: 8) {
                    speechBubble
                    pet
                }
                glass(rings)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("home")) } action: { ringsFrame = $0 }
                actions
                if let f = state.festival, state.festivalCard {
                    FestivalCard(festival: f, onCelebrate: celebrate, onHide: onHideFestival).id("festival")
                }
                if let m = state.morning { MorningCard(info: m, onDismiss: onDismissMorning).id("morning") }
                if state.sleepPhase == .windDown || state.sleepPhase == .night {
                    BedtimePromptCard(phase: state.sleepPhase, onStart: onOpenBedtime).id("bedtime")
                }
                if let r = state.review { WeeklyReviewCard(review: r, onDismiss: onDismissReview).id("review") }
                if !state.quests.isEmpty { questList }
                TasksSection(onCheer: cheer).id("tasks")
                Text("Zuletzt aktualisiert: \(state.updatedAt.formatted(.relative(presentation: .named)))")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            .padding()
            // Reihenfolge: das zuerst angehängte .background liegt OBEN. Deko also vor dem Szenenbild anhängen,
            // sonst verdeckt das (deckende) Bild sie.
            .background(alignment: .top) {
                // Jahreszeit + Fest: Tönung, Requisiten (Tannenbaum, Ballons …) und schwebende Teilchen über dem Bild,
                // mit und ohne Hintergrund, bis knapp unter die Ringe
                if ringsFrame.height > 0 && stageFrame.height > 0 {
                    let layerH = max(200, ringsFrame.maxY) + topInset
                    let day = EpochDay.of(.now)
                    let props = SceneTheme.props(festival: state.festival, season: state.season, seasons: state.seasonsOn,
                                                 adventCandles: Festivals.adventNumber(day, Festivals.firstAdvent(Festivals.ymd(day).y)))
                    ZStack(alignment: .top) {
                        SceneThemeLayer(props: props, tint: state.seasonsOn && hasScene ? SceneTheme.tint(state.season) : nil,
                                        feetY: topInset + stageFrame.minY + stageFrame.height * 0.92,
                                        garlandY: topInset + headerFrame.maxY + 6)
                        if let decor = SceneTheme.particles(festival: state.festival, season: state.season, seasons: state.seasonsOn) {
                            FestivalDecor(decor: decor, burst: celebrateAt)
                        }
                    }
                    .frame(height: layerH)
                    .frame(maxWidth: .infinity)
                    .offset(y: -topInset)
                }
            }
            .background(alignment: .top) {
                if hasScene && stageFrame.height > 0 && ringsFrame.height > 0 {
                    // Ebene reicht nach oben unter die Statusleiste (topInset) und endet knapp unter den Ringen.
                    // Die ScrollView reicht selbst bis an den Bildschirmrand, darum ist der Teil oberhalb sichtbar.
                    let layerH = max(200, ringsFrame.maxY + 14) + topInset
                    SceneBackground(scene: scene, mood: snap.mood,
                                    feetY: topInset + stageFrame.minY + stageFrame.height * 0.92,
                                    fade: ringsFrame.height * 0.75 + 14)
                        .frame(height: layerH)
                        .frame(maxWidth: .infinity)
                        .offset(y: -topInset)
                }
            }
            .coordinateSpace(.named("home"))
        }
        .onAppear {
            guard let target = demoScrollTo else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { proxy.scrollTo(target, anchor: .center) }
        }
        }
        .onGeometryChange(for: CGFloat.self) { $0.safeAreaInsets.top } action: { topInset = $0 }
        // Hinweise (Entwicklung, neuer Hintergrund) unten als Einblendung: verschieben Kopfzeile und Wesen nicht
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if state.evolutionEvent != nil || !state.newScenes.isEmpty {
                VStack(spacing: 8) {
                    if let event = state.evolutionEvent { EvolutionBanner(event: event, onAck: onAckEvolution) }
                    if !state.newScenes.isEmpty { SceneUnlockBanner(scenes: state.newScenes, onUse: onUseScene, onDismiss: onAckScenes) }
                }
                .padding(.horizontal).padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.35), value: state.newScenes.isEmpty)
        .background(
            Group {
                if hasScene { Color(.systemBackground) }
                else { LinearGradient(colors: [snap.mood.color.opacity(0.22), Color(.systemBackground)], startPoint: .top, endPoint: .center) }
            }
            .ignoresSafeArea())
    }

    /// Glas-Karte über dem Bild (dunkel, halbtransparent, weiße Schrift). Ohne Szene: unverändert.
    @ViewBuilder
    private func glass<V: View>(_ v: V) -> some View {
        if hasScene {
            v.padding(.horizontal, 12).padding(.vertical, 10)
                .background(Color(red: 14 / 255, green: 18 / 255, blue: 48 / 255).opacity(0.55), in: RoundedRectangle(cornerRadius: 22))
                .shadow(color: .black.opacity(0.25), radius: 8, y: 3)
                .environment(\.colorScheme, .dark)
        } else {
            v
        }
    }

    /// Sprechblase über der Figur: Reaktionstext 2,6 s, sonst Stimmungssatz.
    private var speechBubble: some View {
        TimelineView(.periodic(from: .now, by: 0.25)) { tl in
            let say = reaction.flatMap { $0.saying(tl.date) ? $0.say : nil }
            Text(say ?? state.festival?.greeting ?? PetCopy.line(for: snap))
                .font(say == nil ? .callout : .callout.weight(.semibold))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                .shadow(color: .black.opacity(hasScene ? 0.2 : 0), radius: 6, y: 2)
                .animation(.easeOut(duration: 0.2), value: say)
        }
    }

    /// Das Wesen (antippbar) mit Partikeln.
    private var pet: some View {
        PetAvatarView(mood: snap.mood, overlays: snap.overlays,
                      form: state.evolution?.form, branch: state.evolution?.branch,
                      energy: snap.stats.e, rest: snap.stats.r,
                      size: hasScene ? 260 : 220, reaction: reaction, accessory: state.festival?.accessory)
            .overlay { if let r = reaction { ReactionParticles(reaction: r) } }
            .contentShape(Rectangle())
            .onTapGesture { tapPet() }
            .accessibilityAddTraits(.isButton)
            .accessibilityHint("Antippen für eine Reaktion")
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("home")) } action: { stageFrame = $0 }
    }

    /// Aufgabe abgehakt / Miteinander-Tipp ausprobiert: das Wesen freut sich mit (alles geschafft = Drehung + Konfetti).
    private func cheer(_ all: Bool, _ line: String) {
        reaction = ActiveReaction(kind: all ? .spin : .giggle, start: .now, say: line)
        if all { celebrateAt = .now }
    }

    private func celebrate() {
        reaction = picker.start(.spin)
        celebrateAt = .now
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func tapPet() {
        let k = picker.pick(mood: snap.mood, overlays: snap.overlays)
        reaction = picker.start(k)
        switch k {
        case .dizzy, .startle: UINotificationFeedbackGenerator().notificationOccurred(.warning)
        case .weak, .sleepy, .night: UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.6)
        default: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            StatusRing(title: "Wohlbefinden", symbol: "heart.fill", value: snap.wellbeing,
                       color: snap.mood.color, size: 72)
            VStack(alignment: .leading, spacing: 2) {
                Text(snap.mood.title).font(.title2.bold())
                if let evo = state.evolution {
                    // Figurenname bleibt sichtbar; die Entwicklungsform steht dahinter
                    Text(PetFigure.byID(figureID).name + " · " + evo.form.title + (evo.hybrid.map { " \($0.badge)" } ?? ""))
                        .font(.subheadline).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
                } else {
                    Text(PetFigure.byID(figureID).name).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let onOpenSettings {
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape").font(.title3)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Einstellungen")
            }
        }
    }

    private var rings: some View {
        HStack(alignment: .top) {
            StatusRing(title: "Energie", symbol: "bolt.fill", value: snap.stats.e, color: .yellow)
            StatusRing(title: "Ruhe", symbol: "moon.fill", value: snap.stats.r, color: hasScene ? Color(red: 0.49, green: 0.55, blue: 1) : .indigo,
                       onMissingTap: state.screenTimeEnabled ? nil : { onMissing(.screenTime) })
            StatusRing(title: "Fitness", symbol: "internaldrive.fill", value: snap.stats.f, color: .orange)
            StatusRing(title: "Vitalität", symbol: "figure.walk", value: snap.stats.v, color: .green,
                       onMissingTap: state.stepsEnabled ? nil : { onMissing(.steps) })
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var actions: some View {
        if let end = state.focusEndsAt, end > .now {
            HStack {
                Label {
                    Text(timerInterval: Date.now...end, countsDown: true)
                        .monospacedDigit()
                } icon: { Image(systemName: "moon.zzz.fill") }
                Spacer()
                Button("Beenden", role: .destructive, action: onCancelFocus)
            }
            .padding()
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
        } else {
            HStack(spacing: 12) {
                Menu {
                    Button("15 Minuten") { onStartFocus(15) }
                    Button("30 Minuten") { onStartFocus(30) }
                    Button("60 Minuten") { onStartFocus(60) }
                } label: {
                    Label("Fokus starten", systemImage: "moon.zzz.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)

                Button(action: onPat) {
                    Label("Streicheln", systemImage: "hand.tap.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered).controlSize(.large)
                .disabled(!state.canPat)
            }
            if let ready = state.patReadyAt {
                Text("Streicheln wieder ab \(ready.formatted(date: .omitted, time: .shortened))")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var questList: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Heute").font(.headline)
            ForEach(state.quests) { QuestCard(quest: $0) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Bausteine

struct EvolutionBanner: View {
    let event: PetEvoEvent
    let onAck: () -> Void

    private var text: String {
        switch event.kind {
        case .evolved:
            return "Dein Wesen hat sich entwickelt: \(event.to.badge) \(event.to.title)!"
        case .metamorphosis:
            return "Metamorphose! Aus \(event.from?.title ?? "deinem Wesen") wird \(event.to.badge) \(event.to.title)."
        }
    }

    var body: some View {
        HStack {
            Text("✨ \(text)").font(.callout)
            Spacer()
            Button("Okay", action: onAck).buttonStyle(.bordered)
        }
        .padding(12)
        .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 16))
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.2), radius: 10, y: 4)
        .accessibilityElement(children: .combine)
    }
}

struct StatusRing: View {
    let title: String
    let symbol: String
    let value: Float?                     // nil = Sensor/Erlaubnis fehlt
    let color: Color
    var size: CGFloat = 60
    var onMissingTap: (() -> Void)? = nil

    var body: some View {
        let content = VStack(spacing: 5) {
            ZStack {
                Circle().stroke(color.opacity(0.22), lineWidth: 8)
                if let value {
                    Circle()
                        .trim(from: 0, to: CGFloat(max(0, min(100, value)) / 100))
                        .stroke(color, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.easeOut(duration: 0.6), value: value)
                } else {
                    Circle().stroke(Color.secondary.opacity(0.5),
                                    style: StrokeStyle(lineWidth: 8, dash: [3, 6]))
                }
                Image(systemName: symbol)
                    .font(size > 64 ? .title2 : .body)
                    .foregroundStyle(value == nil ? AnyShapeStyle(.secondary) : AnyShapeStyle(color))
            }
            .frame(width: size, height: size)
            Text(value.map { "\(Int($0.rounded()))" } ?? "–")
                .font(.subheadline.monospacedDigit().bold())
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)

        Group {
            if value == nil, let tap = onMissingTap {
                Button(action: tap) { content }.buttonStyle(.plain)
                    .accessibilityLabel("\(title): keine Daten. Tippen, um die Berechtigung zu erteilen.")
            } else {
                content
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(value.map { "\(title) \(Int($0.rounded())) Prozent" } ?? "\(title): keine Daten")
            }
        }
    }
}

struct QuestCard: View {
    let quest: Quest

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: quest.isDone ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(quest.isDone ? .green : .secondary)
                Text(quest.title).font(.subheadline.weight(.medium))
                Spacer()
                Text(quest.detail).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            ProgressView(value: quest.progress)
                .tint(quest.isLimit && quest.progress >= 0.8 ? .orange : .accentColor)
        }
        .padding(12)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Das Wesen (gewählte Figur, gezeichnet von PetFigure.swift)

/// Dünne Hülle um PetFigureView, damit Onboarding und Home dieselbe Schnittstelle behalten.
/// Die Figur kommt aus der Auswahl in den Einstellungen ("pet.figure" im App-Group-Speicher).
struct PetAvatarView: View {
    let mood: PetMood
    let overlays: Set<PetOverlay>
    let form: PetForm?
    let branch: PetBranch?
    var energy: Float? = 100
    var rest: Float? = 100
    var charging = false
    var petted = false
    var size: CGFloat = 220
    var reaction: ActiveReaction? = nil
    var accessory: Accessory? = nil

    var body: some View {
        PetFigureView(pose: PetPose(mood: mood, overlays: overlays, form: form, branch: branch,
                                    energy: energy, rest: rest, charging: charging, petted: petted, accessory: accessory),
                      reaction: reaction)
            .frame(width: size, height: size)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Dein Wesen: \(mood.title)")
    }
}

/// Emoji-Partikel der Reaktion: steigen auf und verblassen (Dauer = Reaktion + 0,4 s).
struct ReactionParticles: View {
    let reaction: ActiveReaction
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation) { tl in
            let total = reaction.kind.duration + 0.4
            let u = min(1, max(0, tl.date.timeIntervalSince(reaction.start) / total))
            let fx = reaction.kind.fx
            let n = max(3, fx.count * 2)
            ZStack {
                if u < 1 && !reduceMotion {
                    ForEach(0..<n, id: \.self) { i in
                        let d = Double(i) * 0.08
                        let p = min(1, max(0, (u - d) / (1 - d)))
                        let x = (Double(i) - Double(n - 1) / 2) * 34 + sin(p * .pi * 2 + Double(i)) * 8
                        Text(fx[i % fx.count]).font(.system(size: 22))
                            .offset(x: x, y: -40 - 90 * p)
                            .opacity(p <= 0 ? 0 : (p < 0.15 ? p / 0.15 : 1 - (p - 0.15) / 0.85))
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - Previews

extension HomeState {
    static func sample(_ mood: PetMood, overlays: Set<PetOverlay> = [], form: PetForm? = .chatterling,
                       branch: PetBranch = .standard) -> HomeState {
        let stats: PetStats
        switch mood {
        case .radiant:     stats = PetStats(e: 90, r: 85, f: 95, v: 90, a: 80)
        case .content:     stats = PetStats(e: 70, r: 65, f: 60, v: 50, a: 60)
        case .tired:       stats = PetStats(e: 40, r: 45, f: 50, v: 30, a: 40)
        case .sick:        stats = PetStats(e: 22, r: 30, f: 25, v: 20, a: 30)
        case .hibernation: stats = PetStats(e: 8, r: 12, f: 10, v: 5, a: 10)
        }
        var s = HomeState.empty
        s.snapshot = PetSnapshot(stats: stats, wellbeing: PetCore.wellbeing(stats), mood: mood, overlays: overlays)
        s.evolution = form.map { PetEvolution(form: $0, hybrid: .bingeon, branch: branch) }
        s.screenTimeEnabled = true
        s.stepsEnabled = true
        s.quests = [
            Quest(id: "focus", title: "Handy 45 Min weglegen", detail: "30 / 45 Min", progress: 0.66),
            Quest(id: "steps", title: "5.000 Schritte gehen", detail: "5200 / 5000", progress: 1),
            Quest(id: "screen", title: "Bildschirmzeit unter Ziel bleiben", detail: "150 / 180 Min",
                  progress: 0.83, isLimit: true),
        ]
        return s
    }
}

#Preview("Strahlend") { HomeContent(state: .sample(.radiant, branch: .radiant)) }
#Preview("Zufrieden") { HomeContent(state: .sample(.content)) }
#Preview("Müde + gähnend") { HomeContent(state: .sample(.tired, overlays: [.yawning])) }
#Preview("Gestresst + pummelig") { HomeContent(state: .sample(.tired, overlays: [.stressed, .chubby])) }
#Preview("Kränklich + träge") { HomeContent(state: .sample(.sick, overlays: [.sluggish], branch: .wild)) }
#Preview("Winterschlaf") { HomeContent(state: .sample(.hibernation, form: nil)) }
#Preview("Dunkel, ohne Erlaubnis") {
    var s = HomeState.sample(.content)
    s.screenTimeEnabled = false
    s.stepsEnabled = false
    s.snapshot.stats.r = nil
    s.snapshot.stats.v = nil
    return HomeContent(state: s).preferredColorScheme(.dark)
}
