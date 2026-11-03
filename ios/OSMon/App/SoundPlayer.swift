// SoundPlayer.swift: Spielt Geräusche und Stimme des Wesens (Shared/Logic/SoundSynth.swift). Gegenstück: SoundPlayer.kt.
// Einstellung „Töne & Stimme“ (Standard an). Audio-Kategorie „ambient“: respektiert den Stumm-Schalter und
// unterbricht keine Musik. Erzeugt wird im Hintergrund, abgespielt mit AVAudioPlayer (WAV im Speicher).

import AVFoundation

final class SoundPlayer {
    static let shared = SoundPlayer()
    static let key = "sound.on"
    private let queue = DispatchQueue(label: "osmon.sound")
    private var cache: [String: Data] = [:]
    private var playing: [AVAudioPlayer] = []
    private var lastVoice = Date.distantPast
    private var sessionReady = false

    static var enabled: Bool { STShared.defaults.object(forKey: key) as? Bool ?? true }

    private var figureID: String { STShared.defaults.string(forKey: PetFigureStore.key) ?? PetFigure.defaultID }

    func play(_ s: Sfx, stage: GrowthStage? = nil) {
        guard Self.enabled else { return }
        let fig = figureID
        queue.async {
            let key = "\(s.rawValue)|\(fig)|\(stage?.rawValue ?? "")"
            let data = self.cache[key] ?? SoundSynth.wav(SoundSynth.sfx(s, figureID: fig, stage: stage))
            self.cache[key] = data
            self.out(data)
        }
    }

    /// Brabbel-Stimme zur Sprechblase (höchstens alle 1,5 s).
    func say(_ text: String, stage: GrowthStage? = nil) {
        guard Self.enabled, !text.trimmingCharacters(in: .whitespaces).isEmpty, Date().timeIntervalSince(lastVoice) > 1.5 else { return }
        lastVoice = Date()
        let fig = figureID
        queue.async { self.out(SoundSynth.wav(SoundSynth.babble(text, figureID: fig, stage: stage))) }
    }

    private func out(_ data: Data) {
        if !sessionReady {
            try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
            try? AVAudioSession.sharedInstance().setActive(true)
            sessionReady = true
        }
        guard let p = try? AVAudioPlayer(data: data) else { return }
        p.volume = 0.8
        p.play()
        DispatchQueue.main.async {
            self.playing.append(p)
            self.playing.removeAll { !$0.isPlaying && $0 !== p }
        }
    }
}
