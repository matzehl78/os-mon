// WallpaperExport.swift: Hintergrundbild für Sperr- und Home-Bildschirm mit dem eigenen Wesen.
//
// iOS erlaubt Apps nicht, das Hintergrundbild selbst zu setzen. Deshalb: Bild in Bildschirmgröße rendern
// (gewählte Szene in voller Auflösung, Figur in aktueller Stimmung mit Fest-Zubehör, oben Platz für die Uhr),
// über „Bild sichern“ in Fotos legen und dort „Als Hintergrundbild verwenden“.

import SwiftUI

/// Das Motiv. Plattform bei 72 % der Höhe, Figur 62 % der Breite: Uhr, Datum und Widgets bleiben oben frei.
struct WallpaperArt: View {
    let scene: PetScene
    let figure: PetFigure
    let pose: PetPose
    let hour: Double

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let feetY = h * 0.72, size = w * 0.62
            ZStack(alignment: .topLeading) {
                background(feetY: feetY)
                PetFigureView(figure: figure, pose: pose, animated: false)
                    .frame(width: size, height: size)
                    .offset(x: (w - size) / 2, y: feetY - size * 0.92)
            }
        }
    }

    @ViewBuilder
    private func background(feetY: CGFloat) -> some View {
        if let asset = scene.asset, let ui = UIImage(named: asset) {
            Canvas { ctx, size in
                let aspect = ui.size.width / ui.size.height
                let fit = sceneFit(layerW: size.width, layerH: size.height, feetY: feetY, floor: scene.floor, aspect: aspect)
                let iw = fit.height * aspect
                var g = ctx
                g.addFilter(SceneMood.filter(pose.mood, hour: hour))
                g.draw(Image(uiImage: ui), in: CGRect(x: (size.width - iw) / 2, y: fit.top, width: iw, height: fit.height))
            }
        } else {
            LinearGradient(colors: [pose.mood.color, Color(red: 14 / 255, green: 18 / 255, blue: 48 / 255)],
                           startPoint: .top, endPoint: .bottom)
        }
    }
}

enum WallpaperExport {
    /// Aktueller Stand: letzte Stimmung, gewählte Figur und Szene, heutiges Fest-Zubehör.
    static func currentArt(now: Date = .now) -> WallpaperArt {
        let d = STShared.defaults
        var pose = PetSync.lastSnapshot().map { PetPose(snapshot: $0.snapshot) } ?? .preview
        if d.object(forKey: "festivals.enabled") as? Bool ?? true {
            let m = d.integer(forKey: "user.birthday.m"), dd = d.integer(forKey: "user.birthday.d")
            pose.accessory = Festivals.today(EpochDay.of(now), birthday: m > 0 && dd > 0 ? (m, dd) : nil,
                                             hatchDay: nil, pet: PetFigureStore.current.name)?.accessory
        }
        return WallpaperArt(scene: PetScene.byID(d.string(forKey: SceneStore.key)), figure: PetFigureStore.current,
                            pose: pose, hour: now.hourOfDay)
    }

    @MainActor
    static func render(size: CGSize, scale: CGFloat) -> UIImage? {
        let r = ImageRenderer(content: currentArt().frame(width: size.width, height: size.height))
        r.scale = scale
        r.isOpaque = true
        return r.uiImage
    }
}

/// Vollbild-Vorschau mit Uhr-Platzhalter, „Bild sichern“ und kurzer Anleitung.
struct WallpaperPreview: View {
    let onClose: () -> Void
    @Environment(\.displayScale) private var scale
    @State private var image: UIImage?
    @State private var showHelp = false

    var body: some View {
        GeometryReader { geo in
            ZStack {
                WallpaperExport.currentArt()
                // So ungefähr sitzt die Uhr auf dem Sperrbildschirm
                VStack(spacing: 2) {
                    Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide))
                        .font(.headline)
                    Text(Date.now, format: .dateTime.hour().minute())
                        .font(.system(size: 84, weight: .semibold, design: .rounded))
                }
                .foregroundStyle(.white.opacity(0.9))
                .shadow(radius: 6)
                .frame(maxHeight: .infinity, alignment: .top)
                .padding(.top, max(geo.safeAreaInsets.top, 54) + 14)
                .accessibilityHidden(true)

                VStack(spacing: 10) {
                    Spacer()
                    if showHelp { help }
                    HStack(spacing: 12) {
                        Button("Fertig", action: onClose)
                            .buttonStyle(.bordered)
                            .tint(.white)
                            .foregroundStyle(.white)
                        if let image {
                            ShareLink(item: Image(uiImage: image), preview: SharePreview("OS-Mon Hintergrund", image: Image(uiImage: image))) {
                                Label("Bild sichern", systemImage: "square.and.arrow.down")
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.white)
                            .foregroundStyle(.black)
                            .simultaneousGesture(TapGesture().onEnded { showHelp = true })
                        } else {
                            ProgressView()
                        }
                    }
                    .controlSize(.large)
                    Button(showHelp ? "Anleitung ausblenden" : "So wird es dein Hintergrund") { showHelp.toggle() }
                        .font(.footnote).foregroundStyle(.white.opacity(0.85))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, geo.safeAreaInsets.bottom + 12)
            }
            .ignoresSafeArea()
            .task {
                let full = CGSize(width: geo.size.width, height: geo.size.height + geo.safeAreaInsets.top + geo.safeAreaInsets.bottom)
                image = WallpaperExport.render(size: full, scale: max(scale, 2))
            }
        }
        .ignoresSafeArea()
        .statusBarHidden()
    }

    private var help: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("1. „Bild sichern“ tippen → „Bild sichern“ wählen")
            Text("2. Fotos öffnen, Bild antippen, Teilen → „Als Hintergrundbild verwenden“")
            Text("3. Tipp: Das OS-Mon-Widget für den Sperrbildschirm zeigt die Stimmung live.")
        }
        .font(.footnote)
        .foregroundStyle(.white)
        .padding(12)
        .background(.black.opacity(0.45), in: RoundedRectangle(cornerRadius: 14))
        .transition(.opacity)
    }
}
