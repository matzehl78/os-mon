// NightRoutine.swift: Abend- und Morgenablauf (rein). Gegenstück: NightRoutine.kt, gleiche Tests.
//
// Phasen (Minuten seit Mitternacht, lokale Zeit): windDown 30 Min vor der Schlafenszeit, night bis zur Aufstehzeit,
// morning 3 Std. ab Aufstehzeit, sonst day. Eine Nacht gehört zum Morgen danach („Nacht-Tag“).

import Foundation

enum DayPhase: String { case day, windDown, night, morning }

struct SleepConfig: Equatable {
    var enabled = true
    var bedMin = 22 * 60
    var wakeMin = 7 * 60
}

enum NightRating: String { case great, ok, restless }

struct MorningReport: Equatable {
    let rating: NightRating
    let detail: String
    let saidGoodnight: Bool
}

enum NightRoutine {
    static let windDownMin = 30
    static let morningMin = 180
    private static let dayMin = 24 * 60

    private static func norm(_ m: Int) -> Int { ((m % dayMin) + dayMin) % dayMin }

    /// true, wenn m im Fenster [start, end) liegt (Fenster darf über Mitternacht gehen).
    static func inWindow(_ m: Int, _ start: Int, _ end: Int) -> Bool {
        let a = norm(start), b = norm(end), x = norm(m)
        if a == b { return false }
        return a < b ? (x >= a && x < b) : (x >= a || x < b)
    }

    static func phase(_ minuteOfDay: Int, _ c: SleepConfig) -> DayPhase {
        if !c.enabled { return .day }
        if inWindow(minuteOfDay, c.bedMin, c.wakeMin) { return .night }
        if inWindow(minuteOfDay, c.bedMin - windDownMin, c.bedMin) { return .windDown }
        if inWindow(minuteOfDay, c.wakeMin, c.wakeMin + morningMin) { return .morning }
        return .day
    }

    /// Nacht-Tag: der Tag des Morgens, zu dem dieser Zeitpunkt (Abend/Nacht) gehört.
    static func nightDay(_ epochDay: Int, _ minuteOfDay: Int, _ c: SleepConfig) -> Int {
        let evening = c.bedMin > c.wakeMin && norm(minuteOfDay) >= norm(c.bedMin - windDownMin)
        return evening ? epochDay + 1 : epochDay
    }

    static func rating(nightMinutes: Float?, saidGoodnight: Bool, nightOpens: Int) -> NightRating {
        var score = (nightMinutes ?? 0) + 5 * Float(nightOpens)
        if !saidGoodnight { score += 10 }
        return score <= 10 ? .great : score <= 30 ? .ok : .restless
    }

    static func morningReport(nightMinutes: Float?, saidGoodnight: Bool, nightOpens: Int) -> MorningReport {
        let detail: String
        if let m = nightMinutes, m < 1 { detail = "Nach Mitternacht kein Blick aufs Handy." }
        else if let m = nightMinutes { detail = "Nach Mitternacht \(Int(m.rounded())) Min am Handy." }
        else if nightOpens == 0 && saidGoodnight { detail = "Nach dem Gute-Nacht-Sagen blieb die App zu." }
        else if nightOpens > 0 { detail = "Die App wurde nachts \(nightOpens)× geöffnet." }
        else { detail = "Gestern gab es kein Gute-Nacht-Sagen." }
        return MorningReport(rating: rating(nightMinutes: nightMinutes, saidGoodnight: saidGoodnight, nightOpens: nightOpens),
                             detail: detail, saidGoodnight: saidGoodnight)
    }

    /// Feste Auswahl pro Tag, salt trennt verschiedene Listen.
    static func pickIndex(_ size: Int, _ epochDay: Int, salt: Int = 0) -> Int {
        guard size > 0 else { return 0 }
        return ((epochDay * 31 + salt) % size + size) % size
    }

    static func clock(_ min: Int) -> String { String(format: "%02d:%02d", norm(min) / 60, norm(min) % 60) }
}
