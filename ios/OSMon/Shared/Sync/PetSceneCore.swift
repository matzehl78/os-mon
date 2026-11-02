// PetSceneCore.swift: Hintergrund-Szenen – Katalog, Auswahl, Stimmungsfarbe, Bildlage, Zeichnung
//
// Targets: App und Widget-Extension (Ordner Shared/Sync). Die Bilder liegen im Asset-Katalog
// Shared/Sync/Scenes.xcassets (scene_<id>, 1024×1536), der so in beide Targets kommt.
// „floor“ = Höhe der Plattform im Bild (0...1); dort stehen die Füße der Figur. Werte 1:1 aus dem Prototyp.
//
// Speicher (App Group): "pet.scene" gewählte Szene, "pet.scenes.unlocked" freigeschaltete Szenen.

import SwiftUI
import UIKit
import WidgetKit

// MARK: - Katalog

struct PetScene: Identifiable, Equatable {
    let id: String
    let name: String
    let asset: String?
    let floor: CGFloat

    static let all: [PetScene] = [
        PetScene(id: "none", name: "Keine", asset: nil, floor: 0.7),
        PetScene(id: "neonstadt", name: "Neonstadt", asset: "scene_neonstadt", floor: 0.69),
        PetScene(id: "zimmer", name: "Zukunftszimmer", asset: "scene_zimmer", floor: 0.61),
        PetScene(id: "garteninsel", name: "Garteninsel", asset: "scene_garteninsel", floor: 0.62),
        PetScene(id: "raumstation", name: "Raumstation", asset: "scene_raumstation", floor: 0.64),
        PetScene(id: "zauberwald", name: "Zauberwald", asset: "scene_zauberwald", floor: 0.645),
        PetScene(id: "unterwasser", name: "Unterwasserkuppel", asset: "scene_unterwasser", floor: 0.64),
        PetScene(id: "suess", name: "Süßigkeitenwelt", asset: "scene_suess", floor: 0.655),
        PetScene(id: "mondbasis", name: "Mondbasis", asset: "scene_mondbasis", floor: 0.68),
        PetScene(id: "zen", name: "Zen-Garten", asset: "scene_zen", floor: 0.72),
        PetScene(id: "studio", name: "Neon-Studio", asset: "scene_studio", floor: 0.665),
    ]
    static let defaultID = "neonstadt"
    static func byID(_ id: String?) -> PetScene { all.first { $0.id == id } ?? all.first { $0.id == defaultID }! }
}

// MARK: - Speicher

enum SceneStore {
    static let key = "pet.scene"
    static let unlockedKey = "pet.scenes.unlocked"
    private static var d: UserDefaults { STShared.defaults }

    static var unlocked: Set<String> { Set(d.stringArray(forKey: unlockedKey) ?? []) }

    /// Nur freigeschaltete Szenen lassen sich wählen.
    @discardableResult
    static func select(_ id: String, status: [SceneUnlock]) -> Bool {
        guard status.contains(where: { $0.sceneID == id && $0.unlocked }) else { return false }
        d.set(id, forKey: key)
        WidgetCenter.shared.reloadTimelines(ofKind: STShared.widgetKind)   // Widget zeigt dieselbe Szene
        return true
    }

    static func addUnlocked(_ ids: [String]) {
        guard !ids.isEmpty else { return }
        d.set(Array(unlocked.union(ids)).sorted(), forKey: unlockedKey)
    }

    static func reviewSeen(_ weekStart: Int) -> Bool { d.bool(forKey: "pet.review.seen.\(weekStart)") }
    static func markReviewSeen(_ weekStart: Int) { d.set(true, forKey: "pet.review.seen.\(weekStart)") }
}

// MARK: - Stimmung färbt die Szene (wie CSS brightness → saturate → sepia im Prototyp)

enum SceneMood {
    private static func of(_ m: PetMood) -> (b: Float, s: Float, sep: Float) {
        switch m {
        case .radiant: return (1.08, 1.18, 0)
        case .content: return (1, 1, 0)
        case .tired: return (0.8, 0.85, 0)
        case .sick: return (0.88, 0.45, 0.18)
        case .hibernation: return (0.5, 0.6, 0)
        }
    }

    /// Abends/nachts dunkler: 22–6 Uhr × 0,72, 20–22 und 6–7 Uhr × 0,86.
    static func night(_ hour: Double) -> Float { hour >= 22 || hour < 6 ? 0.72 : (hour >= 20 || hour < 7 ? 0.86 : 1) }

    /// 3×3-Farbmatrix (Zeilen R, G, B); gleiche Rechnung wie PetScene.kt.
    static func matrix3(_ mood: PetMood, hour: Double) -> [Float] {
        let m = of(mood), n = night(hour)
        let b = m.b * n, s = m.s * (n < 1 ? 0.85 : 1), q = 1 - m.sep
        let sat: [Float] = [0.213 + 0.787 * s, 0.715 - 0.715 * s, 0.072 - 0.072 * s,
                            0.213 - 0.213 * s, 0.715 + 0.285 * s, 0.072 - 0.072 * s,
                            0.213 - 0.213 * s, 0.715 - 0.715 * s, 0.072 + 0.928 * s]
        let sep: [Float] = [0.393 + 0.607 * q, 0.769 - 0.769 * q, 0.189 - 0.189 * q,
                            0.349 - 0.349 * q, 0.686 + 0.314 * q, 0.168 - 0.168 * q,
                            0.272 - 0.272 * q, 0.534 - 0.534 * q, 0.131 + 0.869 * q]
        var r = [Float](repeating: 0, count: 9)
        for i in 0..<3 { for j in 0..<3 { r[i * 3 + j] = (0..<3).reduce(Float(0)) { $0 + sep[i * 3 + $1] * sat[$1 * 3 + j] } * b } }
        return r
    }

    static func filter(_ mood: PetMood, hour: Double) -> GraphicsContext.Filter {
        let m = matrix3(mood, hour: hour)
        var c = ColorMatrix()
        c.r1 = m[0]; c.r2 = m[1]; c.r3 = m[2]
        c.g1 = m[3]; c.g2 = m[4]; c.g3 = m[5]
        c.b1 = m[6]; c.b2 = m[7]; c.b3 = m[8]
        return .colorMatrix(c)
    }
}

// MARK: - Darstellung

/// Bildlage wie layoutScene() im Prototyp: Bild füllt die Ebene, Plattform (floor) liegt unter den Füßen (feetY).
func sceneFit(layerW: CGFloat, layerH: CGFloat, feetY: CGFloat, floor: CGFloat, aspect: CGFloat = 2.0 / 3.0) -> (height: CGFloat, top: CGFloat) {
    let h = max(layerH, feetY / floor, (layerH - feetY) / (1 - floor), layerW / aspect)
    return (h, feetY - floor * h)
}

/// Szenenbild, einmal pro Größe verkleinert (das Widget hat wenig Speicher; 512×768 reicht für jede Größe).
enum SceneImages {
    private static var cache: [String: UIImage] = [:]
    private static let lock = NSLock()

    static func image(_ asset: String) -> UIImage? {
        lock.lock(); defer { lock.unlock() }
        if let c = cache[asset] { return c }
        guard let full = UIImage(named: asset) else { return nil }
        let small = full.preparingThumbnail(of: CGSize(width: 512, height: 768)) ?? full
        cache[asset] = small
        return small
    }
}

/// Zeichnet die Szene so, dass die Plattform bei feetY liegt (Stimmungsfilter inklusive).
/// topShade: oben abdunkeln (Anteil der Höhe), bottomShade: unten dunkel auslaufen (Widget-Text), fadeTo: unten in eine Farbe auslaufen.
struct SceneArt: View {
    let scene: PetScene
    let mood: PetMood
    let hour: Double
    let feetY: (CGSize) -> CGFloat
    var topShade: CGFloat = 0.34
    var bottomShade: CGFloat = 0
    var fade: CGFloat = 0
    var fadeTo: Color = Color(.systemBackground)

    var body: some View {
        if let asset = scene.asset, let ui = SceneImages.image(asset) {
            Canvas { ctx, size in
                let aspect = ui.size.width / ui.size.height
                let fit = sceneFit(layerW: size.width, layerH: size.height, feetY: feetY(size), floor: scene.floor, aspect: aspect)
                let w = fit.height * aspect
                var g = ctx
                g.addFilter(SceneMood.filter(mood, hour: hour))
                g.draw(Image(uiImage: ui), in: CGRect(x: (size.width - w) / 2, y: fit.top, width: w, height: fit.height))
                let navy = Color(red: 10 / 255, green: 12 / 255, blue: 40 / 255)
                if topShade > 0 {
                    let h = size.height * topShade
                    ctx.fill(Path(CGRect(x: 0, y: 0, width: size.width, height: h)),
                             with: .linearGradient(Gradient(colors: [navy.opacity(0.62), .clear]), startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
                }
                if bottomShade > 0 {
                    let h = size.height * bottomShade
                    ctx.fill(Path(CGRect(x: 0, y: size.height - h, width: size.width, height: h)),
                             with: .linearGradient(Gradient(colors: [.clear, navy.opacity(0.78)]),
                                                   startPoint: CGPoint(x: 0, y: size.height - h), endPoint: CGPoint(x: 0, y: size.height)))
                }
                if fade > 0 {
                    ctx.fill(Path(CGRect(x: 0, y: size.height - fade, width: size.width, height: fade)),
                             with: .linearGradient(Gradient(colors: [.clear, fadeTo]),
                                                   startPoint: CGPoint(x: 0, y: size.height - fade), endPoint: CGPoint(x: 0, y: size.height)))
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}

extension Date {
    /// Stunde mit Minutenanteil (für Tageszeit-Färbung).
    var hourOfDay: Double {
        let c = Calendar.current.dateComponents([.hour, .minute], from: self)
        return Double(c.hour ?? 12) + Double(c.minute ?? 0) / 60
    }
}
