// RealPet3DView.swift: Das realistische Wesen (Naturdoku-Bild) mit 2,5D-Tiefe im Home-Screen.
// Gegenstück: RealPet in RealPetUI.kt. Bilder und Stufen: Shared/Logic/RealFigureArt.swift.
//
// Das Bild neigt sich mit dem Finger (ziehen) und mit dem iPhone (CoreMotion), atmet langsam,
// Kugeln und Herz pulsieren (Leuchtebene real_*_glow, additiv darübergelegt und leicht versetzt = Tiefe),
// darunter ein weicher Bodenschatten, der sich gegenläufig verschiebt.
// „Bewegung reduzieren“ schaltet Neigen, Atmen und Pulsieren ab. Antippen/Federn regelt HomeView.

import CoreMotion
import SwiftUI

/// Lage des iPhones als geglättete Neigung in Grad (nur solange die Ansicht sichtbar ist).
@MainActor
final class DeviceTilt: ObservableObject {
    @Published var x: Double = 0      // links/rechts → Drehung um die Hochachse
    @Published var y: Double = 0      // vor/zurück → Drehung um die Querachse
    private let motion = CMMotionManager()
    static let maxDegrees = 8.0

    func start() {
        guard motion.isDeviceMotionAvailable, !motion.isDeviceMotionActive else { return }
        motion.deviceMotionUpdateInterval = 1.0 / 30
        motion.startDeviceMotionUpdates(to: .main) { [weak self] data, _ in
            guard let self, let g = data?.gravity else { return }
            let tx = max(-1, min(1, g.x)) * Self.maxDegrees
            let ty = max(-1, min(1, -g.y - 0.7)) * Self.maxDegrees
            self.x += (tx - self.x) * 0.12
            self.y += (ty - self.y) * 0.12
        }
    }

    func stop() { motion.stopDeviceMotionUpdates(); x = 0; y = 0 }
}

struct RealPet3DView: View {
    let figureID: String
    let stage: RealArtStage
    var side: CGFloat = 250

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var device = DeviceTilt()
    @State private var drag: CGSize = .zero
    private let maxTilt = 14.0

    var body: some View {
        let base = RealFigureImageLoader.image(figureID, stage)
        let glow = RealFigureImageLoader.glow(figureID, stage)
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let breath = reduceMotion ? 1 : 1 + 0.009 * (1 - cos(t * .pi / 1.6))          // 3,2 s pro Atemzug
            let pulse = reduceMotion ? 0.25 : 0.25 + 0.3 * (1 - cos(t * .pi / 1.2))        // 0,25 … 0,85
            let rotY = clamp(Double(drag.width) * 0.12 + device.x)
            let rotX = clamp(-Double(drag.height) * 0.12 + device.y)
            let floor = side * RealFigureArt.floorInset
            ZStack(alignment: .bottom) {
                // Bodenschatten
                Ellipse()
                    .fill(RadialGradient(colors: [.black.opacity(0.35), .clear], center: .center, startRadius: 0, endRadius: side * 0.26))
                    .frame(width: side * 0.52, height: side * 0.07)
                    .offset(x: -rotY / maxTilt * side * 0.04, y: -floor + side * 0.03)
                // Bild + Leuchtebene
                ZStack {
                    if let base { Image(uiImage: base).resizable().interpolation(.high).scaledToFit() }
                    if let glow {
                        Image(uiImage: glow).resizable().interpolation(.high).scaledToFit()
                            .blendMode(.plusLighter)
                            .opacity(pulse)
                            .offset(x: rotY / maxTilt * side * 0.012, y: -rotX / maxTilt * side * 0.008)
                    }
                }
                .compositingGroup()
                .scaleEffect(x: 1, y: breath, anchor: .bottom)
                .rotation3DEffect(.degrees(rotY), axis: (0, 1, 0), anchor: .bottom, perspective: 0.5)
                .rotation3DEffect(.degrees(rotX), axis: (1, 0, 0), anchor: .bottom, perspective: 0.5)
            }
            .frame(width: side, height: side, alignment: .bottom)
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 8)
                .onChanged { v in if !reduceMotion { drag = v.translation } }
                .onEnded { _ in withAnimation(.spring(response: 0.45, dampingFraction: 0.55)) { drag = .zero } }
        )
        .onAppear { if !reduceMotion { device.start() } }
        .onDisappear { device.stop() }
        .onChange(of: reduceMotion) { _, off in if off { device.stop() } else { device.start() } }
    }

    private func clamp(_ v: Double) -> Double { max(-maxTilt, min(maxTilt, v)) }
}
