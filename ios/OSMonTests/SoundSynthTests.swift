// SoundSynthTests.swift: gleiche Fälle wie SoundSynthTest.kt.

import XCTest

final class SoundSynthTests: XCTestCase {
    func testSyllablesFollowWords() {
        XCTAssertEqual(SoundSynth.syllables("Hi"), 2)
        XCTAssertEqual(SoundSynth.syllables("Hallo, wie geht’s dir?"), 4)
        XCTAssertEqual(SoundSynth.syllables("eins zwei drei vier fünf sechs sieben acht neun zehn"), 8)
        XCTAssertEqual(SoundSynth.syllables("🌟 !"), 2)
    }

    func testPitchPerFigureAndStage() {
        let a = SoundSynth.voicePitch("lumo", nil)
        XCTAssertTrue((280...460).contains(a))
        XCTAssertEqual(SoundSynth.voicePitch("lumo", .baby), a * 1.35)
        // gleiche Werte wie Kotlin (Hash über die Figuren-ID)
        XCTAssertEqual(a, SoundSynth.voicePitch("lumo", .adult))
        let ids = ["lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki"]
        XCTAssertGreaterThanOrEqual(Set(ids.map { SoundSynth.voicePitch($0, nil) }).count, 5)
    }

    func testSoundsAreAudibleAndClipped() {
        for s in Sfx.allCases {
            let b = SoundSynth.sfx(s, figureID: "lumo")
            XCTAssertTrue(!b.isEmpty && b.count < SoundSynth.rate * 2, s.rawValue)
            let peak = b.map(abs).max() ?? 0
            XCTAssertTrue((0.1...0.95).contains(peak), "\(s.rawValue) \(peak)")
        }
        let v = SoundSynth.babble("Hallo! Wie geht es dir?", figureID: "kasto", stage: .child)
        XCTAssertTrue((SoundSynth.rate / 2...SoundSynth.rate * 2).contains(v.count))
        XCTAssertTrue((0.1...0.95).contains(v.map(abs).max() ?? 0))
        XCTAssertEqual(v, SoundSynth.babble("Hallo! Wie geht es dir?", figureID: "kasto", stage: .child))
        let w = SoundSynth.wav(v)
        XCTAssertEqual(w.count, 44 + v.count * 2)
    }
}
