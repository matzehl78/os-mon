// EvolutionGoldenTests.swift: prüft EvolutionTracker.swift gegen golden_evolution.json
//
// Einrichtung (Unit-Test-Target, wie bei PetCoreGoldenTests.swift):
//   1. Sources im Test-Target: PetCore.swift, EvolutionTracker.swift, ScreenTimeShared.swift
//   2. golden_evolution.json als Resource ("Copy Bundle Resources")
//   3. Erzeugen: python3 golden_evolution.py

import XCTest

final class EvolutionGoldenTests: XCTestCase {

    private struct Doc: Decodable {
        let tolerance: Float
        let scenarios: [Scenario]
    }
    private struct Scenario: Decodable {
        let name: String
        let hatchDay: Int
        let config: PetConfig
        let steps: [Step]
    }
    private struct Step: Decodable {
        let record: PetDayRecord
        let expect: Expect
        let ack: Bool
    }
    private struct Expect: Decodable {
        let current: PetEvolution?
        let lastEvolutionDay: Int?
        let challenger: PetForm?
        let challengerSince: Int?
        let pendingEvent: PetEvoEvent?
        let blended: GroupShares?
        let historySize: Int
    }

    private func load() throws -> Doc {
        let url = try XCTUnwrap(
            Bundle(for: Self.self).url(forResource: "golden_evolution", withExtension: "json"),
            "golden_evolution.json fehlt im Test-Bundle")
        return try JSONDecoder().decode(Doc.self, from: Data(contentsOf: url))
    }

    func testScenarios() throws {
        let doc = try load()
        XCTAssertFalse(doc.scenarios.isEmpty)

        for sc in doc.scenarios {
            var state = PetEvoState(hatchDay: sc.hatchDay)
            for (k, step) in sc.steps.enumerated() {
                let label = "\(sc.name) [Tag \(step.record.epochDay), Schritt \(k)]"
                state = EvolutionTracker.closeDay(state, step.record, config: sc.config)
                let e = step.expect

                XCTAssertEqual(state.current, e.current, "\(label) / current")
                XCTAssertEqual(state.lastEvolutionDay, e.lastEvolutionDay, "\(label) / lastEvolutionDay")
                XCTAssertEqual(state.challenger, e.challenger, "\(label) / challenger")
                XCTAssertEqual(state.challengerSince, e.challengerSince, "\(label) / challengerSince")
                XCTAssertEqual(state.pendingEvent, e.pendingEvent, "\(label) / pendingEvent")
                XCTAssertEqual(state.history.count, e.historySize, "\(label) / historySize")

                switch (state.blended, e.blended) {
                case (nil, nil): break
                case let (got?, want?):
                    for g in UsageGroup.allCases {
                        XCTAssertEqual(got[g.rawValue] ?? 0, want[g.rawValue] ?? 0, accuracy: doc.tolerance,
                                       "\(label) / blended[\(g.rawValue)]")
                    }
                default:
                    XCTFail("\(label) / blended: erhalten \(String(describing: state.blended)), erwartet \(String(describing: e.blended))")
                }

                if step.ack { state = EvolutionTracker.acknowledge(state) }
            }
        }
    }

    func testIgnoresAlreadyClosedDays() {
        let cfg = PetConfig()
        let shares: GroupShares = ["social": 1]
        let s1 = EvolutionTracker.closeDay(PetEvoState(hatchDay: 0),
                                           PetDayRecord(epochDay: 5, shares: shares, totalMinutes: 100, avgWellbeing: 70),
                                           config: cfg)
        let same = PetDayRecord(epochDay: 5, shares: shares, totalMinutes: 200, avgWellbeing: 10)
        let earlier = PetDayRecord(epochDay: 3, shares: shares, totalMinutes: 200, avgWellbeing: 10)
        XCTAssertEqual(EvolutionTracker.closeDay(s1, same, config: cfg), s1)
        XCTAssertEqual(EvolutionTracker.closeDay(s1, earlier, config: cfg), s1)
    }
}
