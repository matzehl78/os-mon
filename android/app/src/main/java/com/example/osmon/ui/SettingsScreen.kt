// SettingsScreen.kt: Einstellungen mit Berechtigungs-Dashboard (Jetpack Compose), Gegenstück zu SettingsView.swift
//
// PRD 1.4: je Sensor sehen, was gelesen wird, und es an- oder abschalten; sehen, was gespeichert wird;
// alles exportieren; alles in einem Schritt löschen. Dazu: Meldungen abschalten, Ziele einstellen,
// Hintergrund-Aktualisierung prüfen.
//
// Einstieg: HomeScreen(onOpenSettings = { ... }) bzw. ein Tipp auf einen leeren Ring. Navigation macht die
// Activity (z. B. ein einfacher State "showSettings" im Root-Composable):
//   if (showSettings) SettingsScreen(onBack = { showSettings = false },
//                                    onDataDeleted = { showSettings = false; onboardingDone = false })
//   else HomeScreen(onOpenSettings = { showSettings = true })
// Export braucht keine FileProvider-Einträge: Die Person wählt den Speicherort selbst (Storage Access Framework).

package com.example.osmon.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import com.example.osmon.EvolutionScheduler
import com.example.osmon.PetScheduler
import com.example.osmon.PetSync
import com.example.osmon.PetWidget
import com.example.osmon.PushDelivery
import com.example.osmon.PushStore
import com.example.osmon.ScreenTime
import com.example.osmon.Steps
import com.example.osmon.engine.PushPolicy
import com.example.osmon.figure.PetFigurePicker
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ---------- Export und Löschen ----------

object PetData {
    /** Alle SharedPreferences-Dateien, die OS-Mon anlegt ("pet" ist die Altlast aus der ersten Widget-Version). */
    val PREF_FILES = listOf("home", "push", "daylog", "evolution", "steps", "pet")

    private fun parseIfJson(v: String): Any = when {
        v.startsWith("{") -> runCatching { JSONObject(v) }.getOrDefault(v)
        v.startsWith("[") -> runCatching { JSONArray(v) }.getOrDefault(v)
        else -> v
    }

    /** Lesbarer JSON-Export aller eigenen Daten (codierte Werte werden aufgelöst). */
    fun exportJson(ctx: Context): String {
        val data = JSONObject()
        for (file in PREF_FILES) {
            val all = ctx.getSharedPreferences(file, Context.MODE_PRIVATE).all
            if (all.isEmpty()) continue
            val o = JSONObject()
            all.toSortedMap().forEach { (k, v) ->
                o.put(k, when (v) {
                    is String -> parseIfJson(v)
                    is Set<*> -> JSONArray(v.map { it.toString() }.sorted())
                    else -> v
                })
            }
            data.put(file, o)
        }
        return JSONObject()
            .put("app", "OS-Mon")
            .put("exportiertAm", LocalDate.now().toString())
            .put("hinweis", "Alle Daten, die OS-Mon auf diesem Gerät gespeichert hat. Nichts davon wurde hochgeladen.")
            .put("daten", data)
            .toString(2)
    }

    fun entryCount(ctx: Context) = PREF_FILES.sumOf { ctx.getSharedPreferences(it, Context.MODE_PRIVATE).all.size }

    fun loggedDays(ctx: Context) =
        ctx.getSharedPreferences("daylog", Context.MODE_PRIVATE).all.keys.count { it.startsWith("n.") }

    /**
     * Löscht alles, was OS-Mon gespeichert oder eingeplant hat, und gibt die Health-Connect-Freigabe zurück.
     * Schritte in Health Connect selbst gehören nicht OS-Mon und bleiben unberührt. Den Nutzungszugriff
     * kann nur die Person in den Systemeinstellungen entziehen (darauf weist der Dialog hin).
     */
    suspend fun deleteAll(ctx: Context) = withContext(Dispatchers.IO) {
        com.example.osmon.family.FamilyVault.load(ctx)?.let {
            runCatching { com.example.osmon.family.FamilyApi(it).call("DELETE", "/v1/device") }
        }
        com.example.osmon.family.FamilyVault.clear(ctx)
        PetScheduler.stop(ctx)
        EvolutionScheduler.stop(ctx)
        NotificationManagerCompat.from(ctx).cancelAll()
        runCatching { Steps.client(ctx)?.permissionController?.revokeAllPermissions() }
        for (file in PREF_FILES) ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { PetWidget().updateAll(ctx) }
    }
}

// ---------- Hilfen ----------

private fun isIgnoringBatteryOptimizations(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

/** Führt block bei jedem Zurückkehren aus (z. B. aus den Systemeinstellungen). */
@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private fun hoursText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h Std." else "$h Std. $m Min."
}

private fun thousands(n: Int) = n.toString().reversed().chunked(3).joinToString(".").reversed()

// ---------- Screen ----------

@Composable
fun SettingsScreen(onBack: () -> Unit, onDataDeleted: () -> Unit, onShowIntro: () -> Unit = {}) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sp = remember { PetSync.prefs(ctx) }
    BackHandler(onBack = onBack)

    var usageAccess by remember { mutableStateOf(ScreenTime.hasAccess(ctx)) }
    var screenOn by remember { mutableStateOf(sp.getBoolean("feature.screentime", false)) }
    var stepsOn by remember { mutableStateOf(sp.getBoolean("feature.steps", false)) }
    var stepsGranted by remember { mutableStateOf(false) }
    var canPost by remember { mutableStateOf(PushDelivery.canPost(ctx)) }
    var pushOn by remember { mutableStateOf(!PushDelivery.optedOut(ctx)) }
    var cap by remember { mutableIntStateOf(PushPolicy.dailyCap(PushStore.load(ctx))) }
    var batteryOk by remember { mutableStateOf(isIgnoringBatteryOptimizations(ctx)) }
    val cfg = remember { PetSync.config(ctx) }
    var screenGoal by remember { mutableIntStateOf(cfg.screenGoalMin.toInt()) }
    var stepGoal by remember { mutableIntStateOf(cfg.stepGoal) }
    var entries by remember { mutableIntStateOf(PetData.entryCount(ctx)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val hcStatus = remember { HealthConnectClient.getSdkStatus(ctx) }

    /** Wesen und Widget nach jeder Änderung nachziehen. */
    fun refreshPet() {
        scope.launch(Dispatchers.Default) {
            val t = PetSync.tick(ctx)
            PetSync.updateWidgetIfNeeded(ctx, t.snapshot, force = true)
            entries = PetData.entryCount(ctx)
        }
    }

    OnResume {
        usageAccess = ScreenTime.hasAccess(ctx)
        canPost = PushDelivery.canPost(ctx)
        batteryOk = isIgnoringBatteryOptimizations(ctx)
        cap = PushPolicy.dailyCap(PushStore.load(ctx))
        // Zugriff in den Systemeinstellungen erteilt, während der Schalter an war: jetzt wirksam.
        if (screenOn && usageAccess) refreshPet()
    }
    LaunchedEffect(Unit) {
        stepsGranted = Steps.client(ctx)?.let { Steps.hasPermission(it) } ?: false
    }

    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        stepsGranted = ok
        stepsOn = ok
        sp.edit().putBoolean("feature.steps", ok).apply()
        refreshPet()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(PetData.exportJson(ctx).toByteArray()) }
            }.isSuccess
            message = if (ok) "Export gespeichert." else "Export fehlgeschlagen."
        }
    }

    fun open(intent: Intent) = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !busy) { Text("‹ Zurück") }
            Spacer(Modifier.width(8.dp))
            Text("Einstellungen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Eltern-Bereich (PIN) – sperrt Ziele, Schlafenszeit und Daten löschen
        var showParent by remember { mutableStateOf(false) }
        var parentVersion by remember { mutableIntStateOf(0) }
        ParentEntry { showParent = true }
        if (showParent) ParentDialog { showParent = false; parentVersion++ }
        val locked = remember(parentVersion) { ParentStore.settingsLocked(ctx) }

        Card("Figur", "Alle Figuren zeigen dasselbe: Herz = Energie, leuchtende Antennen = Ruhe. Das Widget wechselt mit. 3D: Mit dem Finger drehen; Widget, Hintergrundbild und Uhr bleiben 2D.") {
            PetFigurePicker()
            var use3D by remember { mutableStateOf(PetSync.prefs(ctx).getBoolean(Pet3D.KEY, true)) }
            ToggleRow("3D-Wesen", use3D) {
                use3D = it
                PetSync.prefs(ctx).edit().putBoolean(Pet3D.KEY, it).apply { if (it) putBoolean(Pet3D.FAILED, false) }.apply()
            }
            var sound by remember { mutableStateOf(SoundPlayer.enabled(ctx)) }
            ToggleRow("Töne & Stimme", sound) { sound = it; SoundPlayer.setEnabled(ctx, it); if (it) SoundPlayer.play(ctx, com.example.osmon.engine.Sfx.GIGGLE) }
        }

        Card("Hintergrund", "Weitere Hintergründe schaltest du mit gesunden Gewohnheiten frei: Bewegung, Fokus-Pausen, weniger Bildschirm. Freigeschaltet bleibt freigeschaltet.") {
            val sceneStatus = remember { ProgressData.refresh(ctx).status }
            ScenePicker(sceneStatus, onSelect = { SceneStore.select(ctx, it, sceneStatus) })
        }

        Card("Auf dem Sperrbildschirm", "Dein Wesen als lebendes Hintergrundbild für Sperr- und Startbildschirm: gewählte Figur und Hintergrund, aktuelle Stimmung, Fest-Zubehör. Es bewegt sich nur, solange der Bildschirm an ist.") {
            var active by remember { mutableStateOf(com.example.osmon.PetWallpaper.isActive(ctx)) }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) active = com.example.osmon.PetWallpaper.isActive(ctx) }
                owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
            }
            if (active) Status(true, "Ist als Hintergrundbild aktiv")
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(com.example.osmon.PetWallpaper.intent(ctx)) }
                    .onFailure { runCatching { ctx.startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } }
            }) { Text(if (active) "Hintergrundbild erneut festlegen" else "Als Hintergrundbild festlegen") }
        }

        Card(null, null) {
            TextButton(onClick = onShowIntro) { Text("▶  Einführungsfilm ansehen") }
        }

        if (locked) LockedByParent("Abend und Morgen") { showParent = true } else Card("Abend und Morgen", "30 Minuten vor der Schlafenszeit wird dein Wesen müde und lädt zur Gute-Nacht-Routine ein. Morgens gibt es einen kurzen Bericht über die Nacht und einen Tipp für den Tag.") {
            SleepSettings()
        }

        Card("Feste, Jahreszeiten und Geburtstag", "Weihnachten, Ostern, Halloween und mehr: Dein Wesen trägt passendes Zubehör, der Hintergrund bekommt Deko (z. B. Tannenbaum, Ballons). Jahreszeiten färben das Bild leicht und bringen Blüten, Blätter oder Schnee. Der Geburtstag wird nur mit Tag und Monat auf diesem Gerät gespeichert.") {
            FestivalSettings()
        }

        Card("Was OS-Mon liest", "Alles wird auf diesem Gerät berechnet. Es gibt kein Konto und keinen Upload.") {
            Info("🔋 Akku", "Ladestand und Laden. Keine Erlaubnis nötig.")
            Info("💾 Speicher", "Nur der Anteil freien Speichers. Keine Inhalte.")
        }

        Card(null, "OS-Mon speichert nur Minuten pro Kategorie, keine App-Namen. Ohne Bildschirmzeit bleibt „Ruhe“ leer und es gibt keine Bildschirmzeit- und Nacht-Meldungen.") {
            ToggleRow("🌙 Bildschirmzeit", screenOn, enabled = !busy) { on ->
                screenOn = on
                sp.edit().putBoolean("feature.screentime", on).apply()
                if (on && !usageAccess) open(ScreenTime.accessSettingsIntent())
                refreshPet()
            }
            Status(
                ok = screenOn && usageAccess,
                text = when {
                    !screenOn && usageAccess -> "Aus (der Nutzungszugriff ist noch erteilt, siehe unten)"
                    !screenOn -> "Aus"
                    usageAccess -> "Aktiv"
                    else -> "Nutzungszugriff fehlt"
                }
            )
            OutlinedButton(onClick = { open(ScreenTime.accessSettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Nutzungszugriff in den Systemeinstellungen")
            }
        }

        Card(null, "Nur die Tagessumme wird gelesen, nichts geschrieben. Manuell eingetragene Schritte lassen sich auf Android nicht ausschließen.") {
            when (hcStatus) {
                HealthConnectClient.SDK_UNAVAILABLE -> Info("👟 Schritte", "Health Connect wird auf diesem Gerät nicht unterstützt.")
                else -> {
                    ToggleRow("👟 Schritte (Health Connect)", stepsOn, enabled = !busy) { on ->
                        if (on) {
                            if (stepsGranted) {
                                stepsOn = true
                                sp.edit().putBoolean("feature.steps", true).apply()
                                refreshPet()
                            } else if (hcStatus == HealthConnectClient.SDK_AVAILABLE) {
                                hcLauncher.launch(Steps.permissions)
                            } else {
                                open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                            }
                        } else {
                            stepsOn = false
                            sp.edit().putBoolean("feature.steps", false).apply()
                            refreshPet()
                        }
                    }
                    Status(stepsOn && stepsGranted, when {
                        !stepsOn -> "Aus"
                        stepsGranted -> "Aktiv"
                        else -> "Freigabe fehlt"
                    })
                    OutlinedButton(onClick = { open(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()) { Text("Freigaben in Health Connect") }
                }
            }
        }

        Card("Meldungen", "Nie zwischen 22 und 8 Uhr (außer wenn du nachts am Handy bist). Wenn du Meldungen ignorierst, meldet sich dein Wesen seltener.") {
            ToggleRow("🔔 Meldungen vom Wesen", pushOn, enabled = !busy) { on ->
                pushOn = on
                PushDelivery.setOptedOut(ctx, !on)
            }
            Status(pushOn && canPost, when {
                !canPost -> "Im System ausgeschaltet"
                pushOn -> "Erlaubt"
                else -> "In OS-Mon ausgeschaltet"
            })
            if (!canPost) {
                OutlinedButton(onClick = {
                    open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }, modifier = Modifier.fillMaxWidth()) { Text("Benachrichtigungen im System") }
            }
            if (pushOn) {
                Text("Derzeit höchstens $cap ${if (cap == 1) "Meldung" else "Meldungen"} am Tag",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cap < PushPolicy.BASE_CAP) {
                    TextButton(onClick = {
                        PushStore.update(ctx) { PushPolicy.onEngaged(it) }
                        cap = PushPolicy.dailyCap(PushStore.load(ctx))
                    }) { Text("Wieder bis zu ${PushPolicy.BASE_CAP} Meldungen erlauben") }
                }
            }
        }

        Card("Hintergrund", "Manche Hersteller beenden Hintergrundjobs früh. Dann aktualisiert sich dein Wesen seltener.") {
            Status(batteryOk, if (batteryOk) "Von der Akku-Optimierung ausgenommen" else "Akku-Optimierung aktiv")
            if (!batteryOk) {
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()) { Text("Akku-Optimierung öffnen") }
            }
        }

        if (locked) LockedByParent("Ziele") { showParent = true } else Card("Ziele", "Bestimmen, ab wann dein Wesen gestresst wird bzw. voller Energie ist.") {
            StepRow("Bildschirmzeit am Tag", hoursText(screenGoal),
                canDec = screenGoal > PetSync.SCREEN_GOAL_RANGE.first, canInc = screenGoal < PetSync.SCREEN_GOAL_RANGE.last,
                onDec = { screenGoal -= 15; PetSync.setGoals(ctx, screenGoal, stepGoal); refreshPet() },
                onInc = { screenGoal += 15; PetSync.setGoals(ctx, screenGoal, stepGoal); refreshPet() })
            StepRow("Schritte am Tag", thousands(stepGoal),
                canDec = stepGoal > PetSync.STEP_GOAL_RANGE.first, canInc = stepGoal < PetSync.STEP_GOAL_RANGE.last,
                onDec = { stepGoal -= 500; PetSync.setGoals(ctx, screenGoal, stepGoal); refreshPet() },
                onInc = { stepGoal += 500; PetSync.setGoals(ctx, screenGoal, stepGoal); refreshPet() })
        }

        Card("Was gespeichert wird", "$entries Einträge, ausschließlich auf diesem Gerät.") {
            Info("🐾 Zustand deines Wesens", "Letzte Werte und Stimmung.")
            Info("📅 Tageswerte", "Wohlbefinden-Mittel für ${PetData.loggedDays(ctx)} Tage, Streichel- und Fokus-Zähler.")
            Info("✨ Entwicklung", "Schlüpftag, Form und die letzten 28 Tage als Anteile pro Kategorie.")
            Info("🌙 Bildschirmzeit", "Minuten heute (Zwischenspeicher, 10 Minuten gültig). Keine App-Namen.")
            Info("👟 Schritte", "Nur die Tagessumme.")
            Info("🔔 Meldungen", "Wann welche Meldung kam und ob du reagiert hast.")
        }

        Card(null, "Der Export enthält alles, was OS-Mon über dich gespeichert hat, in lesbarer Form.") {
            OutlinedButton(onClick = { exportLauncher.launch("OS-Mon-Export-${LocalDate.now()}.json") },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Daten exportieren (JSON)") }
            if (!locked) OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Alle Daten löschen", color = MaterialTheme.colorScheme.error)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Alle Daten löschen?") },
            text = {
                Text("Dein Wesen, seine Entwicklung, alle Tageswerte und Einstellungen werden von diesem Gerät entfernt. " +
                    "Das lässt sich nicht rückgängig machen. Den Nutzungszugriff entziehst du danach in den Systemeinstellungen.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        PetData.deleteAll(ctx)
                        busy = false
                        onDataDeleted()
                    }
                }) { Text("Alles löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

// ---------- Bausteine ----------

@Composable
private fun Card(title: String?, footer: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
        footer?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Info(title: String, detail: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun Status(ok: Boolean, text: String) {
    Text((if (ok) "✅ " else "⚠️ ") + text, style = MaterialTheme.typography.bodySmall,
        color = if (ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepRow(
    title: String, value: String, canDec: Boolean, canInc: Boolean, onDec: () -> Unit, onInc: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onDec, enabled = canDec,
            modifier = Modifier.semantics { contentDescription = "$title verringern" }) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onInc, enabled = canInc,
            modifier = Modifier.semantics { contentDescription = "$title erhöhen" }) { Text("+") }
    }
}
