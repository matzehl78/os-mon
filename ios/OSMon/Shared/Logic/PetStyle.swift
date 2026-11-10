// PetStyle.swift: Darstellung des Wesens – 2D (gezeichnet), 3D (WebGL) oder realistisch (Naturdoku-Bilder).
// Gegenstück: PetStyle.kt. Gilt für Home-Screen und Widget; Sperrbildschirm-Hintergrund und Uhr bleiben 2D.
// Speicher: App Group "pet.style" ("2d" | "3d" | "real"). Ältere Stände ohne diesen Schlüssel übernehmen die
// frühere 3D-Einstellung "pet.3d" (Standard an → 3D).

import Foundation

enum PetStyle: String, CaseIterable {
    case flat = "2d", threeD = "3d", real = "real"

    static let key = "pet.style"
    static let legacy3DKey = "pet.3d"

    var title: String {
        switch self { case .flat: "2D"; case .threeD: "3D"; case .real: "Realistisch" }
    }

    /// Gespeicherter Wert (leer = noch nicht gewählt) → Darstellung. legacy3D = frühere Einstellung "pet.3d".
    static func resolve(_ raw: String?, legacy3D: Bool?) -> PetStyle {
        if let raw, let s = PetStyle(rawValue: raw) { return s }
        return (legacy3D ?? true) ? .threeD : .flat
    }

    static var current: PetStyle {
        get {
            let d = STShared.defaults
            return resolve(d.string(forKey: key), legacy3D: d.object(forKey: legacy3DKey) as? Bool)
        }
        set {
            let d = STShared.defaults
            d.set(newValue.rawValue, forKey: key)
            d.set(newValue == .threeD, forKey: legacy3DKey)      // ältere Teile lesen noch "pet.3d"
        }
    }
}
