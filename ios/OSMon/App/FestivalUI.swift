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
    /// Jahreszeiten im Hintergrund (Tönung, Pflanzen/Schnee, Teilchen). Standard an.
    static var seasons: Bool {
        get { d.object(forKey: "seasons.enabled") as? Bool ?? true }
        set { d.set(newValue, forKey: "seasons.enabled") }
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
        case .leaves: return ["🍂", "🍁", "🍃"][i % 3]
        case .butterflies: return i % 3 == 0 ? "🦋" : nil          // nur wenige Schmetterlinge
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
                    if decor == .butterflies && emoji(i) == nil { continue }
                    if decor == .petals {
                        g.translateBy(x: x, y: y); g.rotate(by: .radians(t * (0.8 + rnd(i, 6)) + Double(i)))
                        g.fill(Path(ellipseIn: CGRect(x: -5 * s, y: -3 * s, width: 10 * s, height: 6 * s)), with: .color(Color(red: 1, green: 0.72, blue: 0.85)))
                    } else if let e = emoji(i) {
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
    @State private var seasons = FestivalStore.seasons
    @State private var hasBirthday = FestivalStore.birthday != nil
    @State private var month = FestivalStore.birthday?.m ?? 1
    @State private var day = FestivalStore.birthday?.d ?? 1
    private let months = Calendar(identifier: .gregorian).standaloneMonthSymbols

    var body: some View {
        Section {
            Toggle("Feste feiern", isOn: $enabled)
            Toggle("Jahreszeiten im Hintergrund", isOn: $seasons)
            Toggle("Mein Geburtstag", isOn: $hasBirthday)
            if hasBirthday {
                HStack {
                    Picker("Tag", selection: $day) { ForEach(1...31, id: \.self) { Text("\($0).").tag($0) } }
                    Picker("Monat", selection: $month) { ForEach(1...12, id: \.self) { Text(months[$0 - 1]).tag($0) } }
                }
                .pickerStyle(.menu)
            }
        } header: {
            Text("Feste, Jahreszeiten und Geburtstag")
        } footer: {
            Text("Weihnachten, Ostern, Halloween und mehr: Dein Wesen trägt passendes Zubehör, der Hintergrund bekommt Deko (z. B. Tannenbaum, Ballons). Jahreszeiten färben das Bild leicht und bringen Blüten, Blätter oder Schnee. Der Geburtstag wird nur mit Tag und Monat auf diesem Gerät gespeichert.")
        }
        .onChange(of: enabled) { _, v in FestivalStore.enabled = v }
        .onChange(of: seasons) { _, v in FestivalStore.seasons = v }
        .onChange(of: hasBirthday) { _, _ in save() }
        .onChange(of: month) { _, _ in save() }
        .onChange(of: day) { _, _ in save() }
    }

    private func save() { FestivalStore.birthday = hasBirthday ? (month, day) : nil }
}

// MARK: - Jahreszeit und Fest-Requisiten im Hintergrund

/// Requisiten (Tannenbaum, Ballons, Wimpelkette …), Jahreszeiten-Tönung und -Deko. Liegt über dem Szenenbild, unter dem Inhalt.
/// feetY = Plattformhöhe (Füße der Figur), garlandY = Höhe für Girlanden (knapp unter der Kopfzeile), beides in Ebenen-Koordinaten.
struct SceneThemeLayer: View {
    let props: [Prop]
    let tint: UInt32?
    let feetY: CGFloat
    let garlandY: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static func col(_ argb: UInt32) -> Color {
        Color(red: Double((argb >> 16) & 0xFF) / 255, green: Double((argb >> 8) & 0xFF) / 255, blue: Double(argb & 0xFF) / 255,
              opacity: Double((argb >> 24) & 0xFF) / 255)
    }
    private static let flagColors: [Color] = [.pink, .yellow, .cyan, .orange, .green, .purple]

    /// Durchhängende Kette über die ganze Breite.
    private func chain(_ w: CGFloat, _ y0: CGFloat) -> (Path, (CGFloat) -> CGPoint) {
        let sag: CGFloat = 26
        let pt: (CGFloat) -> CGPoint = { u in CGPoint(x: u * w, y: y0 + sag * 4 * u * (1 - u)) }
        var p = Path(); p.move(to: pt(0))
        for k in 1...40 { p.addLine(to: pt(CGFloat(k) / 40)) }
        return (p, pt)
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 20, paused: reduceMotion)) { tl in
            Canvas { ctx, size in
                let t = reduceMotion ? 0 : tl.date.timeIntervalSinceReferenceDate
                let w = size.width
                if let tint { ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.col(tint))) }
                for p in props {
                    switch p.kind {
                    case .emoji:
                        let bob = p.dy > 0 ? 5 * sin(t * 1.3 + p.x * 9) : 0
                        ctx.draw(Text(p.text).font(.system(size: p.size)), at: CGPoint(x: p.x * w, y: feetY + 6 - p.dy + bob), anchor: .bottom)
                    case .bunting, .lightChain, .lanterns, .heartGarland:
                        let (line, pt) = chain(w, garlandY)
                        ctx.stroke(line, with: .color(.white.opacity(0.75)), lineWidth: 1.5)
                        let n = p.kind == .lanterns ? 6 : p.kind == .bunting ? 12 : 16
                        for i in 0..<n {
                            let u = (CGFloat(i) + 0.5) / CGFloat(n), c = pt(u)
                            switch p.kind {
                            case .bunting:
                                var f = Path(); f.move(to: CGPoint(x: c.x - 11, y: c.y)); f.addLine(to: CGPoint(x: c.x + 11, y: c.y)); f.addLine(to: CGPoint(x: c.x, y: c.y + 22)); f.closeSubpath()
                                ctx.fill(f, with: .color(Self.flagColors[i % 6]))
                            case .lightChain:
                                let on = 0.55 + 0.45 * sin(t * 2.4 + Double(i) * 1.7)
                                let bc = [Color.yellow, .red, .green, .cyan][i % 4]
                                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 9, y: c.y - 3, width: 18, height: 18)), with: .color(bc.opacity(0.25 * on)))
                                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 3.5, y: c.y + 2.5, width: 7, height: 9)), with: .color(bc.opacity(0.5 + 0.5 * on)))
                            case .lanterns:
                                let sway = 3 * sin(t * 1.5 + Double(i))
                                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 22 + sway, y: c.y + 2, width: 44, height: 44)), with: .color(.orange.opacity(0.22)))
                                ctx.fill(Path(roundedRect: CGRect(x: c.x - 11 + sway, y: c.y + 8, width: 22, height: 28), cornerRadius: 9),
                                         with: .color([Color.orange, .yellow, .red][i % 3]))
                                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 4 + sway, y: c.y + 17, width: 8, height: 9)), with: .color(Color(red: 1, green: 0.95, blue: 0.6)))
                            default:
                                ctx.draw(Text(i % 2 == 0 ? "💗" : "💕").font(.system(size: 16)), at: CGPoint(x: c.x, y: c.y + 10))
                            }
                        }
                    case .fireworks:
                        for b in 0..<3 {
                            let period = 2.2, ph = (t / period + Double(b) * 0.37).truncatingRemainder(dividingBy: 1)
                            let cx = w * [0.22, 0.78, 0.5][b], cy = garlandY + 40 + [30.0, 70, 10][b]
                            let r = 18 + 70 * ph, a = max(0, 1 - ph * 1.2)
                            let bc = [Color.yellow, .pink, .cyan][b]
                            for k in 0..<14 {
                                let ang = Double(k) / 14 * 2 * .pi
                                ctx.fill(Path(ellipseIn: CGRect(x: cx + r * cos(ang) - 2.5, y: cy + r * sin(ang) - 2.5, width: 5, height: 5)), with: .color(bc.opacity(a)))
                            }
                        }
                    case .snowGround:
                        for (x, rw, rh) in [(0.0, 0.34, 34.0), (0.18, 0.26, 26.0), (0.82, 0.26, 26.0), (1.0, 0.34, 34.0)] {
                            ctx.fill(Path(ellipseIn: CGRect(x: (x - rw / 2) * w, y: feetY - rh * 0.35, width: rw * w, height: rh * 2)), with: .color(.white.opacity(0.85)))
                        }
                    case .sunGlow:
                        let c = CGPoint(x: w * 0.86, y: garlandY + 10)
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 170, y: c.y - 170, width: 340, height: 340)),
                                 with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.85, blue: 0.4).opacity(0.45), .clear]), center: c, startRadius: 0, endRadius: 170))
                    case .candles:
                        let lit = Int(p.text) ?? 1, cx = p.x * w, base = feetY + 4
                        ctx.fill(Path(ellipseIn: CGRect(x: cx - 44, y: base - 14, width: 88, height: 22)), with: .color(Color(red: 0.13, green: 0.45, blue: 0.2)))
                        for k in 0..<4 {
                            let x = cx - 30 + CGFloat(k) * 20, h: CGFloat = 26 + (k % 2 == 0 ? 0 : 6)
                            ctx.fill(Path(roundedRect: CGRect(x: x - 5, y: base - 10 - h, width: 10, height: h), cornerRadius: 2), with: .color(Color(red: 0.85, green: 0.15, blue: 0.2)))
                            if k < lit {
                                let fl = 1 + 0.15 * sin(t * 9 + Double(k))
                                ctx.fill(Path(ellipseIn: CGRect(x: x - 7, y: base - 10 - h - 22, width: 14, height: 22)), with: .color(.orange.opacity(0.25)))
                                ctx.fill(Path(ellipseIn: CGRect(x: x - 3, y: base - 10 - h - 12 * fl, width: 6, height: 12 * fl)), with: .color(.yellow))
                            }
                        }
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
