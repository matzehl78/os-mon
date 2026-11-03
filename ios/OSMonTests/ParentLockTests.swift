// ParentLockTests.swift: gleiche Fälle wie ParentLockTest.kt.

import XCTest

final class ParentLockTests: XCTestCase {
    func testValidPins() {
        XCTAssertTrue(ParentLock.isValidPin("0429")); XCTAssertFalse(ParentLock.isValidPin("123")); XCTAssertFalse(ParentLock.isValidPin("12a4"))
        XCTAssertTrue(ParentLock.isTooSimple("1111")); XCTAssertTrue(ParentLock.isTooSimple("1234")); XCTAssertTrue(ParentLock.isTooSimple("9876"))
        XCTAssertFalse(ParentLock.isTooSimple("0429"))
    }

    func testHashAndVerify() {
        let h = ParentLock.hash("0429", salt: "abc")
        XCTAssertEqual(h, "5af02ec23a7d86f2efb251d1b65558b1a507b519b3b17e156c83a3a68882bcf6")
        XCTAssertTrue(ParentLock.verify("0429", salt: "abc", stored: h))
        XCTAssertFalse(ParentLock.verify("0428", salt: "abc", stored: h))
        XCTAssertFalse(ParentLock.verify("0429", salt: "abd", stored: h))
    }

    func testWaits() {
        XCTAssertEqual(ParentLock.wait(4), 0); XCTAssertEqual(ParentLock.wait(5), 60)
        XCTAssertEqual(ParentLock.wait(6), 120); XCTAssertEqual(ParentLock.wait(20), 900)
    }
}
