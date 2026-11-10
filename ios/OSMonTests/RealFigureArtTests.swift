// RealFigureArtTests.swift: gleiche Fälle wie RealFigureArtTest.kt.

import XCTest

final class RealFigureArtTests: XCTestCase {
    func testStages() {
        XCTAssertEqual(RealFigureArt.stage(for: .egg), .egg)
        XCTAssertEqual(RealFigureArt.stage(for: .baby, hatchedDay: 100, today: 100), .hatchling)
        XCTAssertEqual(RealFigureArt.stage(for: .baby, hatchedDay: 100, today: 101), .baby)
        XCTAssertEqual(RealFigureArt.stage(for: .baby), .baby)
        XCTAssertEqual(RealFigureArt.stage(for: .child), .child)
        XCTAssertEqual(RealFigureArt.stage(for: .teen), .teen)
        XCTAssertEqual(RealFigureArt.stage(for: .adult, hatchedDay: 1, today: 1), .adult)
    }

    func testNames() {
        XCTAssertEqual(RealFigureArt.resourceName("sunni", .hatchling), "real_sunni_hatchling")
        XCTAssertEqual(RealFigureArt.resourceName("unbekannt", .adult), "real_lumo_adult")
        XCTAssertEqual(RealFigureArt.figureIDs.count, 11)
    }
}
