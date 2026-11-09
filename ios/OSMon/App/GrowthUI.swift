// GrowthUI.swift: Wachsen vom Ei bis zum Erwachsenen – Nachtragen, Ei antippen, Banner. Gegenstück: GrowthUI.kt.
// Regeln: Shared/Logic/Growth.swift. Ei auf der Startseite antippen (5×, mit Rissen) → schlüpft als Baby.

import CoreMotion
import SwiftUI

struct GrowthInfo: Equatable {
    let state: GrowthState
    let stage: GrowthStage
    let eggTaps: Int
    let ageDays: Int
    let progress: Double?
    let daysLeft: Int?
    var roughToday = 0                 // grobe Aktionen heute (wildes Antippen, Schütteln)
    var pending: GrowthStage? { state.pendingStage }
    var pendingDown: Bool { state.pendingDown ?? false }
    var risk: GrowthRisk? { Growth.risk(state) }
}

enum GrowthStore {
    private static var today: Int { EpochDay.of(.now) }

    static func load() -> GrowthState {
        if let s = GrowthStorage.load() { return s }
        // Erster Aufruf: wer OS-Mon schon hatte, ist geschlüpft (nichts geht verloren); sonst liegt ein Ei da.
        let s = STShared.defaults.bool(forKey: "onboarding.done")
            ? Growth.migrated(hatchDay: EvolutionStore.load()?.hatchDay ?? today, today: today)
            : GrowthState(hatched: false, lastDay: today)
        GrowthStorage.save(s)
        return s
    }

    /// Ende des Onboardings: neues Wesen beginnt als Ei.
    static func startEgg() {
        guard GrowthStorage.load() == nil else { return }
        GrowthStorage.eggTaps = 0
        GrowthStorage.save(GrowthState(hatched: false, lastDay: today))
    }

    /// Gute Tage bis gestern nachtragen.
    static func refresh() -> GrowthInfo {
        var s = load()
        let t = today
        if s.hatched && s.lastDay < t - 1 {
            for d in max(s.lastDay + 1, t - Growth.maxCatchUp)..<t {
                let avg = DayLog.average(day: d), r = rough(d)
                // Für „Wieder aufgepäppelt“: es gab einen gemessenen Tag, an dem das Wachsen pausiert hat
                if avg != nil && Growth.delta(avg, rough: r) == 0 { STShared.defaults.set(true, forKey: "growth.dropped") }
                s = Growth.closeDay(s, day: d, avgWellbeing: avg, rough: r)
            }
            GrowthStorage.save(s)
        }
        return info(s)
    }

    static func info(_ s: GrowthState = load()) -> GrowthInfo {
        let p = Growth.progress(s)
        return GrowthInfo(state: s, stage: Growth.stage(s), eggTaps: GrowthStorage.eggTaps,
                          ageDays: s.hatchedDay.map { today - $0 } ?? 0, progress: p?.fraction, daysLeft: p?.daysLeft,
                          roughToday: rough(today))
    }

    /// Grobe Aktionen eines Tages (wildes Antippen, Schütteln).
    static func rough(_ day: Int) -> Int { STShared.defaults.integer(forKey: "growth.rough.\(day)") }

    /// Grobe Aktion zählen (nur nach dem Schlüpfen).
    static func addRough() -> GrowthInfo {
        let s = load()
        if s.hatched { STShared.defaults.set(rough(today) + 1, forKey: "growth.rough.\(today)") }
        return info(s)
    }

    static func tapEgg() -> GrowthInfo {
        var s = load()
        guard !s.hatched else { return info(s) }
        GrowthStorage.eggTaps += 1
        if GrowthStorage.eggTaps >= Growth.eggTaps { s = Growth.hatch(s, today: today); GrowthStorage.save(s) }
        return info(s)
    }

    static func ackStage() {
        var s = load()
        // Sammelalbum „Wieder aufgepäppelt“: nach einer Wachstumspause wieder eine Stufe gewachsen
        // (Schlüssel heißt aus Kompatibilität noch growth.dropped; einen Rückfall gibt es nicht mehr)
        if s.pendingStage != nil {
            let d = STShared.defaults
            if d.bool(forKey: "growth.dropped") { d.set(false, forKey: "growth.dropped"); AchievementStore.addComeback() }
        }
        s.pendingStage = nil; s.pendingDown = nil; GrowthStorage.save(s)
    }
}

/// „ein Baby“, „ein Kind“, „jugendlich“, „erwachsen“
func growthArticle(_ s: GrowthStage) -> String {
    switch s { case .baby: "ein Baby"; case .child: "ein Kind"; case .teen: "jugendlich"; case .adult: "erwachsen"; case .egg: "ein Ei" }
}

/// „Lumo · Kind · Tag 9“
func growthLabel(_ name: String, _ g: GrowthInfo) -> String {
    g.stage == .egg ? "\(name) · Ei" : "\(name) · \(g.stage.title) · Tag \(g.ageDays + 1)"
}

/// Fortschritt zur nächsten Stufe (in der Kopfzeile). Warnungen stehen darunter in GrowthAlertsView.
struct GrowthProgressView: View {
    let g: GrowthInfo
    var body: some View {
        if let p = g.progress, let left = g.daysLeft, let next = g.stage.next {
            VStack(alignment: .leading, spacing: 2) {
                ProgressView(value: p).tint(.green)
                Text("Noch \(left) gute \(left == 1 ? "Tag" : "Tage") bis \(next.title)")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            .padding(.top, 2)
            .accessibilityElement(children: .combine)
        }
    }
}

/// Warnungen als eigene Zeile unter der Kopfzeile: grob behandelt, Rückfall droht.
struct GrowthAlertsView: View {
    let g: GrowthInfo

    static func has(_ g: GrowthInfo?) -> Bool {
        guard let g, g.stage != .egg else { return false }
        return g.roughToday > 0 || g.risk != nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if g.roughToday > 0 {
                row("hand.raised.fill", .pink, g.roughToday >= Growth.roughLimit
                    ? "Heute zu grob behandelt – dein Wesen macht heute eine Wachstumspause."
                    : "Heute \(g.roughToday)× grob behandelt. Ab \(Growth.roughLimit)× macht dein Wesen heute eine Wachstumspause.")
            }
            if let r = g.risk {
                row("exclamationmark.triangle.fill", .orange,
                    "Noch \(r.badDaysLeft) schlechte \(r.badDaysLeft == 1 ? "Tag" : "Tage"), dann wird dein Wesen wieder \(growthArticle(r.to)).")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func row(_ symbol: String, _ color: Color, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Image(systemName: symbol).foregroundStyle(color)
            Text(text).font(.footnote).fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct GrowthBanner: View {
    let name: String
    let stage: GrowthStage
    var down = false
    let onAck: () -> Void

    private var text: String {
        if down { return "\(name) ist wieder \(growthArticle(stage)) geworden. Mit guter Pflege wächst es wieder." }
        return switch stage {
        case .baby: "\(name) ist geschlüpft! Willkommen, kleines Baby."
        case .child: "\(name) ist jetzt ein Kind! Größer, mutiger, neugieriger."
        case .teen: "\(name) ist jetzt jugendlich! Fast schon groß."
        case .adult: "\(name) ist erwachsen! Danke, dass du so gut aufgepasst hast."
        case .egg: ""
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Text(down ? "🥺" : "🎉").font(.title)
            Text(text).font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, alignment: .leading)
            Button(down ? "Ich pass auf" : "Juhu!", action: onAck).buttonStyle(.borderedProminent)
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
    }
}

/// Sprechblase, solange das Ei noch nicht geschlüpft ist.
func eggLine(_ g: GrowthInfo?) -> String? {
    guard let g, g.stage == .egg else { return nil }
    return g.eggTaps == 0 ? "Da liegt ein Ei! Tippe es an …" : "Es knackt! Noch \(Growth.eggTaps - g.eggTaps)× tippen …"
}

/// Erkennt kräftiges Schütteln (ab ca. 2,6 g, höchstens einmal pro 1,5 s), solange der Startbildschirm sichtbar ist.
final class ShakeMonitor: ObservableObject {
    private let motion = CMMotionManager()
    private var last = Date.distantPast
    var onShake: () -> Void = {}

    func start() {
        guard motion.isAccelerometerAvailable, !motion.isAccelerometerActive else { return }
        motion.accelerometerUpdateInterval = 1.0 / 30
        motion.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let self, let a = data?.acceleration else { return }
            let g = (a.x * a.x + a.y * a.y + a.z * a.z).squareRoot()
            if g > 2.6, Date().timeIntervalSince(self.last) > 1.5 { self.last = Date(); self.onShake() }
        }
    }

    func stop() { motion.stopAccelerometerUpdates() }
}
