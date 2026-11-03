// Festivals.swift: Feste, Geburtstag und Schlüpftag (rein). Gegenstück: Festivals.kt, gleiche Tests und Vorrangregeln.
// Targets: App, Widget, Tests (Shared/Logic).

import Foundation

enum FestivalID: String { case birthday, hatchday, milestone, christmas, silvester, newYear, nikolaus, stMartin, halloween,
                               valentine, carnival, easter, mothersDay, fathersDay, advent }

/// Zubehör, das die Figur trägt (Zeichnung: PetAccessory.swift).
enum Accessory: String { case partyHat, santaHat, bunnyEars, witchHat, crown, flowers, lantern, jester,
    beanie, cap, bow, topHat, wizardHat, pirateHat }   // ab beanie: Kleiderschrank (Wardrobe.swift)

/// Was über dem Hintergrund schwebt.
enum Decor: String { case confetti, snow, hearts, eggs, pumpkins, stars, lights, petals, leaves, butterflies }

struct Festival: Equatable {
    let id: FestivalID
    let title: String
    let greeting: String
    let accessory: Accessory?
    let decor: Decor
    let emoji: String
    var song = false
}

struct Ymd: Equatable { let y: Int, m: Int, d: Int }

enum Festivals {
    static let milestones = [7, 30, 100, 200, 500]

    private static func floorDiv(_ a: Int, _ b: Int) -> Int { Int((Double(a) / Double(b)).rounded(.down)) }
    private static func floorMod(_ a: Int, _ b: Int) -> Int { ((a % b) + b) % b }

    // MARK: Kalender (Algorithmus nach H. Hinnant)

    static func epochDay(_ y: Int, _ m: Int, _ d: Int) -> Int {
        let yy = m <= 2 ? y - 1 : y
        let era = floorDiv(yy, 400)
        let yoe = yy - era * 400
        let doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    static func ymd(_ epochDay: Int) -> Ymd {
        let z = epochDay + 719468
        let era = floorDiv(z, 146097)
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        return Ymd(y: yoe + era * 400 + (m <= 2 ? 1 : 0), m: m, d: d)
    }

    /// 0 = Sonntag … 6 = Samstag.
    static func weekday(_ epochDay: Int) -> Int { floorMod(epochDay + 4, 7) }

    static func easter(_ y: Int) -> Int {
        let a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4
        let f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30
        let i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451
        let month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1
        return epochDay(y, month, day)
    }

    static func firstAdvent(_ y: Int) -> Int {
        let xmas = epochDay(y, 12, 25), wd = weekday(xmas)
        return xmas - (wd == 0 ? 7 : wd) - 21
    }

    static func mothersDay(_ y: Int) -> Int {
        let may1 = epochDay(y, 5, 1)
        return may1 + floorMod(7 - weekday(may1), 7) + 7
    }

    static func adventNumber(_ day: Int, _ first: Int) -> Int { min(4, max(1, (day - first) / 7 + 1)) }

    /// Fest am Tag `day`. birthday = (Monat, Tag) oder nil, hatchDay = Schlüpftag oder nil.
    static func today(_ day: Int, birthday: (m: Int, d: Int)?, hatchDay: Int?, pet: String) -> Festival? {
        let t = ymd(day)
        if let b = birthday, b.m == t.m, b.d == t.d {
            return Festival(id: .birthday, title: "Alles Gute zum Geburtstag!", greeting: "Happy Birthday! Ich hab extra meinen Partyhut auf. 🎂",
                            accessory: .partyHat, decor: .confetti, emoji: "🎂", song: true)
        }
        if let hd = hatchDay, day > hd {
            let h = ymd(hd)
            if h.m == t.m && h.d == t.d {
                let years = t.y - h.y
                return Festival(id: .hatchday, title: "\(pet) hat Schlüpftag!",
                                greeting: "Heute vor \(years == 1 ? "einem Jahr" : "\(years) Jahren") bin ich geschlüpft. Danke, dass du da bist!",
                                accessory: .crown, decor: .confetti, emoji: "🥚", song: true)
            }
            let age = day - hd
            if milestones.contains(age) {
                return Festival(id: .milestone, title: "\(age) Tage zusammen!", greeting: "Schon \(age) Tage sind wir ein Team. Das feiern wir!",
                                accessory: .partyHat, decor: .stars, emoji: "⭐")
            }
        }
        let e = easter(t.y), adv = firstAdvent(t.y)
        switch (t.m, t.d) {
        case (12, 24...26): return Festival(id: .christmas, title: "Frohe Weihnachten!", greeting: "Frohe Weihnachten! Zeit für Familie, Kekse und wenig Handy.", accessory: .santaHat, decor: .snow, emoji: "🎄")
        case (12, 31): return Festival(id: .silvester, title: "Guten Rutsch!", greeting: "Heute ist Silvester! Kommst du gut ins neue Jahr?", accessory: .partyHat, decor: .confetti, emoji: "🎆")
        case (1, 1): return Festival(id: .newYear, title: "Frohes neues Jahr!", greeting: "Frohes neues Jahr! Was nehmen wir uns zusammen vor?", accessory: .partyHat, decor: .confetti, emoji: "🥂")
        case (12, 6): return Festival(id: .nikolaus, title: "Nikolaustag", greeting: "Hast du deine Stiefel geputzt? Heute ist Nikolaus!", accessory: .santaHat, decor: .snow, emoji: "🎅")
        case (11, 11): return Festival(id: .stMartin, title: "St. Martin", greeting: "Laterne, Laterne! Heute teilen wir, wie St. Martin.", accessory: .lantern, decor: .lights, emoji: "🏮")
        case (10, 31): return Festival(id: .halloween, title: "Halloween", greeting: "Buh! Hab ich dich erschreckt? Süßes oder Saures!", accessory: .witchHat, decor: .pumpkins, emoji: "🎃")
        case (2, 14): return Festival(id: .valentine, title: "Valentinstag", greeting: "Heute ist Valentinstag. Sag jemandem, dass du ihn magst!", accessory: nil, decor: .hearts, emoji: "💗")
        default: break
        }
        if (e - 52...e - 47).contains(day) {
            return Festival(id: .carnival, title: "Fasching", greeting: "Helau und Alaaf! Ich hab mich verkleidet.", accessory: .jester, decor: .confetti, emoji: "🎭")
        }
        if (e - 2...e + 1).contains(day) {
            return Festival(id: .easter, title: "Frohe Ostern!", greeting: "Frohe Ostern! Hast du schon Eier gefunden?", accessory: .bunnyEars, decor: .eggs, emoji: "🐣")
        }
        if day == mothersDay(t.y) {
            return Festival(id: .mothersDay, title: "Muttertag", greeting: "Heute ist Muttertag. Ein Anruf oder eine Umarmung freut sehr!", accessory: .flowers, decor: .hearts, emoji: "💐")
        }
        if day == e + 39 {
            return Festival(id: .fathersDay, title: "Vatertag", greeting: "Heute ist Vatertag. Zeit für einen Ausflug ohne Handy?", accessory: nil, decor: .stars, emoji: "🧢")
        }
        if day >= adv && (t.m == 11 || (t.m == 12 && t.d <= 23)) {
            return Festival(id: .advent, title: "\(adventNumber(day, adv)). Advent", greeting: "Es ist Adventszeit. Ich liebe Kerzenlicht und Plätzchen!", accessory: nil, decor: .snow, emoji: "🕯️")
        }
        return nil
    }

    /// Für Bildschirmfotos und Vorschauen: ein Fest nach ID, unabhängig vom Datum.
    static func sample(_ id: FestivalID, pet: String = "Lumo") -> Festival? {
        let probe: [FestivalID: Int] = [.christmas: epochDay(2026, 12, 24), .silvester: epochDay(2026, 12, 31), .newYear: epochDay(2027, 1, 1),
            .nikolaus: epochDay(2026, 12, 6), .stMartin: epochDay(2026, 11, 11), .halloween: epochDay(2026, 10, 31),
            .valentine: epochDay(2027, 2, 14), .carnival: epochDay(2026, 2, 16), .easter: epochDay(2026, 4, 5),
            .mothersDay: epochDay(2026, 5, 10), .fathersDay: epochDay(2026, 5, 14), .advent: epochDay(2026, 12, 1)]
        switch id {
        case .birthday: return today(0, birthday: (1, 1), hatchDay: nil, pet: pet)
        case .hatchday: return today(epochDay(2027, 3, 1), birthday: nil, hatchDay: epochDay(2026, 3, 1), pet: pet)
        case .milestone: return today(130, birthday: nil, hatchDay: 30, pet: pet)
        default: return probe[id].flatMap { today($0, birthday: nil, hatchDay: nil, pet: pet) }
        }
    }
}
