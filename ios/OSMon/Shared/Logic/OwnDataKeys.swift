// OwnDataKeys.swift: Welche Schlüssel in der App Group zu OS-Mon gehören (für „Daten exportieren“ und „Alle Daten löschen“).
// Gegenstück Android: SettingsScreen.kt löscht ganze SharedPreferences-Dateien (PREF_FILES), dort gibt es keine Präfixliste.
//
// Zwei Quellen, damit beim Löschen nichts übrig bleibt:
// 1. alle Schlüssel, die in der App-Group-Domain selbst stehen (erfasst auch künftige Schlüssel),
// 2. alle Schlüssel mit einem bekannten OS-Mon-Präfix (falls die Domain-Abfrage nichts liefert).
// Systemschlüssel aus anderen Domains (AppleLanguages, Startargumente wie -OSMonDemo …) bleiben unberührt.

import Foundation

enum OwnDataKeys {
    /// Jedes Präfix, unter dem OS-Mon speichert. Neue Speicher hier eintragen (Test: OwnDataKeysTests).
    static let prefixes = [
        "pet.", "st.", "steps.", "daylog.", "push.", "goal.", "feature.", "onboarding.", "sel.",
        "ach.", "festivals.", "seasons.", "user.", "games.", "growth.", "kind.", "parent.", "premium.",
        "sleep.", "sound.", "tasks.", "watch.", "wr.", "intro.",
    ]

    static func isOwn(_ key: String) -> Bool { prefixes.contains { key.hasPrefix($0) } }

    /// domainKeys: Schlüssel der App-Group-Domain (persistentDomain), allKeys: dictionaryRepresentation (inkl. System).
    static func select(domainKeys: [String], allKeys: [String]) -> [String] {
        Array(Set(domainKeys).union(allKeys.filter(isOwn))).sorted()
    }

    static func keys(in defaults: UserDefaults, suite: String) -> [String] {
        select(domainKeys: defaults.persistentDomain(forName: suite).map { Array($0.keys) } ?? [],
               allKeys: Array(defaults.dictionaryRepresentation().keys))
    }
}
