// OSMonWatchWidget.swift: Komplikationen fürs Zifferblatt der Apple Watch (WidgetKit).
//
// Rund: Ring = Wohlbefinden, Wesen in der Mitte. Rechteckig: Wesen, Stimmung, Aufgaben x/y.
// Ecke: Wesen mit Ring am Rand. Zeile: „😄 Strahlend · 2/5 ✓“. Daten: letztes Paket vom iPhone (WatchStore).

import SwiftUI
import WidgetKit

struct WatchEntry: TimelineEntry {
    let date: Date
    let payload: WatchPayload?
}

struct WatchProvider: TimelineProvider {
    private func load() -> WatchPayload? { WatchPayload.decode(STShared.defaults.data(forKey: WatchPayload.key)) }

    func placeholder(in context: Context) -> WatchEntry { WatchEntry(date: .now, payload: nil) }
    func getSnapshot(in context: Context, completion: @escaping (WatchEntry) -> Void) {
        completion(WatchEntry(date: .now, payload: load()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchEntry>) -> Void) {
        // Neu geladen wird, wenn das iPhone ein Paket schickt (WatchStore); sonst stündlich.
        completion(Timeline(entries: [WatchEntry(date: .now, payload: load())], policy: .after(.now.addingTimeInterval(3600))))
    }
}

struct WatchComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchEntry

    private var p: WatchPayload? { entry.payload }
    private var wellbeing: Double { Double(p?.snapshot.wellbeing ?? 0) }

    @ViewBuilder
    private var figure: some View {
        if p?.growth == .egg {
            PetEggView(figure: PetFigure.byID(p?.figureID), cracks: 2, animated: false)
        } else {
            PetFigureView(figure: PetFigure.byID(p?.figureID), pose: pose, animated: false)
        }
    }

    private var pose: PetPose {
        var pose = p.map { PetPose(snapshot: $0.snapshot) } ?? .preview
        pose.accessory = p?.accessory.flatMap(Accessory.init(rawValue:))
        pose.stage = p?.growth
        return pose
    }

    private var tasksText: String? {
        guard let p, p.progress.total > 0 else { return nil }
        return "\(p.progress.done)/\(p.progress.total) ✓"
    }

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                Circle().stroke(.white.opacity(0.25), lineWidth: 3.5)
                Circle().trim(from: 0, to: wellbeing / 100)
                    .stroke(.white, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .widgetAccentable()
                figure.padding(5)
            }
            .containerBackground(.clear, for: .widget)

        case .accessoryCorner:
            figure
                .widgetLabel {
                    Gauge(value: wellbeing, in: 0...100) { Text("OS-Mon") }
                }
                .containerBackground(.clear, for: .widget)

        case .accessoryInline:
            Text("\(p?.snapshot.mood.emoji ?? "🙂") \(p?.snapshot.mood.title ?? "OS-Mon")\(tasksText.map { " · \($0)" } ?? "")")
                .containerBackground(.clear, for: .widget)

        default: // accessoryRectangular
            HStack(spacing: 6) {
                figure.frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text(p?.snapshot.mood.title ?? "OS-Mon").font(.headline).widgetAccentable()
                    if let p {
                        Text("Wohlbefinden \(Int(p.snapshot.wellbeing.rounded()))").font(.caption2)
                        if let t = tasksText { Text("Aufgaben \(t)").font(.caption2) }
                    } else {
                        Text("iPhone-App öffnen").font(.caption2)
                    }
                }
                Spacer(minLength: 0)
            }
            .containerBackground(.clear, for: .widget)
        }
    }
}

@main
struct OSMonWatchWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "OSMonWatchWidget", provider: WatchProvider()) { entry in
            WatchComplicationView(entry: entry)
        }
        .configurationDisplayName("OS-Mon")
        .description("Dein Wesen, seine Stimmung und deine Aufgaben.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner, .accessoryInline])
    }
}
