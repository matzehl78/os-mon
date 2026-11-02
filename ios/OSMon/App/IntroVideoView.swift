// IntroVideoView.swift: Vorspann-Film beim ersten Start (vor dem Onboarding), erneut abrufbar in den Einstellungen.
//
// Video: App-Bundle "intro.mp4" (720×1280, ca. 41 s, Text im Bild, Musik). Ton folgt dem Lautlos-Schalter
// (Audio-Session .ambient), unterbricht also keine laufende Musik. Ohne Video endet die Ansicht sofort.

import SwiftUI
import AVFoundation

struct IntroVideoView: View {
    var onFinish: () -> Void
    @State private var player: AVPlayer?
    @State private var ended = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let player {
                PlayerLayerView(player: player)
                    .ignoresSafeArea()
                    .accessibilityLabel("Einführungsfilm: Dein Handy ist ein Haustier. Akku wird zu Energie, Bildschirmzeit zu Ruhe, freier Speicher zu Fitness, Schritte zu Vitalität. Antippen, Streicheln und Pausen machen es glücklich, gute Gewohnheiten schalten neue Hintergründe frei. Alles bleibt auf deinem Handy.")
            }
            Button(action: finish) {
                Text("Überspringen")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(.ultraThinMaterial, in: Capsule())
            }
            .foregroundStyle(.white)
            .padding()
        }
        .onAppear(perform: start)
        .onDisappear { player?.pause() }
        .onReceive(NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)) { n in
            if (n.object as? AVPlayerItem) === player?.currentItem { finish() }
        }
    }

    private func start() {
        guard player == nil else { return }
        guard let url = Bundle.main.url(forResource: "intro", withExtension: "mp4") else { finish(); return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        let p = AVPlayer(url: url)
        player = p
        p.play()
    }

    private func finish() {
        guard !ended else { return }
        ended = true
        player?.pause()
        onFinish()
    }
}

/// AVPlayerLayer randlos (füllt den Bildschirm, Ränder werden beschnitten).
private struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    func makeUIView(context: Context) -> PlayerUIView { let v = PlayerUIView(); v.playerLayer.player = player; return v }
    func updateUIView(_ v: PlayerUIView, context: Context) { v.playerLayer.player = player }

    final class PlayerUIView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        override init(frame: CGRect) { super.init(frame: frame); playerLayer.videoGravity = .resizeAspectFill; backgroundColor = .black }
        required init?(coder: NSCoder) { fatalError() }
    }
}
