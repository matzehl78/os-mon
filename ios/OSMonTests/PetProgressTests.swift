// PetProgressTests.swift: Wochenrückblick und Freischalt-Regeln (gleiche Fälle wie PetProgressTest.kt)
// Target: OSMonTests (PetProgress.swift liegt in Shared/Logic und wird direkt mitkompiliert)

import XCTest


final class PetProgressTests: XCTestCase {
    // 2026-11-02 = epochDay 20759, ein Montag
    let MON = 20759

    private func unlocked(_ l: [SceneUnlock], _ id: String) -> Bool { l.first { $0.sceneID == id }!.unlocked }
    private func progress(_ l: [SceneUnlock], _ id: String) -> Float { l.first { $0.sceneID == id }!.progress }

    func testWeekStartIsMonday() {
        XCTAssertEqual(PetProgress.weekStart(MON), MON)
        XCTAssertEqual(PetProgress.weekStart(MON + 6), MON)
        XCTAssertEqual(PetProgress.weekStart(MON - 1), MON - 7)
        XCTAssertEqual(PetProgress.weekStart(0), -3)
    }

    func testReviewNeedsThreeDays() {
        let days = [DayStat(day: MON - 7, avgWellbeing: 70), DayStat(day: MON - 6, steps: 4000)]
        XCTAssertNil(PetProgress.weeklyReview(days, today: MON + 2))
    }

    func testReviewScreenDropHeadline() throws {
        let prev = (MON - 14 ..< MON - 7).map { DayStat(day: $0, avgWellbeing: 55, screenMin: 200) }
        let week = (MON - 7 ..< MON).map { DayStat(day: $0, avgWellbeing: 60, screenMin: 170, steps: 3000, pats: 1) }
        let r = try XCTUnwrap(PetProgress.weeklyReview(prev + week, today: MON + 3))
        XCTAssertEqual(r.weekStart, MON - 7)
        XCTAssertEqual(r.daysWithData, 7)
        XCTAssertEqual(r.screenPerDayPrev! - r.screenPerDay!, 30, accuracy: 0.01)
        XCTAssertEqual(r.headline, "30 Min weniger Bildschirm pro Tag!")
        XCTAssertEqual(r.steps, 21000)
        XCTAssertEqual(r.pats, 7)
        XCTAssertEqual(r.lines, [
            "Bildschirm: 30 Min weniger pro Tag als in der Vorwoche",
            "Zufrieden an 0 von 7 Tagen",
            "21.000 Schritte, bester Tag 3.000",
            "7× gestreichelt",
        ])
    }

    func testReviewHappyHeadlineAndFocusSingular() throws {
        let week = (MON - 7 ..< MON).map { DayStat(day: $0, avgWellbeing: $0 < MON - 2 ? 70 : 50, focus: $0 == MON - 7 ? 1 : 0) }
        let r = try XCTUnwrap(PetProgress.weeklyReview(week, today: MON))
        XCTAssertEqual(r.happyDays, 5)
        XCTAssertEqual(r.headline, "5 Tage zufrieden – starke Woche!")
        XCTAssertTrue(r.lines.contains("1 Fokus-Session geschafft"))
        XCTAssertNil(r.screenPerDay)
    }

    func testReviewIgnoresCurrentWeek() {
        let cur = (MON ..< MON + 5).map { DayStat(day: $0, avgWellbeing: 90) }
        XCTAssertNil(PetProgress.weeklyReview(cur, today: MON + 5))
    }

    func testFreeScenesAlwaysOpen() {
        let l = PetProgress.unlocks([], age: 0, evolved: false, screenGoal: 180)
        XCTAssertEqual(l.count, 11)
        for id in ["none", "neonstadt", "zimmer", "garteninsel"] { XCTAssertTrue(unlocked(l, id)) }
        for id in ["raumstation", "zauberwald", "unterwasser", "suess", "mondbasis", "zen", "studio"] { XCTAssertFalse(unlocked(l, id)) }
    }

    func testStepsWindowAndGaps() {
        let d = (0 ..< 6).map { DayStat(day: MON + $0, steps: 8000) } + [DayStat(day: MON + 7, steps: 9000)]
        let l = PetProgress.unlocks(d, age: 0, evolved: false, screenGoal: 180)
        XCTAssertFalse(unlocked(l, "raumstation"))
        XCTAssertEqual(progress(l, "raumstation"), 0.98, accuracy: 0.001)
        let l2 = PetProgress.unlocks(d + [DayStat(day: MON + 6, steps: 2000)], age: 0, evolved: false, screenGoal: 180)
        XCTAssertTrue(unlocked(l2, "raumstation"))
    }

    func testHappyRunNeedsConsecutiveDays() {
        let d = [0, 1, 2, 3, 5, 6].map { DayStat(day: MON + $0, avgWellbeing: 70) }
        let l = PetProgress.unlocks(d, age: 0, evolved: false, screenGoal: 180)
        XCTAssertFalse(unlocked(l, "zauberwald"))
        XCTAssertEqual(progress(l, "zauberwald"), 0.8, accuracy: 0.001)
        let l2 = PetProgress.unlocks(d + [DayStat(day: MON + 4, avgWellbeing: 65)], age: 0, evolved: false, screenGoal: 180)
        XCTAssertTrue(unlocked(l2, "zauberwald"))
    }

    func testScreenRunUsesGoal() {
        let d = (0 ..< 7).map { DayStat(day: MON + $0, screenMin: $0 == 3 ? 181 : 120) }
        XCTAssertFalse(unlocked(PetProgress.unlocks(d, age: 0, evolved: false, screenGoal: 180), "zen"))
        XCTAssertTrue(unlocked(PetProgress.unlocks(d, age: 0, evolved: false, screenGoal: 190), "zen"))
    }

    func testCountersAgeEvolution() {
        let d = [DayStat(day: MON, pats: 6, focus: 2), DayStat(day: MON + 9, pats: 4, focus: 1)]
        let l = PetProgress.unlocks(d, age: 14, evolved: true, screenGoal: 180)
        for id in ["unterwasser", "suess", "mondbasis", "studio"] { XCTAssertTrue(unlocked(l, id)) }
        let l2 = PetProgress.unlocks(d, age: 7, evolved: false, screenGoal: 180)
        XCTAssertEqual(progress(l2, "mondbasis"), 0.5, accuracy: 0.001)
        XCTAssertFalse(unlocked(l2, "studio"))
    }

    func testAlreadyUnlockedStaysOpen() {
        let l = PetProgress.unlocks([], age: 0, evolved: false, screenGoal: 180, already: ["zen"])
        XCTAssertTrue(unlocked(l, "zen"))
        XCTAssertEqual(progress(l, "zen"), 1)
    }
}
