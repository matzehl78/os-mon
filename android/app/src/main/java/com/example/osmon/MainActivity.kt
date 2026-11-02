// MainActivity.kt: Einstieg der App. Verbindet Onboarding, Home-Screen und Einstellungen
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
import com.example.osmon.ui.HomeScreen
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
    var showSettings by rememberSaveable { mutableStateOf(false) }

    when {
        !done -> OnboardingScreen(onFinished = { done = true })
        showSettings -> SettingsScreen(
            onBack = { showSettings = false },
            onDataDeleted = { showSettings = false; done = false }
        )
        else -> HomeScreen(onOpenSettings = { showSettings = true })
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
