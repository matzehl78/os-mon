// MiniGameUI.swift: Mini-Spiele (30 Sekunden, wenige Runden pro Tag). Gegenstück: MiniGameUI.kt.
// Regeln, Zufall und Punkte: Shared/Logic/MiniGames.swift. Belohnung: Erfolge im Sammelalbum und eine
// Freude-Reaktion auf der Startseite.

import SwiftUI

/// Knopf auf der Startseite: „🎮 Spielen · 2/2“ (aus, wenn die Eltern Spiele ausgeschaltet haben).
struct GameButton: View {
    let version: Int
    let action: () -> Void
    var body: some View {
        let limit = GameStore.limit, left = GameStore.left
        if limit > 0 {
            Button(action: action) { Text("🎮 Spielen · \(left)/\(limit)").font(.subheadline) }
                .accessibilityLabel("Mini-Spiele, heute noch \(left) Runden")
                .id(version)
        }
    }
}

struct GameView: View {
    let figureName: String
    let stage: GrowthStage?
    /// (Sterne, Satz) nach einer Runde; nil = ohne zu spielen geschlossen
    let onClose: (Int?, String?) -> Void
    var demoGame: MiniGame? = nil                 // Screenshots: direkt im Spiel
    private enum Phase { case choose, play, result }
    @State private var phase = Phase.choose
    @State private var game = MiniGame.stars
    @State private var score = 0
    @State private var left = GameStore.left
    private let premium = PremiumFlag.isPremium
    private let outfit = WardrobeStore.outfit

    var body: some View {
        ZStack {
            Color(red: 0.06, green: 0.08, blue: 0.2).ignoresSafeArea()
            VStack(spacing: 14) {
                switch phase {
                case .choose: choose
                case .play: GamePlayView(game: game, stage: stage) { s in
                        score = s
                        GameStore.recordFinish()
                        SoundPlayer.shared.play(.gameEnd)
                        phase = .result
                    }
                case .result: result
                }
            }
            .padding()
            .foregroundStyle(.white)
        }
        .onAppear { if let g = demoGame { game = g; phase = .play } }
    }

    private func close() {
        if phase == .result { let st = MiniGames.stars(score); onClose(st, MiniGames.cheer(st, name: figureName)) } else { onClose(nil, nil) }
    }

    private func pose(_ mood: PetMood = .radiant) -> PetPose {
        var p = PetPose(mood: mood, accessory: outfit.hat); p.glasses = outfit.glasses; p.stage = stage; return p
    }

    private var choose: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("🎮 Mini-Spiele").font(.title2.weight(.semibold))
                Spacer()
                Button("Fertig") { close() }
            }
            Text(left > 0 ? "Heute noch \(left) \(left == 1 ? "Runde" : "Runden") · je 30 Sekunden"
                 : "Für heute ist Schluss – \(figureName) braucht jetzt Pause. Morgen geht’s weiter! 🌙")
                .font(.callout).opacity(0.8)
            ForEach(MiniGame.allCases, id: \.self) { g in
                let ok = !g.premium || premium
                HStack(spacing: 12) {
                    Text(g.emoji).font(.system(size: 32))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(g.title).font(.headline)
                        Text(ok ? g.hint : "💎 Premium (Eltern-Bereich)").font(.footnote).opacity(0.75)
                    }
                    Spacer()
                    Button("Los") { guard GameStore.left > 0 else { return }; GameStore.recordStart(); left = GameStore.left; game = g; phase = .play }.buttonStyle(.borderedProminent).disabled(!ok || left == 0)
                }
                .padding(14)
                .background(.white.opacity(ok ? 0.12 : 0.05), in: RoundedRectangle(cornerRadius: 16))
            }
            Spacer()
            PetFigureView(pose: pose()).frame(width: 150, height: 150).frame(maxWidth: .infinity)
        }
    }

    private var result: some View {
        let st = MiniGames.stars(score)
        return VStack(spacing: 12) {
            Spacer().frame(height: 20)
            Text("\(game.emoji) \(game.title)").font(.title2)
            Text("\(score) Punkte").font(.system(size: 44, weight: .bold))
            Text(String(repeating: "⭐", count: st) + String(repeating: "☆", count: 3 - st)).font(.system(size: 40)).foregroundStyle(.yellow)
                .accessibilityLabel("\(st) von 3 Sternen")
            PetFigureView(pose: pose(), reaction: ActiveReaction(kind: st >= 2 ? .spin : .giggle, start: .now, say: ""))
                .frame(width: 180, height: 180)
            Text(MiniGames.cheer(st, name: figureName)).font(.headline).multilineTextAlignment(.center)
            Spacer()
            HStack(spacing: 12) {
                if left > 0 { Button("Noch eine Runde (\(left))") { phase = .choose }.buttonStyle(.borderedProminent) }
                Button("Fertig") { close() }.buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct Pop: Identifiable { let id = UUID(); let x: Float; let y: Float; let text: String; let at: Float }

/// Die laufende Runde: Zeichenfläche mit Zielen, Punkte, Zeitbalken; das Wesen schaut unten rechts zu.
struct GamePlayView: View {
    let game: MiniGame
    let stage: GrowthStage?
    let onEnd: (Int) -> Void
    @State private var sim: MiniGameSim
    @State private var pops: [Pop] = []
    @State private var last: Date? = nil
    @State private var ended = false

    init(game: MiniGame, stage: GrowthStage?, onEnd: @escaping (Int) -> Void) {
        self.game = game; self.stage = stage; self.onEnd = onEnd
        _sim = State(initialValue: MiniGameSim(game, seed: Int64(Date().timeIntervalSince1970 * 1000) & 0xFFFFFFFF))
    }

    var body: some View {
        TimelineView(.animation) { tl in
            let _ = advance(tl.date)
            VStack(spacing: 10) {
                HStack(spacing: 12) {
                    Text("\(game.emoji) \(sim.score)").font(.system(size: 26, weight: .bold)).accessibilityLabel("\(sim.score) Punkte")
                    ProgressView(value: Double(sim.left / MiniGameSim.duration)).tint(.yellow)
                    Text("\(Int(sim.left.rounded(.up))) s").font(.subheadline.monospacedDigit())
                }
                GeometryReader { geo in
                    let w = Float(geo.size.width), h = Float(geo.size.height)
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, size in
                            for t in sim.targets { draw(ctx, t, size) }
                        }
                        ForEach(pops) { p in
                            let a = max(0, 1 - Double(sim.time - p.at) / 0.8)
                            Text(p.text).font(.title3.bold()).opacity(a)
                                .position(x: CGFloat(p.x * w), y: CGFloat((p.y - 0.06 - (sim.time - p.at) * 0.08) * h))
                        }
                        PetFigureView(pose: { var q = PetPose(mood: .radiant); q.stage = stage; return q }())
                            .frame(width: 70, height: 70)
                            .position(x: geo.size.width - 44, y: geo.size.height - 44)
                            .allowsHitTesting(false)
                    }
                    .background(LinearGradient(colors: background, startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 20))
                    .contentShape(Rectangle())
                    .gesture(SpatialTapGesture().onEnded { v in
                        if let hit = sim.tap(Float(v.location.x) / w, Float(v.location.y) / h, aspect: h / w) {
                            pops.append(Pop(x: hit.x, y: hit.y, text: hit.points > 0 ? "+\(hit.points)" : hit.kind == .cloud ? "−1" : "0", at: sim.time))
                            SoundPlayer.shared.play(hit.kind == .cloud ? .oops : .pop)
                        }
                    })
                }
            }
        }
    }

    private var background: [Color] {
        switch game {
        case .stars: return [Color(red: 0.1, green: 0.12, blue: 0.36), Color(red: 0.04, green: 0.05, blue: 0.16)]
        case .bubbles: return [Color(red: 0.31, green: 0.76, blue: 0.97), Color(red: 0.11, green: 0.43, blue: 0.6)]
        case .fireflies: return [Color(red: 0.05, green: 0.16, blue: 0.12), Color(red: 0.02, green: 0.08, blue: 0.05)]
        }
    }

    private func advance(_ now: Date) {
        if let l = last { sim.step(Float(min(0.1, now.timeIntervalSince(l)))) }
        DispatchQueue.main.async {
            last = now
            pops.removeAll { sim.time - $0.at > 0.8 }
            if sim.done && !ended { ended = true; onEnd(sim.score) }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ t: GameTarget, _ size: CGSize) {
        let a = Double(t.alpha(sim.time))
        let c = CGPoint(x: CGFloat(t.x) * size.width, y: CGFloat(t.y) * size.height)
        let r = CGFloat(t.r) * size.width
        func circle(_ p: CGPoint, _ rr: CGFloat) -> Path { Path(ellipseIn: CGRect(x: p.x - rr, y: p.y - rr, width: 2 * rr, height: 2 * rr)) }
        switch t.kind {
        case .star, .gold:
            let col: Color = t.kind == .gold ? Color(red: 1, green: 0.76, blue: 0.03) : Color(red: 1, green: 0.96, blue: 0.62)
            ctx.fill(circle(c, r * 1.5), with: .color(col.opacity(0.25 * a)))
            let s = star(c, r, CGFloat(t.age(sim.time)) * 1.5)
            ctx.fill(s, with: .color(col.opacity(a)))
            ctx.stroke(s, with: .color(Color(red: 0.11, green: 0.12, blue: 0.29).opacity(a)), lineWidth: r * 0.08)
        case .bubble:
            ctx.fill(circle(c, r), with: .color(.white.opacity(0.18 * a)))
            ctx.stroke(circle(c, r), with: .color(.white.opacity(0.85 * a)), lineWidth: r * 0.08)
            ctx.fill(circle(CGPoint(x: c.x - r * 0.4, y: c.y - r * 0.4), r * 0.22), with: .color(.white.opacity(0.8 * a)))
        case .cloud:
            let g = Color(red: 0.33, green: 0.43, blue: 0.48).opacity(a)
            ctx.fill(circle(CGPoint(x: c.x - r * 0.45, y: c.y + r * 0.1), r * 0.55), with: .color(g))
            ctx.fill(circle(CGPoint(x: c.x, y: c.y - r * 0.15), r * 0.7), with: .color(g))
            ctx.fill(circle(CGPoint(x: c.x + r * 0.5, y: c.y + r * 0.12), r * 0.5), with: .color(g))
            var b = Path()
            b.move(to: CGPoint(x: c.x + r * 0.05, y: c.y + r * 0.2)); b.addLine(to: CGPoint(x: c.x - r * 0.2, y: c.y + r * 0.7))
            b.addLine(to: CGPoint(x: c.x + r * 0.05, y: c.y + r * 0.62)); b.addLine(to: CGPoint(x: c.x - r * 0.08, y: c.y + r * 1.05))
            b.addLine(to: CGPoint(x: c.x + r * 0.28, y: c.y + r * 0.5)); b.addLine(to: CGPoint(x: c.x + r * 0.05, y: c.y + r * 0.55)); b.closeSubpath()
            ctx.fill(b, with: .color(Color.yellow.opacity(a)))
        case .fly:
            ctx.fill(circle(c, r * 1.6), with: .radialGradient(Gradient(colors: [Color(red: 1, green: 0.95, blue: 0.46).opacity(0.7 * a), .clear]),
                                                                center: c, startRadius: 0, endRadius: r * 1.6))
            ctx.fill(circle(c, r * 0.35), with: .color(Color(red: 1, green: 0.99, blue: 0.9).opacity(a)))
        }
    }

    private func star(_ c: CGPoint, _ r: CGFloat, _ rot: CGFloat) -> Path {
        var p = Path()
        for k in 0..<10 {
            let a = rot + (-.pi / 2 + CGFloat(k) * .pi / 5), rr = k % 2 == 0 ? r : r * 0.45
            let pt = CGPoint(x: c.x + rr * cos(a), y: c.y + rr * sin(a))
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath(); return p
    }
}
