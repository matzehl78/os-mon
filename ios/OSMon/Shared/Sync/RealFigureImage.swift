// RealFigureImage.swift: Lädt die realistischen Figurenbilder (WebP, ab iOS 14 nativ) und zeigt sie an.
// Namen und Stufen: Shared/Logic/RealFigureArt.swift. Liegt in Shared/Sync, damit App und Widget sie nutzen;
// die Bilder (App/RealFigures) sind in beiden Targets als Ressourcen eingetragen (project.yml).

import SwiftUI
import UIKit

enum RealFigureImageLoader {
    private static let cache = NSCache<NSString, UIImage>()

    static func image(_ figureID: String, _ stage: RealArtStage) -> UIImage? {
        image(named: RealFigureArt.resourceName(figureID, stage))
    }

    /// Leuchtebene (Kugeln und Herz) oder nil.
    static func glow(_ figureID: String, _ stage: RealArtStage) -> UIImage? {
        image(named: RealFigureArt.glowName(figureID, stage))
    }

    static func image(named name: String) -> UIImage? {
        if let hit = cache.object(forKey: name as NSString) { return hit }
        // XcodeGen legt die Dateien flach ins Bundle; der Unterordner ist nur Rückfall für Ordner-Referenzen.
        let url = Bundle.main.url(forResource: name, withExtension: "webp")
            ?? Bundle.main.url(forResource: name, withExtension: "webp", subdirectory: "RealFigures")
        guard let url, let img = UIImage(contentsOfFile: url.path) else { return nil }
        cache.setObject(img, forKey: name as NSString)
        return img
    }
}

/// Realistische Figur, unten mittig ausgerichtet (gemeinsame Bodenlinie aller Stufen).
struct RealFigureView: View {
    let figureID: String
    let stage: RealArtStage

    var body: some View {
        if let img = RealFigureImageLoader.image(figureID, stage) {
            Image(uiImage: img)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .accessibilityLabel(Text("\(figureID.capitalized), \(stage.rawValue)"))
        } else {
            Color.clear
        }
    }
}
