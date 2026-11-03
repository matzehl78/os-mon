// OnboardingScreen.kt: Onboarding (Jetpack Compose), Gegenstück zu OnboardingView.swift
//
// Ablauf: Willkommen -> Ei schlüpfen lassen -> Akku/Speicher (ohne Erlaubnis) -> Bildschirmzeit
//   (Nutzungszugriff) -> Schritte (Health Connect, optional) -> Extras (Meldungen, Akku-Optimierung).
// Jede Erlaubnis einzeln und mit Klartext. "Später" ist gleichwertig sichtbar; ohne jede einzelne
// Erlaubnis läuft die App degradiert weiter.
//
// Setzt in den SharedPreferences "home": feature.screentime, feature.steps, feature.notifications,
// onboarding.done. Beim Abschluss startet es außerdem PetScheduler (der Worker lief bisher nur, wenn
// ein Widget platziert war).
//
// Manifest zusätzlich: <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
// (Android 13+, siehe README). Health-Connect-Einträge siehe StepsHealthConnect.kt.
//
// Einstieg (Root-Composable):
//   val done = remember { prefs.getBoolean("onboarding.done", false) }
//   if (done) HomeScreen() else OnboardingScreen(onFinished = { /* State aktualisieren */ })

package com.example.osmon.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.osmon.EvolutionScheduler
import com.example.osmon.EvolutionStore
import com.example.osmon.PetScheduler
import com.example.osmon.ScreenTime
import com.example.osmon.Steps
import com.example.osmon.engine.Mood
import com.example.osmon.figure.PetFigurePicker
import com.example.osmon.figure.rememberCurrentFigure

private enum class Step { WELCOME, EGG, BASICS, SCREEN_TIME, STEPS, EXTRAS }

private fun isIgnoringBatteryOptimizations(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

private fun notificationsAllowed(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Führt block bei jedem Zurückkehren in die App aus (z. B. nach den Systemeinstellungen). */
@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val preview = LocalInspectionMode.current
    val sp = remember { ctx.getSharedPreferences("home", Context.MODE_PRIVATE) }

    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var eggTaps by rememberSaveable { mutableIntStateOf(0) }

    var usageGranted by remember { mutableStateOf(!preview && ScreenTime.hasAccess(ctx)) }
    var stepsGranted by remember { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(!preview && notificationsAllowed(ctx)) }
    var batteryOk by remember { mutableStateOf(!preview && isIgnoringBatteryOptimizations(ctx)) }
    val hcStatus = remember {
        if (preview) HealthConnectClient.SDK_AVAILABLE else HealthConnectClient.getSdkStatus(ctx)
    }

    // Nach Rückkehr aus den Systemeinstellungen neu prüfen. Nutzungszugriff erteilt = Feature aktiv.
    OnResume {
        usageGranted = ScreenTime.hasAccess(ctx)
        batteryOk = isIgnoringBatteryOptimizations(ctx)
        if (usageGranted) sp.edit().putBoolean("feature.screentime", true).apply()
    }
    LaunchedEffect(Unit) {
        if (!preview) Steps.client(ctx)?.let { stepsGranted = Steps.hasPermission(it) }
        if (stepsGranted) sp.edit().putBoolean("feature.steps", true).apply()
    }

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        stepsGranted = ok
        sp.edit().putBoolean("feature.steps", ok).apply()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifGranted = ok
        sp.edit().putBoolean("feature.notifications", ok).apply()
    }

    fun next() { Step.values().getOrNull(step.ordinal + 1)?.let { step = it } }
    fun back() { Step.values().getOrNull(step.ordinal - 1)?.let { step = it } }
    fun openStore() {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProgressDots(Step.values().size, step.ordinal)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                Step.WELCOME -> WelcomeContent()
                Step.EGG -> EggContent(eggTaps) { eggTaps++ }
                Step.BASICS -> BasicsContent()
                Step.SCREEN_TIME -> ScreenTimeContent(usageGranted)
                Step.STEPS -> StepsContent(stepsGranted, hcStatus)
                Step.EXTRAS -> ExtrasContent(notifGranted, batteryOk, onAskNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, onOpenBattery = {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                })
            }
        }

        // Fußleiste: Primäraktion + gleichwertig sichtbares "Später"
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                Step.WELCOME -> Primary("Los geht’s") { next() }
                Step.EGG -> Primary("Dieses Ei nehmen") { next() }
                Step.BASICS -> Primary("Weiter") { next() }
                Step.SCREEN_TIME ->
                    if (usageGranted) Primary("Weiter") { next() } else {
                        Primary("Nutzungszugriff öffnen") { ctx.startActivity(ScreenTime.accessSettingsIntent()) }
                        Secondary("Später") { next() }
                    }
                Step.STEPS -> when {
                    stepsGranted || hcStatus == HealthConnectClient.SDK_UNAVAILABLE -> Primary("Weiter") { next() }
                    hcStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Primary("Health Connect installieren") { openStore() }
                        Secondary("Später") { next() }
                    }
                    else -> {
                        Primary("Schritte erlauben") { hcLauncher.launch(Steps.permissions) }
                        Secondary("Später") { next() }
                    }
                }
                Step.EXTRAS -> Primary("Wesen kennenlernen") {
                    sp.edit().putBoolean("onboarding.done", true).apply()
                    PetScheduler.start(ctx)                       // Hintergrund-Sync auch ohne Widget
                    EvolutionStore.ensureHatched(ctx)             // Schlüpftag festlegen
                    GrowthStore.startEgg(ctx)                     // Ei liegt auf dem Startbildschirm
                    EvolutionScheduler.start(ctx)                 // täglicher Evolutions-Abschluss
                    onFinished()
                }
            }
            if (step != Step.WELCOME) TextButton(onClick = { back() }, modifier = Modifier.fillMaxWidth()) { Text("Zurück") }
        }
    }
}

// ---------- Schritte ----------

@Composable
private fun WelcomeContent() = StepPage(
    "💚", "Dein Handy ist das Haustier",
    "OS-Mon ist ein Wesen, das sich freut, wenn es deinem Handy und dir gut geht."
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡ Akku wird zu Energie")
        Text("💾 Freier Speicher wird zu Fitness")
        Text("🌙 Bildschirmzeit wird zu Ruhe")
        Text("👟 Schritte werden zu Vitalität")
    }
    PrivacyNote("Alles wird auf deinem Gerät berechnet. Nichts wird hochgeladen. Das Wesen stirbt nie, es schläft höchstens.")
}

@Composable
private fun EggContent(taps: Int, onTap: () -> Unit) {
    // Ei aussuchen: jede Figur hat ihr eigenes Ei (Farben). Geschlüpft wird später auf dem Startbildschirm.
    val figure = rememberCurrentFigure()
    StepPage(
        null, "Such dir ein Ei aus",
        "Daraus schlüpft ${figure.name}. Tippe das Ei gleich auf dem Startbildschirm an, bis es aufbricht. " +
            "Danach wächst dein Wesen in etwa drei Monaten vom Baby zum Erwachsenen – jeder gute Tag zählt."
    ) {
        EggCanvas(figure, 0, null, Modifier.size(170.dp).semantics { contentDescription = "Ei von ${figure.name}" })
        PetFigurePicker(Modifier.fillMaxWidth())
    }
}

@Composable
private fun BasicsContent() = StepPage(
    "🔋", "Das geht ohne Erlaubnis", "Diese beiden Werte liest OS-Mon direkt vom Gerät:"
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🔋 Akkustand und Ladezustand")
        Text("💾 Freier Speicherplatz (nur als Anteil)")
    }
    PrivacyNote("OS-Mon sieht keine Dateien, Fotos oder Inhalte, nur die beiden Zahlen.")
}

@Composable
private fun ScreenTimeContent(granted: Boolean) = StepPage(
    "🌙", "Bildschirmzeit",
    "Dafür braucht OS-Mon den „Nutzungszugriff“. Er lässt sich nur in den Systemeinstellungen erteilen: " +
        "Öffnen, OS-Mon in der Liste wählen, Zugriff erlauben."
) {
    if (granted) Text("✅ Aktiv", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    PrivacyNote(
        "OS-Mon rechnet nur Tagesminuten, Nachtminuten und Kategorien aus und speichert keine App-Namen. " +
            "Ohne Nutzungszugriff bleibt der Ring „Ruhe“ leer, alles andere funktioniert."
    )
}

@Composable
private fun StepsContent(granted: Boolean, hcStatus: Int) = StepPage(
    "👟", "Schritte (optional)",
    when (hcStatus) {
        HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect wird auf diesem Gerät nicht unterstützt. Der Ring „Vitalität“ bleibt leer."
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Dafür braucht OS-Mon Health Connect. Bitte installiere oder aktualisiere die App."
        else -> "OS-Mon liest nur deine Tagesschritte aus Health Connect. Geschrieben wird nichts."
    }
) {
    if (granted) Text("✅ Aktiv", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    PrivacyNote(
        "Schritte sind Gesundheitsdaten: Sie bleiben auf dem Gerät und du kannst die Freigabe jederzeit in " +
            "Health Connect widerrufen. Manuell eingetragene Schritte lassen sich auf Android nicht ausschließen."
    )
}

@Composable
private fun ExtrasContent(
    notifGranted: Boolean,
    batteryOk: Boolean,
    onAskNotifications: () -> Unit,
    onOpenBattery: () -> Unit
) = StepPage(
    "🔔", "Fast geschafft",
    "Dein Wesen meldet sich höchstens dreimal am Tag, nie in der Nachtruhe und lernt, wenn du Meldungen ignorierst."
) {
    if (Build.VERSION.SDK_INT >= 33) {
        OutlinedButton(onClick = onAskNotifications, enabled = !notifGranted, modifier = Modifier.fillMaxWidth()) {
            Text(if (notifGranted) "✅ Meldungen erlaubt" else "Meldungen erlauben")
        }
    } else {
        Text("✅ Meldungen sind aktiv")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tipp: Hintergrund-Aktualisierung", fontWeight = FontWeight.SemiBold)
        Text(
            "Manche Hersteller beenden Hintergrundjobs früh. Nimm OS-Mon von der Akku-Optimierung aus, " +
                "auf Xiaomi, Samsung und Huawei zusätzlich unter Akku-Einstellungen Autostart bzw. „Nie schlafende Apps“ erlauben.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onOpenBattery, modifier = Modifier.fillMaxWidth()) {
            Text(if (batteryOk) "✅ Von der Akku-Optimierung ausgenommen" else "Akku-Optimierung öffnen")
        }
    }
}

// ---------- Bausteine ----------

@Composable
private fun StepPage(
    symbol: String?,
    title: String,
    text: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        symbol?.let { Text(it, fontSize = 54.sp) }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        content()
    }
}

@Composable
private fun PrivacyNote(text: String) {
    Text("🔒 $text", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(text) }
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(text) }
}

@Composable
private fun ProgressDots(count: Int, index: Int) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Schritt ${index + 1} von $count" },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val active = i == index
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (active) 22.dp else 8.dp).height(8.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800) @Composable
private fun PreviewOnboarding() = MaterialTheme { OnboardingScreen(onFinished = {}) }
