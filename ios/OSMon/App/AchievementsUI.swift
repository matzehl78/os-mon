// AchievementsUI.swift: Sammelalbum – Kennzahlen sammeln, freischalten, Album und Banner. Gegenstück: AchievementsUI.kt.
// Regeln und Liste: Shared/Logic/Achievements.swift. Alles nur auf dem Gerät.

import SwiftUI

struct AlbumInfo: Equatable {
    let metrics: [Metric: Int]
    let unlocked: Set<String>
    let pending: [Achievement]
    var count: Int { unlocked.count }
}

enum AchievementStore {
    private static var d: UserDefaults { STShared.defaults }

    static func addTask() { inc("ach.tasks") }
    static func addComeback() { inc("ach.comeback") }
    /// Gute Nacht gesagt (einmal pro Nacht zählen).
    static func addGoodnight(night: Int) {
        guard d.object(forKey: "ach.night.last") as? Int != night else { return }
        d.set(night, forKey: "ach.night.last"); inc("ach.goodnights")
    }
    /// Fest gefeiert (verschiedene Feste zählen).
    static func addFestival(_ id: String) {
        var s = Set(d.stringArray(forKey: "ach.festivals") ?? [])
        if s.insert(id).inserted { d.set(Array(s).sorted(), forKey: "ach.festivals") }
    }
    private static func inc(_ k: String) { d.set(d.integer(forKey: k) + 1, forKey: k) }

    private static func loadMetrics() -> [Metric: Int] {
        var out: [Metric: Int] = [:]
        for (k, v) in (d.dictionary(forKey: "ach.metrics") as? [String: Int]) ?? [:] { if let m = Metric(rawValue: k) { out[m] = v } }
        return out
    }
    static var unlocked: Set<String> { Set(d.stringArray(forKey: "ach.unlocked") ?? []) }
    private static var pendingIDs: [String] { d.stringArray(forKey: "ach.pending") ?? [] }

    /// Kennzahlen einsammeln (Höchstwerte bleiben), neu Erreichtes freischalten und zum Zeigen vormerken.
    static func refresh(growth: GrowthInfo?, scenesUnlocked: Int) -> AlbumInfo {
        let day = EpochDay.of(.now)
        let now: [Metric: Int] = [
            .stage: growth.map { GrowthStage.allCases.firstIndex(of: $0.stage) ?? 0 } ?? 0,
            .tasks: d.integer(forKey: "ach.tasks"),
            .streak: DayTasks.streak(TaskStore.load(), today: day),
            .kind: TaskStore.kindCount,
            .goodnight: d.integer(forKey: "ach.goodnights"),
            .festivals: (d.stringArray(forKey: "ach.festivals") ?? []).count,
            .scenes: scenesUnlocked,
            .comeback: d.integer(forKey: "ach.comeback"),
            .days: growth?.ageDays ?? 0,
            .games: d.integer(forKey: "ach.games"),
            .goodDays: growth?.state.goodDays ?? 0
        ]
        let m = Achievements.merge(loadMetrics(), now)
        WardrobeStore.noteGoodDays(m[.goodDays] ?? 0)                  // Kleiderschrank: Höchstwert der guten Tage
        let un = unlocked
        let fresh = Achievements.newlyUnlocked(m, unlocked: un)
        var pend = pendingIDs
        for a in fresh where !pend.contains(a.id) { pend.append(a.id) }
        d.set(Dictionary(uniqueKeysWithValues: m.map { ($0.key.rawValue, $0.value) }), forKey: "ach.metrics")
        d.set(Array(un.union(fresh.map(\.id))).sorted(), forKey: "ach.unlocked")
        d.set(pend, forKey: "ach.pending")
        return AlbumInfo(metrics: m, unlocked: un.union(fresh.map(\.id)), pending: pend.compactMap(Achievements.byID))
    }

    static func ackFirst() { d.set(Array(pendingIDs.dropFirst()), forKey: "ach.pending") }

    static func info() -> AlbumInfo {
        AlbumInfo(metrics: loadMetrics(), unlocked: unlocked, pending: pendingIDs.compactMap(Achievements.byID))
    }
}

// MARK: - Bausteine

struct AchievementBanner: View {
    let achievement: Achievement
    let onOpen: () -> Void
    let onAck: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Text(achievement.emoji).font(.system(size: 30))
            VStack(alignment: .leading, spacing: 1) {
                Text("Neuer Erfolg!").font(.caption.weight(.semibold)).foregroundStyle(.orange)
                Text(achievement.title).font(.headline)
            }
            Spacer(minLength: 0)
            Button("Album", action: onOpen).buttonStyle(.borderless)
            Button("Super!", action: onAck).buttonStyle(.borderedProminent)
        }
        .padding(14)
        .background(Color(red: 1, green: 0.95, blue: 0.77), in: RoundedRectangle(cornerRadius: 16))
        .environment(\.colorScheme, .light)
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
    }
}

struct AlbumButton: View {
    let album: AlbumInfo
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Label("Sammelalbum \(album.count)/\(Achievements.all.count)", systemImage: "trophy.fill").font(.subheadline.weight(.semibold))
        }
        .buttonStyle(.bordered)
        .tint(.orange)
        .accessibilityLabel("Sammelalbum, \(album.count) von \(Achievements.all.count) Erfolgen")
    }
}

struct AlbumView: View {
    let album: AlbumInfo
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("\(album.count) von \(Achievements.all.count) Erfolgen gesammelt").font(.subheadline).foregroundStyle(.secondary)
                    ProgressView(value: Double(album.count), total: Double(Achievements.all.count)).tint(.orange)
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(Achievements.all) { a in badge(a) }
                    }
                }
                .padding()
            }
            .navigationTitle("Sammelalbum")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        }
    }

    private func badge(_ a: Achievement) -> some View {
        let done = album.unlocked.contains(a.id)
        let p = Achievements.progress(a, album.metrics)
        return VStack(spacing: 4) {
            Text(a.emoji).font(.system(size: 32)).opacity(done ? 1 : 0.35)
            Text(a.title).font(.caption.weight(.semibold)).multilineTextAlignment(.center).lineLimit(2)
            Text(a.hint).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center).lineLimit(3)
                .frame(height: 40, alignment: .top)
            if !done { ProgressView(value: p).tint(.orange) }
        }
        .padding(10)
        .frame(maxWidth: .infinity)
        .background(done ? Color(red: 1, green: 0.95, blue: 0.77) : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(a.title): \(done ? "erreicht" : "noch offen, \(Int(p * 100)) Prozent"). \(a.hint)")
    }
}
