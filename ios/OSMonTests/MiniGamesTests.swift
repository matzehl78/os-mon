// MiniGamesTests.swift: gleiche Fälle wie MiniGamesTest.kt.

import XCTest

final class MiniGamesTests: XCTestCase {
    func testDailyLimit() {
        XCTAssertEqual(MiniGames.left(lastDay: nil, playedThatDay: 0, today: 100, limit: 2), 2)
        XCTAssertEqual(MiniGames.left(lastDay: 100, playedThatDay: 1, today: 100, limit: 2), 1)
        XCTAssertEqual(MiniGames.left(lastDay: 100, playedThatDay: 2, today: 100, limit: 2), 0)
        XCTAssertEqual(MiniGames.left(lastDay: 99, playedThatDay: 2, today: 100, limit: 2), 2)
        XCTAssertEqual(MiniGames.left(lastDay: nil, playedThatDay: 0, today: 100, limit: 9), 3)
        XCTAssertEqual(MiniGames.left(lastDay: nil, playedThatDay: 0, today: 100, limit: 0), 0)
        XCTAssertEqual(MiniGames.afterPlay(lastDay: 100, playedThatDay: 1, today: 100), 2)
        XCTAssertEqual(MiniGames.afterPlay(lastDay: 99, playedThatDay: 2, today: 100), 1)
    }

    func testClockRollbackDoesNotResetLimit() {
        // Datum vorgestellt (Tag 101), zwei Runden gespielt, Datum zurück auf Tag 100: keine neuen Runden
        XCTAssertEqual(MiniGames.left(lastDay: 101, playedThatDay: 2, today: 100, limit: 2), 0)
        XCTAssertEqual(MiniGames.afterPlay(lastDay: 101, playedThatDay: 1, today: 100), 2)
        XCTAssertEqual(MiniGames.dayAfterPlay(lastDay: 101, today: 100), 101)
        XCTAssertEqual(MiniGames.dayAfterPlay(lastDay: 99, today: 100), 100)
        XCTAssertEqual(MiniGames.dayAfterPlay(lastDay: nil, today: 100), 100)
    }

    func testStarsAndGames() {
        XCTAssertEqual([7, 8, 15, 24].map(MiniGames.stars), [0, 1, 2, 3])
        XCTAssertEqual(MiniGames.available(premium: false), [.stars, .bubbles])
        XCTAssertEqual(MiniGames.available(premium: true).count, 3)
    }

    func testRandomIsPortable() {
        var r = GameRandom(42)
        XCTAssertEqual([r.next(), r.next(), r.next()], [0.25234514, 0.08812499, 0.5772812] as [Float])
    }

    func testGameRunsThirtySeconds() {
        for g in MiniGame.allCases {
            let s = MiniGameSim(g, seed: 7)
            var maxT = 0
            for _ in 0..<400 { s.step(0.1); maxT = max(maxT, s.targets.count) }
            XCTAssertTrue(s.done && s.left == 0, g.rawValue)
            XCTAssertTrue((1...MiniGameSim.maxTargets).contains(maxT), g.rawValue)
            XCTAssertNil(s.tap(0.5, 0.5))
        }
    }

    func testTappingScores() {
        let s = MiniGameSim(.stars, seed: 1)
        var taps = 0
        while !s.done {
            s.step(0.05)
            if let t = s.targets.first(where: { $0.alpha(s.time) > 0.5 }), s.tap(t.x, t.y) != nil { taps += 1 }
        }
        XCTAssertTrue(taps >= 20 && s.score >= taps && s.hits == taps)
        XCTAssertEqual(MiniGames.stars(s.score), 3)
        let m = MiniGameSim(.stars, seed: 1); m.step(1)
        XCTAssertNil(m.tap(-1, -1))
    }

    func testCloudsCostAPointButNeverBelowZero() {
        let s = MiniGameSim(.bubbles, seed: 3)
        var cloud: GameHit?
        while !s.done && cloud == nil {
            s.step(0.05)
            if let t = s.targets.first(where: { $0.kind == .cloud && $0.alpha(s.time) > 0.5 && $0.y < 0.95 }) { cloud = s.tap(t.x, t.y) }
        }
        XCTAssertEqual(cloud?.kind, .cloud)
        XCTAssertEqual(s.score, 0)
        XCTAssertEqual(cloud?.points, 0)
    }
}
