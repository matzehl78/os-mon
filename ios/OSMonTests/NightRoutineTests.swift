// NightRoutineTests.swift: gleiche Fälle wie NightRoutineTest.kt (bedtime.json prüft BedtimeContentTest.kt).

import XCTest

final class NightRoutineTests: XCTestCase {
    let c = SleepConfig(bedMin: 22 * 60, wakeMin: 7 * 60)
    private func at(_ h: Int, _ m: Int = 0) -> Int { h * 60 + m }

    func testWindowAcrossMidnight() {
        XCTAssertTrue(NightRoutine.inWindow(at(23), at(22), at(7)))
        XCTAssertTrue(NightRoutine.inWindow(at(3), at(22), at(7)))
        XCTAssertFalse(NightRoutine.inWindow(at(7), at(22), at(7)))
        XCTAssertFalse(NightRoutine.inWindow(at(12), at(22), at(7)))
        XCTAssertTrue(NightRoutine.inWindow(at(8), at(7), at(10)))
        XCTAssertFalse(NightRoutine.inWindow(at(8), at(7), at(7)))
    }

    func testPhases() {
        XCTAssertEqual(NightRoutine.phase(at(21, 29), c), .day)
        XCTAssertEqual(NightRoutine.phase(at(21, 30), c), .windDown)
        XCTAssertEqual(NightRoutine.phase(at(22), c), .night)
        XCTAssertEqual(NightRoutine.phase(at(6, 59), c), .night)
        XCTAssertEqual(NightRoutine.phase(at(7), c), .morning)
        XCTAssertEqual(NightRoutine.phase(at(9, 59), c), .morning)
        XCTAssertEqual(NightRoutine.phase(at(10), c), .day)
        var off = c; off.enabled = false
        XCTAssertEqual(NightRoutine.phase(at(23), off), .day)
    }

    func testLateBedtimeAfterMidnight() {
        let late = SleepConfig(bedMin: at(0, 30), wakeMin: at(8))
        XCTAssertEqual(NightRoutine.phase(at(0, 10), late), .windDown)
        XCTAssertEqual(NightRoutine.phase(at(0), late), .windDown)
        XCTAssertEqual(NightRoutine.phase(at(1), late), .night)
        XCTAssertEqual(NightRoutine.phase(at(23, 59), late), .day)
        XCTAssertEqual(NightRoutine.nightDay(100, at(0, 10), late), 100)
    }

    func testNightDayBelongsToMorning() {
        XCTAssertEqual(NightRoutine.nightDay(100, at(21, 45), c), 101)
        XCTAssertEqual(NightRoutine.nightDay(100, at(23), c), 101)
        XCTAssertEqual(NightRoutine.nightDay(100, at(2), c), 100)
        XCTAssertEqual(NightRoutine.nightDay(100, at(8), c), 100)
        XCTAssertEqual(NightRoutine.nightDay(100, at(21), c), 100)
    }

    func testRatingAndReport() {
        XCTAssertEqual(NightRoutine.rating(nightMinutes: 0, saidGoodnight: true, nightOpens: 0), .great)
        XCTAssertEqual(NightRoutine.rating(nightMinutes: nil, saidGoodnight: true, nightOpens: 2), .great)
        XCTAssertEqual(NightRoutine.rating(nightMinutes: nil, saidGoodnight: false, nightOpens: 1), .ok)
        XCTAssertEqual(NightRoutine.rating(nightMinutes: 25, saidGoodnight: true, nightOpens: 0), .ok)
        XCTAssertEqual(NightRoutine.rating(nightMinutes: 25, saidGoodnight: false, nightOpens: 0), .restless)
        XCTAssertEqual(NightRoutine.morningReport(nightMinutes: 0, saidGoodnight: true, nightOpens: 0).detail, "Nach Mitternacht kein Blick aufs Handy.")
        XCTAssertEqual(NightRoutine.morningReport(nightMinutes: 42.3, saidGoodnight: true, nightOpens: 0).detail, "Nach Mitternacht 42 Min am Handy.")
        XCTAssertEqual(NightRoutine.morningReport(nightMinutes: nil, saidGoodnight: true, nightOpens: 0).detail, "Nach dem Gute-Nacht-Sagen blieb die App zu.")
        XCTAssertEqual(NightRoutine.morningReport(nightMinutes: nil, saidGoodnight: true, nightOpens: 3).detail, "Die App wurde nachts 3× geöffnet.")
        XCTAssertEqual(NightRoutine.morningReport(nightMinutes: nil, saidGoodnight: false, nightOpens: 0).detail, "Gestern gab es kein Gute-Nacht-Sagen.")
    }

    func testPickAndClock() {
        XCTAssertEqual(NightRoutine.pickIndex(8, 20759), NightRoutine.pickIndex(8, 20759))
        XCTAssertTrue((0..<8).contains(NightRoutine.pickIndex(8, -5, salt: 3)))
        XCTAssertEqual(NightRoutine.pickIndex(0, 5), 0)
        XCTAssertEqual(NightRoutine.clock(22 * 60 + 5), "22:05")
        XCTAssertEqual(NightRoutine.clock(-30 + 24 * 60 + 60), "00:30")
    }

    /// Kotlin und Swift rechnen die Tagesauswahl gleich (Werte aus NightRoutineTest.kt nachgerechnet).
    func testPickMatchesKotlin() {
        XCTAssertEqual(NightRoutine.pickIndex(8, 20759), (20759 * 31) % 8)
        XCTAssertEqual(NightRoutine.pickIndex(20, 20760, salt: 7), (20760 * 31 + 7) % 20)
    }
}
