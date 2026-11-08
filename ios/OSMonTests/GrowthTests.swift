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

    func testGoodAndBadDays() {
        var s = Growth.hatch(GrowthState(hatched: false, lastDay: d0), today: d0)
        XCTAssertEqual(s.pendingStage, .baby)
        s.pendingStage = nil
        s = Growth.closeDay(s, day: d0, avgWellbeing: 90)
        XCTAssertEqual(s.goodDays, 0)
        s = Growth.closeDay(s, day: d0 + 1, avgWellbeing: 40)
        s = Growth.closeDay(s, day: d0 + 2, avgWellbeing: 80)
        s = Growth.closeDay(s, day: d0 + 3, avgWellbeing: nil)
        s = Growth.closeDay(s, day: d0 + 3, avgWellbeing: 80)
        XCTAssertEqual(s.goodDays, 2)
        s = Growth.closeDay(s, day: d0 + 4, avgWellbeing: 39.9)
        XCTAssertEqual(s.goodDays, 2)
        s = Growth.closeDay(s, day: d0 + 5, avgWellbeing: 10)
        XCTAssertEqual(s.goodDays, 2)
        XCTAssertEqual(s.lastDay, d0 + 5)
        XCTAssertEqual(Growth.stage(s), .baby)
        XCTAssertEqual(([nil, 40, 39, 19] as [Float?]).map { Growth.delta($0) }, [0, 1, 0, 0])
    }

    func testBadDaysPauseButNeverDrop() {
        var s = GrowthState(hatched: true, goodDays: 14, lastDay: d0, stage: .child)
        for i in 1...30 { s = Growth.closeDay(s, day: d0 + i, avgWellbeing: 10) }
        XCTAssertEqual(s.goodDays, 14); XCTAssertEqual(Growth.stage(s), .child)
        XCTAssertNil(s.pendingStage); XCTAssertNotEqual(s.pendingDown, true); XCTAssertNil(Growth.risk(s))
        for i in 31...58 { s = Growth.closeDay(s, day: d0 + i, avgWellbeing: 70) }
        XCTAssertEqual(Growth.stage(s), .teen); XCTAssertEqual(s.pendingStage, .teen)
    }

    func testAdultStaysAdult() {
        var s = GrowthState(hatched: true, goodDays: 84, lastDay: d0, stage: .adult)
        XCTAssertNil(Growth.progress(s))
        for i in 1...10 { s = Growth.closeDay(s, day: d0 + i, avgWellbeing: 10) }
        XCTAssertEqual(Growth.stage(s), .adult); XCTAssertNotEqual(s.pendingDown, true)
    }

    func testOldStateBelowStageMinimumKeepsStage() {
        let s = Growth.closeDay(GrowthState(hatched: true, goodDays: 12, lastDay: d0, stage: .child), day: d0 + 1, avgWellbeing: 10)
        XCTAssertEqual(Growth.stage(s), .child); XCTAssertNil(Growth.risk(s))
    }

    func testRoughBehaviour() {
        XCTAssertEqual(Growth.delta(80, rough: 2), 1)
        XCTAssertEqual(Growth.delta(80, rough: 3), 0)
        XCTAssertEqual(Growth.delta(nil, rough: 3), 0)
        XCTAssertEqual(Growth.delta(10, rough: 5), 0)
        var s = GrowthState(hatched: true, goodDays: 5, lastDay: d0, stage: .baby)
        s = Growth.closeDay(s, day: d0 + 1, avgWellbeing: 80, rough: 4)
        XCTAssertEqual(s.goodDays, 5)
    }

    func testEggDoesNotGrow() {
        let egg = GrowthState(hatched: false, lastDay: d0)
        XCTAssertEqual(Growth.closeDay(egg, day: d0 + 1, avgWellbeing: 90), egg)
    }

    func testStageUpIsPending() {
        var s = GrowthState(hatched: true, goodDays: 13, lastDay: d0)
        s = Growth.closeDay(s, day: d0 + 1, avgWellbeing: 70)
        XCTAssertEqual(s.pendingStage, .child)
        XCTAssertEqual(s.pendingDown, false)
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
        XCTAssertEqual(Growth.stage(GrowthState(hatched: true, goodDays: 50, lastDay: d0)), .teen)
    }

    func testCracksAndLook() {
        XCTAssertEqual(Growth.cracks(0), 0); XCTAssertEqual(Growth.cracks(2), 1); XCTAssertEqual(Growth.cracks(5), 4); XCTAssertEqual(Growth.cracks(9), 4)
        XCTAssertEqual(Growth.look(.adult).sx, 1); XCTAssertEqual(Growth.look(nil).face, 1)
        XCTAssertLessThan(Growth.look(.baby).sx, Growth.look(.child).sx)
    }
}
