// PushGoldenTests.swift: prüft PushPolicy.swift gegen golden_push.json
//
// Einrichtung (Unit-Test-Target):
//   1. PushPolicy.swift als Source ins Test-Target
//   2. golden_push.json als Resource ("Copy Bundle Resources")
//   3. Erzeugen: python3 golden_push.py

import XCTest

final class PushGoldenTests: XCTestCase {

    private struct Doc: Decodable { let scenarios: [Scenario] }

    private struct Scenario: Decodable {
        let name: String
        let initial: PushState
        let steps: [Step]
    }

    private struct Step: Decodable {
        let op: String
        let signals: PushSignals?
        let clock: PushClock?
        let type: PushType?
        let expectMessage: PushMessage?
        let expectState: PushState
        let expectCap: Int
    }

    private func load() throws -> Doc {
        let url = try XCTUnwrap(
            Bundle(for: Self.self).url(forResource: "golden_push", withExtension: "json"),
            "golden_push.json fehlt im Test-Bundle")
        return try JSONDecoder().decode(Doc.self, from: Data(contentsOf: url))
    }

    func testScenarios() throws {
        let doc = try load()
        XCTAssertFalse(doc.scenarios.isEmpty)
        var evaluated = 0

        for sc in doc.scenarios {
            var state = sc.initial
            for (k, step) in sc.steps.enumerated() {
                let label = "\(sc.name) [Schritt \(k), \(step.op)]"
                switch step.op {
                case "evaluate":
                    evaluated += 1
                    let got = PushPolicy.evaluate(state, try XCTUnwrap(step.signals, label), try XCTUnwrap(step.clock, label))
                    XCTAssertEqual(got, step.expectMessage, "\(label) / message")
                case "sent":
                    state = PushPolicy.onSent(state, try XCTUnwrap(step.type, label), try XCTUnwrap(step.clock, label))
                case "ignored":
                    state = PushPolicy.onIgnored(state)
                case "engaged":
                    state = PushPolicy.onEngaged(state)
                default:
                    XCTFail("\(label): unbekannte Operation")
                }
                XCTAssertEqual(state, step.expectState, "\(label) / state")
                XCTAssertEqual(PushPolicy.dailyCap(state), step.expectCap, "\(label) / cap")
            }
        }
        XCTAssertGreaterThan(evaluated, 0)
    }

    /// PushState muss als JSON-Objekt (nicht als Paar-Array) gespeichert werden, sonst passt die App Group
    /// nicht zu den Vektoren.
    func testStateEncodesAsObject() throws {
        let s = PushState(dayKey: "2026-11-01", sentToday: 1, lastSentMs: ["BATTERY_LOW": 5], lastAnyMs: 5, ignoredStreak: 0)
        let json = try XCTUnwrap(String(data: JSONEncoder().encode(s), encoding: .utf8))
        XCTAssertTrue(json.contains("\"BATTERY_LOW\":5"))
    }
}
