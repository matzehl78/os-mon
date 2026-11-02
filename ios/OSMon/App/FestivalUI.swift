// FestivalUI.swift: Feste und Geburtstag auf dem Home-Screen (Karte, Deko über dem Hintergrund, Ständchen) und Einstellungen.
// Logik: Festivals.swift (Shared/Logic). Zubehör auf der Figur: PetAccessory.swift.
//
// Speicher (App Group): festivals.enabled (Standard an), user.birthday.m / user.birthday.d (nur Tag und Monat, freiwillig),
// pet.festival.celebrated.<Tag> (Karte zugeklappt).

import SwiftUI
import AVFoundation

enum FestivalStore {
    private static var d: UserDefaults { STShared.defaults }

    static var enabled: Bool {
        get { d.object(forKey: "festivals.enabled") as? Bool ?? true }
        set { d.set(newValue, forKey: "festivals.enabled") }
    }
    static var birthday: (m: Int, d: Int)? {
        get {
            let m = d.integer(forKey: "user.birthday.m"), dd = d.integer(forKey: "user.birthday.d")
            return (1...12).contains(m) && (1...31).contains(dd) ? (m, dd) : nil
        }
        set {
            if let b = newValue { d.set(b.m, forKey: "user.birthday.m"); d.set(b.d, forKey: "user.birthday.d") }
            else { d.removeObject(forKey: "user.birthday.m"); d.removeObject(forKey: "user.birthday.d") }
        }
    }

    /// Fest heute (nil = keins oder ausgeschaltet).
    static func today(_ now: Date = .now) -> Festival? {
        guard enabled else { return nil }
        let pet = PetFigure.byID(d.string(forKey: PetFigureStore.key)).name
        return Festivals.today(EpochDay.of(now), birthday: birthday, hatchDay: EvolutionStore.load()?.hatchDay, pet: pet)
    }

    static func cardHidden(_ day: Int) -> Bool { d.bool(forKey: "pet.festival.celebrated.\(day)") }
    static func hideCard(_ day: Int) { d.set(true, forKey: "pet.festival.celebrated.\(day)") }
}

/// Spielt „Zum Geburtstag viel Glück“ (Spieluhr, einmal).
@MainActor
final class BirthdaySong: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var playing = false
    private var player: AVAudioPlayer?

    func toggle() {
        if playing { player?.stop(); playing = false; return }
        guard let url = Bundle.main.url(forResource: "song_geburtstag", withExtension: "m4a"),
              let p = try? AVAudioPlayer(contentsOf: url) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        p.delegate = self
        p.play()
        player = p
        playing = true
    }
    nonisolated func audioPlayerDidFinishPlaying(_ p: AVAudioPlayer, successfully flag: Bool) { Task { @MainActor in self.playing = false } }
}

/// Karte unter den Aktionen: Titel, Feiern (Reaktion + Konfetti), bei Geburtstag/Schlüpftag ein Ständchen.
struct FestivalCard: View {
    let festival: Festival
    let onCelebrate: () -> Void
    let onHide: () -> Void
    @StateObject private var song = BirthdaySong()

    var body: some View {
        HStack(spacing: 12) {
            Text(festival.emoji).font(.system(size: 38))
            VStack(alignment: .leading, spacing: 6) {
                Text(festival.title).font(.headline)
                HStack(spacing: 8) {
                    Button("Feiern 🎉", action: onCelebrate).buttonStyle(.borderedProminent)
                    if festival.song {
                        Button { song.toggle() } label: {
                            Label(song.playing ? "Stopp" : "Ständchen", systemImage: song.playing ? "stop.fill" : "music.note")
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            Spacer(minLength: 0)
            Button(action: onHide) { Image(systemName: "xmark").font(.caption.weight(.bold)) }
                .buttonStyle(.borderless).foregroundStyle(.secondary).accessibilityLabel("Karte ausblenden")
        }
        .padding(14)
        .background(LinearGradient(colors: [Color.pink.opacity(0.18), Color.yellow.opacity(0.16)], startPoint: .leading, endPoint: .trailing),
                    in: RoundedRectangle(cornerRadius: 18))
    }
}

/// Schwebende Deko über dem Hintergrund (Schnee, Konfetti, Herzen …). Bei „Bewegung reduzieren“ stehen die Teilchen still.
struct FestivalDecor: View {
    let decor: Decor
    var burst: Date? = nil                          // „Feiern“: 3 s lang dichter und schneller
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let confettiColors: [Color] = [.pink, .yellow, .cyan, .orange, .green, .purple]
    private func emoji(_ i: Int) -> String? {
        switch decor {
        case .hearts: return ["💗", "💕", "💖"][i % 3]
        case .eggs: return ["🥚", "🐣", "🌷"][i % 3]
        case .pumpkins: return ["🎃", "🦇", "🍬"][i % 3]
        case .stars: return ["⭐", "✨", "🌟"][i % 3]
        default: return nil
        }
    }
    private func rnd(_ i: Int, _ k: Int) -> Double {      // feste Zufallswerte je Teilchen
        let x = sin(Double(i * 127 + k * 311) * 12.9898) * 43758.5453
        return x - x.rounded(.down)
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion)) { tl in
            Canvas { ctx, size in
                let t = reduceMotion ? 0 : tl.date.timeIntervalSinceReferenceDate
                let boost = burst.map { max(0, 1 - tl.date.timeIntervalSince($0) / 3) } ?? 0
                let n = 22 + Int(boost * 30)
                for i in 0..<n {
                    let up = decor == .lights                              // Laternenlichter steigen auf
                    let speed = (0.04 + 0.05 * rnd(i, 1)) * (1 + 2 * boost)
                    var v = (t * speed + rnd(i, 2)).truncatingRemainder(dividingBy: 1.15) - 0.075
                    if up { v = 1 - v }
                    let x = (rnd(i, 3) + 0.03 * sin(t * (0.6 + rnd(i, 4)) + Double(i))) * size.width
                    let y = v * size.height
                    let s = 0.6 + 0.8 * rnd(i, 5)
                    var g = ctx
                    g.opacity = 0.85
                    if let e = emoji(i) {
                        g.draw(Text(e).font(.system(size: 14 + 10 * s)), at: CGPoint(x: x, y: y))
                    } else if decor == .snow {
                        g.fill(Path(ellipseIn: CGRect(x: x, y: y, width: 3 + 4 * s, height: 3 + 4 * s)), with: .color(.white.opacity(0.9)))
                    } else if decor == .lights {
                        let r = 4 + 5 * s
                        g.fill(Path(ellipseIn: CGRect(x: x - r * 1.8, y: y - r * 1.8, width: r * 3.6, height: r * 3.6)), with: .color(.orange.opacity(0.18)))
                        g.fill(Path(ellipseIn: CGRect(x: x - r / 2, y: y - r / 2, width: r, height: r)), with: .color(Color(red: 1, green: 0.85, blue: 0.4)))
                    } else {                                                // Konfetti
                        g.translateBy(x: x, y: y)
                        g.rotate(by: .radians(t * (1 + 2 * rnd(i, 6)) + Double(i)))
                        g.fill(Path(CGRect(x: -4 * s, y: -2 * s, width: 8 * s, height: 4 * s)), with: .color(Self.confettiColors[i % 6]))
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Einstellungen: Feste an/aus, eigener Geburtstag (nur Tag und Monat).
struct FestivalSettingsSection: View {
    @State private var enabled = FestivalStore.enabled
    @State private var hasBirthday = FestivalStore.birthday != nil
    @State private var month = FestivalStore.birthday?.m ?? 1
    @State private var day = FestivalStore.birthday?.d ?? 1
    private let months = Calendar(identifier: .gregorian).standaloneMonthSymbols

    var body: some View {
        Section {
            Toggle("Feste feiern", isOn: $enabled)
            Toggle("Mein Geburtstag", isOn: $hasBirthday)
            if hasBirthday {
                HStack {
                    Picker("Tag", selection: $day) { ForEach(1...31, id: \.self) { Text("\($0).").tag($0) } }
                    Picker("Monat", selection: $month) { ForEach(1...12, id: \.self) { Text(months[$0 - 1]).tag($0) } }
                }
                .pickerStyle(.menu)
            }
        } header: {
            Text("Feste und Geburtstag")
        } footer: {
            Text("Weihnachten, Ostern, Halloween und mehr: Dein Wesen trägt dann passendes Zubehör. Der Geburtstag wird nur mit Tag und Monat auf diesem Gerät gespeichert.")
        }
        .onChange(of: enabled) { _, v in FestivalStore.enabled = v }
        .onChange(of: hasBirthday) { _, _ in save() }
        .onChange(of: month) { _, _ in save() }
        .onChange(of: day) { _, _ in save() }
    }

    private func save() { FestivalStore.birthday = hasBirthday ? (month, day) : nil }
}
