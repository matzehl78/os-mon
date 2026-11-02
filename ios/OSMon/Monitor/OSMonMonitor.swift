// OSMonMonitor.swift
// Target: Device Activity Monitor Extension (eigenes Target).
// Capabilities: Family Controls, App Groups (gleiche Gruppe wie App/Widget).
// Zusätzlich im Target: ScreenTimeShared.swift, PushPolicy.swift, PushDelivery.swift.
//
// Info.plist der Extension:
//   NSExtension
//     NSExtensionPointIdentifier = com.apple.deviceactivity.monitor-extension
//     NSExtensionPrincipalClass  = $(PRODUCT_MODULE_NAME).OSMonMonitor
//
// Hinweis: Monitor-Extensions haben ein sehr knappes Speicherlimit und sollen kurz laufen.
// Hier nur Zähler hochsetzen, Widget anstoßen und die Push-Regeln prüfen (Bildschirmziel, Nachtnutzung).
// Keine Netzwerkzugriffe, keine großen Objekte, kein PetCore/PetSync.
//
// Nachtnutzung: Der Nacht-Zähler misst kumuliert ab 22:30, die Regel will "aktiv seit 23:30".
// Weil der Callback nur bei gerade laufender Nutzung kommt, ist die Person in diesem Moment aktiv;
// als Minutenwert wird der Zähler seit 22:30 verwendet (Näherung, eher zu früh als zu spät).

import DeviceActivity
import WidgetKit

final class OSMonMonitor: DeviceActivityMonitor {

    /// Wird aufgerufen, wenn die kumulierte Nutzung einer Gruppe eine Schwelle erreicht.
    /// Event-Name-Schema: "<gruppe>.<minuten>" bzw. "night.<minuten>".
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name,
                                         activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)

        let parts = event.rawValue.split(separator: ".")
        guard parts.count == 2, let minutes = Int(parts[1]) else { return }
        let name = String(parts[0])

        // Nur bekannte Namen zulassen
        guard name == "night" || UsageGroup(rawValue: name) != nil else { return }

        STShared.raise(name, to: minutes)

        // Widget neu laden. Das System begrenzt Reloads: nur bei Schwellen, nicht in Schleife.
        WidgetCenter.shared.reloadTimelines(ofKind: STShared.widgetKind)

        // Push-Regeln prüfen. Akku und Speicher bleiben hier leer (zu teuer bzw. nicht nötig);
        // die meldet der App-Prozess (Kurzbefehl, HealthKit-Wake-up, Hintergrund-Aktualisierung).
        var sig = PushDelivery.baseSignals()
        if name == "night" { sig.nightActiveMinutes = Float(minutes) }
        PushDelivery.evaluateAndDeliverBlocking(sig)
    }

    // Tageszähler sind pro Datum geschlüsselt: zum Tageswechsel muss nichts zurückgesetzt werden.
    // Alte Schlüssel (> 14 Tage) können hier optional aufgeräumt werden.
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
    }
}
