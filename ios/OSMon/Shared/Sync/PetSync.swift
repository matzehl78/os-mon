// PetSync.swift: gemeinsamer Kern für App, Widget und App Intents (iOS 17+)
//
// Targets: Haupt-App UND Widget-Extension (beide mit derselben App Group).
// Abhängigkeiten in beiden Targets: PetCore.swift, ScreenTimeShared.swift (STShared, UsageGroup).
//
// Ein Zustand, eine Formelquelle: Home-Screen, Widget-Timeline, Streicheln im Widget und die
// Kurzbefehl-Aktion rechnen alle über PetSync.tick -> PetCore.tick und lesen/schreiben denselben
// Snapshot im App-Group-Speicher. Die alte Energie-Formel des Widgets (PetState/PetEngine) ist entfallen.
//
// Hierher verschoben (vorher nur im App-Target): StepsStore (aus StepsHealthKit.swift),
// EpochDay und DayLog (aus EvolutionJob.swift), Anzeige-Zuordnungen und PetCopy (aus HomeView.swift).
//
// Nebenläufigkeit: App und Widget sind getrennte Prozesse und teilen sich UserDefaults. Gleichzeitige
// Ticks können sich überschreiben; das Ergebnis ist dann ein Tick "zu wenig" Trägheit, kein falscher
// Zustand, weil jeder Tick vollständig aus Sensorwerten + letztem Snapshot neu rechnet.

import Foundation
import SwiftUI
import UIKit
import WidgetKit

// MARK: - Schritte (von StepsProvider in der App geschrieben, vom Widget gelesen)

enum StepsStore {
    private static func key(_ day: String) -> String { "steps.\(day)" }

    static var today: Int {
        get { STShared.defaults.integer(forKey: key(STShared.dayKey())) }
        set { STShared.defaults.set(newValue, forKey: key(STShared.dayKey())) }
    }
}

// MARK: - Tage zählen (lokales Datum)

enum EpochDay {
    private static var utc: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }

    /// Tage seit 1970-01-01 für das lokale Kalenderdatum von `date`.
    static func of(_ date: Date = .now) -> Int {
        let comps = Calendar.current.dateComponents([.year, .month, .day], from: date)
        let day = utc.date(from: comps) ?? date
        return Int((day.timeIntervalSince1970 / 86_400).rounded())
    }

    /// Lokale Mitternacht des Tages.
    static func date(_ epochDay: Int) -> Date {
        let ref = Date(timeIntervalSince1970: Double(epochDay) * 86_400)
        let comps = utc.dateComponents([.year, .month, .day], from: ref)
        return Calendar.current.date(from: comps) ?? ref
    }
}

// MARK: - Tageslog: Mittel des Wohlbefindens (Grundlage für den Gesundheits-Zweig der Evolution)

enum DayLog {
    private static let minGap: TimeInterval = 30 * 60        // höchstens eine Messung je 30 Minuten
    private static var defaults: UserDefaults { STShared.defaults }

    static func record(_ wellbeing: Float, now: Date = .now) {
        if let last = defaults.object(forKey: "daylog.last") as? Date, now.timeIntervalSince(last) < minGap { return }
        let day = EpochDay.of(now)
        defaults.set(defaults.float(forKey: "daylog.sum.\(day)") + wellbeing, forKey: "daylog.sum.\(day)")
        defaults.set(defaults.integer(forKey: "daylog.n.\(day)") + 1, forKey: "daylog.n.\(day)")
        defaults.set(now, forKey: "daylog.last")
    }

    static func average(day: Int) -> Float? {
        let n = defaults.integer(forKey: "daylog.n.\(day)")
        return n == 0 ? nil : defaults.float(forKey: "daylog.sum.\(day)") / Float(n)
    }
}

// MARK: - Anzeige-Zuordnungen (App und Widget)

extension PetMood {
    var title: String {
        switch self {
        case .hibernation: return "Winterschlaf"
        case .sick:        return "Kränklich"
        case .tired:       return "Müde"
        case .content:     return "Zufrieden"
        case .radiant:     return "Strahlend"
        }
    }

    /// Kompakte Darstellung fürs Widget (in der App zeichnet PetAvatarView das Wesen).
    var emoji: String {
        switch self {
        case .hibernation: return "😴"
        case .sick:        return "🤒"
        case .tired:       return "🥱"
        case .content:     return "🙂"
        case .radiant:     return "😄"
        }
    }

    var color: Color {
        switch self {
        case .hibernation: return Color(red: 0.29, green: 0.34, blue: 0.71)
        case .sick:        return Color(red: 0.85, green: 0.40, blue: 0.48)
        case .tired:       return Color(red: 0.88, green: 0.54, blue: 0.12)
        case .content:     return Color(red: 0.11, green: 0.60, blue: 0.60)
        case .radiant:     return Color(red: 0.18, green: 0.62, blue: 0.36)
        }
    }
}

extension PetForm {
    var badge: String {
        switch self {
        case .chatterling: return "💬"
        case .bingeon:     return "📺"
        case .pixelo:      return "🎮"
        case .zenfuchs:    return "🦊"
        case .wandero:     return "🍃"
        }
    }

    var title: String {
        switch self {
        case .chatterling: return "Chatterling"
        case .bingeon:     return "Bingeon"
        case .pixelo:      return "Pixelo"
        case .zenfuchs:    return "Zenfuchs"
        case .wandero:     return "Wandero"
        }
    }
}

enum PetCopy {
    /// Was das Wesen sagt: Overlays haben Vorrang vor der Grundstimmung.
    static func line(for s: PetSnapshot) -> String {
        if s.overlays.contains(.yawning)  { return "Gähn … mir geht die Energie aus." }
        if s.overlays.contains(.stressed) { return "Puh, ganz schön viel Bildschirm heute." }
        if s.overlays.contains(.chubby)   { return "Mir ist so eng … schaffen wir Platz?" }
        if s.overlays.contains(.sluggish) { return "Ich könnte mal ein paar Schritte vertragen." }
        switch s.mood {
        case .radiant:     return "Mir geht’s super!"
        case .content:     return "Alles gut bei uns."
        case .tired:       return "Ich bin heute ein bisschen müde."
        case .sick:        return "Mir ist nicht so gut …"
        case .hibernation: return "Zzz …"
        }
    }
}

// MARK: - Ergebnis eines Ticks

struct PetTick {
    let snapshot: PetSnapshot
    let inputs: PetInputs
    let at: Date
}

/// Ein vorausberechneter Zeitpunkt für die Widget-Timeline.
struct PetProjection {
    let date: Date
    let snapshot: PetSnapshot
    let battery: Float?
    let charging: Bool
}

// MARK: - Kern

enum PetSync {
    /// Ziele aus den Einstellungen (PetGoals in ScreenTimeShared.swift), sonst PRD-Startwerte.
    static var config: PetConfig {
        PetConfig(screenGoalMin: Float(PetGoals.screenMinutes), stepGoal: PetGoals.steps)
    }
    static let patCooldown: TimeInterval = 3 * 3600
    private static var defaults: UserDefaults { STShared.defaults }

    // ---- Tageszähler (Streicheln, Fokus)

    static func counter(_ name: String, now: Date = .now) -> Int {
        defaults.integer(forKey: "pet.\(name).\(STShared.dayKey(now))")
    }

    static func bump(_ name: String, by n: Int = 1, now: Date = .now) {
        defaults.set(counter(name, now: now) + n, forKey: "pet.\(name).\(STShared.dayKey(now))")
    }

    // ---- Tick: Sensoren -> PetCore -> Speicher

    /// Ein vollständiger Update-Zyklus. Aufrufbar aus App, Widget-Provider und App Intents.
    @discardableResult
    static func tick(now: Date = .now) -> PetTick {
        completeFocusIfDue(now)
        let prev = loadSnapshot()
        let inputs = liveInputs(now: now)
        learnDrain(inputs, now: now)

        let dt = prev.map { Float(now.timeIntervalSince($0.at) / 3600) } ?? 0.25
        let snap = PetCore.tick(prev: prev?.snapshot, inputs: inputs, config: config,
                                dtHours: min(max(dt, 0), 24))
        saveSnapshot(snap, at: now)
        DayLog.record(snap.wellbeing, now: now)
        return PetTick(snapshot: snap, inputs: inputs, at: now)
    }

    /// Letzter gespeicherter Zustand ohne neu zu rechnen (z. B. für Previews oder Fehlerfälle).
    static func lastSnapshot() -> (snapshot: PetSnapshot, at: Date)? { loadSnapshot() }

    // ---- Vorausberechnung für die Widget-Timeline

    /// Rechnet ab `t` alle `stepHours` bis `hours` voraus. Annahme: Laden bzw. Entladen läuft mit der
    /// gelernten Rate weiter, alle übrigen Eingaben bleiben gleich. Nichts wird gespeichert.
    static func project(from t: PetTick, hours: Double = 4, stepHours: Double = 0.25) -> [PetProjection] {
        let drain = Float(defaults.object(forKey: "pet.drain") as? Double ?? 6)
        var out = [PetProjection(date: t.at, snapshot: t.snapshot, battery: t.inputs.batteryPct,
                                 charging: t.inputs.charging)]
        var snap = t.snapshot
        let steps = Int((hours / stepHours).rounded())
        guard steps > 0 else { return out }

        for i in 1...steps {
            let h = Float(Double(i) * stepHours)
            var inputs = t.inputs
            if let b = t.inputs.batteryPct {
                inputs.batteryPct = t.inputs.charging ? min(100, b + 25 * h) : max(0, b - drain * h)
            }
            snap = PetCore.tick(prev: snap, inputs: inputs, config: config, dtHours: Float(stepHours))
            out.append(PetProjection(date: t.at.addingTimeInterval(Double(h) * 3600), snapshot: snap,
                                     battery: inputs.batteryPct, charging: inputs.charging))
        }
        return out
    }

    // ---- Widget neu laden, aber nur bei spürbarer Änderung (Reload-Budget schonen)

    /// Vergleicht mit dem zuletzt im Widget gezeigten Zustand, nicht mit dem letzten Tick:
    /// So summieren sich viele kleine Änderungen und lösen irgendwann doch einen Reload aus.
    /// force = true z. B. nach dem Streicheln (Button-Zustand ändert sich, Stimmung nicht unbedingt).
    static func reloadWidgetIfNeeded(_ s: PetSnapshot, force: Bool = false) {
        let lastMood = defaults.string(forKey: "pet.widget.mood")
        let lastH = defaults.object(forKey: "pet.widget.h") as? Double
        let lastOverlays = Set(defaults.stringArray(forKey: "pet.widget.overlays") ?? [])
        let overlays = Set(s.overlays.map(\.rawValue))
        let changed = lastMood != s.mood.rawValue || lastOverlays != overlays
            || lastH.map { abs($0 - Double(s.wellbeing)) >= 3 } ?? true
        guard force || changed else { return }
        markWidgetRendered(s)
        WidgetCenter.shared.reloadTimelines(ofKind: STShared.widgetKind)
    }

    /// Vom Widget-Provider nach dem Rendern aufrufen.
    static func markWidgetRendered(_ s: PetSnapshot) {
        defaults.set(s.mood.rawValue, forKey: "pet.widget.mood")
        defaults.set(Double(s.wellbeing), forKey: "pet.widget.h")
        defaults.set(s.overlays.map(\.rawValue).sorted(), forKey: "pet.widget.overlays")
    }

    // ---- Streicheln (wirkt laut PRD nur auf die Zuneigung, nicht auf die Energie)

    static func patReadyAt(now: Date = .now) -> Date? {
        guard let last = defaults.object(forKey: "pet.lastPat") as? Date else { return nil }
        let ready = last.addingTimeInterval(patCooldown)
        return ready > now ? ready : nil
    }

    static func canPat(now: Date = .now) -> Bool { patReadyAt(now: now) == nil }

    /// true, wenn das Streicheln gezählt wurde (höchstens 1× pro 3 h).
    @discardableResult
    static func pat(now: Date = .now) -> Bool {
        guard canPat(now: now) else { return false }
        defaults.set(now, forKey: "pet.lastPat")
        bump("pats", now: now)
        return true
    }

    // ---- Fokus-Session (zählt erst, wenn sie bis zum Ende durchläuft)

    static var focusEndsAt: Date? { defaults.object(forKey: "pet.focusEnds") as? Date }

    static func startFocus(minutes: Int, now: Date = .now) {
        defaults.set(now.addingTimeInterval(TimeInterval(minutes * 60)), forKey: "pet.focusEnds")
        defaults.set(minutes, forKey: "pet.focusLen")
    }

    static func cancelFocus() {
        defaults.removeObject(forKey: "pet.focusEnds")
    }

    private static func completeFocusIfDue(_ now: Date) {
        guard let end = focusEndsAt, end <= now else { return }
        // Der Session-Tag ist der Tag, an dem sie endete.
        bump("focusSessions", now: end)
        bump("focusMinutes", by: defaults.integer(forKey: "pet.focusLen"), now: end)
        defaults.removeObject(forKey: "pet.focusEnds")
    }

    // ---- Sensoren

    static func liveInputs(now: Date = .now) -> PetInputs {
        let device = UIDevice.current
        device.isBatteryMonitoringEnabled = true
        let level = device.batteryLevel                       // -1 = unbekannt (z. B. Simulator)
        let screenOn = defaults.bool(forKey: "feature.screentime")
        let stepsOn = defaults.bool(forKey: "feature.steps")
        return PetInputs(
            batteryPct: level >= 0 ? level * 100 : nil,
            charging: device.batteryState == .charging || device.batteryState == .full,
            lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled,
            freeStorageRatio: freeStorageRatio(),
            screenMinutes: screenOn ? Float(STShared.totalMinutes(day: STShared.dayKey(now))) : nil,
            nightMinutes: screenOn ? Float(STShared.minutes("night", day: STShared.dayKey(now))) : nil,
            pickups: nil,                                     // iOS liefert keine Pickups
            steps: stepsOn ? StepsStore.today : nil,
            pats: counter("pats", now: now),
            focusSessions: counter("focusSessions", now: now))
    }

    static func freeStorageRatio() -> Float? {
        let url = URL(fileURLWithPath: NSHomeDirectory())
        guard let v = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey,
                                                        .volumeTotalCapacityKey]),
              let free = v.volumeAvailableCapacityForImportantUsage,
              let total = v.volumeTotalCapacity, total > 0 else { return nil }
        return Float(free) / Float(total)
    }

    /// Entladerate lernen (gleitender Mittelwert, nur ungeladene Intervalle über 6 Minuten).
    private static func learnDrain(_ i: PetInputs, now: Date) {
        guard let level = i.batteryPct else { return }
        if let lastLevel = defaults.object(forKey: "pet.batt.level") as? Double,
           let lastAt = defaults.object(forKey: "pet.batt.at") as? Date {
            let hours = now.timeIntervalSince(lastAt) / 3600
            let wasCharging = defaults.bool(forKey: "pet.batt.charging")
            if hours > 0.1, !wasCharging, !i.charging, Double(level) < lastLevel {
                let rate = min((lastLevel - Double(level)) / hours, 30)
                let old = defaults.object(forKey: "pet.drain") as? Double ?? 6
                defaults.set(0.7 * old + 0.3 * rate, forKey: "pet.drain")
            }
        }
        defaults.set(Double(level), forKey: "pet.batt.level")
        defaults.set(now, forKey: "pet.batt.at")
        defaults.set(i.charging, forKey: "pet.batt.charging")
    }

    // ---- Snapshot-Speicher (App Group)

    private static func loadSnapshot() -> (snapshot: PetSnapshot, at: Date)? {
        guard let data = defaults.data(forKey: "pet.snapshot"),
              let snap = try? JSONDecoder().decode(PetSnapshot.self, from: data),
              let at = defaults.object(forKey: "pet.snapshot.at") as? Date else { return nil }
        return (snap, at)
    }

    private static func saveSnapshot(_ s: PetSnapshot, at: Date) {
        guard let data = try? JSONEncoder().encode(s) else { return }
        defaults.set(data, forKey: "pet.snapshot")
        defaults.set(at, forKey: "pet.snapshot.at")
    }
}
