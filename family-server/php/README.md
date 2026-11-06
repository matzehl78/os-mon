# OS-Mon Eltern – checkdomain-Installation (PHP/MySQL)

Ziel: https://parents.ulmatti-studios.com. Dieses Paket ersetzt den Python-Server für Shared Hosting; die API-Pfade und Antwortfelder der nativen Apps bleiben erhalten. Keine echten Zugangsdaten im Paket. Die bestehende SQLite-Datenbank wird nicht automatisch importiert; für diesen neuen Server Geräte neu koppeln.

## Ersteinrichtungsassistent ohne SSH (empfohlener Weg)

1. Paket entpacken und `public` sowie `private` per SFTP oder Hosting-Dateimanager nach `ulmatti-studios.com/parents` hochladen. Subdomain-Ziel auf `ulmatti-studios.com/parents/public` setzen. `private` darf über keine Domain öffentlich abrufbar sein; dort liegt zusätzlich eine sperrende .htaccess.
2. Im privaten Ordner eine neue reine Textdatei **setup-enable.txt** erstellen. Ihr gesamter Inhalt ist ein eigener zufälliger Code mit 32–100 Zeichen: A–Z, a–z, Ziffern, `_` und `-`. Mit einem Passwortgenerator erzeugen. Kein vorhandenes Passwort oder Beispielcode verwenden. Rechte nach Möglichkeit 600 setzen. Die mitgelieferte `setup-enable.example.txt` ist nur eine Anleitung und aktiviert nichts.
3. `https://parents.ulmatti-studios.com/setup` im eigenen Browser öffnen bzw. neu laden. Der Server übernimmt den Code als Hash in eine private Einrichtungsdatei und entfernt setup-enable.txt. Ab diesem Aufruf gilt die Freischaltung 30 Minuten. Denselben Code im Browser eingeben; er ist einmal verwendbar. Nie im Chat oder als URL-Parameter übermitteln.
4. Die Serverprüfliste ansehen und eigene **leere** MySQL-Datenbank eintragen. Verbindung prüfen und Tabellen vorbereiten. Datenbankpasswort nur hier auf dem eigenen HTTPS-Server eingeben.
5. Adminname und neues Adminpasswort wählen. Den angezeigten Schlüssel manuell in eine Authenticator-App übernehmen (6 Ziffern, 30 Sekunden, SHA-1). Keine externen QR-Dienste. Aktuellen Authenticator-Code bestätigen.
6. Der Assistent schreibt die private Konfiguration, richtet den Admin ein und sperrt sich dauerhaft. Für die erste Anmeldung unter `/admin` auf den nächsten neuen Authenticator-Code warten.

Kein SSH-Terminal nötig. Auch das manuelle Bearbeiten von config.php entfällt. Ratenbegrenzungs- und Admin-Verschlüsselungsschlüssel werden automatisch erzeugt. Die folgenden manuellen Abschnitte bleiben als Alternative für Wartung mit Terminalzugang gültig. Alternativ kann weiterhin `php private/setup-start.php` einen Code erzeugen.

Falls der Dateimanager keine neuen Textdateien erstellen kann: eine reine UTF-8-Textdatei mit diesem Namen auf dem eigenen Gerät erstellen und per SFTP hochladen. Keine Word-/RTF-Datei und nicht versehentlich `setup-enable.txt.txt`. Passwort und Code nicht hier hochladen. Die Freischaltdatei gehört ausschließlich nach private, nie nach public. Auch über die Studio-Hauptdomain darf der Ordner nicht zugänglich sein.

Nach Ablauf den Assistenten einmal aufrufen (dadurch wird die abgelaufene `.setup-state.json` entfernt), eine neue Freischaltdatei mit neuem zufälligem Code anlegen und erneut laden. Bei Browserverlust oder ausgeschöpften Authenticator-Versuchen vor Abschluss `.setup-state.json` im privaten Ordner per Dateimanager entfernen und neu freischalten. Ein bereits fertig eingerichteter Server lässt sich dadurch **nicht** neu einrichten: config.php und `.setup-complete` bleiben gesperrt. Nicht zur Wiederherstellung diese Produktivdateien löschen.

Einmalcode vertraulich behandeln. Temporäre Einrichtungsdaten liegen mit Rechten 600 außerhalb des öffentlichen Verzeichnisses. Das Adminpasswort wird darin nur gehasht gespeichert. Nach Abschluss wird diese Zwischenablage gelöscht; abgelaufene Zwischenablagen werden beim nächsten Aufruf entfernt. Bereits angelegte Datenbanktabellen werden bei Abbruch **nicht** automatisch gelöscht. Nach erneutem Start mit neuer Sitzung eine andere leere Datenbank verwenden oder die dokumentierte manuelle Installation abschließen lassen.

Eine vorhandene config.php oder `.setup-complete` verhindert jede erneute Einrichtung. Verlorene Adminzugänge benötigen weiterhin die separate Wiederherstellung durch jemanden mit Hosting-/Terminalzugang; dafür gibt es keine ungeschützte Web-Zurücksetzung.

Der Assistent prüft PHP-Module, Schreibrechte, Datenbank und lokale Admin-Einrichtung. Sicherheitswartung des Hosters, Apache-Zuordnung, Backups, Rechtstexte, automatische Bereinigung und echte Geräteverbindungen müssen vor Livebetrieb zusätzlich geprüft werden. Geheimnisse werden nicht an ChatGPT oder Dritte geschickt.

## Voraussetzungen und Stand

- PHP 8.3 oder neuer, Zielhosting PHP 8.5; Erweiterungen PDO_MYSQL und mbstring.
- MySQL 5.7 oder neuer, InnoDB, eigene leere Datenbank. Keine MySQL-8-spezifischen Funktionen. MySQL 5.7 erhält vom Hersteller keine reguläre neue Pflege mehr; Hoster-Sicherheitswartung vor Echtbetrieb klären.
- Apache 2.4 mit mod_rewrite und AllowOverride (für .htaccess), HTTPS. Der Webserver muss PHP als HTTPS erkennen. Keine clientseitigen Forwarded-Header als Ersatz vertrauen. Bei TLS-Proxy die vertrauenswürdige Serverkonfiguration mit dem Hoster klären.
- SSH/SFTP für Installation, optional Cronjob. PHP im SSH kann eine andere Version als die Webdomain haben: CLI-Pfad zu PHP 8.5 bei checkdomain prüfen.
- Bereits bestehende Apps müssen eine Version mit Familienanbindung enthalten; Store-Releases werden hierdurch nicht automatisch aktualisiert.

## 1. Dateien hochladen und Verzeichnis zuordnen

ZIP entpacken. Es enthält `osmon-parents/public`, `osmon-parents/private` und diese Anleitung.

Empfohlene Ablage: den Inhalt von `osmon-parents` in das Verzeichnis `ulmatti-studios.com/parents` hochladen, sodass dort `public` und `private` nebeneinander liegen. Die Subdomain muss dann **ausschließlich** auf `ulmatti-studios.com/parents/public` zeigen. Das bisher genannte `.../parents` allein darf nicht als öffentliches Ziel bestehen bleiben.

Der SFTP-Pfad kann vom absoluten SSH-Dateisystempfad abweichen. Per SSH mit `pwd`/`ls` prüfen; keine vorhandene Studio-Webseite überschreiben. Passwörter, private Schlüssel und Konfigurationsdateien nicht in den Chat oder in GitHub kopieren. SFTP-Port 22 verwenden, keinen unverschlüsselten FTP-Upload.

Falls die Domainverwaltung das öffentliche Unterverzeichnis nicht erlaubt: Installation stoppen und private Dateien in ein wirklich außerhalb des Domain-Webroots liegendes Verzeichnis verlegen; die Include-Pfade müssen dann angepasst werden. Nicht auf .htaccess allein zum Schutz des Datenbankpassworts verlassen.

## 2. Private Konfiguration

In `private` die Datei `config.example.php` nach `config.php` kopieren. Lokal auf dem Hosting bearbeiten:

- origin: `https://parents.ulmatti-studios.com` (ohne abschließenden Slash)
- host: `localhost`, port: `3306`
- database und username: Werte aus deiner Datenbankverwaltung
- password: **neu vergebenes** Datenbankpasswort
- rate_secret: 64 zufällige Hex-Zeichen, einmalig erzeugen:

```sh
php -r 'echo bin2hex(random_bytes(32)), PHP_EOL;'
```

`config.php` auf Dateirechte 600 setzen, soweit der PHP-Prozess sie damit lesen kann. Private Ordner nur dem Hostingkonto zugänglich machen. Die Konfiguration wird vor Ausführung geprüft und muss außerhalb des DocumentRoot liegen.

## 3. Datenbank einrichten

Per SSH im Ordner `parents` mit dem passenden PHP-CLI-Interpreter ausführen:

```sh
php -v
php -m
php private/install.php
```

Die Einrichtung erstellt nur Tabellen mit Präfix `osmon_`. Sie löscht keine fremden Tabellen. Trotzdem eine separate leere Datenbank verwenden. Alternativ `private/schema.sql` in der Datenbankverwaltung importieren. Kein öffentliches Setup-Skript und kein phpinfo() hochladen.

## 4. Webseite und App verbinden

`https://parents.ulmatti-studios.com` öffnen. `/web-api/status` muss JSON mit `configured: true` und zunächst `authenticated: false` liefern. Browser und Apps bekommen HTTPS; unverschlüsselte API-Anfragen werden abgelehnt.

- Native Eltern-App: diese HTTPS-Adresse eintragen, Elternzugang erstellen.
- Kinder-App: im PIN-Elternbereich dieselbe Adresse eintragen und Gerätecode erstellen; in der Eltern-App koppeln.
- Webportal: Web-Anmeldecode aus der Eltern-App verwenden. So benutzen beide denselben Familienzugang. Alternativ unabhängigen Webzugang erstellen; dieser übernimmt keine bestehenden App-Geräte.
- Aufgaben und Spielelimit senden. Kinder-App geöffnet lassen und Übernahme prüfen.
- Standortanfragen ausdrücklich am Kindergerät erlauben. Erst dann kann ein Elternteil „Neuen Standort anfragen“ verwenden. Keine Hintergrundortung/Pushzustellung. Anfrage verfällt nach 90 Sekunden, Positionen werden nach 24 Stunden ausgeblendet.

Browser verwendet ein HttpOnly/Secure/SameSite=Strict-Cookie mit sieben Tagen Laufzeit. Elternzugänge laufen nach 90 Tagen ab. Konto-/Passwortwiederherstellung ist noch nicht enthalten. Bei Verlust am Kindergerät trennen und neu koppeln.

## 5. Bereinigung und Betrieb

Alle API-Anfragen bereinigen abgelaufene Daten. Zusätzlich regelmäßig über Hosting-Cronjob aufrufen, beispielsweise alle 15 Minuten:

```sh
php /ABSOLUTER/PFAD/parents/private/cleanup.php
```

Dadurch werden Standorte auch bei fehlendem Seitenverkehr spätestens mit dem nächsten Cronlauf physisch gelöscht. Sichtbarkeit endet nach 24 Stunden; Backups separat mit kurzer Aufbewahrung und Löschfrist behandeln. Keine Tokens, Aufgaben, Standortdaten oder Anfragekörper in Logs aufnehmen. Datenbank ist transportseitig über HTTPS geschützt, nicht Ende-zu-Ende-verschlüsselt.

Der Server speichert nur den letzten Standort. Auf dem Kindergerät deaktivierte Standortfreigabe löscht offene Anfrage und Position; manuelles Einmal-Teilen bleibt möglich. Offline-Löschen muss online wiederholt werden. Geräte-/Elternlöschung entfernt abhängige Datensätze. Globale kurze InnoDB-Transaktionen schützen Kopplung, Zugangscode-Verbrauch, Widerruf und Einstellungsrevisionen vor parallelen Schreibkonflikten. Für viele Familien sind Lasttests und spätere gezieltere Sperren nötig; keine Kapazitätsgarantie für Shared Hosting.

Vor Produktivfreigabe: dokumentierte Sicherheitswartung von MySQL 5.7, Betreiber-/Datenschutzhinweise, Store-Datenschutzangaben und echte iOS-/Android-Tests ergänzen. Die Webseite benötigt außerdem vor öffentlicher Vermarktung die passenden Anbieterinformationen. Der automatische Testaufbau läuft getrennt gegen eine Wegwerf-Datenbank `osmon_test`.

## Prüfungen

```sh
php tests/store_test.php
python3 tests/http_test.py
```

Diese Tests erfordern `OSMON_TEST_DATABASE=osmon_test` und die Test-Datenbankvariablen aus dem GitHub-Workflow; sie löschen Testdaten und gehören niemals auf die Produktivdatenbank. Die Test-HTTP-Router-Datei simuliert TLS ausschließlich für den lokalen PHP-Testserver und ist nicht im Installationspaket enthalten.

Installation lokal erstellen: `python3 package.py /absoluter/pfad/OSMon_Parents_Checkdomain.zip`. Frontend-Dateien stammen automatisch aus dem gemeinsamen `family-server/web`-Ordner.

## Betreiber-Adminbereich

Adresse nach Installation: `https://parents.ulmatti-studios.com/admin`. Eigenständige Anmeldung, getrennt von Elternzugängen. Kein öffentliches Registrieren und kein Standardpasswort. Die Verwaltung zeigt ausschließlich technische Metadaten: zufällige Support-ID, Anzahl Geräte, Zugangslaufzeit, Sperrstatus, Geräte-IDs, letzte Rückmeldung und Einstellungsrevisionen. Keine Aufgabeninhalte, Gerätealiasnamen, Standortwerte oder Zugangsschlüssel. Eine fehlende Rückmeldung ist keine sichere Aussage über die Erreichbarkeit.

### Einrichtung nur direkt per SSH

1. Aktuelles Paket hochladen und `php private/install.php` erneut ausführen. Bestehende Familien erhalten zusätzliche Spalten `public_id` und `suspended`. Daten bleiben erhalten, vorher Backup erstellen. Die zusätzlichen Admin-Tabellen sind eigenständig.
2. In `private/config.php` zusätzlich `admin_key` setzen: weitere 64 zufällige Hex-Zeichen, getrennt von `rate_secret`. Mit demselben lokalen Zufallsbefehl wie oben erzeugen. Dieser Schlüssel verschlüsselt den Authenticator-Schlüssel mit AES-256-GCM; separat sichern. Nicht ändern, solange eingerichtete Admins bestehen. Bei Verlust ist eine neue Einrichtung per SSH nötig.
3. `php private/admin-setup.php` ausführen. Adminname, starkes Passwort (12–72 Bytes) und Authenticator-Code direkt im Terminal eingeben. Passwort wird verdeckt eingegeben. Einrichtungsgeheimnis in die eigene Authenticator-App übernehmen (TOTP, SHA-1, 6 Ziffern, 30 Sekunden). Kein externer QR-Dienst wird aufgerufen. Den angezeigten Geheimschlüssel weder hier noch in GitHub/Chats teilen.
4. Den angeforderten Authenticator-Code zur Bestätigung eingeben. Der Code wird damit verbraucht; für die Browseranmeldung auf den nächsten neuen Code warten.

Falls das SSH-Terminal keine verdeckte Eingabe unterstützt: eine temporäre Passwortdatei **außerhalb des öffentlichen Verzeichnisses** mit Rechten 600 erstellen. `OSMON_ADMIN_PASSWORD_FILE=/absoluter/privater/pfad php private/admin-setup.php` liest diese Datei statt der Terminal-Passworteingabe. Danach Datei löschen. Passwort selbst nie als Befehlsargument oder Umgebungsvariable angeben.

Bei Verlust von Passwort/Authenticator denselben CLI-Befehl mit identischem Adminnamen erneut ausführen. Dadurch werden Passwort und Authenticator-Schlüssel erneuert und alle bisherigen Sitzungen dieses Admins beendet. Keine öffentliche Passwortzurücksetzung. Keine Wiederherstellung ohne Hosting-/SSH-Zugang.

### Bedienung und Schutz

- Übersicht zu Familien/verbundenen Geräten und Zeitpunkt der letzten Rückmeldung, PHP-Version und Datenbankerreichbarkeit. Kein vollständiges Monitoring oder Fehlerlogportal.
- Familien sperren/entsperren. Sperren stoppt Serverzugriff, entfernt Browser-Anmeldungen, offene Webcodes, Standortfreigaben, Positionen und Standortanfragen. Lokale Spielstände/Aufgaben/Limits auf Geräten werden dadurch nicht entfernt. Nach Entsperren Browser neu anmelden und Standortfreigabe bei Bedarf auf dem Kindergerät erneut aktivieren. Der primäre Eltern-App-Zugang bleibt bis zu seinem normalen Ablauf erhalten.
- Familienzugänge samt Serverdaten endgültig löschen oder einzelne Geräteverbindungen aufheben. Jede Aktion benötigt Bestätigung und ist im Verwaltungsprotokoll nachvollziehbar.
- Protokoll: Zeit, Adminname, Aktion und zufällige Referenz. Keine IP-Adressen, Aufgabentitel, Koordinaten oder Authenticator-Codes im Protokoll. Automatische Löschung nach 90 Tagen über bestehende Bereinigung. Backups können ältere Einträge enthalten und benötigen eine eigene Frist. Das Datenbankprotokoll ist nicht manipulationssicher gegenüber dem Hosting-/Datenbankbetreiber.
- Passwort-Hash, verschlüsselter Authenticator-Schlüssel und nur gehashte Sitzungsschlüssel. Zwei Stunden absolute Sitzungsdauer und 30 Minuten Inaktivitätsfrist. Neue Anmeldung beendet frühere Sitzungen desselben Admins. Erfolgreich verwendete TOTP-Zeitfenster dürfen nicht wiederverwendet werden. Fünf Fehlversuche sperren den Adminnamen 15 Minuten; zusätzlich fünf Login-Anfragen pro IP/Minute. Serverseitige Uhr synchron halten.
- Admin-Cookie `__Host-osmon_admin`: HttpOnly, Secure, SameSite=Strict. Schreibaktionen benötigen Origin-Prüfung, eigenen Header und sitzungsgebundenes CSRF-Token. Eltern-Cookies/-Tokens erhalten keine Adminrechte, Adminzugänge sind keine Elternzugänge. Nach Arbeit abmelden. Regelmäßige Aktualisierung nur per Button, damit offene Tabs nicht automatisch die Inaktivitätsfrist verlängern.

PHP benötigt zusätzlich OpenSSL. Der Adminbereich ist Bestandteil der PHP-Installation; der alternative Python-Server enthält diese Betreiberverwaltung nicht.

## E-Mail-Einstellungen im Adminbereich (Update)

Neue Funktion: `/admin` → E-Mail-Versand. SMTP-Hostname, Benutzername und Passwort aus den tatsächlichen Postfach-Einstellungen übernehmen. Unterstützt: TLS/465 oder STARTTLS/587 mit geprüften Zertifikaten, kein unverschlüsselter Versand. Absender noreply@ulmatti-studios.com und Support/Reply-To support@ulmatti-studios.com eintragen. Speichern, danach Test an eine eigene Adresse senden. SMTP-Annahme beweist nicht die Zustellung; Spamordner prüfen. SPF/DKIM/DMARC mit dem Mailanbieter einrichten. Kein Versand, bevor du Testmail absendest.

Bestehende Installation OHNE SSH aktualisieren: Dateien aus dem Update-ZIP in die gleichnamigen public/private-Ordner kopieren und nur diese Programmdateien überschreiben. Vorher Backup dieser Programmdateien erstellen. config.php, .setup-complete und bestehende .mail-* Dateien niemals ersetzen/löschen. Keine SQL-Migration und kein erneutes /setup nötig. Adminseite neu laden. Der private Ordner muss weiterhin über jede Domain gesperrt sein. Für ein Rollback die gesicherten Programmdateien zurückkopieren; Mail-Einstellungen bleiben privat und ungenutzt erhalten.

SMTP-Konfiguration wird mit dem bestehenden admin_key (AES-256-GCM, eigener Kontext smtp-v1) in private/.mail-settings gespeichert. config.php samt Schlüssel und die private Datei gemeinsam sichern. Passwort wird nie an den Browser zurückgegeben. Leeres Passwortfeld behält es bei. Geheimnisse sind weder in Fehlern noch im Verwaltungsprotokoll enthalten. Private Dateirechte 600; der PHP-Prozess braucht Schreibrechte im privaten Ordner. Testversand ist auf einen Versuch pro Minute begrenzt. SMTP-Ziele müssen öffentliche IP-Adressen besitzen; DNS wird einmal aufgelöst und die geprüfte Adresse für die Verbindung festgehalten. Der Hoster muss ausgehende Verbindungen auf 465 oder 587 erlauben.

SMTP-Einstellungen und Testversand bilden die Grundlage für die folgenden Elternkonten.


## Elternkonten mit E-Mail (PHP-Server)

Nach Upload des Konten-Updates im Adminbereich auf „Elternkonten aktivieren / Datenbank prüfen“ klicken. Erst diese authentifizierte, CSRF-geschützte Aktion legt zwei neue Tabellen an (CREATE IF NOT EXISTS); bestehende Familien werden nicht verändert. Kein Terminal und kein erneutes /setup nötig. Frische Installationen enthalten die Tabellen bereits. Mail-Test muss vorher erfolgreich sein. Der Python-Alternativserver unterstützt die Konten nicht.

Neue Eltern: im Webportal oder in der aktualisierten Eltern-App E-Mail registrieren. Per E-Mail-Link im Browser ein Passwort festlegen (12–72 UTF-8-Bytes). Der Link ist einmalig und 24 Stunden gültig. Danach Anmeldung mit E-Mail/Passwort in App und Portal. Die Apps speichern ausschließlich das Sitzungstoken im Keychain/Keystore, kein Passwort. Sitzungen gelten sieben Tage. Ein Konto je Familie, mehrere parallele Anmeldungen.

Bestehende Familie erhalten: in der bisherigen Eltern-App „Diese Familie mit E-Mail absichern“ wählen oder zuerst den Web-Anmeldecode aus der Eltern-App im Webportal einlösen und dort diese Familie registrieren. Nicht abgemeldet ein zweites Konto erstellen, wenn die bestehenden Geräte übernommen werden sollen. Eine bereits anderweitig registrierte E-Mail wird nicht automatisch mit einer anderen Familie zusammengeführt. Bei ausstehender Bestätigung dieselbe Adresse verwenden. Im Webportal kann eine neue Bestätigung angefordert werden.

Bei erfolgreicher Bestätigung wird die interne Elternidentität erneuert, die Kindergeräte, Aufgaben, Statusdaten und Support-ID bleiben erhalten. Alle bisherigen Eltern-Sitzungen und offenen Web-Anmeldecodes dieser Familie werden ungültig. In der neuen Eltern-App „Abmelden / neu anmelden“, dann E-Mail und Passwort verwenden. Kindergeräte müssen nicht neu gekoppelt werden. Legacy-Zugänge ohne Kontoverknüpfung funktionieren unverändert bis zu ihrem bisherigen 90-Tage-Ablauf. Verifizierte Konten entfernen diese 90-Tage-Löschung (technischer Familienablauf 2100); das Konto bleibt bis zur ausdrücklichen Löschung bestehen.

„Passwort vergessen“ sendet einen einmaligen Link (30 Minuten gültig). Neues Passwort setzen beendet alle Eltern-Sitzungen, widerruft alte primäre Eltern-Tokens und Webcodes, erhält Kindergeräte. Zurücksetzen ist keine Entsperrung eines vom Betreiber gesperrten Kontos. Konto löschen nutzt die bisherige Funktion „Elternzugang vollständig löschen“ und entfernt Familie, Konto und Tokens; zuletzt übernommene lokale Einstellungen der Kinder-App bleiben bestehen.

E-Mail-Adressen werden normalisiert in der Datenbank gespeichert, Passwörter nur gehasht, Bestätigungs-/Reset-Tokens und Sitzungstokens nur als SHA-256-Hash. Links tragen den geheimen Code im Fragment, das der Browser sofort aus der Adresszeile entfernt. Erst das explizite Formular löst den Link ein. Keine Zustandsänderung beim GET/Linkscanner. Versand ist synchron nach Abschluss der Datenbanktransaktion, ohne Datenbanklock. Kein Outbox-/Retry-Dienst: bei SMTP-Fehlern erneut senden; Versand funktioniert nur bei korrekt eingerichteter SMTP-Konfiguration. Öffentliche Antworten zu Register/Reset/Resend sind absichtlich gleich; SMTP-Laufzeiten können unterschiedlich sein. Maximal fünf Anfragen je Route/IP/Minute, fünf Mails je Konto/Stunde und mindestens 60 Sekunden Abstand; fünf fehlgeschlagene Kontoanmeldungen sperren Anmeldungen für 15 Minuten. Tokens nach Ablauf beim nächsten Kontoaufruf entfernt; zusätzliche regelmäßige Bereinigung empfohlen. Backups haben separate Fristen.

Neue API (nur PHP): POST /v1/account/{register,resend,forgot,verify,reset,login}, GET /v1/account/status. Registrierung/Resend/Forgot benötigen email, Login email+password, Verify/Reset code+password. Register kann einen vorhandenen Eltern-Bearer zur Verknüpfung verwenden. Browser-Gegenstücke /web-api/account/... verwenden nur das vorhandene sichere Cookie und Origin-/Header-Prüfung; Login gibt im Browser kein Token zurück. Native Loginantwort enthält token/expires. Admin und Eltern bleiben getrennt.

Validierung umfasst MySQL-Integration, Registrierung, Einmaligkeit/Ablauf, familienerhaltende Migration, Reset-Widerruf, Rollen/CSRF, Cookie-/Bearer-Verhalten sowie bestehende Family-/Setup-Tests. Echter Mailversand, iPhone/Android-Geräte und Zustellung im Mailprogramm müssen auf dem Hosting zusätzlich geprüft werden.
