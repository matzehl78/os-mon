// DemoMode.swift: feste Beispiel-Zustände für Screenshots (nur DEBUG).
//
// Start mit Launch-Argumenten, z. B. im Simulator:
//   xcrun simctl launch booted com.example.osmon -OSMonDemo home -OSMonScene zauberwald -OSMonFigure lila -OSMonMood content
// Ohne -OSMonDemo verhält sich die App normal. Der CI-Workflow „Screenshots“ nutzt das.

import SwiftUI

#if DEBUG
enum DemoMode {
    private static var args: UserDefaults { .standard }        // Launch-Argumente "-Key Wert" landen hier
    static var screen: String? { args.string(forKey: "OSMonDemo") }

    /// Szene und Figur setzen, bevor die Oberfläche sie liest.
    static func prepare() {
        guard screen != nil else { return }
        let d = STShared.defaults
        d.set(args.string(forKey: "OSMonScene") ?? "neonstadt", forKey: SceneStore.key)
        d.set(args.string(forKey: "OSMonFigure") ?? "lumo", forKey: PetFigureStore.key)
        d.set(["raumstation", "zauberwald", "unterwasser", "suess", "mondbasis", "zen", "studio"], forKey: SceneStore.unlockedKey)
        if flag("OSMonTasks") || screen == "tasks" || screen == "kindness" { TaskDemo.seed() } else { TaskStore.save([]) }
        if screen == "parent" {                                      // Eltern-Bereich: PIN gesetzt und entsperrt, eine Eltern-Aufgabe
            ParentStore.setPin("0429")
            ParentStore.addTask("Zähne putzen", rule: .daily)
        } else { ParentStore.removePin() }
        d.set(flag("OSMon3D"), forKey: Pet3D.key)                     // 3D nur mit -OSMon3D YES (alte Bilder bleiben 2D)
        d.set(false, forKey: Pet3D.failedKey)
        // Kleiderschrank: -OSMonOutfit "cap,sun" (Teile anziehen, alles freigeschaltet), -OSMonPremium YES
        d.set(flag("OSMonPremium"), forKey: "premium.demo")
        d.set(args.string(forKey: "OSMonOutfit") ?? "", forKey: "wr.equipped")
        d.set(args.object(forKey: "OSMonGoodDays") != nil ? args.integer(forKey: "OSMonGoodDays") : 99, forKey: "wr.maxgood")
        d.set(Wardrobe.all.map(\.id), forKey: "wr.known")
        d.set(nil, forKey: "games.day"); d.set(0, forKey: "games.played")
        d.set(false, forKey: SoundPlayer.key)                        // Screenshots ohne Ton
    }

    static var mood: PetMood { PetMood(rawValue: args.string(forKey: "OSMonMood") ?? "") ?? .radiant }
    static func flag(_ key: String) -> Bool { args.bool(forKey: key) }

    static func album(pending: Bool) -> AlbumInfo {
        let m: [Metric: Int] = [.stage: 2, .tasks: 23, .streak: 4, .kind: 6, .goodnight: 9, .festivals: 2, .scenes: 4, .days: 31]
        let un = Set(Achievements.all.filter { Achievements.isReached($0, m) }.map(\.id))
        return AlbumInfo(metrics: m, unlocked: un, pending: pending ? [Achievements.byID("streak3")!] : [])
    }

    static func state() -> HomeState {
        let m = mood
        var s = HomeState.sample(m, overlays: m == .tired ? [.yawning] : [],
                                 form: m == .hibernation ? nil : .chatterling,
                                 branch: m == .radiant ? .radiant : .standard)
        s.updatedAt = .now.addingTimeInterval(-120)
        if flag("OSMonReview") {
            s.review = WeeklyReview(weekStart: 0, daysWithData: 7, avgWellbeing: 71, happyDays: 5,
                                    screenPerDay: 152, screenPerDayPrev: 184, steps: 41_300, bestStepsDay: 9_200,
                                    focus: 3, pats: 9, headline: "32 Min weniger Bildschirm pro Tag!",
                                    lines: ["Bildschirm: 32 Min weniger pro Tag als in der Vorwoche",
                                            "Zufrieden an 5 von 7 Tagen",
                                            "41.300 Schritte, bester Tag 9.200",
                                            "3 Fokus-Sessions geschafft"])
        }
        if flag("OSMonUnlock") { s.newScenes = [PetScene.byID("zauberwald")] }
        if flag("OSMonBedtimeCard") { s.sleepPhase = .windDown }
        if flag("OSMonMorning") {
            let r = NightRoutine.morningReport(nightMinutes: 0, saidGoodnight: true, nightOpens: 0)
            s.morning = MorningInfo(day: EpochDay.of(.now), greeting: BedtimeContent.shared.morningLine(r.rating, day: EpochDay.of(.now)),
                                    report: r, tip: BedtimeContent.shared.tipOfTheDay(EpochDay.of(.now)))
        }
        if let f = args.string(forKey: "OSMonFestival"), let id = FestivalID(rawValue: f) {
            s.festival = Festivals.sample(id, pet: PetFigure.byID(args.string(forKey: "OSMonFigure")).name)
            s.festivalCard = true
        }
        if let se = args.string(forKey: "OSMonSeason"), let v = Season(rawValue: se) { s.season = v }
        if let f = args.string(forKey: "OSMonFestival"), f == "none" { s.festival = nil }
        if flag("OSMonEvent") { s.evolutionEvent = PetEvoEvent(kind: .evolved, from: nil, to: .chatterling, epochDay: 0) }
        // Wachstum: -OSMonStage egg|baby|child|teen|adult, -OSMonEggTaps n, -OSMonGrowUp YES (Aufstiegs-Banner)
        if let raw = args.string(forKey: "OSMonStage"), let st = GrowthStage(rawValue: raw) {
            let today = EpochDay.of(.now)
            let good = st == .egg ? 0 : st == .adult ? 90 : st.minDays + (st.next.map { ($0.minDays - st.minDays) / 2 } ?? 0)
            var g = GrowthState(hatched: st != .egg, goodDays: good, lastDay: today - 1, hatchedDay: st == .egg ? nil : today - good - 3)
            g.stage = st
            if flag("OSMonGrowUp") { g.pendingStage = st }
            if flag("OSMonDown") { g.pendingStage = st; g.pendingDown = true }       // Rückfall-Banner
            if flag("OSMonRisk") { g.goodDays = st.minDays - 2 }                   // Warnung vor dem Rückfall
            let p = Growth.progress(g)
            s.growth = GrowthInfo(state: g, stage: st, eggTaps: args.integer(forKey: "OSMonEggTaps"),
                                  ageDays: g.hatchedDay.map { today - $0 } ?? 0, progress: p?.fraction, daysLeft: p?.daysLeft,
                                  roughToday: args.integer(forKey: "OSMonRough"))
        }
        // Sammelalbum: -OSMonAlbum YES (Banner + Zähler)
        if flag("OSMonAlbum") { s.album = DemoMode.album(pending: true) }
        return s
    }
}

/// Bildschirm für den Demo-Start.
struct DemoRoot: View {
    private func args(_ k: String) -> String? { UserDefaults.standard.string(forKey: k) }
    var body: some View {
        switch DemoMode.screen {
        case "settings": SettingsView()
        case "onboarding": OnboardingView {}
        case "logo": LogoSplashView {}
        case "wallpaper": WallpaperPreview {}
        case "album": AlbumView(album: DemoMode.album(pending: false))
        case "parent": ParentView()
        case "wardrobe": WardrobeView(figureName: PetFigure.byID(args("OSMonFigure")).name, stage: nil)
        case "games": GameView(figureName: PetFigure.byID(args("OSMonFigure")).name, stage: nil, onClose: { _, _ in },
                               demoGame: UserDefaults.standard.string(forKey: "OSMonGame").flatMap(MiniGame.init(rawValue:)))
        case "tasks": TasksManageView(model: TaskModel(), content: .shared, onToggle: { _ in })
        case "kindness": KindnessListView(model: TaskModel(), content: .shared)
        case "bedtime": BedtimeView {}
        case "story": if let st = BedtimeContent.shared.stories.first { StoryView(story: st, reader: StoryReader()) }
        case "songs": SongListView(songs: BedtimeContent.shared.songs, player: SongPlayer())
        default: HomeContent(state: DemoMode.state(), onOpenSettings: {},
                             demoScrollTo: UserDefaults.standard.string(forKey: "OSMonScroll"))
        }
    }
}
#endif
