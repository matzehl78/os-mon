// LogoSplashView.swift: Logo-Film „Ulmatti Studios“ bei jedem App-Start (Kaltstart), vor Vorspann/Onboarding/Home.
//
// Videos (weißer Grund, mittig eingepasst auf Weiß):
//   logo.mp4        ganze Fassung, 8,5 s, mit Ton – erster Start, vor dem Vorspann
//   logo_short.mp4  kurze Fassung, ca. 3,3 s (3× schneller), ohne Ton – jeder weitere Start
// Antippen überspringt. Ton folgt dem Lautlos-Schalter (.ambient). Ohne Video endet die Ansicht sofort.

import SwiftUI
import AVFoundation

struct LogoSplashView: View {
    var full = true
    var onFinish: () -> Void
    @State private var player: AVPlayer?
    @State private var ended = false

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            if let player { LogoPlayerView(player: player).ignoresSafeArea() }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: finish)
        .accessibilityElement()
        .accessibilityLabel("Ulmatti Studios")
        .accessibilityHint("Tippen zum Überspringen")
        .accessibilityAddTraits(.isButton)
        .preferredColorScheme(.light)
        .onAppear(perform: start)
        .onDisappear { player?.pause() }
        .onReceive(NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)) { n in
            if (n.object as? AVPlayerItem) === player?.currentItem { finish() }
        }
    }

    private func start() {
        guard player == nil else { return }
        guard let url = Bundle.main.url(forResource: full ? "logo" : "logo_short", withExtension: "mp4") else { finish(); return }
        if full { try? AVAudioSession.sharedInstance().setCategory(.ambient) }
        let p = AVPlayer(url: url)
        p.isMuted = !full
        player = p
        p.play()
    }

    private func finish() {
        guard !ended else { return }
        ended = true
        player?.pause()
        withAnimation(.easeOut(duration: 0.35)) { onFinish() }
    }
}

/// AVPlayerLayer, eingepasst (Querformat-Logo mittig, Rest weiß).
private struct LogoPlayerView: UIViewRepresentable {
    let player: AVPlayer
    func makeUIView(context: Context) -> V { let v = V(); v.playerLayer.player = player; return v }
    func updateUIView(_ v: V, context: Context) { v.playerLayer.player = player }

    final class V: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
        override init(frame: CGRect) { super.init(frame: frame); playerLayer.videoGravity = .resizeAspect; backgroundColor = .white }
        required init?(coder: NSCoder) { fatalError() }
    }
}
