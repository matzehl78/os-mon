// Achievements.swift: Sammelalbum mit Erfolgen. Gegenstück: Achievements.kt (gleiche Liste, gleiche Tests).
//
// Jeder Erfolg hängt an einer Kennzahl (Metric) und einer Zielzahl. Kennzahlen sammelt die App (AchievementStore):
// Zähler wie erledigte Aufgaben und Höchstwerte wie die längste Serie. Freigeschaltet bleibt freigeschaltet.

import Foundation

enum Metric: String, Codable, CaseIterable { case stage, tasks, streak, kind, goodnight, festivals, scenes, comeback, days, games, goodDays }

struct Achievement: Identifiable, Equatable {
    let id, emoji, title, hint: String
    let metric: Metric
    let target: Int
}

enum Achievements {
    static let all: [Achievement] = [
        Achievement(id: "hatched", emoji: "🐣", title: "Hallo Welt!", hint: "Lass dein Ei schlüpfen.", metric: .stage, target: 1),
        Achievement(id: "child", emoji: "🎒", title: "Schulkind", hint: "Dein Wesen wird ein Kind.", metric: .stage, target: 2),
        Achievement(id: "teen", emoji: "🛹", title: "Fast groß", hint: "Dein Wesen wird jugendlich.", metric: .stage, target: 3),
        Achievement(id: "adult", emoji: "🏆", title: "Erwachsen", hint: "Pflege dein Wesen, bis es erwachsen ist.", metric: .stage, target: 4),
        Achievement(id: "task1", emoji: "✅", title: "Erster Haken", hint: "Erledige deine erste Aufgabe.", metric: .tasks, target: 1),
        Achievement(id: "task10", emoji: "📋", title: "Fleißig", hint: "Erledige 10 Aufgaben.", metric: .tasks, target: 10),
        Achievement(id: "task50", emoji: "🌟", title: "Aufgaben-Profi", hint: "Erledige 50 Aufgaben.", metric: .tasks, target: 50),
        Achievement(id: "task200", emoji: "🚀", title: "Unaufhaltsam", hint: "Erledige 200 Aufgaben.", metric: .tasks, target: 200),
        Achievement(id: "streak3", emoji: "🔥", title: "Drei am Stück", hint: "Schaffe 3 Tage hintereinander alle Aufgaben.", metric: .streak, target: 3),
        Achievement(id: "streak7", emoji: "🗓️", title: "Eine Woche stark", hint: "Schaffe 7 Tage hintereinander alle Aufgaben.", metric: .streak, target: 7),
        Achievement(id: "streak30", emoji: "🏅", title: "Monats-Held", hint: "Schaffe 30 Tage hintereinander alle Aufgaben.", metric: .streak, target: 30),
        Achievement(id: "kind1", emoji: "💛", title: "Gute Tat", hint: "Probiere einen Miteinander-Tipp aus.", metric: .kind, target: 1),
        Achievement(id: "kind10", emoji: "💖", title: "Herzensmensch", hint: "Probiere 10 Miteinander-Tipps aus.", metric: .kind, target: 10),
        Achievement(id: "kind30", emoji: "😇", title: "Engel", hint: "Probiere 30 Miteinander-Tipps aus.", metric: .kind, target: 30),
        Achievement(id: "night1", emoji: "🌙", title: "Gute Nacht", hint: "Sag deinem Wesen zum ersten Mal Gute Nacht.", metric: .goodnight, target: 1),
        Achievement(id: "night7", emoji: "😴", title: "Schlafprofi", hint: "Sag 7-mal Gute Nacht.", metric: .goodnight, target: 7),
        Achievement(id: "night30", emoji: "✨", title: "Traumfänger", hint: "Sag 30-mal Gute Nacht.", metric: .goodnight, target: 30),
        Achievement(id: "fest1", emoji: "🎉", title: "Erstes Fest", hint: "Feiere ein Fest mit deinem Wesen.", metric: .festivals, target: 1),
        Achievement(id: "fest5", emoji: "🎊", title: "Feierbiest", hint: "Feiere 5 verschiedene Feste.", metric: .festivals, target: 5),
        Achievement(id: "scene3", emoji: "🗺️", title: "Entdecker", hint: "Schalte 3 Hintergründe frei.", metric: .scenes, target: 3),
        Achievement(id: "scene10", emoji: "🌍", title: "Weltreisender", hint: "Schalte alle 10 Hintergründe frei.", metric: .scenes, target: 10),
        Achievement(id: "comeback", emoji: "🩹", title: "Wieder aufgepäppelt", hint: "Lass dein Wesen nach einer Pause wieder eine Stufe wachsen.", metric: .comeback, target: 1),
        Achievement(id: "days100", emoji: "💯", title: "100 Tage zusammen", hint: "Seid 100 Tage zusammen.", metric: .days, target: 100),
        Achievement(id: "game1", emoji: "🎮", title: "Spielkind", hint: "Spiel ein Mini-Spiel mit deinem Wesen.", metric: .games, target: 1),
        Achievement(id: "game20", emoji: "🕹️", title: "Spiele-Fan", hint: "Spiel 20 Mini-Spiele.", metric: .games, target: 20)
    ]

    static func byID(_ id: String) -> Achievement? { all.first { $0.id == id } }

    static func isReached(_ a: Achievement, _ m: [Metric: Int]) -> Bool { (m[a.metric] ?? 0) >= a.target }

    static func progress(_ a: Achievement, _ m: [Metric: Int]) -> Double { min(1, max(0, Double(m[a.metric] ?? 0) / Double(a.target))) }

    static func newlyUnlocked(_ m: [Metric: Int], unlocked: Set<String>) -> [Achievement] {
        all.filter { !unlocked.contains($0.id) && isReached($0, m) }
    }

    /// Höchstwerte bleiben erhalten (nichts geht verloren, wenn z. B. eine Serie reißt).
    static func merge(_ old: [Metric: Int], _ now: [Metric: Int]) -> [Metric: Int] {
        var out: [Metric: Int] = [:]
        for k in Metric.allCases { let v = max(old[k] ?? 0, now[k] ?? 0); if v > 0 { out[k] = v } }
        return out
    }
}
