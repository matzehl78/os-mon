// PetScene.swift: Hintergrund im Home-Screen, Auswahl mit Freischalten, Wochenrückblick (Gegenstück zu PetScene.kt)
//
// Target: nur App. Katalog, Speicher, Stimmungsfarbe und Zeichnung liegen in PetSceneCore.swift (App + Widget).
// Tagesdaten: PetEvoState.history (Bildschirm, Wohlbefinden) + Zähler pet.pats / pet.focusSessions + steps.<Datum>.
// Wochenrückblick gesehen: "pet.review.seen.<Mo>".

import SwiftUI

// MARK: - Tagesdaten -> PetProgress

struct ProgressResult {
    var review: WeeklyReview?            // nil = keine Daten oder schon weggeklickt
    var status: [SceneUnlock]
    var newlyUnlocked: [PetScene]        // seit dem letzten Aufruf neu freigeschaltet (Banner)
}

enum ProgressData {
    /// Abgeschlossene Tage (bis 28) plus heute (nur Zähler/Schritte, für zeitnahes Freischalten).
    static func days(today: Int = EpochDay.of()) -> [DayStat] {
        let d = STShared.defaults
        let stepsOn = d.bool(forKey: "feature.steps")
        func stat(_ day: Int, screen: Float?, avg: Float?) -> DayStat {
            let k = STShared.dayKey(EpochDay.date(day))
            let st = stepsOn && d.object(forKey: "steps.\(k)") != nil ? d.integer(forKey: "steps.\(k)") : nil
            return DayStat(day: day, avgWellbeing: avg, screenMin: screen, steps: st,
                           pats: d.integer(forKey: "pet.pats.\(k)"), focus: d.integer(forKey: "pet.focusSessions.\(k)"))
        }
        let hist = EvolutionStore.load()?.history ?? []
        return hist.filter { $0.epochDay < today }.map { stat($0.epochDay, screen: $0.totalMinutes, avg: $0.avgWellbeing) }
            + [stat(today, screen: nil, avg: nil)]
    }

    /// Einmal pro Home-Aktualisierung: Rückblick + Freischalt-Stand; neu Freigeschaltetes wird dauerhaft gespeichert.
    static func refresh(today: Int = EpochDay.of()) -> ProgressResult {
        let evo = EvolutionStore.load()
        let days = days(today: today)
        let already = SceneStore.unlocked
        let status = PetProgress.unlocks(days, age: evo.map { today - $0.hatchDay } ?? 0, evolved: evo?.current != nil,
                                         screenGoal: PetSync.config.screenGoalMin, already: already)
        let fresh = status.filter { $0.unlocked && !PetProgress.free.contains($0.sceneID) && !already.contains($0.sceneID) }.map(\.sceneID)
        SceneStore.addUnlocked(fresh)
        var review = PetProgress.weeklyReview(days, today: today)
        if let r = review, SceneStore.reviewSeen(r.weekStart) { review = nil }
        return ProgressResult(review: review, status: status, newlyUnlocked: fresh.map(PetScene.byID))
    }
}

// MARK: - Darstellung

/// Hintergrund von oben bis unter die Statusringe. Rahmen setzt der Aufrufer (volle Breite, Höhe der Ebene).
struct SceneBackground: View {
    let scene: PetScene
    let mood: PetMood
    let feetY: CGFloat            // Füße der Figur ab Oberkante dieser Ebene
    let fade: CGFloat             // weicher Auslauf unten

    var body: some View {
        TimelineView(.everyMinute) { tl in
            SceneArt(scene: scene, mood: mood, hour: tl.date.hourOfDay, feetY: { _ in feetY }, fade: fade)
        }
    }
}

/// Auswahl-Raster: gesperrte Szenen zeigen Schloss, Regel und Fortschritt.
struct ScenePicker: View {
    let status: [SceneUnlock]
    @AppStorage(SceneStore.key, store: STShared.defaults) private var sceneID = PetScene.defaultID

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(minimum: 0), spacing: 8), count: 3), spacing: 8) {
            ForEach(PetScene.all) { sc in
                let st = status.first { $0.sceneID == sc.id }
                let open = st?.unlocked ?? PetProgress.free.contains(sc.id)
                let selected = sc.id == PetScene.byID(sceneID).id
                Button {
                    SceneStore.select(sc.id, status: status)
                } label: {
                    VStack(spacing: 4) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8).fill(Color.secondary.opacity(0.15))
                            if let a = sc.asset {
                                Image(a).resizable().scaledToFill().saturation(open ? 1 : 0)
                            } else {
                                Text("ohne").font(.caption2).foregroundStyle(.secondary)
                            }
                            if !open {
                                Color.black.opacity(0.6)
                                VStack(spacing: 2) {
                                    Image(systemName: "lock.fill")
                                    Text(st?.rule ?? "").font(.caption2).multilineTextAlignment(.center)
                                }
                                .foregroundStyle(.white).padding(4)
                            }
                        }
                        .aspectRatio(2.0 / 3.0, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        Text(sc.name).font(.caption2).lineLimit(1).minimumScaleFactor(0.8)
                        if !open, let st { ProgressView(value: Double(st.progress)).tint(.accentColor) }
                    }
                    .padding(6)
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(selected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: selected ? 2 : 1))
                }
                .buttonStyle(.plain)
                .disabled(!open)
                .accessibilityLabel(open ? sc.name : "\(sc.name), gesperrt: \(st?.rule ?? ""), \(Int(((st?.progress ?? 0) * 100).rounded())) Prozent")
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

/// Wochenrückblick (ab Montag bis zum Wegklicken).
struct WeeklyReviewCard: View {
    let review: WeeklyReview
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Deine Woche", systemImage: "calendar").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Text(review.headline).font(.headline)
            ForEach(review.lines, id: \.self) { Text("• \($0)").font(.subheadline) }
            HStack { Spacer(); Button("Schön!", action: onDismiss).buttonStyle(.bordered) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.accentColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }
}

/// Hinweis auf neu freigeschaltete Szenen.
struct SceneUnlockBanner: View {
    let scenes: [PetScene]
    let onUse: (PetScene) -> Void
    let onDismiss: () -> Void

    var body: some View {
        if let s = scenes.first {
            HStack {
                Text("🎉 Neuer Hintergrund: \(s.name)" + (scenes.count > 1 ? " (+\(scenes.count - 1))" : "")).font(.callout)
                Spacer()
                Button("Nutzen") { onUse(s) }.buttonStyle(.borderedProminent)
                Button("Später", action: onDismiss).buttonStyle(.borderless)
            }
            .padding(12)
            .background(Color.green.opacity(0.15), in: RoundedRectangle(cornerRadius: 16))
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.2), radius: 10, y: 4)
        }
    }
}
