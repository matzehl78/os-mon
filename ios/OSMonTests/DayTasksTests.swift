// DayTasksTests.swift: gleiche Fälle wie DayTasksTest.kt (tasks.json prüft DayTasksContentTest.kt).

import XCTest

final class DayTasksTests: XCTestCase {
    let mon = 20759   // 2026-11-02, Montag

    private func t(_ id: String, _ r: TaskRepeat = .once, created: Int? = nil, done: Set<Int> = []) -> DayTask {
        DayTask(id: id, title: id, emoji: "⭐", repeatRule: r, createdDay: created ?? mon, doneDays: done)
    }

    func testWeekday() {
        XCTAssertEqual(DayTasks.weekday(0), 3)
        XCTAssertEqual(DayTasks.weekday(mon), 0)
        XCTAssertEqual(DayTasks.weekday(mon + 5), 5)
        XCTAssertEqual(DayTasks.weekday(mon + 6), 6)
    }

    func testOnceCarriesOverUntilDone() {
        let a = t("a")
        XCTAssertFalse(DayTasks.isDue(a, mon - 1))
        XCTAssertTrue(DayTasks.isDue(a, mon))
        XCTAssertTrue(DayTasks.isDue(a, mon + 3))
        XCTAssertTrue(DayTasks.isCarried(a, mon + 1))
        XCTAssertFalse(DayTasks.isCarried(a, mon))
        let done = DayTasks.toggle(a, mon + 2)
        XCTAssertTrue(DayTasks.isDue(done, mon + 2))
        XCTAssertTrue(DayTasks.isDone(done, mon + 2))
        XCTAssertFalse(DayTasks.isCarried(done, mon + 2))
        XCTAssertFalse(DayTasks.isDue(done, mon + 3))
    }

    func testRepeatRules() {
        let wd = t("w", .weekdays), we = t("e", .weekend), d = t("d", .daily)
        XCTAssertTrue(DayTasks.isDue(wd, mon + 4)); XCTAssertFalse(DayTasks.isDue(wd, mon + 5))
        XCTAssertFalse(DayTasks.isDue(we, mon + 4)); XCTAssertTrue(DayTasks.isDue(we, mon + 6))
        XCTAssertTrue(DayTasks.isDue(d, mon + 10)); XCTAssertFalse(DayTasks.isDue(d, mon - 1))
        let x = DayTasks.toggle(d, mon)
        XCTAssertTrue(DayTasks.isDone(x, mon)); XCTAssertFalse(DayTasks.isDone(x, mon + 1))
        XCTAssertFalse(DayTasks.isDone(DayTasks.toggle(x, mon), mon))
    }

    func testProgressAndStages() {
        XCTAssertEqual(DayTasks.progress([], mon).stage, .empty)
        let list = [t("a"), t("b"), t("c"), t("d")]
        XCTAssertEqual(DayTasks.progress(list, mon), TaskProgress(done: 0, total: 4))
        XCTAssertEqual(DayTasks.progress(list, mon).stage, .start)
        var one = list; one[0] = DayTasks.toggle(one[0], mon)
        XCTAssertEqual(DayTasks.progress(one, mon).stage, .going)
        var two = one; two[1] = DayTasks.toggle(two[1], mon)
        XCTAssertEqual(DayTasks.progress(two, mon).stage, .half)
        let all = two.map { DayTasks.isDone($0, mon) ? $0 : DayTasks.toggle($0, mon) }
        XCTAssertEqual(DayTasks.progress(all, mon).stage, .all)
        XCTAssertEqual(DayTasks.progress(all, mon).fraction, 1)
    }

    func testOrderOpenCarriedFirst() {
        let old = t("alt", created: mon - 1)
        let done = DayTasks.toggle(t("fertig"), mon)
        let open = t("neu")
        XCTAssertEqual(DayTasks.forDay([done, open, old], mon).map(\.id), ["alt", "neu", "fertig"])
    }

    func testStreak() {
        let d = t("d", .daily, created: mon - 5, done: [mon - 3, mon - 2, mon - 1])
        XCTAssertEqual(DayTasks.streak([d], today: mon), 3)
        XCTAssertEqual(DayTasks.streak([DayTasks.toggle(d, mon)], today: mon), 4)
        let we = t("we", .weekdays, created: mon - 7, done: [mon - 7, mon - 6, mon - 5, mon - 4, mon - 3])
        XCTAssertEqual(DayTasks.streak([we], today: mon), 5)
        XCTAssertEqual(DayTasks.streak([], today: mon), 0)
    }

    func testLimitsAndCleanup() {
        let many = (1...DayTasks.maxTasks).map { t("t\($0)") }
        XCTAssertFalse(DayTasks.canAdd(many, mon, title: "noch eine"))
        XCTAssertTrue(DayTasks.canAdd(Array(many.dropFirst()), mon, title: "noch eine"))
        XCTAssertFalse(DayTasks.canAdd([], mon, title: "   "))
        XCTAssertEqual(DayTasks.cleanup([t("x", created: mon - 100, done: [mon - 90])], today: mon), [])
        XCTAssertEqual(DayTasks.cleanup([t("offen", created: mon - 100)], today: mon).count, 1)
    }

    func testPickAndFill() {
        XCTAssertEqual(DayTasks.pick(["a", "b", "c"], day: 1, salt: 2), "a")
        XCTAssertEqual(DayTasks.pick(["a", "b", "c"], day: 2, salt: 2), "b")
        XCTAssertNil(DayTasks.pick([String](), day: 1, salt: 0))
        XCTAssertEqual(DayTasks.fill("{name} freut sich über {task}!", pet: "Lumo", task: "Zähne putzen"),
                       "Lumo freut sich über Zähne putzen!")
    }

}
