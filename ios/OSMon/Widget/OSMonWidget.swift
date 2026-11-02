// OSMonWidget.swift: Widget-Extension (iOS 17+, interaktiv über App Intents)
//
// Target: Widget-Extension. Dateien im Target: OSMonWidget.swift, PetSync.swift, PetCore.swift,
// ScreenTimeShared.swift, PetFigure.swift, PetFigureData.swift (gewählte Figur statt Emoji),
// PetReaction.swift, PetSceneCore.swift + Scenes.xcassets (gewählter Hintergrund wie im Home-Screen). Capabilities: App Groups (dieselbe Gruppe wie die App, siehe STShared.appGroup).
//
// Rechnet über denselben Kern wie die App (PetSync.tick -> PetCore.tick) und liest/schreibt denselben
// Snapshot. getTimeline macht einen echten Tick (Akku, Speicher, Bildschirmzeit-Zähler, Schritte) und
// rechnet die nächsten 4 Stunden in 15-Minuten-Schritten voraus, mit gelernter Entladerate und mit
// Trägheit und Hysterese aus PetCore. Neu geladen wird es von der App (PetSync.reloadWidgetIfNeeded),
// von der DeviceActivity-Extension bei Bildschirmzeit-Schwellen und nach dem Streicheln.

import WidgetKit
import SwiftUI
import AppIntents

// MARK: - Timeline

struct PetEntry: TimelineEntry {
    let date: Date
    let snapshot: PetSnapshot
    let battery: Float?
    let charging: Bool
    let canPat: Bool
    let patReadyAt: Date?
    let updatedAt: Date
    var sceneID: String = PetScene.defaultID
    var scene: PetScene { PetScene.byID(sceneID) }
}

extension PetEntry {
    static let placeholder = PetEntry(
        date: .now,
        snapshot: PetSnapshot(stats: PetStats(e: 72, r: 65, f: 80, v: 55, a: 60),
                              wellbeing: 70, mood: .content, overlays: []),
        battery: 72, charging: false, canPat: true, patReadyAt: nil, updatedAt: .now)
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> PetEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (PetEntry) -> Void) {
        // Galerie-Vorschau: nichts rechnen oder speichern, nur zeigen, was da ist.
        if context.isPreview {
            completion(.placeholder)
            return
        }
        let t = PetSync.tick()
        completion(entry(PetProjection(date: t.at, snapshot: t.snapshot, battery: t.inputs.batteryPct,
                                       charging: t.inputs.charging), updatedAt: t.at))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PetEntry>) -> Void) {
        let t = PetSync.tick()
        PetSync.markWidgetRendered(t.snapshot)

        let entries = PetSync.project(from: t).map { entry($0, updatedAt: t.at) }

        // Frischer Messwert in ca. 1 h; die Vorausberechnung überbrückt, falls iOS später lädt.
        completion(Timeline(entries: entries, policy: .after(t.at.addingTimeInterval(3600))))
    }

    private func entry(_ p: PetProjection, updatedAt: Date) -> PetEntry {
        let ready = PetSync.patReadyAt(now: p.date)
        return PetEntry(date: p.date, snapshot: p.snapshot, battery: p.battery, charging: p.charging,
                        canPat: ready == nil, patReadyAt: ready, updatedAt: updatedAt,
                        sceneID: STShared.defaults.string(forKey: SceneStore.key) ?? PetScene.defaultID)
    }
}

// MARK: - Interaktion

/// Streicheln direkt im Widget: zählt zur Zuneigung (A), höchstens 1× pro 3 Stunden.
/// Nach perform() lädt iOS die Timeline automatisch neu.
struct PatIntent: AppIntent {
    static let title: LocalizedStringResource = "Streicheln"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        if PetSync.pat() { PetSync.tick() }
        return .result()
    }
}

// MARK: - Views

struct PetWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PetEntry

    private var snap: PetSnapshot { entry.snapshot }
    private var h: Int { Int(snap.wellbeing.rounded()) }

    var body: some View {
        if entry.scene.asset != nil && (family == .systemSmall || family == .systemMedium) {
            sceneLayout
        } else {
            plainLayout
        }
    }

    // MARK: Mit Hintergrund-Szene (wie im Home-Screen: Plattform unter den Füßen, Stimmung färbt das Bild)

    @ViewBuilder
    private var sceneLayout: some View {
        if family == .systemMedium {
            HStack(spacing: 0) {
                // Bühne links: quadratisch, Figur steht auf der Plattform
                GeometryReader { g in
                    let f = min(g.size.width * 0.86, g.size.height * 0.78)
                    let feet = g.size.height * 0.86
                    ZStack(alignment: .topLeading) {
                        SceneArt(scene: entry.scene, mood: snap.mood, hour: entry.date.hourOfDay,
                                 feetY: { _ in feet }, topShade: 0)
                        figure.frame(width: f, height: f)
                            .position(x: g.size.width / 2, y: feet - 0.92 * f + f / 2)
                    }
                }
                .aspectRatio(1, contentMode: .fit)

                // Werte rechts auf dunklem Glas
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text(snap.mood.title).font(.caption.bold())
                        Spacer(minLength: 0)
                        Text("\(h)").font(.caption.bold()).monospacedDigit()
                    }
                    Text(PetCopy.line(for: snap)).font(.caption2).lineLimit(2).opacity(0.9)
                    StatBar(symbol: "bolt.fill", value: snap.stats.e)
                    StatBar(symbol: "moon.fill", value: snap.stats.r)
                    StatBar(symbol: "internaldrive.fill", value: snap.stats.f)
                    StatBar(symbol: "figure.walk", value: snap.stats.v)
                    patRow
                }
                .padding(12)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(red: 14 / 255, green: 18 / 255, blue: 48 / 255).opacity(0.92))
            }
            .foregroundStyle(.white)
        } else {
            // klein: Szene füllt das Widget, Text unten auf dunklem Verlauf
            GeometryReader { g in
                let f = min(g.size.width * 0.62, g.size.height * 0.6)
                let feet = g.size.height * 0.66
                ZStack(alignment: .topLeading) {
                    SceneArt(scene: entry.scene, mood: snap.mood, hour: entry.date.hourOfDay,
                             feetY: { _ in feet }, topShade: 0, bottomShade: 0.42)
                    figure.frame(width: f, height: f)
                        .position(x: g.size.width / 2, y: feet - 0.92 * f + f / 2)
                    VStack(spacing: 3) {
                        Spacer()
                        HStack {
                            Text(snap.mood.title).font(.caption.bold())
                            Spacer(minLength: 0)
                            Text("\(h)").font(.caption.bold()).monospacedDigit()
                        }
                        ProgressView(value: Double(snap.wellbeing), total: 100).tint(.white)
                    }
                    .padding(.horizontal, 12).padding(.bottom, 10)
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .foregroundStyle(.white)
        }
    }

    @ViewBuilder
    private var patRow: some View {
        HStack {
            if entry.canPat {
                Button(intent: PatIntent()) {
                    Label("Streicheln", systemImage: "hand.tap.fill").font(.caption2)
                }
                .buttonStyle(.bordered)
                .tint(.white)
            } else if let ready = entry.patReadyAt {
                Text("Streicheln ab \(ready.formatted(date: .omitted, time: .shortened))")
                    .font(.caption2).opacity(0.8)
            }
            Spacer(minLength: 0)
            Text(entry.updatedAt, style: .relative).font(.caption2).opacity(0.7)
        }
    }

    // MARK: Ohne Szene (bisherige Darstellung auf Stimmungsfarbe)

    @ViewBuilder
    private var plainLayout: some View {
        switch family {
        case .accessoryCircular:
            Gauge(value: Double(snap.wellbeing), in: 0...100) {
                Text("OS-Mon")
            } currentValueLabel: {
                Text(snap.mood.emoji)
            }
            .gaugeStyle(.accessoryCircular)
            .widgetAccentable()

        case .accessoryRectangular:
            HStack(spacing: 6) {
                Text(snap.mood.emoji).font(.title2)
                VStack(alignment: .leading, spacing: 1) {
                    Text(snap.mood.title).font(.headline).widgetAccentable()
                    Text(compactStats).font(.caption).monospacedDigit()
                }
            }

        case .systemMedium:
            HStack(spacing: 14) {
                VStack(spacing: 2) {
                    figure.frame(width: 84, height: 84)
                    Text(snap.mood.title).font(.caption.bold())
                    Text("\(h)").font(.title3.bold()).monospacedDigit()
                }
                .frame(width: 92)

                VStack(alignment: .leading, spacing: 5) {
                    Text(PetCopy.line(for: snap)).font(.caption).lineLimit(2)
                    StatBar(symbol: "bolt.fill", value: snap.stats.e)
                    StatBar(symbol: "moon.fill", value: snap.stats.r)
                    StatBar(symbol: "internaldrive.fill", value: snap.stats.f)
                    StatBar(symbol: "figure.walk", value: snap.stats.v)
                    patRow
                }
            }
            .padding()
            .foregroundStyle(.white)

        default: // systemSmall
            VStack(spacing: 2) {
                figure.frame(width: 86, height: 86)
                Text(snap.mood.title).font(.caption.bold())
                ProgressView(value: Double(snap.wellbeing), total: 100).tint(.white)
                Text("Wohlbefinden \(h)").font(.caption2).monospacedDigit()
            }
            .padding()
            .foregroundStyle(.white)
        }
    }

    /// Die gewählte Figur als Standbild (Widgets animieren nicht). Sperrbildschirm bleibt beim Emoji,
    /// weil er einfarbig dargestellt wird.
    private var figure: some View {
        PetFigureView(figure: PetFigureStore.current,
                      pose: PetPose(snapshot: snap, charging: entry.charging), animated: false)
            .accessibilityLabel("Dein Wesen: \(snap.mood.title)")
    }

    /// PRD 4.3, Lockscreen rechteckig: "Ruhe 72 · Fit 55" (fehlende Werte werden ausgelassen).
    private var compactStats: String {
        var parts: [String] = []
        if let r = snap.stats.r { parts.append("Ruhe \(Int(r.rounded()))") }
        if let f = snap.stats.f { parts.append("Fit \(Int(f.rounded()))") }
        if parts.isEmpty, let e = snap.stats.e { parts.append("Energie \(Int(e.rounded()))") }
        return parts.joined(separator: " · ")
    }
}

private struct StatBar: View {
    let symbol: String
    let value: Float?

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.caption2).frame(width: 14)
            if let value {
                ProgressView(value: Double(max(0, min(100, value))), total: 100).tint(.white)
            } else {
                Text("–").font(.caption2).opacity(0.7)
                Spacer(minLength: 0)
            }
        }
    }
}

struct OSMonWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: STShared.widgetKind, provider: Provider()) { entry in
            PetWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    // mit Szene zeichnet der Inhalt das Bild selbst (randlos); dunkler Grund für die Ränder
                    if entry.scene.asset != nil { Color(red: 14 / 255, green: 18 / 255, blue: 48 / 255) }
                    else { entry.snapshot.mood.color.gradient }
                }
        }
        .contentMarginsDisabled()           // Szene randlos; ohne Szene setzen die Layouts ihren Rand selbst
        .configurationDisplayName("OS-Mon")
        .description("Dein Handy-Wesen: Akku, Speicher, Bildschirmzeit und Schritte auf einen Blick.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

@main
struct OSMonWidgetBundle: WidgetBundle {
    var body: some Widget { OSMonWidget() }
}

#Preview(as: .systemMedium) {
    OSMonWidget()
} timeline: {
    PetEntry.placeholder
}
