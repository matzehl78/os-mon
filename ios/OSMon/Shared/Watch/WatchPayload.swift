// WatchPayload.swift: Was das iPhone der Apple Watch schickt (WatchConnectivity, applicationContext) und
// was die Uhr zurückmeldet. Targets: iPhone-App, Watch-App, Watch-Komplikation.
//
// Das iPhone rechnet alles (Sensoren, Bildschirmzeit, Schritte); die Uhr zeigt nur an und meldet Aktionen
// (Aufgabe abgehakt, Miteinander-Tipp ausprobiert). Gespeichert wird das letzte Paket auf der Uhr in der
// App Group, damit die Komplikation es lesen kann.

import Foundation

struct WatchPayload: Codable, Equatable {
    var snapshot: PetSnapshot
    var updatedAt: Date
    var figureID: String
    var petName: String
    var accessory: String?           // Accessory.rawValue (Fest-Zubehör)
    var form: String?                // PetForm.rawValue (Entwicklungsstufe)
    var day: Int                     // epochDay, für den die Aufgaben gelten
    var tasks: [DayTask]
    var motivation: String           // Satz vom Wesen passend zum Fortschritt
    var kindTip: String?             // Miteinander: Tipp des Tages
    var kindCategory: String?
    var kindDone: Bool
    var stage: String? = nil         // GrowthStage.rawValue (egg … adult)

    static let key = "watch.payload"

    func encoded() -> Data? { try? JSONEncoder().encode(self) }
    static func decode(_ data: Data?) -> WatchPayload? { data.flatMap { try? JSONDecoder().decode(WatchPayload.self, from: $0) } }

    var progress: TaskProgress { DayTasks.progress(tasks, day) }
    var growth: GrowthStage? { stage.flatMap(GrowthStage.init(rawValue:)) }
}

/// Aktionen von der Uhr ans iPhone (sendMessage, sonst transferUserInfo).
enum WatchAction {
    static let kind = "action"
    static let toggleTask = "toggleTask"     // + "id"
    static let kindDone = "kindDone"
    static let requestUpdate = "update"
}
