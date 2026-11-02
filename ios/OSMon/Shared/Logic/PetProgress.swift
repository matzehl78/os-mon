// PetProgress.swift: Wochenrückblick und freischaltbare Hintergründe (Gegenstück zu PetProgress.kt)
//
// Targets: App und Test-Target. Rein, ohne Plattform-Zugriffe.
// Belohnt wird gesundes Verhalten (weniger Bildschirm, Bewegung, Pausen, Zuwendung), nie mehr Nutzung.
// Tage: epochDay = Tage seit 1970-01-01 (lokales Datum); 1970-01-01 war ein Donnerstag.

import Foundation

/// Ein abgeschlossener Tag. nil = keine Daten (Sensor aus oder nicht erlaubt).
struct DayStat: Equatable {
    var day: Int
    var avgWellbeing: Float? = nil
    var screenMin: Float? = nil
    var steps: Int? = nil
    var pats: Int = 0
    var focus: Int = 0
}

struct WeeklyReview: Equatable {
    let weekStart: Int            // Montag der ausgewerteten Woche
    let daysWithData: Int
    let avgWellbeing: Float?
    let happyDays: Int            // Tage mit Mittel >= 65 (mindestens „Zufrieden“)
    let screenPerDay: Float?
    let screenPerDayPrev: Float?
    let steps: Int
    let bestStepsDay: Int
    let focus: Int
    let pats: Int
    let headline: String
    let lines: [String]
}

struct SceneUnlock: Equatable {
    let sceneID: String
    let unlocked: Bool
    let progress: Float
    let rule: String
}

enum PetProgress {
    static let happy: Float = 65

    /// Montag der Woche, in der `day` liegt.
    static func weekStart(_ day: Int) -> Int {
        let m = ((day + 3) % 7 + 7) % 7
        return day - m
    }

    /// Rückblick auf die letzte abgeschlossene Woche (Mo–So vor der aktuellen). nil bei weniger als 3 Tagen mit Daten.
    static func weeklyReview(_ days: [DayStat], today: Int) -> WeeklyReview? {
        let ws = weekStart(today) - 7
        let week = days.filter { (ws...(ws + 6)).contains($0.day) }
        let prev = days.filter { ((ws - 7)...(ws - 1)).contains($0.day) }
        let withData = week.filter { $0.avgWellbeing != nil || $0.screenMin != nil || $0.steps != nil }.count
        guard withData >= 3 else { return nil }

        let hs = week.compactMap(\.avgWellbeing)
        let avgH: Float? = hs.isEmpty ? nil : hs.reduce(0, +) / Float(hs.count)
        let happyDays = hs.filter { $0 >= happy }.count
        func perDay(_ l: [DayStat]) -> Float? {
            let m = l.compactMap(\.screenMin)
            return m.count < 4 ? nil : m.reduce(0, +) / Float(m.count)
        }
        let sNow = perDay(week), sPrev = perDay(prev)
        let steps = week.reduce(0) { $0 + ($1.steps ?? 0) }
        let best = week.map { $0.steps ?? 0 }.max() ?? 0
        let focus = week.reduce(0) { $0 + $1.focus }
        let pats = week.reduce(0) { $0 + $1.pats }

        var lines: [String] = []
        let drop: Float? = (sNow != nil && sPrev != nil) ? sPrev! - sNow! : nil
        if let drop {
            let d = Int(abs(drop).rounded())
            lines.append(drop >= 0 ? "Bildschirm: \(d) Min weniger pro Tag als in der Vorwoche"
                                   : "Bildschirm: \(d) Min mehr pro Tag als in der Vorwoche")
        } else if let sNow {
            lines.append("Bildschirm: im Schnitt \(Int(sNow.rounded())) Min pro Tag")
        }
        if !hs.isEmpty { lines.append("Zufrieden an \(happyDays) von \(hs.count) Tagen") }
        if steps > 0 { lines.append("\(fmt(steps)) Schritte, bester Tag \(fmt(best))") }
        if focus > 0 { lines.append("\(focus) Fokus-Session\(focus == 1 ? "" : "s") geschafft") }
        if pats > 0 { lines.append("\(pats)× gestreichelt") }

        let headline: String
        if let drop, drop >= 15 { headline = "\(Int(drop.rounded())) Min weniger Bildschirm pro Tag!" }
        else if happyDays >= 5 { headline = "\(happyDays) Tage zufrieden – starke Woche!" }
        else if steps >= 35_000 { headline = "\(fmt(steps)) Schritte diese Woche!" }
        else if focus >= 3 { headline = "\(focus) Fokus-Sessions – sehr konzentriert!" }
        else { headline = "Deine Woche mit deinem Wesen" }

        return WeeklyReview(weekStart: ws, daysWithData: withData, avgWellbeing: avgH, happyDays: happyDays,
                            screenPerDay: sNow, screenPerDayPrev: sPrev, steps: steps, bestStepsDay: best,
                            focus: focus, pats: pats, headline: headline, lines: lines)
    }

    // MARK: Freischaltbare Hintergründe

    static let free: Set<String> = ["none", "neonstadt", "zimmer", "garteninsel"]

    /// Stand aller Hintergründe. `already` = früher freigeschaltet (bleibt frei).
    static func unlocks(_ days: [DayStat], age: Int, evolved: Bool, screenGoal: Float, already: Set<String> = []) -> [SceneUnlock] {
        let d = days.sorted { $0.day < $1.day }
        let steps7 = rollingMax(d, 7) { Float($0.steps ?? 0) }
        let happyRun = longestRun(d) { ($0.avgWellbeing ?? -1) >= happy }
        let screenRun = longestRun(d) { $0.screenMin.map { $0 <= screenGoal } ?? false }
        let focus = d.reduce(0) { $0 + $1.focus }
        let pats = d.reduce(0) { $0 + $1.pats }
        func u(_ id: String, _ p: Float, _ rule: String) -> SceneUnlock {
            let has = already.contains(id)
            return SceneUnlock(sceneID: id, unlocked: free.contains(id) || has || p >= 1, progress: has ? 1 : min(max(p, 0), 1), rule: rule)
        }
        return [
            u("none", 1, ""), u("neonstadt", 1, ""), u("zimmer", 1, ""), u("garteninsel", 1, ""),
            u("raumstation", steps7 / 50_000, "50.000 Schritte in 7 Tagen"),
            u("zauberwald", Float(happyRun) / 5, "5 Tage am Stück zufrieden"),
            u("unterwasser", Float(focus) / 3, "3 Fokus-Sessions abschließen"),
            u("suess", Float(pats) / 10, "10× streicheln"),
            u("mondbasis", Float(age) / 14, "14 Tage zusammen"),
            u("zen", Float(screenRun) / 7, "7 Tage am Stück unter dem Bildschirmziel"),
            u("studio", evolved ? 1 : 0, "Erste Entwicklung erleben"),
        ]
    }

    /// Größte Summe über `n` aufeinanderfolgende Kalendertage (fehlende Tage zählen 0).
    private static func rollingMax(_ d: [DayStat], _ n: Int, _ v: (DayStat) -> Float) -> Float {
        guard let first = d.first?.day, let last = d.last?.day else { return 0 }
        let byDay = Dictionary(d.map { ($0.day, $0) }, uniquingKeysWith: { a, _ in a })
        var best: Float = 0
        for start in first...last {
            var s: Float = 0
            for k in 0..<n { if let x = byDay[start + k] { s += v(x) } }
            best = max(best, s)
        }
        return best
    }

    /// Längste Folge aufeinanderfolgender Kalendertage, an denen `ok` gilt.
    private static func longestRun(_ d: [DayStat], _ ok: (DayStat) -> Bool) -> Int {
        var best = 0, run = 0
        var last: Int? = nil
        for x in d {
            if ok(x) { run = (last != nil && x.day == last! + 1 && run > 0) ? run + 1 : 1 } else { run = 0 }
            last = x.day
            best = max(best, run)
        }
        return best
    }

    private static func fmt(_ n: Int) -> String {
        let f = NumberFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.numberStyle = .decimal
        f.groupingSeparator = "."
        f.usesGroupingSeparator = true
        return f.string(from: NSNumber(value: n)) ?? "\(n)"
    }
}
