// SoundSynth.swift: Geräusche und Stimme des Wesens, zur Laufzeit erzeugt (keine Tondateien).
// Gegenstück: SoundSynth.kt (gleiche Formeln). Abspielen: SoundPlayer.swift.
//
// Stimme: Brabbeln wie bei Tierfiguren im Spiel – Silben aus Grundton + Obertönen mit leichtem Vibrato. Tonhöhe je
// Figur und Wachstumsstufe (Baby höher); mehr Wörter = mehr Silben, Fragezeichen = Ton geht am Ende hoch.

import Foundation

enum Sfx: String, CaseIterable { case tap, giggle, yawn, crack, hatch, stageUp, stageDown, achievement, pop, oops, gameEnd, wardrobe }

enum SoundSynth {
    static let rate = 22050

    /// Grundton der Stimme (Hz): je Figur eigen (280–460), Baby/Kind höher.
    static func voicePitch(_ figureID: String, _ stage: GrowthStage?) -> Float {
        var h = 0
        for ch in figureID.unicodeScalars { h = (h &* 31 &+ Int(ch.value)) & 0x7FFFFFFF }
        let base = 280 + Float(h % 19) * 10
        let f: Float
        switch stage { case .egg?, .baby?: f = 1.35; case .child?: f = 1.2; case .teen?: f = 1.08; default: f = 1 }
        return base * f
    }

    /// Anzahl Silben für einen Satz: etwa eine pro Wort, 2–8.
    static func syllables(_ text: String) -> Int {
        let words = text.split(whereSeparator: { $0 == " " || $0 == "\n" }).filter { $0.contains { $0.isLetter } }.count
        return min(8, max(2, words))
    }

    static func babble(_ text: String, figureID: String, stage: GrowthStage?) -> [Float] {
        let n = syllables(text)
        let p0 = voicePitch(figureID, stage)
        let question = text.trimmingCharacters(in: .whitespacesAndNewlines).hasSuffix("?")
        let excited = text.contains("!")
        var seed = 7
        for ch in text.utf16 { seed = (seed &* 131 &+ Int(ch)) & 0x7FFFFFFF }
        var rnd = GameRandom(Int64(seed))
        let syl: Float = 0.11, gap: Float = 0.035
        var out = [Float](repeating: 0, count: Int((syl + gap) * Float(n) * Float(rate)) + rate / 20)
        for k in 0..<n {
            let last = k == n - 1
            var p = p0 * (0.9 + 0.25 * rnd.next())
            if excited { p *= 1.08 }
            if last && question { p *= 1.25 }
            let end = last && question ? p * 1.18 : last ? p * 0.88 : p * (0.95 + 0.1 * rnd.next())
            let len = syl * (last ? 1.4 : 0.8 + 0.4 * rnd.next())
            let vowel = rnd.next()
            tone(&out, Int((syl + gap) * Float(k) * Float(rate)), len, p, end, 0.5, harmonics: 0.35 + 0.4 * vowel, vibrato: 0.03)
        }
        return out
    }

    static func sfx(_ s: Sfx, figureID: String = "", stage: GrowthStage? = nil) -> [Float] {
        let p = figureID.isEmpty ? 360 : voicePitch(figureID, stage)
        let R = Float(rate)
        var b: [Float]
        switch s {
        case .tap: b = buf(0.12); tone(&b, 0, 0.1, p * 1.6, p * 2.1, 0.45, harmonics: 0.2)
        case .giggle: b = buf(0.5); for k in 0..<5 { let q = Float(k) * 0.08; tone(&b, Int(Float(k) * 0.09 * R), 0.07, p * (1.7 - q), p * (1.9 - q), 0.4, harmonics: 0.4) }
        case .yawn: b = buf(1.0); tone(&b, 0, 0.95, p * 1.15, p * 0.6, 0.4, harmonics: 0.55, vibrato: 0.02, attack: 0.12)
        case .crack: b = buf(0.12); noise(&b, 0, 0.09, 0.5, 77); tone(&b, 0, 0.05, 900, 500, 0.2)
        case .hatch: b = buf(1.1); noise(&b, 0, 0.12, 0.5, 3); arp(&b, Int(0.15 * R), [523, 659, 784, 1047], 0.13, 0.4)
        case .stageUp: b = buf(1.2); arp(&b, 0, [392, 523, 659, 784, 1047], 0.14, 0.4)
        case .stageDown: b = buf(0.9); arp(&b, 0, [523, 440, 349], 0.22, 0.35)
        case .achievement: b = buf(0.8); tone(&b, 0, 0.35, 880, 880, 0.35, harmonics: 0.15); tone(&b, Int(0.16 * R), 0.55, 1319, 1319, 0.35, harmonics: 0.15)
        case .pop: b = buf(0.1); tone(&b, 0, 0.07, 700, 1500, 0.45, harmonics: 0.1)
        case .oops: b = buf(0.3); tone(&b, 0, 0.26, 300, 170, 0.4, harmonics: 0.6)
        case .gameEnd: b = buf(1.0); arp(&b, 0, [523, 659, 784], 0.12, 0.4, hold: 0.5)
        case .wardrobe: b = buf(0.45); tone(&b, 0, 0.2, 1047, 1047, 0.3, harmonics: 0.1); tone(&b, Int(0.1 * R), 0.3, 1568, 1568, 0.28, harmonics: 0.1)
        }
        return b
    }

    private static func buf(_ sec: Float) -> [Float] { [Float](repeating: 0, count: Int(sec * Float(rate))) }

    private static func tone(_ out: inout [Float], _ start: Int, _ sec: Float, _ f0: Float, _ f1: Float, _ amp: Float,
                             harmonics: Float = 0.3, vibrato: Float = 0, attack: Float = 0.01) {
        let n = Int(sec * Float(rate))
        var ph = 0.0
        for i in 0..<n {
            let j = start + i; if j >= out.count { break }
            let u = Float(i) / Float(n)
            let f = f0 + (f1 - f0) * u
            let vib = 1 + vibrato * Float(sin(2 * Double.pi * 6 * Double(i) / Double(rate)))
            ph += 2 * Double.pi * Double(f * vib) / Double(rate)
            let env = min(1, (Float(i) / Float(rate)) / attack) * pow(1 - u, 1.6)
            let s = sin(ph) + Double(harmonics) * 0.5 * sin(2 * ph) + Double(harmonics) * 0.25 * sin(3 * ph)
            out[j] += Float(Double(amp * env) * s / (1 + 0.75 * Double(harmonics)))
        }
        clip(&out)
    }

    private static func arp(_ out: inout [Float], _ start: Int, _ notes: [Float], _ step: Float, _ amp: Float, hold: Float = 0.3) {
        for (k, f) in notes.enumerated() {
            let len = k == notes.count - 1 ? hold + step : step * 1.6
            tone(&out, start + Int(Float(k) * step * Float(rate)), len, f, f, amp, harmonics: 0.2)
        }
    }

    private static func noise(_ out: inout [Float], _ start: Int, _ sec: Float, _ amp: Float, _ seed: Int64) {
        var r = GameRandom(seed); let n = Int(sec * Float(rate))
        for i in 0..<n {
            let j = start + i; if j >= out.count { break }
            out[j] += amp * (r.next() * 2 - 1) * exp(-6 * Float(i) / Float(n))
        }
        clip(&out)
    }

    private static func clip(_ out: inout [Float]) { for i in out.indices where abs(out[i]) > 0.95 { out[i] = out[i] > 0 ? 0.95 : -0.95 } }

    /// WAV-Datei (16 Bit, mono) für AVAudioPlayer.
    static func wav(_ samples: [Float]) -> Data {
        var d = Data()
        func u32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
        func u16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
        let bytes = UInt32(samples.count * 2)
        d.append(contentsOf: Array("RIFF".utf8)); u32(36 + bytes); d.append(contentsOf: Array("WAVE".utf8))
        d.append(contentsOf: Array("fmt ".utf8)); u32(16); u16(1); u16(1); u32(UInt32(rate)); u32(UInt32(rate * 2)); u16(2); u16(16)
        d.append(contentsOf: Array("data".utf8)); u32(bytes)
        for s in samples { u16(UInt16(bitPattern: Int16(max(-1, min(1, s)) * 32767))) }
        return d
    }
}
