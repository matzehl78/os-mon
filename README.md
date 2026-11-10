# OS-Mon: Projekte öffnen und starten

Zwei fertige Projekte mit allen Quelldateien an ihrem Platz, plus Werkzeuge.

```
osmon-projekt/
├─ android/   Android-Studio-Projekt (Kotlin, Compose, Glance-Widget, WorkManager)
├─ ios/       Xcode-Projekt als XcodeGen-Beschreibung (App, Widget, Bildschirmzeit-Monitor, Tests)
└─ tools/     Prototyp, Figuren-Generator, Python-Referenzen für die Testdaten
```

## Android

1. Android Studio (Ladybug oder neuer) → **Open** → Ordner `android`.
2. Android Studio lädt Gradle und alle Bibliotheken selbst. Fragt es nach dem Gradle-Wrapper,
   „OK“ wählen (die Wrapper-JAR liegt nicht bei und wird dabei erzeugt).
3. Gerät oder Emulator mit Android 10+ wählen → **Run ▶**.
4. Tests: Rechtsklick auf `app/src/test` → **Run Tests** (Engine, Pushes, Evolution, Figuren).

Paket/App-ID: `com.example.osmon`. Vor einer Veröffentlichung in `app/build.gradle.kts`
(`applicationId`) auf die eigene Domain ändern.

## iOS (Mac mit Xcode 16+)

1. Einmalig: `brew install xcodegen`
2. Im Ordner `ios`: `xcodegen` → erzeugt `OSMon.xcodeproj`.
3. Projekt öffnen, bei allen drei Targets (OSMon, OSMonWidget, OSMonMonitor) unter
   **Signing & Capabilities** das eigene Team wählen.
4. Auf einem **echten iPhone** starten (Bildschirmzeit und HealthKit-Hintergrund laufen nicht im Simulator).
5. Tests: Schema OSMon → **Product › Test** (läuft auch im Simulator).

Wichtig vor dem Store:
- **Family Controls** (Bildschirmzeit) funktioniert in der Entwicklung sofort, für TestFlight/App Store
  muss Apple das Entitlement freigeben (Antrag im Developer-Portal, siehe README Spike S1).
- App Group `group.com.example.osmon` und Bundle-IDs auf die eigene Domain ändern: in `project.yml`
  und in `OSMon/Shared/Logic/ScreenTimeShared.swift` (`STShared.appGroup`), Hintergrund-ID in
  `OSMon/App/PushResponder.swift`.

| Target | Inhalt |
|---|---|
| OSMon | App: Onboarding, Home, Einstellungen, Schritte, Bildschirmzeit, Meldungen |
| OSMonWidget | Widget (klein, mittel, Sperrbildschirm) mit der gewählten Figur |
| OSMonMonitor | Bildschirmzeit-Zähler (DeviceActivity), Meldungen bei Schwellen |
| OSMonTests | Engine-, Push-, Evolutions-, Figuren- und Fortschritts-Tests |

## Werkzeuge (`tools/`)

| Datei | Zweck |
|---|---|
| `osmon-prototyp.html` | Klickbarer Prototyp, Quelle für Figuren-Formen und -Farben |
| `gen_figures.mjs` | `node gen_figures.mjs osmon-prototyp.html` → neue `PetFigureData.*` und `figures.json` |
| `golden_*.py` | Erzeugen die Test-Referenzen (`python3 golden_reference.py` usw.) |

Erzeugte Dateien danach an ihren Platz kopieren:
`PetFigureData.kt` → `android/app/src/main/java/com/example/osmon/figure/`,
`PetFigureData.swift` → `ios/OSMon/Shared/Sync/`, JSON-Dateien in beide Test-Ressourcen.

## Hintergründe, Antippen, Wochenrückblick

- 10 Hintergrundbilder liegen als `scene_<id>` in `ios/OSMon/App/Assets.xcassets` und
  `android/app/src/main/res/drawable-nodpi` (je ca. 1,2 MB zusammen).
- Logik: `PetProgress` (Shared/Logic bzw. `engine`), Reaktionen: `PetReaction` (Shared/Sync bzw. `figure`),
  Oberfläche: `PetScene` (App bzw. `ui`). Details und Freischalt-Regeln: Entwickler-README, Abschnitt 9.

## Realistische Figuren (Naturdoku-Stil)

66 Bilder: 11 Figuren × 6 Stufen (Ei, Schlüpfling, Baby, Kind, Jugend, Erwachsen), 512×512, WebP mit Transparenz,
gleicher Maßstab und gleiche Bodenlinie (Bild unten mittig ausrichten, dann springt beim Stufenwechsel nichts).

| Plattform | Bilder | Code |
|---|---|---|
| Android | `app/src/main/res/drawable-nodpi/real_<figur>_<stufe>.webp` | `figure/RealFigureArt.kt` (`resId`, `bitmap`) |
| iOS | `OSMon/App/RealFigures/real_<figur>_<stufe>.webp` | `Shared/Logic/RealFigureArt.swift`, `App/RealFigureImage.swift` (`RealFigureView`) |

Stufen: `egg, hatchling, baby, child, teen, adult`. Der Schlüpfling erscheint am Schlüpftag, danach das Baby
(`RealFigureArt.stageFor(...)` bzw. `RealFigureArt.stage(for:)`). Neu erzeugen aus den 1024er-Quellbildern:
`python3 tools/real_figures/export_real_figures.py <ordner>`.

Einstellungen › Figur › **Darstellung: 2D · 3D · Realistisch** (`PetStyle`, Schlüssel `pet.style`; ohne Wahl gilt die
frühere 3D-Einstellung `pet.3d`). Realistisch gilt für Home-Screen und Widget; Live-Hintergrund und Uhr bleiben 2D.
Antippen federt das Bild, das Ei wackelt; Stimmung und Zubehör erscheinen in dieser Darstellung nicht im Bild.
Screenshots auf iOS: `-OSMonReal YES`.

## Stand

Kein Teil wurde hier kompiliert (kein Android-SDK und kein Xcode in der Erstellungsumgebung).
Geprüft wurden: Engine-Formeln gegen Python-Referenzen, der Android-Figuren-Renderer gegen den Prototyp
(pixelgleich, auch alle Antipp-Reaktionen), `PetProgress.kt` mit kotlinc (11/11 Tests grün), XML/YAML-Syntax. Beim ersten Build sind kleine Anpassungen an API-Signaturen möglich.
