# OS-Mon Eltern – checkdomain-Installation (PHP/MySQL)

Ziel: https://parents.ulmatti-studios.com. Dieses Paket ersetzt den Python-Server für Shared Hosting; die API-Pfade und Antwortfelder der nativen Apps bleiben erhalten. Keine echten Zugangsdaten im Paket. Die bestehende SQLite-Datenbank wird nicht automatisch importiert; für diesen neuen Server Geräte neu koppeln.

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
