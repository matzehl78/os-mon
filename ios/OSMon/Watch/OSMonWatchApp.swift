// OSMonWatchApp.swift: OS-Mon auf der Apple Watch.
//
// Vier Seiten (senkrecht blättern mit der Krone): Wesen (antippen = Reaktion), Werte, Aufgaben (abhaken),
// Miteinander (Tipp des Tages). Alle Daten kommen vom iPhone (WatchStore). Ohne iPhone-Daten: Hinweis.

import SwiftUI
import WatchKit

@main
struct OSMonWatchApp: App {
    init() { WatchStore.shared.start() }

    var body: some Scene {
        WindowGroup { WatchRoot() }
    }
}

struct WatchRoot: View {
    private let store = WatchStore.shared
    @Environment(\.scenePhase) private var phase
    @State private var page = 0

    var body: some View {
        Group {
            if let p = store.payload {
                TabView(selection: $page) {
                    WatchPetPage(p: p).tag(0)
                    WatchStatsPage(p: p).tag(1)
                    WatchTasksPage(p: p, store: store).tag(2)
                    if p.kindTip != nil { WatchKindPage(p: p, store: store).tag(3) }
                }
                .tabViewStyle(.verticalPage)
                .onAppear {
                    #if DEBUG
                    page = UserDefaults.standard.integer(forKey: "OSMonWatchPage")
                    #endif
                }
            } else {
                VStack(spacing: 8) {
                    PetFigureView(figure: PetFigure.byID(PetFigure.defaultID), pose: .preview)
                        .frame(width: 90, height: 90)
                    Text("Öffne OS-Mon auf dem iPhone, damit dein Wesen hierher kommt.")
                        .font(.footnote).multilineTextAlignment(.center)
                }
                .padding()
            }
        }
        .onChange(of: phase) { _, p in if p == .active { store.requestUpdate() } }
    }
}

// MARK: - Wesen

struct WatchPetPage: View {
    let p: WatchPayload
    @State private var reaction: ActiveReaction? = nil
    private let picker = ReactionPicker()

    private var pose: PetPose {
        var pose = PetPose(snapshot: p.snapshot)
        pose.form = p.form.flatMap(PetForm.init(rawValue:))
        pose.accessory = p.accessory.flatMap(Accessory.init(rawValue:))
        pose.glasses = p.glasses.flatMap(Glasses.init(rawValue:))
        pose.stage = p.growth
        return pose
    }

    var body: some View {
        VStack(spacing: 2) {
            ZStack(alignment: .top) {
                if p.growth == .egg {
                    PetEggView(figure: PetFigure.byID(p.figureID), cracks: 2)
                        .accessibilityLabel("Ein Ei. Auf dem iPhone antippen, damit es schlüpft.")
                } else {
                PetFigureView(figure: PetFigure.byID(p.figureID), pose: pose, animated: true, reaction: reaction)
                    .onTapGesture {
                        let k = picker.pick(mood: p.snapshot.mood, overlays: p.snapshot.overlays)
                        reaction = picker.start(k)
                        WKInterfaceDevice.current().play(.click)
                    }
                    .accessibilityLabel("\(p.petName): \(p.snapshot.mood.title). Antippen für eine Reaktion.")
                    .accessibilityAddTraits(.isButton)
                }
                if let r = reaction, r.saying(.now) {
                    Text(r.say).font(.caption2).padding(.horizontal, 6).padding(.vertical, 3)
                        .background(.ultraThinMaterial, in: Capsule())
                        .transition(.opacity)
                }
            }
            HStack(spacing: 4) {
                Text(p.growth == .egg ? "Ei" : p.snapshot.mood.title).font(.headline)
                Text("\(Int(p.snapshot.wellbeing.rounded()))").font(.headline).monospacedDigit().foregroundStyle(p.snapshot.mood.color)
            }
            Text(p.petName + (p.growth.flatMap { $0 == .egg ? nil : " · \($0.title)" } ?? ""))
                .font(.caption2).foregroundStyle(.secondary)
        }
        .containerBackground(p.snapshot.mood.color.gradient, for: .tabView)
        .task(id: reaction?.start) {
            try? await Task.sleep(for: .seconds(2.7))
            reaction = nil
        }
    }
}

// MARK: - Werte

struct WatchStatsPage: View {
    let p: WatchPayload

    var body: some View {
        VStack(spacing: 6) {
            Text("Werte").font(.headline)
            Grid(horizontalSpacing: 10, verticalSpacing: 8) {
                GridRow {
                    stat("Energie", "bolt.fill", p.snapshot.stats.e, .yellow)
                    stat("Ruhe", "moon.fill", p.snapshot.stats.r, .indigo)
                }
                GridRow {
                    stat("Fitness", "internaldrive.fill", p.snapshot.stats.f, .orange)
                    stat("Vitalität", "figure.walk", p.snapshot.stats.v, .green)
                }
            }
            Text("Stand \(p.updatedAt.formatted(date: .omitted, time: .shortened))")
                .font(.caption2).foregroundStyle(.secondary)
        }
        .containerBackground(Color.black.gradient, for: .tabView)
    }

    private func stat(_ title: String, _ symbol: String, _ v: Float?, _ c: Color) -> some View {
        VStack(spacing: 2) {
            Gauge(value: Double(v ?? 0), in: 0...100) {
                Image(systemName: symbol)
            } currentValueLabel: {
                Text(v.map { "\(Int($0.rounded()))" } ?? "–")
            }
            .gaugeStyle(.accessoryCircular)
            .tint(c)
            Text(title).font(.caption2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title) \(v.map { "\(Int($0.rounded()))" } ?? "keine Daten")")
    }
}

// MARK: - Aufgaben

struct WatchTasksPage: View {
    let p: WatchPayload
    let store: WatchStore

    var body: some View {
        let prog = p.progress
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Aufgaben").font(.headline)
                    Spacer()
                    if prog.total > 0 { Text("\(prog.done)/\(prog.total)").font(.headline).monospacedDigit() }
                }
                if prog.total > 0 { ProgressView(value: prog.fraction).tint(prog.stage == .all ? .green : .teal) }
                Text(store.cheer ?? p.motivation).font(.caption2).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                if p.tasks.isEmpty {
                    Text("Lege Aufgaben in der iPhone-App an.").font(.footnote).foregroundStyle(.secondary).padding(.top, 4)
                }
                ForEach(p.tasks) { t in
                    let done = DayTasks.isDone(t, p.day)
                    Button {
                        store.toggle(t)
                        WKInterfaceDevice.current().play(done ? .click : .success)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: done ? "checkmark.circle.fill" : "circle").foregroundStyle(done ? .green : .secondary)
                            Text(t.emoji)
                            Text(t.title).font(.footnote).strikethrough(done).lineLimit(2)
                            Spacer(minLength: 0)
                        }
                    }
                    .accessibilityValue(done ? "erledigt" : "offen")
                }
            }
        }
        .containerBackground(Color.teal.opacity(0.35).gradient, for: .tabView)
    }
}

// MARK: - Miteinander

struct WatchKindPage: View {
    let p: WatchPayload
    let store: WatchStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                Label("Miteinander", systemImage: "heart.fill").font(.headline).foregroundStyle(.pink)
                if let c = p.kindCategory { Text(c).font(.caption2).foregroundStyle(.secondary) }
                Text(p.kindTip ?? "").font(.footnote).fixedSize(horizontal: false, vertical: true)
                if p.kindDone {
                    Label("Heute ausprobiert", systemImage: "checkmark.circle.fill").font(.footnote).foregroundStyle(.green)
                } else {
                    Button("Hab ich gemacht") {
                        store.kindDone()
                        WKInterfaceDevice.current().play(.success)
                    }
                    .tint(.pink)
                }
            }
        }
        .containerBackground(Color.pink.opacity(0.3).gradient, for: .tabView)
    }
}

// MARK: - Vorführmodus (Screenshots im Simulator)

#if DEBUG
enum WatchDemo {
    static func payload() -> WatchPayload? {
        guard UserDefaults.standard.bool(forKey: "OSMonWatchDemo") else { return nil }
        let d = EpochDay.of(.now)
        let snap = PetSnapshot(stats: PetStats(e: 90, r: 85, f: 95, v: 70), wellbeing: 86, mood: .radiant, overlays: [])
        let tasks = [
            DayTask(id: "1", title: "Zähne putzen", emoji: "🪥", repeatRule: .daily, createdDay: d - 3, doneDays: [d]),
            DayTask(id: "2", title: "Hausaufgaben", emoji: "📚", repeatRule: .weekdays, createdDay: d - 3, doneDays: [d]),
            DayTask(id: "3", title: "Zimmer aufräumen", emoji: "🧹", repeatRule: .once, createdDay: d),
            DayTask(id: "4", title: "Oma anrufen", emoji: "📞", repeatRule: .once, createdDay: d)
        ]
        let fig = UserDefaults.standard.string(forKey: "OSMonFigure") ?? "lumo"
        return WatchPayload(snapshot: snap, updatedAt: .now, figureID: fig, petName: PetFigure.byID(fig).name,
                            accessory: nil, form: "chatterling", day: d, tasks: tasks,
                            motivation: "Halbzeit! Den Rest schaffst du locker.",
                            kindTip: "Halte die Tür für die Person hinter dir auf.", kindCategory: "🚌 Unterwegs", kindDone: false)
    }
}
#endif
