// SceneThemeTests.swift: gleiche Fälle wie SceneThemeTest.kt

import XCTest

final class SceneThemeTests: XCTestCase {
    private func d(_ y: Int, _ m: Int, _ dd: Int) -> Int { Festivals.epochDay(y, m, dd) }

    func testSeasons() {
        XCTAssertEqual(SceneTheme.season(day: d(2026, 2, 28)), .winter)
        XCTAssertEqual(SceneTheme.season(day: d(2026, 3, 1)), .spring)
        XCTAssertEqual(SceneTheme.season(day: d(2026, 6, 1)), .summer)
        XCTAssertEqual(SceneTheme.season(day: d(2026, 11, 2)), .autumn)
        XCTAssertEqual(SceneTheme.season(day: d(2026, 12, 1)), .winter)
        XCTAssertEqual(SceneTheme.title(.autumn), "Herbst")
    }

    func testEveryFestivalHasProps() {
        let all: [FestivalID] = [.birthday, .hatchday, .milestone, .christmas, .silvester, .newYear, .nikolaus, .stMartin, .halloween,
                                 .valentine, .carnival, .easter, .mothersDay, .fathersDay, .advent]
        for f in all { XCTAssertFalse(SceneTheme.festivalProps(f).isEmpty, f.rawValue) }
        XCTAssertTrue(SceneTheme.festivalProps(.christmas).contains { $0.kind == .emoji && $0.text == "🎄" })
        XCTAssertEqual(SceneTheme.festivalProps(.advent, adventCandles: 3).first { $0.kind == .candles }?.text, "3")
        XCTAssertEqual(SceneTheme.festivalProps(.advent, adventCandles: 9).first { $0.kind == .candles }?.text, "4")
    }

    func testCombine() {
        let xmas = Festivals.today(d(2026, 12, 24), birthday: nil, hatchDay: nil, pet: "Lumo")
        let p = SceneTheme.props(festival: xmas, season: .winter, seasons: true)
        XCTAssertTrue(p.contains { $0.text == "🎄" } && p.contains { $0.kind == .snowGround })
        XCTAssertTrue(SceneTheme.props(festival: nil, season: .winter, seasons: false).isEmpty)
        XCTAssertEqual(SceneTheme.particles(festival: xmas, season: .winter, seasons: true), .snow)
        XCTAssertEqual(SceneTheme.particles(festival: nil, season: .autumn, seasons: true), .leaves)
        XCTAssertNil(SceneTheme.particles(festival: nil, season: .summer, seasons: false))
    }
}
