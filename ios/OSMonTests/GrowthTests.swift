// GrowthTests.swift: gleiche Fälle wie GrowthTest.kt.

import XCTest

final class GrowthTests: XCTestCase {
    let d0 = 20759

    func testStagesByGoodDays() {
        XCTAssertEqual(Growth.stage(for: 0), .baby)
        XCTAssertEqual(Growth.stage(for: 13), .baby)
        XCTAssertEqual(Growth.stage(for: 14), .child)
        XCTAssertEqual(Growth.stage(for: 42), .teen)
        XCTAssertEqual(Growth.stage(for: 84), .adult)
        XCTAssertEqual(Growth.stage(for: 500), .adult)
        XCTAssertEqual(Growth.stage(GrowthState(hatched: false, lastDay: d0)), .egg)
    }

    func testOnlyGoodDaysCount() {
        var s = Growth.hatch(GrowthState(hatched: false, lastDay: d0), today: d0)
        XCTAssertEqual(s.pendingStage, .baby)
        s.pendingStage = nil
        s = Growth.closeDay(s, day: d0, avgWellbeing: 90)
        XCTAssertEqual(s.goodDays, 0)
        s = Growth.closeDay(s, day: d0 + 1, avgWellbeing: 39.9)
        s = Growth.closeDay(s, day: d0 + 2, avgWellbeing: nil)
        s = Growth.closeDay(s, day: d0 + 3, avgWellbeing: 40)
        s = Growth.closeDay(s, day: d0 + 3, avgWellbeing: 80)
        XCTAssertEqual(s.goodDays, 1)
        XCTAssertEqual(s.lastDay, d0 + 3)
    }

    func testEggDoesNotGrow() {
        let egg = GrowthState(hatched: false, lastDay: d0)
        XCTAssertEqual(Growth.closeDay(egg, day: d0 + 1, avgWellbeing: 90), egg)
    }

    func testStageUpIsPending() {
        var s = GrowthState(hatched: true, goodDays: 13, lastDay: d0)
        s = Growth.closeDay(s, day: d0 + 1, avgWellbeing: 70)
        XCTAssertEqual(s.pendingStage, .child)
        s.pendingStage = nil
        s = Growth.closeDay(s, day: d0 + 2, avgWellbeing: 70)
        XCTAssertNil(s.pendingStage)
    }

    func testProgressAndMigration() {
        let p = Growth.progress(GrowthState(hatched: true, goodDays: 7, lastDay: d0))
        XCTAssertEqual(p?.fraction, 0.5); XCTAssertEqual(p?.daysLeft, 7)
        XCTAssertEqual(Growth.progress(GrowthState(hatched: true, goodDays: 14, lastDay: d0))?.daysLeft, 28)
        XCTAssertNil(Growth.progress(GrowthState(hatched: true, goodDays: 84, lastDay: d0)))
        XCTAssertNil(Growth.progress(GrowthState(hatched: false, lastDay: d0)))
        let m = Growth.migrated(hatchDay: d0 - 20, today: d0)
        XCTAssertEqual(m.goodDays, 20); XCTAssertEqual(Growth.stage(m), .child); XCTAssertEqual(m.lastDay, d0 - 1)
    }

    func testCracksAndLook() {
        XCTAssertEqual(Growth.cracks(0), 0); XCTAssertEqual(Growth.cracks(2), 1); XCTAssertEqual(Growth.cracks(5), 4); XCTAssertEqual(Growth.cracks(9), 4)
        XCTAssertEqual(Growth.look(.adult).sx, 1); XCTAssertEqual(Growth.look(nil).face, 1)
        XCTAssertLessThan(Growth.look(.baby).sx, Growth.look(.child).sx)
    }
}
