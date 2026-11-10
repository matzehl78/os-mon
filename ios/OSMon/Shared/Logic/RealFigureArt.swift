// RealFigureArt.swift: Realistische Figurenbilder (Naturdoku-Stil) je Figur und Wachstumsstufe.
// Gegenstück: RealFigureArt.kt (gleiche Namen, gleiche Regeln, gleiche Tests).
//
// Bilder: App/RealFigures/real_<figur>_<stufe>.webp, 512×512, transparent (WebP statt Asset-Katalog-PNG:
// 66 Bilder ≈ 4 MB statt ≈ 23 MB). Alle Figuren und Stufen haben denselben Maßstab und dieselbe Bodenlinie
// (Füße 20 px über dem unteren Rand): Bild unten mittig ausrichten, dann springt beim Stufenwechsel nichts.
// Erzeugt mit tools/real_figures/export_real_figures.py. Laden: RealFigureImage.swift (App).
//
// Sechs Bildstufen, fünf Wachstumsstufen: Der Schlüpfling erscheint am Schlüpftag selbst,
// ab dem nächsten Tag zeigt .baby das Baby-Bild.

import Foundation

enum RealArtStage: String, CaseIterable {
    case egg, hatchling, baby, child, teen, adult
}

enum RealFigureArt {
    /// Figuren mit realistischen Bildern (gleiche IDs wie PetFigureData).
    static let figureIDs = ["lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki"]

    /// Abstand der Füße zum unteren Bildrand, relativ zur Bildhöhe (20 von 512 px).
    static let floorInset: Double = 20.0 / 512.0

    /// Bildstufe aus Wachstumsstufe. hatchedDay/today als Epoch-Tage; am Schlüpftag gibt es den Schlüpfling.
    static func stage(for growth: GrowthStage, hatchedDay: Int? = nil, today: Int? = nil) -> RealArtStage {
        switch growth {
        case .egg: return .egg
        case .baby:
            if let h = hatchedDay, let t = today, t <= h { return .hatchling }
            return .baby
        case .child: return .child
        case .teen: return .teen
        case .adult: return .adult
        }
    }

    static func has(_ figureID: String) -> Bool { figureIDs.contains(figureID) }

    /// Dateiname ohne Endung, z. B. real_lumo_baby. Unbekannte Figuren fallen auf Lumo zurück.
    static func resourceName(_ figureID: String, _ stage: RealArtStage) -> String {
        "real_\(has(figureID) ? figureID : "lumo")_\(stage.rawValue)"
    }

    /// Leuchtebene (nur Kugeln und Herz) für das Pulsieren in der 2,5D-Ansicht, z. B. real_lumo_baby_glow.
    static func glowName(_ figureID: String, _ stage: RealArtStage) -> String { resourceName(figureID, stage) + "_glow" }
}
