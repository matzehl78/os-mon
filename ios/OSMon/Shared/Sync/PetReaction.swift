// PetReaction.swift: Reaktionen beim Antippen des Wesens (Gegenstück zu PetReaction.kt)
//
// Targets: App und Widget-Extension (PetFigure.swift nutzt ReactionKind und ReactionMotion).
// 1:1 aus dem Prototyp (REACTIONS, pickReaction, OSLimbs, Körperbewegung). Antippen ist reiner Spaß
// und ändert keine Werte; für Zuneigung bleibt „Streicheln“ zuständig.

import Foundation
import CoreGraphics

enum ReactionKind: String, CaseIterable {
    case giggle, spin, surprise, dizzy, sleepy, weak, startle, morning, noon, evening, night, shy

    var duration: TimeInterval {
        switch self {
        case .giggle, .spin: return 1.1
        case .surprise, .weak, .startle: return 0.9
        case .dizzy: return 1.5
        case .sleepy, .night: return 1.4
        case .morning, .evening: return 1.6
        case .noon: return 1.0
        case .shy: return 1.8
        }
    }
    var fx: [String] {
        switch self {
        case .giggle: return ["💗", "💖", "✨"]
        case .spin: return ["⭐", "✨", "🌟"]
        case .surprise: return ["❗"]
        case .dizzy: return ["💫", "💫"]
        case .sleepy: return ["💤"]
        case .weak: return ["🤒"]
        case .startle: return ["❓"]
        case .morning: return ["☀️", "✨"]
        case .noon: return ["🍎", "☕"]
        case .evening: return ["🌙", "✨"]
        case .night: return ["🌙", "💤"]
        case .shy: return ["💕", "🌸"]
        }
    }
    var say: [String] {
        switch self {
        case .giggle: return ["Hihi, das kitzelt!", "Hehe, nochmal!", "Hihi!"]
        case .spin: return ["Juhuu!", "Wiiie!"]
        case .surprise: return ["Oh! Hallo du!", "Huch!"]
        case .dizzy: return ["Mir ist ganz schwindelig …", "Nicht so viel tippen!"]
        case .sleepy: return ["Gähn … noch fünf Minuten.", "Ich bin so müde …"]
        case .weak: return ["Mir geht’s nicht so gut …", "Danke, dass du da bist."]
        case .startle: return ["Hm? Ich schlaf doch …", "Zzz … was ist?"]
        case .morning: return ["Guten Morgen! Aaah, gut geschlafen.", "Morgen! Bereit für heute?"]
        case .noon: return ["Mittagspause? Ich bin dabei!", "Zeit für eine kleine Pause?"]
        case .evening: return ["Schöner Abend, oder?", "Na, wie war dein Tag?"]
        case .night: return ["Psst … schon spät. Leg mich doch hin?", "Gähn … wollen wir schlafen gehen?"]
        case .shy: return ["Oh … du machst mich ganz verlegen.", "Hihi … danke!", "Ach, du …"]
        }
    }
    /// Fröhliches Gesicht (Augen ^ ^)
    var happy: Bool { [.giggle, .spin, .morning, .noon, .evening].contains(self) }
    /// Hüpfen: Schatten wird kurz kleiner
    var hops: Bool { [.giggle, .spin, .surprise, .noon].contains(self) }
}

/// Laufende Reaktion.
struct ActiveReaction: Equatable {
    let kind: ReactionKind
    let start: Date
    let say: String
    func progress(_ now: Date) -> Double { now.timeIntervalSince(start) / kind.duration }
    func isActive(_ now: Date) -> Bool { (0..<1).contains(progress(now)) }
    func saying(_ now: Date) -> Bool { now.timeIntervalSince(start) < 2.6 }
}

/// Wählt die Reaktion; merkt sich die letzten Tipps und den letzten Tageszeit-Gruß.
final class ReactionPicker {
    private var taps: [Date] = []
    private var lastGreet = ""

    func pick(mood: PetMood, overlays: Set<PetOverlay>, now: Date = .now) -> ReactionKind {
        taps = taps.filter { now.timeIntervalSince($0) < 3 } + [now]
        if taps.count >= 5 { taps = []; return .dizzy }
        if mood == .hibernation { return .startle }
        if mood == .sick { return .weak }
        let c = Calendar.current.dateComponents([.hour, .minute], from: now)
        let h = Double(c.hour ?? 12) + Double(c.minute ?? 0) / 60
        if let per = Self.period(h) {
            let key = "\(EpochDay.of(now))-\(per.rawValue)"
            if key != lastGreet || Double.random(in: 0..<1) < 0.3 { lastGreet = key; return per }
        }
        if mood == .tired || overlays.contains(.yawning) { return .sleepy }
        let r = Double.random(in: 0..<1)
        return r < 0.45 ? .giggle : r < 0.7 ? .spin : r < 0.86 ? .surprise : .shy
    }

    func start(_ kind: ReactionKind, now: Date = .now) -> ActiveReaction {
        ActiveReaction(kind: kind, start: now, say: kind.say.randomElement() ?? "")
    }

    static func period(_ h: Double) -> ReactionKind? {
        if h >= 6 && h < 10 { return .morning }
        if h >= 11 && h < 14 { return .noon }
        if h >= 18 && h < 22 { return .evening }
        if h >= 22 || h < 6 { return .night }
        return nil
    }
}

/// l / r: Arm nach außen-oben in Grad (negativ = nach innen); fl / fr: Fuß anheben (Zeichen-Einheiten).
struct Limbs { var l: CGFloat, r: CGFloat, fl: CGFloat, fr: CGFloat }
/// Körperbewegung in Zeichen-Einheiten, Drehpunkt (110, 210) = Mitte unten.
struct BodyMotion { var dx: CGFloat = 0, dy: CGFloat = 0, rot: CGFloat = 0, sx: CGFloat = 1, sy: CGFloat = 1 }

enum ReactionMotion {
    private static let K: CGFloat = 263.0 / 240.0      // Prototyp-Pixel (240er Bühne) -> Zeichen-Einheiten

    static func limbs(_ k: ReactionKind, _ u: CGFloat) -> Limbs {
        let env = sin(.pi * u)
        let quick = u < 0.2 ? u / 0.2 : (1 - u) / 0.8
        let hold = min(1, u * 4, (1 - u) * 4)
        let alt = sin(u * .pi * 4)
        switch k {
        case .giggle: let f = env * (40 + 25 * sin(u * .pi * 6)); return Limbs(l: f, r: f, fl: 6 * max(0, alt), fr: 6 * max(0, -alt))
        case .spin: return Limbs(l: 85 * env, r: 85 * env, fl: 4 * env, fr: 4 * env)
        case .surprise: return Limbs(l: 140 * quick, r: 140 * quick, fl: 3 * quick, fr: 3 * quick)
        case .dizzy: return Limbs(l: (20 + 25 * sin(u * .pi * 5)) * (1 - u), r: (20 + 25 * sin(u * .pi * 5 + .pi)) * (1 - u),
                                  fl: 3 * max(0, alt) * (1 - u), fr: 3 * max(0, -alt) * (1 - u))
        case .sleepy, .night: return Limbs(l: 10 * env, r: 135 * hold, fl: 0, fr: 0)
        case .morning: return Limbs(l: 165 * env, r: 165 * env, fl: 3 * env, fr: 3 * env)
        case .noon: return Limbs(l: 30 * env + 15 * alt * env, r: 30 * env - 15 * alt * env, fl: 5 * max(0, alt), fr: 5 * max(0, -alt))
        case .evening: return Limbs(l: 10 * env, r: 140 * hold + 20 * sin(u * .pi * 8) * hold, fl: 0, fr: 0)
        case .weak: return Limbs(l: -8 * env, r: -8 * env, fl: 0, fr: 0)
        case .startle: return Limbs(l: 45 * quick, r: 45 * quick, fl: 0, fr: 0)
        case .shy: return Limbs(l: -14 * hold, r: -14 * hold, fl: 3 * max(0, alt) * hold, fr: 3 * max(0, -alt) * hold)   // Arme an den Körper, Füße scharren
        }
    }

    static func body(_ k: ReactionKind, _ u: CGFloat) -> BodyMotion {
        let ease = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
        switch k {
        case .giggle: return BodyMotion(dy: -abs(sin(2 * .pi * u)) * (1 - u / 2) * 26 * K, sx: 1 + 0.05 * sin(4 * .pi * u), sy: 1 - 0.05 * sin(4 * .pi * u))
        case .surprise: return BodyMotion(dy: -sin(.pi * u) * 34 * K)
        case .spin: return BodyMotion(dy: -sin(.pi * u) * 22 * K, sx: cos(2 * .pi * ease))
        case .dizzy: return BodyMotion(rot: 12 * sin(6 * .pi * u) * (1 - u))
        case .sleepy, .night: return BodyMotion(sx: 1 + 0.04 * sin(.pi * u), sy: 1 - 0.07 * sin(.pi * u))
        case .morning: return BodyMotion(sx: 1 - 0.05 * sin(.pi * u), sy: 1 + 0.12 * sin(.pi * u))
        case .evening: return BodyMotion(rot: 7 * sin(2 * .pi * u))
        case .noon: return BodyMotion(dy: -abs(sin(2 * .pi * u)) * 14 * K)
        case .weak, .startle: return BodyMotion(dx: 3 * K * sin(16 * .pi * u) * (1 - u))
        case .shy: return BodyMotion(rot: 6 * sin(3 * .pi * u) * min(1, u * 4, (1 - u) * 4))   // schaukelt verlegen hin und her
        }
    }

    /// Leerlauf (gut gelaunt): ab und zu ein Hüpfer mit Stauchen und Strecken (Muster B), strahlend dazwischen
    /// leichtes Wippen (Muster C). Gleiche Kurve wie im 3D-Renderer; t = Sekunden. Gegenstück in PetReaction.kt.
    static func idle(_ t: Double, radiant: Bool) -> BodyMotion {
        let period = radiant ? 6.5 : 9.5
        let hp = CGFloat(t.truncatingRemainder(dividingBy: period) / 0.95)
        guard hp < 1 else { return BodyMotion(dy: radiant ? -2 * abs(CGFloat(sin(t * 2.1))) : 0) }
        let up = hp > 0.18 && hp < 0.78 ? sin((hp - 0.18) / 0.6 * .pi) : 0
        let sq = hp <= 0.18 ? sin(hp / 0.18 * .pi) * 0.11 : hp >= 0.78 ? sin((hp - 0.78) / 0.22 * .pi) * 0.09 : 0
        return BodyMotion(dy: -up * 13, sx: 1 + sq - up * 0.04, sy: 1 - sq + up * 0.06)
    }
}
