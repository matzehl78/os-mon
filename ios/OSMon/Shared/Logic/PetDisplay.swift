// PetDisplay.swift: Tageszählung und Anzeige-Texte/-Farben der Stimmung.
// Ohne UIKit, damit iPhone-App, Widget, Apple Watch und Watch-Komplikation sie gemeinsam nutzen.

import SwiftUI

// MARK: - Tage zählen (lokales Datum)

enum EpochDay {
    private static var utc: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }

    /// Tage seit 1970-01-01 für das lokale Kalenderdatum von `date`.
    static func of(_ date: Date = .now) -> Int {
        let comps = Calendar.current.dateComponents([.year, .month, .day], from: date)
        let day = utc.date(from: comps) ?? date
        return Int((day.timeIntervalSince1970 / 86_400).rounded())
    }

    /// Lokale Mitternacht des Tages.
    static func date(_ epochDay: Int) -> Date {
        let ref = Date(timeIntervalSince1970: Double(epochDay) * 86_400)
        let comps = utc.dateComponents([.year, .month, .day], from: ref)
        return Calendar.current.date(from: comps) ?? ref
    }
}

// MARK: - Anzeige-Zuordnungen (App und Widget)

extension PetMood {
    var title: String {
        switch self {
        case .hibernation: return "Winterschlaf"
        case .sick:        return "Kränklich"
        case .tired:       return "Müde"
        case .content:     return "Zufrieden"
        case .radiant:     return "Strahlend"
        }
    }

    /// Kompakte Darstellung fürs Widget (in der App zeichnet PetAvatarView das Wesen).
    var emoji: String {
        switch self {
        case .hibernation: return "😴"
        case .sick:        return "🤒"
        case .tired:       return "🥱"
        case .content:     return "🙂"
        case .radiant:     return "😄"
        }
    }

    var color: Color {
        switch self {
        case .hibernation: return Color(red: 0.29, green: 0.34, blue: 0.71)
        case .sick:        return Color(red: 0.85, green: 0.40, blue: 0.48)
        case .tired:       return Color(red: 0.88, green: 0.54, blue: 0.12)
        case .content:     return Color(red: 0.11, green: 0.60, blue: 0.60)
        case .radiant:     return Color(red: 0.18, green: 0.62, blue: 0.36)
        }
    }
}

extension PetForm {
    var badge: String {
        switch self {
        case .chatterling: return "💬"
        case .bingeon:     return "📺"
        case .pixelo:      return "🎮"
        case .zenfuchs:    return "🦊"
        case .wandero:     return "🍃"
        }
    }

    var title: String {
        switch self {
        case .chatterling: return "Chatterling"
        case .bingeon:     return "Bingeon"
        case .pixelo:      return "Pixelo"
        case .zenfuchs:    return "Zenfuchs"
        case .wandero:     return "Wandero"
        }
    }
}

