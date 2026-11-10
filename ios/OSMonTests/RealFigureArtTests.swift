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

final class PetStyleTests: XCTestCase {
    func testResolve() {
        XCTAssertEqual(PetStyle.resolve("real", legacy3D: true), .real)
        XCTAssertEqual(PetStyle.resolve("2d", legacy3D: true), .flat)
        XCTAssertEqual(PetStyle.resolve(nil, legacy3D: nil), .threeD)      // Standard wie bisher: 3D an
        XCTAssertEqual(PetStyle.resolve("", legacy3D: false), .flat)        // frühere Einstellung „3D aus“
        XCTAssertEqual(PetStyle.resolve("kaputt", legacy3D: true), .threeD)
    }
}
