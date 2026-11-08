// ExtrasStore.swift: Speicher für Kleiderschrank, Mini-Spiele, Töne und Premium-Status (App Group, damit Widget und
// Uhr dasselbe zeigen). Gegenstück: WardrobeStore / GameStore / SoundPlayer / PremiumStore in Kotlin.
// Regeln: Shared/Logic/Wardrobe.swift, MiniGames.swift. Kaufen: App/Premium.swift (StoreKit 2).

import Foundation

extension Notification.Name {
    /// Kleiderschrank, Spiel oder Kauf hat sich geändert (Startseite zeichnet neu).
    static let osmonExtrasChanged = Notification.Name("osmon.extrasChanged")
}

enum PremiumFlag {
    static let productID = "com.example.osmon.premium"
    static var isPremium: Bool { STShared.defaults.bool(forKey: "premium.owned") || STShared.defaults.bool(forKey: "premium.demo") }
    static func set(_ on: Bool) {
        guard STShared.defaults.bool(forKey: "premium.owned") != on else { return }
        STShared.defaults.set(on, forKey: "premium.owned")
        NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil)
    }
}

enum WardrobeStore {
    private static var d: UserDefaults { STShared.defaults }

    static var equipped: [String] { (d.string(forKey: "wr.equipped") ?? "").split(separator: ",").map(String.init) }
    static var maxGoodDays: Int { d.integer(forKey: "wr.maxgood") }

    /// Höchstwert der Wachstumstage merken (aus AchievementStore.refresh).
    static func noteGoodDays(_ days: Int) { if days > maxGoodDays { d.set(days, forKey: "wr.maxgood") } }

    static var outfit: Outfit { Wardrobe.outfit(equipped, maxGoodDays: maxGoodDays, premium: PremiumFlag.isPremium) }

    static func toggle(_ id: String) {
        d.set(Wardrobe.toggle(equipped, id).joined(separator: ","), forKey: "wr.equipped")
        NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil)
    }

    private static var known: Set<String> { Set(d.stringArray(forKey: "wr.known") ?? []) }
    /// Neue Teile seit dem letzten Öffnen des Schranks.
    static var fresh: [WearItem] { Wardrobe.newlyUnlocked(maxGoodDays: maxGoodDays, premium: PremiumFlag.isPremium, known: known) }
    static func markSeen() {
        let open = Wardrobe.all.filter { Wardrobe.isUnlocked($0, maxGoodDays: maxGoodDays, premium: PremiumFlag.isPremium) }.map(\.id)
        d.set(Array(known.union(open)).sorted(), forKey: "wr.known")
        NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil)
    }
}

enum GameStore {
    private static var d: UserDefaults { STShared.defaults }
    static var limit: Int { d.object(forKey: "games.limit") as? Int ?? MiniGames.defaultLimit }
    static func setLimit(_ n: Int) { d.set(min(MiniGames.maxLimit, max(0, n)), forKey: "games.limit"); NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil) }
    static var left: Int {
        MiniGames.left(lastDay: d.object(forKey: "games.day") as? Int, playedThatDay: d.integer(forKey: "games.played"), today: EpochDay.of(.now), limit: limit)
    }
    /// Beim Start einer Runde: zählt sofort fürs Tageslimit (Abbrechen gibt keine Runde zurück).
    static func recordStart() {
        let today = EpochDay.of(.now), last = d.object(forKey: "games.day") as? Int
        d.set(MiniGames.afterPlay(lastDay: last, playedThatDay: d.integer(forKey: "games.played"), today: today), forKey: "games.played")
        d.set(MiniGames.dayAfterPlay(lastDay: last, today: today), forKey: "games.day")
        NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil)
    }

    /// Nach einer zu Ende gespielten Runde: nur noch fürs Sammelalbum.
    static func recordFinish() {
        d.set(d.integer(forKey: "ach.games") + 1, forKey: "ach.games")
        NotificationCenter.default.post(name: .osmonExtrasChanged, object: nil)
    }
}
