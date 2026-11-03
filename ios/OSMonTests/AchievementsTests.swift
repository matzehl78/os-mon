// AchievementsTests.swift: gleiche Fälle wie AchievementsTest.kt.

import XCTest

final class AchievementsTests: XCTestCase {
    func testListIsConsistent() {
        XCTAssertEqual(Achievements.all.count, 23)
        XCTAssertEqual(Set(Achievements.all.map(\.id)).count, Achievements.all.count)
        for a in Achievements.all { XCTAssertTrue(a.target > 0 && !a.title.isEmpty && !a.hint.isEmpty, a.id) }
    }

    func testUnlocksOnlyNewOnes() {
        let m: [Metric: Int] = [.stage: 2, .tasks: 12, .kind: 1]
        XCTAssertEqual(Achievements.newlyUnlocked(m, unlocked: ["hatched"]).map(\.id), ["child", "task1", "task10", "kind1"])
        XCTAssertTrue(Achievements.newlyUnlocked([:], unlocked: []).isEmpty)
    }

    func testProgressAndMerge() {
        let a = Achievements.byID("task50")!
        XCTAssertEqual(Achievements.progress(a, [.tasks: 25]), 0.5)
        XCTAssertEqual(Achievements.progress(a, [.tasks: 80]), 1)
        XCTAssertEqual(Achievements.progress(a, [:]), 0)
        XCTAssertEqual(Achievements.merge([.streak: 9, .tasks: 3], [.streak: 2, .tasks: 4]), [.streak: 9, .tasks: 4])
    }
}
