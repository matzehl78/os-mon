// DayTasks.swift: Eigene Tagesaufgaben mit Motivation durch das Wesen. Gegenstück: DayTasks.kt (gleiche Regeln).
//
// Aufgaben sind einmalig oder wiederkehrend (täglich, Mo–Fr, Wochenende). Einmalige Aufgaben, die nicht erledigt
// wurden, wandern auf den nächsten Tag („von gestern“). Erledigt wird pro Tag gespeichert (doneDays).
// Tage = epochDay (lokales Datum, 1970-01-01 = 0, Donnerstag).

import Foundation

enum TaskRepeat: String, Codable, CaseIterable {
    case once, daily, weekdays, weekend
}

struct DayTask: Codable, Equatable, Identifiable {
    var id: String
    var title: String
    var emoji: String = "⭐"
    var repeatRule: TaskRepeat = .once
    var createdDay: Int
    var doneDays: Set<Int> = []
    var fromParent: Bool? = nil              // von den Eltern gestellt (Eltern-Bereich): Kind kann sie nicht löschen
    var isFromParent: Bool { fromParent ?? false }
}

enum TaskStage { case empty, start, going, half, all }

struct TaskProgress: Equatable {
    let done: Int
    let total: Int
    var stage: TaskStage {
        if total == 0 { return .empty }
        if done == 0 { return .start }
        if done >= total { return .all }
        if done * 2 >= total { return .half }
        return .going
    }
    var fraction: Double { total == 0 ? 0 : Double(done) / Double(total) }
}

enum DayTasks {
    static let maxTasks = 12
    static let keepDays = 60

    private static func floorMod(_ a: Int, _ n: Int) -> Int { ((a % n) + n) % n }

    /// 0 = Montag … 6 = Sonntag.
    static func weekday(_ day: Int) -> Int { floorMod(day + 3, 7) }

    static func isDue(_ t: DayTask, _ day: Int) -> Bool {
        switch t.repeatRule {
        case .once:
            let doneOn = t.doneDays.min()
            return day >= t.createdDay && (doneOn == nil || day <= doneOn!)
        case .daily: return day >= t.createdDay
        case .weekdays: return day >= t.createdDay && weekday(day) < 5
        case .weekend: return day >= t.createdDay && weekday(day) >= 5
        }
    }

    static func isDone(_ t: DayTask, _ day: Int) -> Bool { t.doneDays.contains(day) }

    static func isCarried(_ t: DayTask, _ day: Int) -> Bool {
        t.repeatRule == .once && t.createdDay < day && !isDone(t, day)
    }

    /// Offene zuerst (übernommene ganz oben), dann erledigte; sonst Reihenfolge wie angelegt (stabil).
    static func forDay(_ all: [DayTask], _ day: Int) -> [DayTask] {
        let due = all.enumerated().filter { isDue($0.element, day) }
        return due.sorted { a, b in
            let ka = (isDone(a.element, day) ? 1 : 0, isCarried(a.element, day) ? 0 : 1, a.offset)
            let kb = (isDone(b.element, day) ? 1 : 0, isCarried(b.element, day) ? 0 : 1, b.offset)
            return ka < kb
        }.map(\.element)
    }

    static func progress(_ all: [DayTask], _ day: Int) -> TaskProgress {
        let due = all.filter { isDue($0, day) }
        return TaskProgress(done: due.filter { isDone($0, day) }.count, total: due.count)
    }

    static func toggle(_ t: DayTask, _ day: Int) -> DayTask {
        var c = t
        if c.doneDays.contains(day) { c.doneDays.remove(day) } else { c.doneDays.insert(day) }
        c.doneDays = c.doneDays.filter { $0 > day - keepDays }
        return c
    }

    /// Serie: Tage bis heute, an denen alles erledigt war (Tage ohne Aufgaben zählen nicht und unterbrechen nicht).
    /// Ist heute noch nicht alles erledigt, zählt die Serie bis gestern.
    static func streak(_ all: [DayTask], today: Int) -> Int {
        guard let first = all.map(\.createdDay).min() else { return 0 }
        var n = 0
        var d = today
        if progress(all, today).stage != .all { d -= 1 }
        while d >= first && d > today - keepDays {
            switch progress(all, d).stage {
            case .all: n += 1
            case .empty: break
            default: return n
            }
            d -= 1
        }
        return n
    }

    static func canAdd(_ all: [DayTask], _ day: Int, title: String) -> Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && all.filter { isDue($0, day) }.count < maxTasks
    }

    static func cleanup(_ all: [DayTask], today: Int) -> [DayTask] {
        all.filter { !($0.repeatRule == .once && ($0.doneDays.max() ?? Int.max) <= today - keepDays) }
    }

    static func pick<T>(_ list: [T], day: Int, salt: Int) -> T? {
        list.isEmpty ? nil : list[floorMod(day * 31 + salt, list.count)]
    }

    static func fill(_ line: String, pet: String, task: String = "") -> String {
        line.replacingOccurrences(of: "{name}", with: pet).replacingOccurrences(of: "{task}", with: task)
    }
}
