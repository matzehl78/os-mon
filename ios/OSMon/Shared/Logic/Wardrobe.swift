// Wardrobe.swift: Kleiderschrank. Gegenstück: Wardrobe.kt (gleiche Liste, gleiche Tests).
//
// Mützen und Brillen werden durch gute Tage freigeschaltet (Höchstwert der Wachstumstage, geht also durch einen
// Rückfall nicht wieder verloren). Premium-Teile gibt es nur mit dem Einmalkauf. Pro Platz (Kopf, Augen) trägt das
// Wesen höchstens ein Teil. An Festtagen hat das Fest-Zubehör auf dem Kopf Vorrang.

import Foundation

enum Glasses: String, Codable, CaseIterable { case round, sun, heart, star, monocle }
enum WearSlot: String, CaseIterable {
    case hat, glasses
    var title: String { self == .hat ? "Mützen & Hüte" : "Brillen" }
}

struct WearItem: Equatable {
    let id: String, emoji: String, title: String, slot: WearSlot, goodDays: Int
    var premium = false
    var hat: Accessory? = nil
    var glasses: Glasses? = nil
}

/// Was das Wesen trägt (nil = nichts auf diesem Platz).
struct Outfit: Equatable {
    var hat: Accessory? = nil
    var glasses: Glasses? = nil
    var isEmpty: Bool { hat == nil && glasses == nil }
}

enum Wardrobe {
    static let all: [WearItem] = [
        WearItem(id: "beanie", emoji: "🧶", title: "Wollmütze", slot: .hat, goodDays: 2, hat: .beanie),
        WearItem(id: "round", emoji: "👓", title: "Runde Brille", slot: .glasses, goodDays: 5, glasses: .round),
        WearItem(id: "cap", emoji: "🧢", title: "Kappe", slot: .hat, goodDays: 10, hat: .cap),
        WearItem(id: "sun", emoji: "🕶️", title: "Sonnenbrille", slot: .glasses, goodDays: 16, glasses: .sun),
        WearItem(id: "bow", emoji: "🎀", title: "Schleife", slot: .hat, goodDays: 24, hat: .bow),
        WearItem(id: "heart", emoji: "💕", title: "Herzbrille", slot: .glasses, goodDays: 34, glasses: .heart),
        WearItem(id: "tophat", emoji: "🎩", title: "Zylinder", slot: .hat, goodDays: 46, hat: .topHat),
        WearItem(id: "star", emoji: "⭐", title: "Sternbrille", slot: .glasses, goodDays: 60, glasses: .star),
        // Premium (Einmalkauf): sofort verfügbar, keine guten Tage nötig
        WearItem(id: "wizard", emoji: "🧙", title: "Zauberhut", slot: .hat, goodDays: 0, premium: true, hat: .wizardHat),
        WearItem(id: "pirate", emoji: "🏴‍☠️", title: "Piratenhut", slot: .hat, goodDays: 0, premium: true, hat: .pirateHat),
        WearItem(id: "monocle", emoji: "🧐", title: "Monokel", slot: .glasses, goodDays: 0, premium: true, glasses: .monocle)
    ]

    static func byID(_ id: String?) -> WearItem? { all.first { $0.id == id } }

    static func isUnlocked(_ item: WearItem, maxGoodDays: Int, premium: Bool) -> Bool {
        item.premium ? premium : maxGoodDays >= item.goodDays
    }

    /// Fehlende gute Tage bis zum Teil (0 = frei, nil = nur mit Premium).
    static func missing(_ item: WearItem, maxGoodDays: Int, premium: Bool) -> Int? {
        if item.premium { return premium ? 0 : nil }
        return max(0, item.goodDays - maxGoodDays)
    }

    static func newlyUnlocked(maxGoodDays: Int, premium: Bool, known: Set<String>) -> [WearItem] {
        all.filter { !known.contains($0.id) && isUnlocked($0, maxGoodDays: maxGoodDays, premium: premium) }
    }

    /// Nächstes Teil, das durch gute Tage kommt.
    static func next(maxGoodDays: Int) -> WearItem? {
        all.filter { !$0.premium && $0.goodDays > maxGoodDays }.min { $0.goodDays < $1.goodDays }
    }

    /// Getragene Teile → Outfit. Gesperrte (z. B. Premium nach Rückerstattung) fallen weg.
    static func outfit(_ equipped: [String], maxGoodDays: Int, premium: Bool) -> Outfit {
        let items = equipped.compactMap { byID($0) }.filter { isUnlocked($0, maxGoodDays: maxGoodDays, premium: premium) }
        return Outfit(hat: items.last { $0.slot == .hat }?.hat, glasses: items.last { $0.slot == .glasses }?.glasses)
    }

    /// Anziehen/Ausziehen: gleiches Teil → aus, sonst ersetzt es das Teil auf demselben Platz.
    static func toggle(_ equipped: [String], _ id: String) -> [String] {
        guard let item = byID(id) else { return equipped }
        if equipped.contains(id) { return equipped.filter { $0 != id } }
        return equipped.filter { byID($0)?.slot != item.slot } + [id]
    }

    /// Kopf: Fest-Zubehör hat Vorrang, sonst die Mütze aus dem Schrank.
    static func hat(festival: Accessory?, outfit: Outfit) -> Accessory? { festival ?? outfit.hat }
}
