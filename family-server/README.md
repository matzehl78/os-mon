# OS-Mon Eltern 0.1 – Entwicklungsstand

Zwei separate native Eltern-Apps und ein optionaler Familienserver. Noch keine Store-Veröffentlichung oder öffentlich betriebene Instanz. Die bestehende Kinder-App funktioniert ohne Kopplung weiterhin lokal.

## Enthalten
- Android-Modul `:parents`, iOS-Schema `OSMonParents`.
- Gerätegebundener Elternzugang (zufälliger 256-Bit-Bearer-Token, 90 Tage gültig).
- Kind erzeugt im PIN-geschützten Eltern-Bereich einen zehn Minuten gültigen Kopplungscode (10 zufällige Zeichen). Ein Code kann genau einmal verwendet werden.
- Ein Elternhandy verwaltet bis zu zehn Kindergeräte. In dieser ersten Version ein Elternzugang pro Kindergerät.
- Aufgaben mit vier Wiederholungsarten; Spielelimit Aus bis drei Runden pro Tag.
- Kinder-App fragt alle 15 Sekunden bei sichtbarer Startseite ab. Keine garantierte Hintergrundzustellung. Ohne Netz bleiben zuletzt übernommene Werte erhalten.
- Erledigt-Meldung mit lokalem Gerätetag und Zeitpunkt der letzten Übernahme. „Offen“ bedeutet bei fehlender aktueller Rückmeldung nicht sicher „nicht erledigt“.
- Serverseitige Versionsprüfung verhindert überschreibende veraltete Änderungen.
- Die lokale Eltern-Ansicht sperrt bei Sitzungsablauf und beim Wechsel in den Hintergrund. Kopplungsaktionen prüfen die PIN-Sitzung direkt.
- Beidseitiges Trennen; Elternzugang löschen entfernt alle zugehörigen Server-Geräte.

## Was nicht enthalten ist
Passwort-Login, Eltern-Einladungen, Kontowiederherstellung, automatische Wiederkopplung, QR-Scanner, Push-Zustellung, Fernsperren fremder Apps, Bildschirmzeit-/Schrittdatenübertragung, Premiumkäufe oder bezahltes Hosting. In der Bestätigung zum Löschen des Elternzugangs kann alternativ nur der lokale Zugang entfernt werden, auch bei abgelaufenem Zugang oder unerreichbarem Server. Das löscht keine Serverdaten.

Elternhandy wechselt: am Kindergerät Verbindung entfernen und neu koppeln. Bei „Alles löschen“ versucht die Kinder-App auch, die Serververbindung zu löschen. Offline kann dies scheitern; Serverdaten dann über die Eltern-App löschen. Der lokale Zugang wird in jedem Fall entfernt.

## Lokal testen
```sh
python -m unittest discover -s family-server -v
cd family-server
python server.py
```
Server lauscht standardmäßig nur auf 127.0.0.1:8787. Die HTTP-Tests benutzen ausschließlich Loopback. Die mobilen Apps akzeptieren nur HTTPS ohne Weiterleitungen. Keine TLS-Ausnahmen in den Apps einbauen.

## Für einen echten Test zwischen Handys
1. Python-Dienst auf einem eigenen Server mit dauerhaftem Datenverzeichnis starten (alternativ Dockerfile bauen).
2. Eigene HTTPS-Domain über einen Reverse Proxy bereitstellen. Nur `/v1/` zum Dienst weiterleiten. Port 8787 nicht öffentlich öffnen; TLS-Zertifikat automatisch erneuern.
3. Reverse Proxy muss Body-Limit 16 KiB, kurze Timeouts, Begrenzung gleichzeitig offener Verbindungen und Rate Limits je echtem Client durchsetzen. Die eingebaute Begrenzung sieht hinter einem Proxy dessen IP; sie ersetzt dessen Schutz nicht. Keine Authorization-Header, Kopplungscodes oder Bodies protokollieren.
4. Datenbank auf verschlüsseltem Datenträger und Backups geschützt betreiben; Wiederherstellung testen. Betreiber, Löschfristen und Datenschutztext vor echten Familiendaten festlegen. Abgelaufene Elternzugänge werden beim nächsten API-Aufruf samt Geräten gelöscht.
5. Dieselbe HTTPS-Adresse in Kinder- und Eltern-App eingeben.
6. Kinder-App: Eltern-PIN entsperren, „Eltern-App verbinden“, Datenfreigabe lesen, Code erzeugen.
7. Eltern-App: Zugang erstellen, Code eingeben, Gerät öffnen, Aufgaben/Limit bearbeiten und senden.
8. Kinder-App auf der Startseite offen halten. Übernahme sowie Status in der Eltern-App mit „Aktualisieren“ prüfen.

Keine Zugangsdaten oder Domain sind im Repository enthalten. Ohne eingerichteten HTTPS-Server ist die mobile Fernverbindung noch nicht nutzbar.

## Daten und Grenzen
Auf dem Server: Gerätealias, Aufgaben, Spielelimit, aktuell gemeldete erledigte Aufgaben, Gerätetag, letzter Abgleich, gehashte Zugangs- und Kopplungsschlüssel. Keine Sensorwerte und kein Analyse-SDK. Transport über HTTPS; keine Ende-zu-Ende-Verschlüsselung: der Serverbetreiber kann Aufgaben lesen. Der Schlüsselbund bzw. Android Keystore schützt die Zugangsschlüssel auf dem Handy. Nicht als starke Identitäts- oder Altersprüfung verstehen.

Server verweigert fremde Gerätezugriffe auch dann, wenn deren UUID bekannt ist. Kinder-Tokens dürfen keine Konfiguration verändern. Widerruf löscht den Serverdatensatz. Bereits lokal übernommene Aufgaben und Limits bleiben erhalten und sind über den lokalen Eltern-Bereich verwaltbar.

Bei lokal zusammen mehr als zwölf Aufgaben verweigert das Kindergerät die Übernahme ohne Bestätigung; Aufgaben reduzieren und erneut versuchen. Fernaufgaben sind mit `family:` markiert. Sie werden nur in diesem Namensraum ersetzt; vorhandene lokale Aufgaben bleiben erhalten.

Nach Ablauf eines Kopplungscodes oder nach Server-Widerruf auf dem Kindergerät „Nur lokalen Zugang entfernen“ und neu koppeln. Ein Neustart zeigt den Kopplungscode absichtlich nicht wieder; er wird nicht dauerhaft gespeichert.

## Abnahme vor Veröffentlichung
- CI für beide Kinder-Apps, beide Eltern-Apps und Servertests erfolgreich.
- iPhone↔iPhone, Android↔Android und beide gemischten Kombinationen auf Geräten testen.
- Codeablauf, Wiederverwendung, fremdes Elternkonto, Netzabbruch, Neustart, Tageswechsel und Zugangslöschung testen.
- Datenlöschung und Datenschutzerklärung der Kinder-App auf den optionalen Servermodus abstimmen.
- Die ältere unvollständige iOS-Löschroutine aus dem Prüfbericht ist eine separate noch offene Baustelle; diese Erweiterung behauptet keine vollständige Reparatur dieses Altfehlers.

## Weboberfläche (gleichberechtigter Elternzugang)
Die vollständige Browseroberfläche liegt in `web/` und wird vom Python-Dienst unter `/` ausgeliefert. Aufgaben und Spielelimits verwenden dieselben API-Endpunkte, dieselbe Versionsprüfung und dieselben Gerätedaten wie die nativen Apps.

Zusätzlich setzen: `OSMON_WEB_ORIGIN=https://familie.deine-domain.de`. Statische Dateien und `/web-api/` müssen über exakt diese HTTPS-Origin erreichbar sein. Keine CORS-Freigabe nötig oder erwünscht. Der Reverse Proxy muss `/`, die statischen Assets und `/web-api/` weiterleiten. Cookie- und Origin-Header unverändert weitergeben. Eine Webanmeldung über unverschlüsseltes HTTP wird bewusst nicht unterstützt.

In der Eltern-App „Web-Anmeldecode erstellen“ wählen; den fünf Minuten gültigen Code auf der eigenen Eltern-Webseite eingeben. Der Browser erhält eine eigene sieben Tage gültige Sitzung zum bestehenden Elternzugang. Code nur einmal verwendbar; keine Zugangsschlüssel in LocalStorage oder JavaScript. Das Cookie ist HttpOnly, Secure, SameSite=Strict und nur auf dem eigenen Host gültig. Schreibzugriffe prüfen außerdem Origin und einen eigenen Request-Header. Browser-Abmelden widerruft nur diese Sitzung; die Eltern-App bleibt angemeldet. Gesamten Elternzugang löschen widerruft alle Browser-Sitzungen und die Geräteverbindungen.

Ohne Eltern-App kann die Website einen neuen Webzugang erstellen und direkt einen Kindergeräte-Kopplungscode einlösen. Dieser Zugang übernimmt keine bereits einem anderen Elternzugang zugeordneten Geräte. Eine spätere Anmeldung desselben Zugangs in einer neuen nativen Eltern-App ist in dieser Version noch nicht enthalten.

Ohne eingerichteten Server zeigt die bereitgestellte private Vorschau ausschließlich Beispieldaten. Änderungen dort wirken auf kein echtes Gerät. Die Vorschau ist kein zusätzlicher Datenspeicher und kein öffentlicher Familienserver.

## Optionaler Standort (erste Version)
Auf dem gekoppelten Kindergerät im PIN-Bereich bewusst „Aktuellen Standort einmal teilen“ wählen. iOS/Android fordern nur Vordergrundberechtigung an. Kein automatisches oder heimliches Tracking, keine Hintergrundortung, keine Historie. POST /v1/device/location akzeptiert nur den gekoppelten Geräteschlüssel und einen maximal 120 Sekunden alten Messwert mit latitude/longitude/accuracy/measured (Unix-Sekunden). DELETE derselben Route entfernt ihn. Nur der zugehörige Elternzugang sieht den letzten Wert. Er wird nach 24 Stunden nicht mehr angezeigt und bei der nächsten API-Anfrage physisch gelöscht. Regelmäßige Bereinigung im Hosting einplanen, wenn auch ohne Anfragen zeitnahe physische Löschung benötigt wird. Backups separat mit kurzer Frist behandeln.

Eltern-iOS zeigt eine MapKit-Karte, Android öffnet die installierte Karten-App, Web öffnet OpenStreetMap erst nach Klick (Koordinaten werden dann an den Kartenanbieter übergeben). Zeitpunkt und Genauigkeit stehen jeweils dabei; dies ist keine Live-Ortung. Eltern können die Freigabe nicht aus der Ferne aktivieren. Standort kann auf dem Kindergerät gelöscht werden; Offline-Löschen muss bei Internetverbindung wiederholt werden.

Die Erweiterung ist im Python-Server implementiert. PHP/MySQL für checkdomain und die Einrichtung auf eltern.ulmatti-studios.com stehen noch aus. Nicht als bereits live verfügbar darstellen. Erforderlich vor Veröffentlichung: Datenschutzerklärung/Store-Datenschutzangaben aktualisieren, Echtgerätetests zu Berechtigung, Verweigerung, ungenauer Position, Offline-Verhalten und Kartenaufruf durchführen.
