// PetCoreGoldenTests.swift: prüft PetCore.swift gegen golden_vectors.json
//
// Einrichtung (Unit-Test-Target):
//   1. PetCore.swift und ScreenTimeShared.swift als Sources ins Test-Target aufnehmen
//      (kein @testable import nötig).
//   2. golden_vectors.json als Resource ins Test-Target aufnehmen ("Copy Bundle Resources").
//   3. Vektoren neu erzeugen: python3 golden_reference.py
//
// Toleranz kommt aus der Datei (Float32 in Swift/Kotlin gegen Double in der Referenz).

import XCTest

final class PetCoreGoldenTests: XCTestCase {

    // MARK: Vektor-Typen

    private struct Vectors: Decodable {
        let tolerance: Float
        let targets: [TargetCase]
        let step: [StepCase]
        let wellbeing: [WellbeingCase]
        let mood: [MoodCase]
        let overlays: [OverlayCase]
        let sequences: [SequenceCase]
        let evolve: [EvolveCase]
    }

    private struct TargetCase: Decodable {
        let name: String; let config: PetConfig; let inputs: PetInputs; let expect: PetStats
    }
    private struct StepCase: Decodable {
        let name: String; let prev: PetStats?; let target: PetStats; let dtHours: Float; let expect: PetStats
    }
    private struct WellbeingCase: Decodable { let name: String; let stats: PetStats; let expect: Float }
    private struct MoodCase: Decodable { let name: String; let h: Float; let prev: PetMood?; let expect: PetMood }
    private struct OverlayCase: Decodable { let name: String; let stats: PetStats; let expect: [PetOverlay] }
    private struct SnapshotExpect: Decodable {
        let stats: PetStats; let wellbeing: Float; let mood: PetMood; let overlays: [PetOverlay]
    }
    private struct SeqStep: Decodable { let inputs: PetInputs; let dtHours: Float; let expect: SnapshotExpect }
    private struct SequenceCase: Decodable { let name: String; let config: PetConfig; let steps: [SeqStep] }
    private struct EvolveExpect: Decodable { let form: PetForm; let hybrid: PetForm?; let branch: PetBranch }
    private struct EvolveCase: Decodable {
        let name: String
        let shares: [String: Float]          // JSON-Schlüssel sind Strings ("social" ...)
        let avgDailyMinutes: Float
        let avgWellbeing14: Float
        let outdoorShare: Float
        let config: PetConfig
        let expect: EvolveExpect
    }

    // MARK: Laden

    private func loadVectors() throws -> Vectors {
        let url = try XCTUnwrap(
            Bundle(for: Self.self).url(forResource: "golden_vectors", withExtension: "json"),
            "golden_vectors.json fehlt im Test-Bundle"
        )
        return try JSONDecoder().decode(Vectors.self, from: Data(contentsOf: url))
    }

    // MARK: Hilfen

    private func assertStat(_ got: Float?, _ want: Float?, _ tol: Float, _ label: String, _ name: String,
                            file: StaticString = #filePath, line: UInt = #line) {
        switch (got, want) {
        case (nil, nil): break
        case let (g?, w?): XCTAssertEqual(g, w, accuracy: tol, "\(name) / \(label)", file: file, line: line)
        default: XCTFail("\(name) / \(label): erhalten \(String(describing: got)), erwartet \(String(describing: want))",
                         file: file, line: line)
        }
    }

    private func assertStats(_ got: PetStats, _ want: PetStats, _ tol: Float, _ name: String,
                             file: StaticString = #filePath, line: UInt = #line) {
        assertStat(got.e, want.e, tol, "e", name, file: file, line: line)
        assertStat(got.r, want.r, tol, "r", name, file: file, line: line)
        assertStat(got.f, want.f, tol, "f", name, file: file, line: line)
        assertStat(got.v, want.v, tol, "v", name, file: file, line: line)
        assertStat(got.a, want.a, tol, "a", name, file: file, line: line)
    }

    private func groupShares(_ raw: [String: Float]) -> [UsageGroup: Float] {
        var out: [UsageGroup: Float] = [:]
        for (key, value) in raw {
            if let g = UsageGroup(rawValue: key) { out[g] = value }
        }
        return out
    }

    // MARK: Tests

    func testTargets() throws {
        let v = try loadVectors()
        XCTAssertFalse(v.targets.isEmpty)
        for c in v.targets {
            assertStats(PetCore.targets(c.inputs, c.config), c.expect, v.tolerance, c.name)
        }
    }

    func testStep() throws {
        let v = try loadVectors()
        for c in v.step {
            assertStats(PetCore.step(prev: c.prev, target: c.target, dtHours: c.dtHours),
                        c.expect, v.tolerance, c.name)
        }
    }

    func testWellbeing() throws {
        let v = try loadVectors()
        for c in v.wellbeing {
            XCTAssertEqual(PetCore.wellbeing(c.stats), c.expect, accuracy: v.tolerance, c.name)
        }
    }

    func testMoodHysteresis() throws {
        let v = try loadVectors()
        for c in v.mood {
            XCTAssertEqual(PetCore.mood(c.h, prev: c.prev), c.expect, c.name)
        }
    }

    func testOverlays() throws {
        let v = try loadVectors()
        for c in v.overlays {
            XCTAssertEqual(PetCore.overlays(c.stats), Set(c.expect), c.name)
        }
    }

    func testSequences() throws {
        let v = try loadVectors()
        for seq in v.sequences {
            var prev: PetSnapshot? = nil
            for (k, s) in seq.steps.enumerated() {
                let label = "\(seq.name) [Schritt \(k)]"
                let snap = PetCore.tick(prev: prev, inputs: s.inputs, config: seq.config, dtHours: s.dtHours)
                assertStats(snap.stats, s.expect.stats, v.tolerance, label)
                XCTAssertEqual(snap.wellbeing, s.expect.wellbeing, accuracy: v.tolerance, label)
                XCTAssertEqual(snap.mood, s.expect.mood, label)
                XCTAssertEqual(snap.overlays, Set(s.expect.overlays), label)
                prev = snap
            }
        }
    }

    func testEvolution() throws {
        let v = try loadVectors()
        for c in v.evolve {
            let got = PetCore.evolve(shares: groupShares(c.shares), avgDailyMinutes: c.avgDailyMinutes,
                                     avgWellbeing14: c.avgWellbeing14, config: c.config,
                                     outdoorShare: c.outdoorShare)
            XCTAssertEqual(got.form, c.expect.form, c.name)
            XCTAssertEqual(got.hybrid, c.expect.hybrid, c.name)
            XCTAssertEqual(got.branch, c.expect.branch, c.name)
        }
    }

    // MARK: Codable (Persistenz im App-Group-Speicher)

    func testSnapshotRoundTrip() throws {
        let inputs = PetInputs(batteryPct: 80, freeStorageRatio: 0.3, screenMinutes: 90, steps: 4000)
        let snap = PetCore.tick(prev: nil, inputs: inputs, config: PetConfig(), dtHours: 0.25)
        let data = try JSONEncoder().encode(snap)
        XCTAssertEqual(try JSONDecoder().decode(PetSnapshot.self, from: data), snap)
    }
}
