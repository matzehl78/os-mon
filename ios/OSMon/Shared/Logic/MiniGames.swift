// MiniGames.swift: Mini-Spiele. Gegenstück: MiniGames.kt (gleiche Regeln, gleicher Zufall, gleiche Tests).
//
// Jedes Spiel dauert 30 Sekunden; pro Tag nur wenige Runden (Standard 2, im Eltern-Bereich 0–3).
//  • Sternenfangen: Sterne leuchten kurz auf, goldene zählen 3.
//  • Seifenblasen: Blasen steigen auf; Gewitterwolken nicht antippen (−1).
//  • Glühwürmchen (Premium): flackern und fliegen Bögen, zählen 2.
// Koordinaten 0..1 (y wächst nach unten). Eigener LCG, damit beide Plattformen dasselbe Spiel erzeugen.

import Foundation

enum MiniGame: String, CaseIterable {
    case stars, bubbles, fireflies
    var emoji: String { switch self { case .stars: return "⭐"; case .bubbles: return "🫧"; case .fireflies: return "✨" } }
    var title: String { switch self { case .stars: return "Sternenfangen"; case .bubbles: return "Seifenblasen"; case .fireflies: return "Glühwürmchen" } }
    var hint: String {
        switch self {
        case .stars: return "Tippe die Sterne an, bevor sie verglühen. Goldene zählen 3!"
        case .bubbles: return "Lass die Blasen platzen – aber nicht die Gewitterwolken!"
        case .fireflies: return "Fang die flackernden Glühwürmchen. Jedes zählt 2."
        }
    }
    var premium: Bool { self == .fireflies }
}

enum TargetKind: String {
    case star, gold, bubble, cloud, fly
    var points: Int { switch self { case .star, .bubble: return 1; case .gold: return 3; case .cloud: return -1; case .fly: return 2 } }
}

struct GameTarget: Identifiable {
    let id: Int, kind: TargetKind
    var x: Float, y: Float
    let vx: Float, vy: Float, r: Float, born: Float, life: Float
    var phase: Float = 0
    func age(_ t: Float) -> Float { t - born }
    func alpha(_ t: Float) -> Float {
        let a = age(t)
        let fade = max(0, min(1, min(a / 0.15, (life - a) / 0.25)))
        return kind == .fly ? fade * (0.55 + 0.45 * (sin(a * 9 + phase) + 1) / 2) : fade
    }
}

struct GameHit { let kind: TargetKind; let x: Float; let y: Float; let points: Int }

/// Kleiner LCG (Numerical Recipes), bitgleich mit GameRandom (Kotlin).
struct GameRandom {
    private var s: UInt64
    init(_ seed: Int64) { s = UInt64(bitPattern: seed) & 0xFFFFFFFF }
    mutating func next() -> Float { s = (s &* 1664525 &+ 1013904223) & 0xFFFFFFFF; return Float(s >> 8) / 16777216 }
    mutating func range(_ a: Float, _ b: Float) -> Float { a + (b - a) * next() }
}

final class MiniGameSim {
    static let duration: Float = 30
    static let maxTargets = 6
    let game: MiniGame
    private var rnd: GameRandom
    private(set) var time: Float = 0
    private(set) var score = 0
    private(set) var hits = 0
    private(set) var targets: [GameTarget] = []
    private var nextSpawn: Float = 0.3
    private var ids = 0

    init(_ game: MiniGame, seed: Int64) { self.game = game; rnd = GameRandom(seed) }

    var done: Bool { time >= Self.duration }
    var left: Float { max(0, Self.duration - time) }

    private func interval() -> Float { 0.95 - 0.45 * min(1, max(0, time / Self.duration)) }

    func step(_ dt: Float) {
        if done { return }
        time = min(Self.duration, time + dt)
        for i in targets.indices { targets[i].x += targets[i].vx * dt; targets[i].y += targets[i].vy * dt }
        targets.removeAll { $0.age(time) >= $0.life || $0.y < -0.15 || $0.x < -0.15 || $0.x > 1.15 }
        while time >= nextSpawn && !done {
            if targets.count < Self.maxTargets { spawn() }
            nextSpawn += interval()
        }
    }

    private func spawn() {
        ids += 1
        let id = ids
        switch game {
        case .stars:
            let gold = rnd.next() < 0.18
            let x = rnd.range(0.12, 0.88), y = rnd.range(0.12, 0.85)
            targets.append(GameTarget(id: id, kind: gold ? .gold : .star, x: x, y: y, vx: 0, vy: 0,
                                      r: gold ? 0.075 : 0.09, born: time, life: gold ? 1.1 : 1.6))
        case .bubbles:
            let cloud = rnd.next() < 0.22
            let x = rnd.range(0.12, 0.88), vx = rnd.range(-0.05, 0.05), vy = -rnd.range(0.22, 0.36)
            let r: Float = cloud ? 0.1 : rnd.range(0.07, 0.1)
            targets.append(GameTarget(id: id, kind: cloud ? .cloud : .bubble, x: x, y: 1.08, vx: vx, vy: vy, r: r, born: time, life: 6))
        case .fireflies:
            let fromLeft = rnd.next() < 0.5
            let y = rnd.range(0.15, 0.8), vx = (fromLeft ? 1 : -1) * rnd.range(0.18, 0.3), vy = rnd.range(-0.06, 0.06)
            let ph = rnd.range(0, Float.pi * 2)
            targets.append(GameTarget(id: id, kind: .fly, x: fromLeft ? -0.05 : 1.05, y: y, vx: vx, vy: vy, r: 0.08, born: time, life: 4.5, phase: ph))
        }
    }

    /// Antippen bei (x, y); aspect = Höhe/Breite der Spielfläche. Gewitterwolken ziehen 1 ab, nie unter 0.
    @discardableResult
    func tap(_ x: Float, _ y: Float, aspect: Float = 1) -> GameHit? {
        if done { return nil }
        let cands = targets.filter { $0.alpha(time) > 0.2 }.map { t -> (GameTarget, Float) in
            let dx = t.x - x, dy = (t.y - y) * aspect
            return (t, (dx * dx + dy * dy).squareRoot())
        }.filter { $0.1 <= $0.0.r * 1.35 }
        guard let hit = cands.min(by: { $0.1 < $1.1 })?.0 else { return nil }
        targets.removeAll { $0.id == hit.id }
        let before = score
        score = max(0, score + hit.kind.points)
        if hit.kind.points > 0 { hits += 1 }
        return GameHit(kind: hit.kind, x: hit.x, y: hit.y, points: score - before)
    }
}

enum MiniGames {
    static let defaultLimit = 2
    static let maxLimit = 3

    static func stars(_ score: Int) -> Int { score >= 24 ? 3 : score >= 15 ? 2 : score >= 8 ? 1 : 0 }

    static func left(lastDay: Int?, playedThatDay: Int, today: Int, limit: Int) -> Int {
        max(0, min(maxLimit, max(0, limit)) - (lastDay == today ? playedThatDay : 0))
    }

    static func afterPlay(lastDay: Int?, playedThatDay: Int, today: Int) -> Int { (lastDay == today ? playedThatDay : 0) + 1 }

    static func available(premium: Bool) -> [MiniGame] { MiniGame.allCases.filter { !$0.premium || premium } }

    static func cheer(_ stars: Int, name: String) -> String {
        switch stars {
        case 3: return "Wahnsinn! Du bist ein Profi! 🌟"
        case 2: return "Super gespielt! \(name) ist begeistert!"
        case 1: return "Schön! Das hat Spaß gemacht!"
        default: return "Danke fürs Spielen! Nächstes Mal klappt’s besser."
        }
    }
}
