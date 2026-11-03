// WardrobeTests.swift: gleiche Fälle wie WardrobeTest.kt.

import XCTest

final class WardrobeTests: XCTestCase {
    func testListIsConsistent() {
        XCTAssertEqual(Wardrobe.all.count, 11)
        XCTAssertEqual(Set(Wardrobe.all.map(\.id)).count, Wardrobe.all.count)
        for it in Wardrobe.all { XCTAssertTrue((it.slot == .hat) == (it.hat != nil) && (it.slot == .glasses) == (it.glasses != nil), it.id) }
        XCTAssertTrue(Wardrobe.all.filter { !$0.premium }.allSatisfy { (1...GrowthStage.adult.minDays).contains($0.goodDays) })
    }

    func testUnlockByGoodDaysAndPremium() {
        XCTAssertEqual(Wardrobe.newlyUnlocked(maxGoodDays: 5, premium: false, known: []).map(\.id), ["beanie", "round"])
        XCTAssertEqual(Wardrobe.newlyUnlocked(maxGoodDays: 10, premium: false, known: ["beanie", "round"]).map(\.id), ["cap"])
        XCTAssertEqual(Wardrobe.newlyUnlocked(maxGoodDays: 0, premium: true, known: []).map(\.id), ["wizard", "pirate", "monocle"])
        XCTAssertEqual(Wardrobe.missing(Wardrobe.byID("cap")!, maxGoodDays: 7, premium: false), 3)
        XCTAssertNil(Wardrobe.missing(Wardrobe.byID("wizard")!, maxGoodDays: 99, premium: false))
        XCTAssertEqual(Wardrobe.next(maxGoodDays: 5)?.id, "cap")
        XCTAssertNil(Wardrobe.next(maxGoodDays: 60))
    }

    func testToggleAndOutfit() {
        var e = Wardrobe.toggle([], "beanie")
        e = Wardrobe.toggle(e, "round")
        e = Wardrobe.toggle(e, "cap")
        XCTAssertEqual(e, ["round", "cap"])
        XCTAssertEqual(Wardrobe.outfit(e, maxGoodDays: 20, premium: false), Outfit(hat: .cap, glasses: .round))
        XCTAssertEqual(Wardrobe.outfit(e, maxGoodDays: 6, premium: false), Outfit(hat: nil, glasses: .round))
        XCTAssertEqual(Wardrobe.toggle(e, "round"), ["cap"])
        XCTAssertEqual(Wardrobe.outfit(["wizard"], maxGoodDays: 99, premium: false), Outfit())
        XCTAssertEqual(Wardrobe.outfit(["wizard"], maxGoodDays: 0, premium: true), Outfit(hat: .wizardHat))
    }

    func testFestivalWins() {
        let o = Outfit(hat: .beanie, glasses: .sun)
        XCTAssertEqual(Wardrobe.hat(festival: .santaHat, outfit: o), .santaHat)
        XCTAssertEqual(Wardrobe.hat(festival: nil, outfit: o), .beanie)
    }
}
