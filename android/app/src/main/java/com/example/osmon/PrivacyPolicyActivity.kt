// PrivacyPolicyActivity.kt: Erklärung für Health Connect (Pflicht für die Schritte-Berechtigung).
// Wird von Health Connect geöffnet ("Warum braucht OS-Mon das?") und ist in den Systemeinstellungen
// unter "Berechtigungsnutzung" erreichbar. Vor der Veröffentlichung durch die echte Datenschutzerklärung
// (bzw. einen Link darauf) ersetzen.

package com.example.osmon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Datenschutz bei OS-Mon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Warum Schritte?", style = MaterialTheme.typography.titleMedium)
                        Text("Dein Wesen bekommt „Vitalität“ aus deiner Tagessumme an Schritten. OS-Mon liest nur diese Summe. Es schreibt nichts in Health Connect und liest keine anderen Gesundheitsdaten.")
                        Text("Warum im Hintergrund?", style = MaterialTheme.typography.titleMedium)
                        Text("Damit das Widget auch aktuell ist, wenn du die App nicht öffnest. Gelesen wird höchstens alle 15 Minuten.")
                        Text("Was passiert mit den Daten?", style = MaterialTheme.typography.titleMedium)
                        Text("Alles bleibt auf diesem Gerät. Es gibt kein Konto, keinen Server und keinen Upload. In den Einstellungen der App kannst du alles exportieren oder vollständig löschen.")
                        TextButton(onClick = { finish() }) { Text("Schließen") }
                    }
                }
            }
        }
    }
}
