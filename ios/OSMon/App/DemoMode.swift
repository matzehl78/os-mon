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
    }

    static var mood: PetMood { PetMood(rawValue: args.string(forKey: "OSMonMood") ?? "") ?? .radiant }
    static func flag(_ key: String) -> Bool { args.bool(forKey: key) }

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
        return s
    }
}

/// Bildschirm für den Demo-Start.
struct DemoRoot: View {
    var body: some View {
        switch DemoMode.screen {
        case "settings": SettingsView()
        case "onboarding": OnboardingView {}
        case "logo": LogoSplashView {}
        case "bedtime": BedtimeView {}
        case "story": if let st = BedtimeContent.shared.stories.first { StoryView(story: st, reader: StoryReader()) }
        case "songs": SongListView(songs: BedtimeContent.shared.songs, player: SongPlayer())
        default: HomeContent(state: DemoMode.state(), onOpenSettings: {},
                             demoScrollTo: UserDefaults.standard.string(forKey: "OSMonScroll"))
        }
    }
}
#endif
