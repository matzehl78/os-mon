// Bedtime.swift: Gute-Nacht-Routine, Geschichten (Vorlesen), Schlaflieder, Guten-Morgen-Karte, Einstellungen.
//
// Inhalte: bedtime.json im App-Bundle (gleiche Datei wie Android res/raw/bedtime.json, Quelle tools/bedtime.json).
// Lieder: song_<id>.m4a (Spieluhr; „Guten Abend, gut’ Nacht“ ist gemeinfrei, die anderen sind eigene Lieder).
// Vorlesen: AVSpeechSynthesizer mit deutscher Systemstimme, offline.
// Logik (Phasen, Bewertung der Nacht): NightRoutine.swift.
//
// Speicher (App Group):
//   sleep.enabled, sleep.bed, sleep.wake (Minuten), sleep.reminder
//   pet.night.goodnight.<Nacht-Tag>   Zeitpunkt des Gute-Nacht-Sagens
//   pet.night.opens.<Nacht-Tag>       App-Öffnungen danach (bis zum Aufstehen)
//   pet.morning.seen.<Tag>            Guten-Morgen-Karte weggeklickt

import SwiftUI
import AVFoundation
import UserNotifications

// MARK: - Inhalte

struct BedtimeContent: Decodable {
    struct Story: Decodable, Identifiable { let id, title, figure: String; let minutes: Int; let text: String }
    struct Song: Decodable, Identifiable { let id, title, credit, audio, lyrics: String }
    struct Morning: Decodable { let great, ok, restless: [String] }
    let stories: [Story]
    let songs: [Song]
    let goodnight: [String]
    let sleep: [String]
    let morning: Morning
    let tips: [String]

    static let shared: BedtimeContent = {
        guard let url = Bundle.main.url(forResource: "bedtime", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let c = try? JSONDecoder().decode(BedtimeContent.self, from: data) else {
            return BedtimeContent(stories: [], songs: [], goodnight: ["Gute Nacht!"], sleep: ["Schlaf gut!"],
                                  morning: Morning(great: ["Guten Morgen!"], ok: ["Guten Morgen!"], restless: ["Guten Morgen!"]), tips: [])
        }
        return c
    }()

    func pick<T>(_ list: [T], day: Int, salt: Int = 0) -> T? { list.isEmpty ? nil : list[NightRoutine.pickIndex(list.count, day, salt: salt)] }
    func storyOfTheDay(_ day: Int) -> Story? { pick(stories, day: day, salt: 1) }
    func tipOfTheDay(_ day: Int) -> String? { pick(tips, day: day, salt: 2) }
    func morningLine(_ r: NightRating, day: Int) -> String {
        let l = r == .great ? morning.great : r == .ok ? morning.ok : morning.restless
        return pick(l, day: day, salt: 3) ?? "Guten Morgen!"
    }
}

// MARK: - Speicher

enum SleepStore {
    private static var d: UserDefaults { STShared.defaults }

    static var config: SleepConfig {
        get {
            SleepConfig(enabled: d.object(forKey: "sleep.enabled") as? Bool ?? true,
                        bedMin: d.object(forKey: "sleep.bed") as? Int ?? 22 * 60,
                        wakeMin: d.object(forKey: "sleep.wake") as? Int ?? 7 * 60)
        }
        set {
            d.set(newValue.enabled, forKey: "sleep.enabled")
            d.set(newValue.bedMin, forKey: "sleep.bed")
            d.set(newValue.wakeMin, forKey: "sleep.wake")
        }
    }

    static func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
    static func phase(_ now: Date = .now) -> DayPhase { NightRoutine.phase(minuteOfDay(now), config) }
    static func nightDay(_ now: Date = .now) -> Int { NightRoutine.nightDay(EpochDay.of(now), minuteOfDay(now), config) }

    static func markGoodnight(_ now: Date = .now) { d.set(now, forKey: "pet.night.goodnight.\(nightDay(now))") }
    static func saidGoodnight(night: Int) -> Bool { d.object(forKey: "pet.night.goodnight.\(night)") != nil }

    /// Beim Öffnen der App aufrufen: zählt Öffnungen nach dem Gute-Nacht-Sagen bis zum Aufstehen.
    static func noteOpen(_ now: Date = .now) {
        guard phase(now) == .night else { return }
        let n = nightDay(now)
        guard saidGoodnight(night: n) else { return }
        if let last = d.object(forKey: "pet.night.lastOpen") as? Date, now.timeIntervalSince(last) < 300 { return }   // 5 Min = ein Öffnen
        d.set(now, forKey: "pet.night.lastOpen")
        d.set(d.integer(forKey: "pet.night.opens.\(n)") + 1, forKey: "pet.night.opens.\(n)")
    }

    static func morningSeen(_ day: Int) -> Bool { d.bool(forKey: "pet.morning.seen.\(day)") }
    static func markMorningSeen(_ day: Int) { d.set(true, forKey: "pet.morning.seen.\(day)") }

    /// Guten-Morgen-Karte für heute (nil außerhalb der Morgenphase oder schon gesehen).
    static func morning(now: Date = .now, screenTimeOn: Bool) -> MorningInfo? {
        guard phase(now) == .morning else { return nil }
        let day = EpochDay.of(now)
        guard !morningSeen(day) else { return nil }
        let night = screenTimeOn ? Float(STShared.minutes("night", day: STShared.dayKey(now))) : nil
        let r = NightRoutine.morningReport(nightMinutes: night, saidGoodnight: saidGoodnight(night: day),
                                           nightOpens: d.integer(forKey: "pet.night.opens.\(day)"))
        let c = BedtimeContent.shared
        return MorningInfo(day: day, greeting: c.morningLine(r.rating, day: day), report: r, tip: c.tipOfTheDay(day))
    }
}

struct MorningInfo: Equatable {
    let day: Int
    let greeting: String
    let report: MorningReport
    let tip: String?
}

// MARK: - Erinnerung zur Schlafenszeit (lokale Mitteilung, täglich)

enum BedtimeReminder {
    static let id = "osmon.bedtime"
    static var enabled: Bool { STShared.defaults.bool(forKey: "sleep.reminder") }

    static func apply() async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        let cfg = SleepStore.config
        guard enabled, cfg.enabled else { return }
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        guard granted else { return }
        let m = ((cfg.bedMin - NightRoutine.windDownMin) % 1440 + 1440) % 1440
        let content = UNMutableNotificationContent()
        content.title = "Zeit fürs Bett 🌙"
        content.body = "Dein Wesen wird müde. Sagt euch gute Nacht, auf Wunsch mit Geschichte."
        content.sound = .default
        let trigger = UNCalendarNotificationTrigger(dateMatching: DateComponents(hour: m / 60, minute: m % 60), repeats: true)
        try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }
}

// MARK: - Vorlesen und Abspielen

@MainActor
final class StoryReader: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published private(set) var speaking = false
    private let synth = AVSpeechSynthesizer()
    override init() { super.init(); synth.delegate = self }

    func toggle(_ text: String) {
        if speaking { stop(); return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        let u = AVSpeechUtterance(string: text.replacingOccurrences(of: "\n\n", with: "\n"))
        u.voice = AVSpeechSynthesisVoice(language: "de-DE")
        u.rate = AVSpeechUtteranceDefaultSpeechRate * 0.85
        u.pitchMultiplier = 1.05
        u.postUtteranceDelay = 0.3
        synth.speak(u)
        speaking = true
    }
    func stop() { synth.stopSpeaking(at: .immediate); speaking = false }

    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) { Task { @MainActor in self.speaking = false } }
    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didCancel u: AVSpeechUtterance) { Task { @MainActor in self.speaking = false } }
}

@MainActor
final class SongPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var playing: String? = nil
    private var player: AVAudioPlayer?

    func toggle(_ song: BedtimeContent.Song) {
        if playing == song.id { stop(); return }
        stop()
        guard let url = Bundle.main.url(forResource: song.audio, withExtension: "m4a"),
              let p = try? AVAudioPlayer(contentsOf: url) else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        p.delegate = self
        p.volume = 0.8
        p.play()                                  // spielt einmal; endet von selbst (Einschlafen)
        player = p
        playing = song.id
    }
    func stop() { player?.stop(); player = nil; playing = nil }

    nonisolated func audioPlayerDidFinishPlaying(_ p: AVAudioPlayer, successfully flag: Bool) { Task { @MainActor in self.playing = nil } }
}

// MARK: - Gute-Nacht-Ansicht

struct BedtimeView: View {
    var onClose: () -> Void
    @AppStorage(PetFigureStore.key, store: STShared.defaults) private var figureID = PetFigure.defaultID
    @AppStorage(SceneStore.key, store: STShared.defaults) private var sceneID = PetScene.defaultID
    @State private var asleep = false
    @State private var dimmed = false
    @State private var story: BedtimeContent.Story?
    @State private var showSongs = false
    @StateObject private var reader = StoryReader()
    @StateObject private var songs = SongPlayer()

    private let content = BedtimeContent.shared
    private var day: Int { EpochDay.of(.now) }
    private var figure: PetFigure { PetFigure.byID(figureID) }

    var body: some View {
        ZStack {
            VStack(spacing: 18) {
                HStack {
                    Button(action: close) { Image(systemName: "xmark").font(.headline).padding(10).background(.ultraThinMaterial, in: Circle()) }
                        .accessibilityLabel("Schließen")
                    Spacer()
                }
                Spacer(minLength: 0)
                Text(asleep ? "Schlaf gut!" : "Gute Nacht, \(figure.name)").font(.largeTitle.bold())
                Text(asleep ? (content.pick(content.sleep, day: day, salt: 4) ?? "") : (content.pick(content.goodnight, day: day, salt: 5) ?? ""))
                    .font(.title3).multilineTextAlignment(.center).opacity(0.9).fixedSize(horizontal: false, vertical: true)
                PetFigureView(pose: PetPose(mood: asleep ? .hibernation : .tired, overlays: asleep ? [] : [.yawning], energy: 70, rest: asleep ? 100 : 40))
                    .frame(width: 250, height: 250)
                    .accessibilityLabel(asleep ? "\(figure.name) schläft" : "\(figure.name) ist müde")
                Spacer(minLength: 0)
                if asleep {
                    Text("Leg das Handy jetzt weg. Das Display wird gleich dunkel.").font(.footnote).opacity(0.75)
                } else {
                    actions
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .foregroundStyle(.white)
            // Hintergrund als .background: ein randlos skaliertes Bild darf die Breite des Inhalts nicht vergrößern
            .background { background }
            if dimmed {
                Color.black.opacity(0.9).ignoresSafeArea()
                    .onTapGesture { dimmed = false }
                    .accessibilityLabel("Display abgedunkelt. Tippen zum Aufhellen.")
                    .transition(.opacity)
            }
        }
        .environment(\.colorScheme, .dark)
        .sheet(item: $story) { s in StoryView(story: s, reader: reader) }
        .sheet(isPresented: $showSongs) { SongListView(songs: content.songs, player: songs) }
        .onDisappear { reader.stop(); songs.stop() }
        .task(id: asleep) {
            guard asleep else { return }
            try? await Task.sleep(for: .seconds(10))
            withAnimation(.easeInOut(duration: 2)) { dimmed = true }
        }
    }

    private var background: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.04, green: 0.05, blue: 0.16), Color(red: 0.10, green: 0.10, blue: 0.30)], startPoint: .top, endPoint: .bottom)
            if let asset = PetScene.byID(sceneID).asset {
                GeometryReader { g in
                    Image(asset).resizable().scaledToFill().brightness(-0.35).saturation(0.7).opacity(0.75)
                        .frame(width: g.size.width, height: g.size.height).clipped()
                }
            }
            LinearGradient(colors: [.black.opacity(0.5), .clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
        }
        .ignoresSafeArea()
    }

    private var actions: some View {
        VStack(spacing: 12) {
            if let s = content.storyOfTheDay(day) {
                Button { story = s } label: {
                    Label("Geschichte: \(s.title)", systemImage: "book.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered).controlSize(.large)
            }
            HStack(spacing: 12) {
                Menu {
                    ForEach(content.stories) { s in Button(s.title) { story = s } }
                } label: { Label("Andere", systemImage: "books.vertical").frame(maxWidth: .infinity) }
                .buttonStyle(.bordered).controlSize(.large)
                Button { showSongs = true } label: { Label("Schlaflied", systemImage: "music.note").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).controlSize(.large)
            }
            Button {
                SleepStore.markGoodnight()
                UIImpactFeedbackGenerator(style: .soft).impactOccurred()
                withAnimation(.easeInOut(duration: 0.8)) { asleep = true }
            } label: { Label("Gute Nacht sagen", systemImage: "moon.zzz.fill").frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).controlSize(.large)
        }
    }

    private func close() { reader.stop(); songs.stop(); onClose() }
}

struct StoryView: View {
    let story: BedtimeContent.Story
    @ObservedObject var reader: StoryReader
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 14) {
                        PetFigureView(figure: PetFigure.byID(story.figure), pose: PetPose(mood: .content), animated: false)
                            .frame(width: 64, height: 64)
                        Text("ca. \(story.minutes) Min").font(.subheadline).foregroundStyle(.secondary)
                    }
                    Text(story.text).font(.title3).lineSpacing(6)
                }
                .padding()
            }
            .navigationTitle(story.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fertig") { reader.stop(); dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    Button { reader.toggle(story.title + ".\n" + story.text) } label: {
                        Label(reader.speaking ? "Stopp" : "Vorlesen", systemImage: reader.speaking ? "stop.fill" : "speaker.wave.2.fill")
                    }
                }
            }
        }
        .onDisappear { reader.stop() }
    }
}

struct SongListView: View {
    let songs: [BedtimeContent.Song]
    @ObservedObject var player: SongPlayer
    @Environment(\.dismiss) private var dismiss
    @State private var open: String?

    var body: some View {
        NavigationStack {
            List(songs) { s in
                VStack(alignment: .leading, spacing: 8) {
                    Button { player.toggle(s); open = s.id } label: {
                        HStack {
                            Image(systemName: player.playing == s.id ? "stop.circle.fill" : "play.circle.fill").font(.title)
                            VStack(alignment: .leading) {
                                Text(s.title).font(.headline)
                                Text(s.credit).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    if open == s.id { Text(s.lyrics).font(.body).foregroundStyle(.secondary).padding(.leading, 40) }
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("Schlaflieder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fertig") { player.stop(); dismiss() } } }
        }
    }
}

// MARK: - Karten auf dem Home-Screen

/// Abends: Einladung zur Gute-Nacht-Routine.
struct BedtimePromptCard: View {
    let phase: DayPhase
    let onStart: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Text("🌙").font(.largeTitle)
            VStack(alignment: .leading, spacing: 2) {
                Text(phase == .night ? "Schlafenszeit" : "Bald Schlafenszeit").font(.headline)
                Text("Geschichte, Schlaflied und gute Nacht sagen.").font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Los", action: onStart).buttonStyle(.borderedProminent)
        }
        .padding(14)
        .background(Color.indigo.opacity(0.16), in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }
}

/// Morgens: Gruß, Bericht über die Nacht, Tipp des Tages.
struct MorningCard: View {
    let info: MorningInfo
    let onDismiss: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Guten Morgen", systemImage: "sun.max.fill").font(.caption.weight(.semibold)).foregroundStyle(.orange)
            Text(info.greeting).font(.headline)
            Text(info.report.detail).font(.subheadline)
            if let tip = info.tip {
                Text("Tipp für heute: \(tip)").font(.subheadline).foregroundStyle(.secondary).padding(.top, 2)
            }
            HStack { Spacer(); Button("Danke!", action: onDismiss).buttonStyle(.bordered) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.13), in: RoundedRectangle(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Einstellungen

struct SleepSettingsSection: View {
    @State private var cfg = SleepStore.config
    @AppStorage("sleep.reminder", store: STShared.defaults) private var reminder = false

    private func date(_ m: Int) -> Date { Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: .now) ?? .now }
    private func minutes(_ d: Date) -> Int { SleepStore.minuteOfDay(d) }

    var body: some View {
        Section {
            Toggle("Gute-Nacht-Routine", isOn: $cfg.enabled)
            DatePicker("Schlafenszeit", selection: Binding(get: { date(cfg.bedMin) }, set: { cfg.bedMin = minutes($0) }), displayedComponents: .hourAndMinute)
                .disabled(!cfg.enabled)
            DatePicker("Aufstehzeit", selection: Binding(get: { date(cfg.wakeMin) }, set: { cfg.wakeMin = minutes($0) }), displayedComponents: .hourAndMinute)
                .disabled(!cfg.enabled)
            Toggle("Erinnerung zur Schlafenszeit", isOn: $reminder).disabled(!cfg.enabled)
        } header: {
            Text("Abend und Morgen")
        } footer: {
            Text("30 Minuten vor der Schlafenszeit wird dein Wesen müde und lädt zur Gute-Nacht-Routine ein. Morgens gibt es einen kurzen Bericht über die Nacht und einen Tipp für den Tag.")
        }
        .onChange(of: cfg) { _, new in SleepStore.config = new; Task { await BedtimeReminder.apply() } }
        .onChange(of: reminder) { _, _ in Task { await BedtimeReminder.apply() } }
    }
}
