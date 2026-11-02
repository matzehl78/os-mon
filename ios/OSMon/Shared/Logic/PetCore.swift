// PetCore.swift: Swift-Port von PetCore.kt (Kotlin) für Widget, Extensions und App
//
// Targets: Widget-Extension, Monitor-Extension (falls dort ausgewertet), Haupt-App, Test-Target.
// Abhängigkeit: ScreenTimeShared.swift (liefert `UsageGroup`).
// Rein und ohne UI/Plattform-Zugriffe: alle Funktionen sind deterministisch.
//
// Parität: Verhalten und Namen entsprechen PetCore.kt. Die Referenzwerte stehen in
// golden_vectors.json (erzeugt von golden_reference.py), geprüft in PetCoreGoldenTests.swift.
// Änderungen an Formeln immer in allen drei Fassungen (Kotlin, Swift, Python) nachziehen.
//
// Namen tragen den Präfix "Pet", damit sie nicht mit den Typen des Widget-Codes kollidieren
// (dort existiert z. B. ein eigenes `Mood` für die Anzeige).

import Foundation

// MARK: - Typen

struct PetConfig: Codable, Equatable {
    var screenGoalMin: Float = 180      // Z; nach Woche 1 adaptiv: 0,8 * Median, begrenzt auf 90...240
    var stepGoal: Int = 7000
    var pickupRef: Float = 50
}

struct PetInputs: Codable, Equatable {
    var batteryPct: Float? = nil
    var charging: Bool = false
    var lowPower: Bool = false
    var freeStorageRatio: Float? = nil  // frei / gesamt, 0...1
    var screenMinutes: Float? = nil     // heute
    var nightMinutes: Float? = nil
    var pickups: Int? = nil             // nur Android; auf iOS nil
    var steps: Int? = nil
    var pats: Int = 0
    var focusSessions: Int = 0
}

/// E Energie, R Ruhe, F Fitness, V Vitalität, A Zuneigung (je 0...100, nil = ausgeblendet).
struct PetStats: Codable, Equatable {
    var e: Float? = nil
    var r: Float? = nil
    var f: Float? = nil
    var v: Float? = nil
    var a: Float? = nil
}

enum PetMood: String, Codable, CaseIterable {
    case hibernation, sick, tired, content, radiant

    var lowerBound: Float {
        switch self {
        case .hibernation: return 0
        case .sick:        return 20
        case .tired:       return 40
        case .content:     return 65
        case .radiant:     return 85
        }
    }
}

enum PetOverlay: String, Codable, CaseIterable { case yawning, chubby, stressed, sluggish }

enum PetForm: String, Codable, CaseIterable { case chatterling, bingeon, pixelo, zenfuchs, wandero }

enum PetBranch: String, Codable, CaseIterable { case radiant, standard, wild }

struct PetEvolution: Codable, Equatable {
    var form: PetForm
    var hybrid: PetForm?
    var branch: PetBranch
}

/// Codable: kann direkt im App-Group-Speicher abgelegt werden (zusammen mit einem Zeitstempel).
struct PetSnapshot: Codable, Equatable {
    var stats: PetStats
    var wellbeing: Float
    var mood: PetMood
    var overlays: Set<PetOverlay>
}

// MARK: - Engine

enum PetCore {
    private static let hysteresis: Float = 5
    private static let maxPats = 4          // Streicheln pro Tag gedeckelt

    private static func clamp(_ x: Float, _ lo: Float, _ hi: Float) -> Float { Swift.min(Swift.max(x, lo), hi) }
    private static func clamp01(_ x: Float) -> Float { clamp(x, 0, 1) }

    // ---- Zielwerte aus Sensordaten

    static func energy(_ i: PetInputs) -> Float? {
        guard let b = i.batteryPct else { return nil }
        return clamp(b * (i.lowPower ? 0.85 : 1) + (i.charging ? 10 : 0), 0, 100)
    }

    static func fitness(_ i: PetInputs) -> Float? {
        guard let f = i.freeStorageRatio else { return nil }
        return 100 * powf(clamp01((f - 0.05) / 0.25), 0.8)
    }

    /// Stress S. Mit Pickups (Android) 0,65/0,25/0,10, ohne (iOS) 0,72/0,28.
    static func stress(_ i: PetInputs, _ c: PetConfig) -> Float? {
        guard let t = i.screenMinutes else { return nil }
        let n = i.nightMinutes ?? 0
        let x: Float
        if let p = i.pickups {
            x = 0.65 * t / (2 * c.screenGoalMin) + 0.25 * n / 45 + 0.10 * Float(p) / (2 * c.pickupRef)
        } else {
            x = 0.72 * t / (2 * c.screenGoalMin) + 0.28 * n / 45
        }
        return clamp(100 * x, 0, 100)
    }

    static func rest(_ i: PetInputs, _ c: PetConfig) -> Float? {
        stress(i, c).map { 100 - $0 }
    }

    static func vitality(_ i: PetInputs, _ c: PetConfig) -> Float? {
        guard let s = i.steps else { return nil }
        return 100 * powf(clamp01(Float(s) / Float(c.stepGoal)), 0.7)
    }

    /// Zuneigung A = 0,7*R + 0,3*Interaktion; ohne R nur die Interaktionskomponente.
    static func affection(rest: Float?, _ i: PetInputs) -> Float {
        let interaction = Swift.min(100, 25 * Float(Swift.min(i.pats, maxPats)) + 50 * Float(i.focusSessions))
        if let r = rest { return 0.7 * r + 0.3 * interaction }
        return interaction
    }

    static func targets(_ i: PetInputs, _ c: PetConfig) -> PetStats {
        let r = rest(i, c)
        return PetStats(e: energy(i), r: r, f: fitness(i), v: vitality(i, c), a: affection(rest: r, i))
    }

    // ---- Trägheit

    /// Nähert die Werte den Zielen an (exponentiell, zeitbasiert).
    /// E: Zeitkonstante 0,5 h, R: 1,5 h, A: 6 h. F und V folgen dem Ziel direkt.
    static func step(prev: PetStats?, target: PetStats, dtHours: Float) -> PetStats {
        let dt = Swift.max(dtHours, 0)
        func ease(_ p: Float?, _ t: Float?, _ tau: Float) -> Float? {
            guard let t else { return nil }
            guard let p else { return t }
            return p + (t - p) * (1 - expf(-dt / tau))
        }
        return PetStats(
            e: ease(prev?.e, target.e, 0.5),
            r: ease(prev?.r, target.r, 1.5),
            f: target.f,
            v: target.v,
            a: ease(prev?.a, target.a, 6)
        )
    }

    // ---- Gesamtwohlbefinden

    /// H_roh = 0,30*E + 0,25*R + 0,20*F + 0,15*V + 0,10*A (renormiert über vorhandene Werte)
    /// H = H_roh * (0,6 + 0,4 * min(E,R,F,V) / 100)   ("schwächstes Glied")
    static func wellbeing(_ s: PetStats) -> Float {
        var parts: [(w: Float, v: Float)] = []
        if let e = s.e { parts.append((0.30, e)) }
        if let r = s.r { parts.append((0.25, r)) }
        if let f = s.f { parts.append((0.20, f)) }
        if let v = s.v { parts.append((0.15, v)) }
        if let a = s.a { parts.append((0.10, a)) }
        if parts.isEmpty { return 50 }
        let weightSum = parts.reduce(0) { $0 + $1.w }
        let raw = parts.reduce(0) { $0 + $1.w * $1.v } / weightSum
        let weakest = [s.e, s.r, s.f, s.v].compactMap { $0 }.min() ?? 100
        return clamp(raw * (0.6 + 0.4 * weakest / 100), 0, 100)
    }

    // ---- Zustände

    /// Hysterese ±5 Punkte gegen Flackern an den Grenzen.
    static func mood(_ h: Float, prev: PetMood?) -> PetMood {
        let all = PetMood.allCases
        var idx = prev.flatMap { all.firstIndex(of: $0) }
            ?? (all.lastIndex(where: { h >= $0.lowerBound }) ?? 0)
        while idx < all.count - 1 && h >= all[idx + 1].lowerBound + hysteresis { idx += 1 }
        while idx > 0 && h < all[idx].lowerBound - hysteresis { idx -= 1 }
        return all[idx]
    }

    static func overlays(_ s: PetStats) -> Set<PetOverlay> {
        var out = Set<PetOverlay>()
        if (s.e ?? 100) < 15 { out.insert(.yawning) }
        if (s.f ?? 100) < 20 { out.insert(.chubby) }
        if (s.r ?? 100) < 30 { out.insert(.stressed) }     // R < 30 entspricht S > 70
        if (s.v ?? 100) < 20 { out.insert(.sluggish) }
        return out
    }

    /// Ein kompletter Update-Zyklus: Eingaben -> neuer Zustand.
    static func tick(prev: PetSnapshot?, inputs: PetInputs, config: PetConfig, dtHours: Float) -> PetSnapshot {
        let stats = step(prev: prev?.stats, target: targets(inputs, config), dtHours: dtHours)
        let h = wellbeing(stats)
        return PetSnapshot(stats: stats, wellbeing: h, mood: mood(h, prev: prev?.mood), overlays: overlays(stats))
    }

    // ---- Evolution

    private static let thresholds: [(group: UsageGroup, form: PetForm, min: Float)] = [
        (.social, .chatterling, 0.35),
        (.entertainment, .bingeon, 0.35),
        (.games, .pixelo, 0.30),
        (.productivity, .zenfuchs, 0.30),
    ]

    /// shares: gewichtete Kategorienanteile der letzten 14 Tage (Summe ca. 1).
    /// outdoorShare: Anteil Outdoor/Navigation/Gesundheit (braucht eine eigene Gruppe im Mapping).
    /// Wandero greift sonst im Balance-Fall: keine Kategorie > 30 % und Tagesdurchschnitt <= Ziel.
    static func evolve(shares: [UsageGroup: Float], avgDailyMinutes: Float, avgWellbeing14: Float,
                       config: PetConfig, outdoorShare: Float = 0) -> PetEvolution {
        // Kandidaten nach Anteil absteigend; bei Gleichstand gewinnt die frühere Form (wie in Kotlin: stabile Sortierung)
        var cands: [(form: PetForm, share: Float, order: Int)] = []
        for (idx, t) in thresholds.enumerated() {
            let s = shares[t.group] ?? 0
            if s >= t.min { cands.append((t.form, s, idx)) }
        }
        if outdoorShare >= 0.25 { cands.append((.wandero, outdoorShare, thresholds.count)) }
        cands.sort { $0.share != $1.share ? $0.share > $1.share : $0.order < $1.order }

        let topShare = shares.filter { $0.key != .other }.values.max() ?? 0
        let balanced = topShare <= 0.30 && avgDailyMinutes <= config.screenGoalMin

        let form: PetForm
        if let first = cands.first {
            form = first.form
        } else if balanced {
            form = .wandero
        } else {                                            // nächstliegende Form (erstes Maximum)
            var best = thresholds[0]
            var bestShare = shares[best.group] ?? 0
            for t in thresholds.dropFirst() {
                let s = shares[t.group] ?? 0
                if s > bestShare { best = t; bestShare = s }
            }
            form = best.form
        }

        // Hybrid-Merkmal: Zweitplatzierter weniger als 5 Prozentpunkte dahinter
        var hybrid: PetForm? = nil
        if cands.count > 1, cands[0].share - cands[1].share < 0.05 { hybrid = cands[1].form }

        let branch: PetBranch = avgWellbeing14 >= 65 ? .radiant : (avgWellbeing14 < 40 ? .wild : .standard)
        return PetEvolution(form: form, hybrid: hybrid, branch: branch)
    }
}

// MARK: - Einbindung (Skizze)
//
// In Provider.getTimeline (OSMonWidget.swift), statt der eigenen Energie-Formel:
//
//   let inputs = PetInputs(batteryPct: Float(level), charging: charging,
//                          lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled,
//                          freeStorageRatio: freeRatio,                          // siehe README 3.3
//                          screenMinutes: Float(STShared.totalMinutes()),
//                          nightMinutes: Float(STShared.minutes("night")),
//                          steps: StepsStore.today)
//   let snap = PetCore.tick(prev: storedSnapshot, inputs: inputs, config: cfg, dtHours: Float(hoursSinceLast))
//
// Das Snapshot samt Zeitstempel (Codable) in der App Group ablegen und beim nächsten Aufruf als prev laden.
// Das Widget-eigene `Mood` kann durch PetMood ersetzt werden (Anzeige-Eigenschaften als Extension).
