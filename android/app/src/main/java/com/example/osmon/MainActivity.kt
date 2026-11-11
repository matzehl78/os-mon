// MainActivity.kt: Einstieg der App. Verbindet Logo-Film (jeder Start), Vorspann-Film, Onboarding, Home-Screen und Einstellungen
// und reicht Tipps auf Meldungen an PushDelivery weiter.

package com.example.osmon

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.osmon.ui.BedtimeScreen
import com.example.osmon.ui.HomeScreen
import com.example.osmon.ui.IntroScreen
import com.example.osmon.ui.LogoScreen
import com.example.osmon.ui.OnboardingScreen
import com.example.osmon.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushDelivery.handleOpen(this, intent)
        if (PetSync.prefs(this).getBoolean("onboarding.done", false)) {
            PetScheduler.start(this)                 // idempotent; läuft sonst erst nach dem Onboarding
            EvolutionScheduler.start(this)
        }
        setContent { OSMonTheme { Root() } }
        // Kaufstatus mit Google Play abgleichen – erst nach dem Start der Oberfläche, nie ein Grund für einen Absturz
        window.decorView.post { runCatching { com.example.osmon.ui.PremiumStore.restore(applicationContext) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PushDelivery.handleOpen(this, intent)
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current.applicationContext
    val prefs = remember { PetSync.prefs(ctx) }
    var done by remember { mutableStateOf(prefs.getBoolean("onboarding.done", false)) }
    var introSeen by remember { mutableStateOf(prefs.getBoolean("intro.seen", false)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var replayIntro by rememberSaveable { mutableStateOf(false) }
    // Tipp auf die Schlafenszeit-Erinnerung öffnet direkt die Gute-Nacht-Routine
    val activity = LocalContext.current as? ComponentActivity
    var showBedtime by rememberSaveable { mutableStateOf(activity?.intent?.getStringExtra("open") == "bedtime") }

    var logoDone by rememberSaveable { mutableStateOf(false) }   // Logo-Film bei jedem Kaltstart
    // Erster Start: ganze Fassung mit Ton vor dem Vorspann; danach kurz (ca. 3 s) und stumm
    val logoFull = remember { !done && !introSeen }

    when {
        !logoDone -> LogoScreen(full = logoFull, onFinished = { logoDone = true })
        // Vorspann einmal vor dem Onboarding
        !done && !introSeen -> IntroScreen(onFinished = { prefs.edit().putBoolean("intro.seen", true).apply(); introSeen = true })
        !done -> OnboardingScreen(onFinished = { done = true })
        replayIntro -> IntroScreen(onFinished = { replayIntro = false })
        showBedtime -> BedtimeScreen(onClose = { showBedtime = false; activity?.intent?.removeExtra("open") })
        showSettings -> SettingsScreen(
            onBack = { showSettings = false },
            onShowIntro = { replayIntro = true },
            onDataDeleted = { showSettings = false; done = false }
        )
        else -> HomeScreen(onOpenSettings = { showSettings = true }, onOpenBedtime = { showBedtime = true })
    }
}

@Composable
private fun OSMonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) { Surface(content = content) }
}
