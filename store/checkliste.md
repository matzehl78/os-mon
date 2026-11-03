# OS-Mon – Checkliste Veröffentlichung

## 1. Einmalig einrichten
- [ ] Eigene ID setzen: `tools/set_ids.sh de.<deinedomain>.osmon`
- [ ] Apple Developer Program (99 €/Jahr), Google Play Console (25 € einmalig)
- [ ] iOS: Team-ID in `ios/project.yml` (DEVELOPMENT_TEAM), App Group `group.<id>` anlegen
- [ ] Datenschutzerklärung veröffentlichen (`store/datenschutz.html`, Platzhalter füllen), Link in beide Stores
- [ ] Android: Upload-Keystore erzeugen und als GitHub-Secrets hinterlegen (siehe unten)

## 2. Besondere Berechtigungen (frühzeitig beantragen!)
- [ ] **iOS Family Controls (Bildschirmzeit)**: Distribution-Entitlement bei Apple beantragen (Formular „Family Controls“). Ohne Freigabe nur Entwicklung möglich.
- [ ] **iOS HealthKit**: Begründung im Review („nur Tagessumme Schritte, nur Lesen“)
- [ ] **Android PACKAGE_USAGE_STATS**: In der Play Console Erklärung als „Digital Wellbeing“-Funktion abgeben (Video vom Einstellen der Berechtigung)
- [ ] **Android Health Connect**: Erklärung „Schritte lesen“ (Formular in der Play Console), Datenschutz-Link muss die Schritte nennen
- [ ] **Hintergrund (Android)**: Akku-Ausnahme nur auf Wunsch des Nutzers (so umgesetzt)

## 3. Kinder und Familien
- [ ] Apple: Zielgruppe festlegen. Für die **Kids-Kategorie**: keine Links nach außen ohne Eltern-Schranke, keine Werbung, kein Tracking ✅ (Eltern-Bereich vorhanden)
- [ ] Google: **Families Policy** – Zielgruppe „Kinder und Erwachsene“, keine Werbe-SDKs ✅, Inhalte altersgerecht ✅
- [ ] Datensicherheit (Google) / App-Datenschutz (Apple): **„Keine Daten erfasst“** – Daten verlassen das Gerät nicht (Uhr-Sync ist Übertragung zwischen eigenen Geräten)

## 4. Testen
- [ ] TestFlight (iOS) mit 2–3 Familien, interner Test (Google Play)
- [ ] Echte Geräte: Bildschirmzeit, Schritte, Widget, Sperrbildschirm, Uhr, 3D, Schütteln
- [ ] Barrierefreiheit: VoiceOver/TalkBack einmal komplett durchgehen

## 5. Einreichen
- [ ] Screenshots (Branch `screenshots`, 6,7″ und 6,5″), Werbefilm als App-Vorschau (max. 30 s Ausschnitt)
- [ ] Texte aus `store/texte_de.md`
- [ ] Android: Workflow „Android Release“ starten → AAB aus den Artefakten in die Play Console laden
- [ ] iOS: in Xcode „Product → Archive“ → „Distribute App“ → App Store Connect

## GitHub-Secrets für den Android-Release
| Secret | Inhalt |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Passwort des Keystores |
| `ANDROID_KEY_ALIAS` | Alias des Schlüssels |
| `ANDROID_KEY_PASSWORD` | Passwort des Schlüssels |

Keystore erzeugen: `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
