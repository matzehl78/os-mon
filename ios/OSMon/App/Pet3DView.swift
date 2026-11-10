// Pet3DView.swift: Das Wesen in 3D (WKWebView mit pet3d.html, gleicher Renderer wie Werbe- und Wachstumsfilm).
// Durchsichtig über dem Szenenbild. Antippen kommt als "tap" zurück, Ziehen dreht das Wesen.
// Schlägt WebGL fehl ("error"), merkt sich die App das und zeigt wieder 2D (Pet3D.failed).
// Inhalte: App/pet3d/ (Kopie von tools/pet3d/).

import SwiftUI
import WebKit

enum Pet3D {
    static let key = PetStyle.legacy3DKey      // frühere Einstellung „3D-Wesen“; maßgeblich ist PetStyle
    static let failedKey = "pet.3d.failed"
    static var enabled: Bool { PetStyle.current == .threeD && !STShared.defaults.bool(forKey: failedKey) }
}

/// Was die 3D-Ansicht zeigen soll.
struct Pet3DState: Equatable {
    var figureID: String
    var accessory: Accessory?            // Kopf: Fest-Zubehör oder Mütze (Wardrobe.hat)
    var mood: PetMood
    var overlays: Set<PetOverlay> = []
    var energy: Float?
    var rest: Float?
    var form: PetForm? = nil
    var branch: PetBranch? = nil
    var stage: GrowthStage? = nil
    var eggCracks: Int? = nil            // nicht nil = Ei
    var platform = true                  // Leuchtring (ohne Hintergrundbild)
    var glasses: Glasses? = nil          // Brille aus dem Kleiderschrank
}

struct Pet3DView: UIViewRepresentable {
    let state: Pet3DState
    var reaction: ActiveReaction? = nil
    var eggShakeAt: Date? = nil
    /// Erst wenn WebGL läuft und die eigene Figur gesetzt ist; bis dahin zeigt die Startseite die 2D-Figur.
    var onReady: () -> Void = {}
    var onTap: () -> Void = {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let cfg = WKWebViewConfiguration()
        cfg.userContentController.add(context.coordinator, name: "pet")
        let web = WKWebView(frame: .zero, configuration: cfg)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.isScrollEnabled = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.isAccessibilityElement = false
        context.coordinator.web = web
        if let url = Bundle.main.url(forResource: "pet3d", withExtension: "html") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        let c = context.coordinator
        c.onTap = onTap
        c.onReady = onReady
        c.apply(state)
        if let r = reaction, r.start != c.lastReaction {
            c.lastReaction = r.start
            c.react(r)
        }
        if let s = eggShakeAt, s != c.lastShake { c.lastShake = s; c.shake() }
    }

    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        web.configuration.userContentController.removeScriptMessageHandler(forName: "pet")
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        weak var web: WKWebView?
        var onTap: () -> Void = {}
        var onReady: () -> Void = {}
        var ready = false
        var current: Pet3DState?
        var sent = ""
        var reacting: ReactionKind? = nil
        var lastReaction: Date?
        var lastShake: Date?

        func userContentController(_ uc: WKUserContentController, didReceive message: WKScriptMessage) {
            switch message.body as? String {
            case "ready":
                ready = true; sent = ""
                // Erst nach dem Setzen der eigenen Figur zeigen (sonst blitzt kurz die Standard-Figur auf)
                if let s = current, let web {
                    current = s; sent = script(s, shake: false)
                    web.evaluateJavaScript(sent) { [weak self] _, _ in DispatchQueue.main.async { self?.onReady() } }
                } else { onReady() }
            case "tap": onTap()
            case "error": STShared.defaults.set(true, forKey: Pet3D.failedKey)   // nächster Aufbau zeigt 2D
            default: break
            }
        }

        func apply(_ s: Pet3DState, shake: Bool = false) {
            current = s
            guard ready, let web else { return }
            let js = script(s, shake: shake)
            guard js != sent || shake else { return }
            sent = js
            web.evaluateJavaScript(js)
        }

        func react(_ r: ActiveReaction) {
            reacting = r.kind
            web?.evaluateJavaScript("OSPet.react('\(r.kind.rawValue)', \(Int(r.kind.duration * 1000)))")
            if let s = current { apply(s) }
            DispatchQueue.main.asyncAfter(deadline: .now() + r.kind.duration) { [weak self] in
                guard let self, self.lastReaction == r.start else { return }
                self.reacting = nil
                if let s = self.current { self.apply(s) }
            }
        }

        func shake() { if let s = current { apply(s, shake: true) } }

        private func script(_ s: Pet3DState, shake: Bool) -> String {
            func q(_ v: String?) -> String { v.map { "'\($0)'" } ?? "null" }
            func n(_ v: Float?) -> String { v.map { String(format: "%.1f", $0) } ?? "null" }
            let ov = s.overlays.map { "'\($0.rawValue)'" }.sorted().joined(separator: ",")
            let color = hex(s.mood.color)
            return "OSPet.setFigure(\(q(s.figureID)), \(q(s.accessory?.rawValue)), \(q(s.glasses?.rawValue)));" +
                "OSPet.update({mood:'\(s.mood.rawValue)',ov:[\(ov)],energy:\(n(s.energy)),rest:\(n(s.rest))," +
                "form:\(q(s.form?.rawValue)),branch:\(q(s.branch?.rawValue)),stage:\(q(s.stage?.rawValue))," +
                "egg:\(s.eggCracks.map(String.init) ?? "null"),shake:\(shake ? 1 : 0),color:'\(color)',platform:\(s.platform)," +
                "react:\(q(reacting?.rawValue))})"
        }

        private func hex(_ c: Color) -> String {
            var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a)
            return String(format: "#%02x%02x%02x", Int(r * 255), Int(g * 255), Int(b * 255))
        }
    }
}
