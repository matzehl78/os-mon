// FestivalsTests.swift: gleiche Fälle wie FestivalsTest.kt

import XCTest

final class FestivalsTests: XCTestCase {
    private func d(_ y: Int, _ m: Int, _ dd: Int) -> Int { Festivals.epochDay(y, m, dd) }
    private func id(_ day: Int, bday: (m: Int, d: Int)? = nil, hatch: Int? = nil) -> FestivalID? {
        Festivals.today(day, birthday: bday, hatchDay: hatch, pet: "Lumo")?.id
    }

    func testCalendar() {
        XCTAssertEqual(d(1970, 1, 1), 0)
        XCTAssertEqual(d(2026, 11, 2), 20759)
        XCTAssertEqual(Festivals.ymd(20759), Ymd(y: 2026, m: 11, d: 2))
        XCTAssertEqual(Festivals.ymd(d(2024, 2, 29)), Ymd(y: 2024, m: 2, d: 29))
        XCTAssertEqual(Festivals.ymd(-1), Ymd(y: 1969, m: 12, d: 31))
        XCTAssertEqual(Festivals.weekday(d(2026, 11, 2)), 1)
        XCTAssertEqual(Festivals.weekday(0), 4)
        XCTAssertEqual(EpochDay.of(EpochDay.date(20759)), 20759)
    }

    func testMovableFeasts() {
        XCTAssertEqual(Festivals.easter(2025), d(2025, 4, 20))
        XCTAssertEqual(Festivals.easter(2026), d(2026, 4, 5))
        XCTAssertEqual(Festivals.easter(2027), d(2027, 3, 28))
        XCTAssertEqual(Festivals.firstAdvent(2026), d(2026, 11, 29))
        XCTAssertEqual(Festivals.firstAdvent(2027), d(2027, 11, 28))
        XCTAssertEqual(Festivals.firstAdvent(2025), d(2025, 11, 30))
        XCTAssertEqual(Festivals.mothersDay(2026), d(2026, 5, 10))
        XCTAssertEqual(Festivals.mothersDay(2027), d(2027, 5, 9))
    }

    func testFixedDays() {
        XCTAssertEqual(id(d(2026, 12, 24)), .christmas)
        XCTAssertEqual(id(d(2026, 12, 26)), .christmas)
        XCTAssertEqual(id(d(2026, 12, 31)), .silvester)
        XCTAssertEqual(id(d(2027, 1, 1)), .newYear)
        XCTAssertEqual(id(d(2026, 12, 6)), .nikolaus)
        XCTAssertEqual(id(d(2026, 11, 11)), .stMartin)
        XCTAssertEqual(id(d(2026, 10, 31)), .halloween)
        XCTAssertEqual(id(d(2027, 2, 14)), .valentine)
        XCTAssertNil(id(d(2026, 11, 2)))
        XCTAssertNil(id(d(2026, 7, 15)))
    }

    func testMovableDays() {
        XCTAssertEqual(id(d(2026, 4, 3)), .easter)
        XCTAssertEqual(id(d(2026, 4, 6)), .easter)
        XCTAssertNil(id(d(2026, 4, 7)))
        XCTAssertEqual(id(d(2026, 2, 12)), .carnival)
        XCTAssertEqual(id(d(2026, 2, 17)), .carnival)
        XCTAssertNil(id(d(2026, 2, 18)))
        XCTAssertEqual(id(d(2026, 5, 10)), .mothersDay)
        XCTAssertEqual(id(d(2026, 5, 14)), .fathersDay)
        XCTAssertEqual(id(d(2026, 11, 29)), .advent)
        XCTAssertEqual(id(d(2026, 12, 23)), .advent)
        XCTAssertNil(id(d(2026, 11, 28)))
        XCTAssertEqual(Festivals.today(d(2026, 12, 20), birthday: nil, hatchDay: nil, pet: "Lumo")?.title, "4. Advent")
        XCTAssertEqual(Festivals.today(d(2026, 12, 1), birthday: nil, hatchDay: nil, pet: "Lumo")?.title, "1. Advent")
    }

    func testPersonalDaysWin() {
        let xmas = d(2026, 12, 24)
        XCTAssertEqual(id(xmas, bday: (12, 24)), .birthday)
        let hatch = d(2025, 12, 24)
        XCTAssertEqual(id(xmas, hatch: hatch), .hatchday)
        XCTAssertEqual(Festivals.today(xmas, birthday: nil, hatchDay: hatch, pet: "Lumo")?.greeting, "Heute vor einem Jahr bin ich geschlüpft. Danke, dass du da bist!")
        XCTAssertEqual(id(d(2026, 7, 15), hatch: d(2026, 7, 15) - 100), .milestone)
        XCTAssertEqual(id(d(2026, 7, 15), hatch: d(2026, 7, 15) - 7), .milestone)
        XCTAssertNil(id(d(2026, 7, 15), hatch: d(2026, 7, 15) - 8))
        XCTAssertNil(id(d(2026, 7, 15), hatch: d(2026, 7, 15)))
        XCTAssertEqual(id(d(2028, 2, 29), bday: (2, 29)), .birthday)
    }

    func testSamplesExist() {
        for f in [FestivalID.birthday, .hatchday, .milestone, .christmas, .silvester, .newYear, .nikolaus, .stMartin, .halloween,
                  .valentine, .carnival, .easter, .mothersDay, .fathersDay, .advent] {
            XCTAssertEqual(Festivals.sample(f)?.id, f, "\(f)")
        }
    }
}
