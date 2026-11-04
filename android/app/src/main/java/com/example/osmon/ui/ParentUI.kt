// ParentUI.kt: Eltern-Bereich mit PIN. Gegenstück: ParentUI.swift.
// PIN (nur als Hash, engine/ParentLock.kt), Wochenübersicht, Aufgaben fürs Kind (kann das Kind nicht löschen),
// Einstellungen sperren (Ziele, Abend und Morgen, Daten löschen). Nach dem Entsperren 5 Minuten offen.

package com.example.osmon.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.osmon.DayLog
import com.example.osmon.PetSync
import com.example.osmon.PhoneWearBridge
import com.example.osmon.engine.DayTask
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.ParentLock
import com.example.osmon.engine.TaskRepeat
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object ParentStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)
    private var unlockedUntil = 0L                 // nur im Speicher: nach Neustart wieder gesperrt

    fun hasPin(ctx: Context) = p(ctx).contains("parent.hash")
    fun isUnlocked() = System.currentTimeMillis() < unlockedUntil
    fun lockNow() { unlockedUntil = 0L }

    /** Einstellungen gesperrt? (PIN gesetzt, Sperre an, nicht gerade entsperrt) */
    fun settingsLocked(ctx: Context) = hasPin(ctx) && p(ctx).getBoolean("parent.lockSettings", true) && !isUnlocked()
    fun lockSettings(ctx: Context) = p(ctx).getBoolean("parent.lockSettings", true)
    fun setLockSettings(ctx: Context, on: Boolean) { p(ctx).edit().putBoolean("parent.lockSettings", on).apply() }

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p(ctx).edit().putString("parent.salt", salt).putString("parent.hash", ParentLock.hash(pin, salt))
            .putInt("parent.failures", 0).putLong("parent.waitUntil", 0L).apply()
        unlockedUntil = System.currentTimeMillis() + ParentLock.SESSION_MS
    }

    fun removePin(ctx: Context) { p(ctx).edit().remove("parent.salt").remove("parent.hash").remove("parent.failures").remove("parent.waitUntil").apply(); lockNow() }

    sealed interface Result { data object Ok : Result; data class Wrong(val left: Int) : Result; data class Wait(val seconds: Long) : Result }

    fun tryUnlock(ctx: Context, pin: String): Result {
        val now = System.currentTimeMillis()
        val waitUntil = p(ctx).getLong("parent.waitUntil", 0L)
        if (now < waitUntil) return Result.Wait((waitUntil - now + 999) / 1000)
        val salt = p(ctx).getString("parent.salt", "")!!; val hash = p(ctx).getString("parent.hash", "")!!
        if (ParentLock.verify(pin, salt, hash)) {
            p(ctx).edit().putInt("parent.failures", 0).apply()
            unlockedUntil = now + ParentLock.SESSION_MS
            return Result.Ok
        }
        val f = p(ctx).getInt("parent.failures", 0) + 1
        val wait = ParentLock.waitMs(f)
        p(ctx).edit().putInt("parent.failures", f).putLong("parent.waitUntil", if (wait > 0) now + wait else 0L).apply()
        return if (wait > 0) Result.Wait(wait / 1000) else Result.Wrong(ParentLock.MAX_TRIES - f)
    }

    fun addTask(ctx: Context, title: String, emoji: String, repeat: TaskRepeat): Boolean {
        val list = TaskStore.load(ctx)
        if (!DayTasks.canAdd(list, TaskStore.today(), title)) return false
        TaskStore.save(ctx, list + TaskStore.newTask(title, emoji, repeat).copy(fromParent = true))
        TaskEvents.changed(); PhoneWearBridge.push(ctx)
        return true
    }

    fun removeTask(ctx: Context, t: DayTask) {
        TaskStore.save(ctx, TaskStore.load(ctx).filterNot { it.id == t.id })
        TaskEvents.changed(); PhoneWearBridge.push(ctx)
    }

    /** Ein Tag der Wochenübersicht. */
    data class DayRow(val day: Long, val wellbeing: Float?, val tasksDone: Int, val tasksDue: Int, val goodnight: Boolean, val rough: Int)

    fun week(ctx: Context): List<DayRow> {
        val today = TaskStore.today(); val tasks = TaskStore.load(ctx)
        return (6 downTo 0).map { today - it }.map { d ->
            val p = DayTasks.progress(tasks, d)
            DayRow(d, DayLog.average(ctx, d), p.done, p.total, SleepStore.saidGoodnight(ctx, d), GrowthStore.rough(ctx, d))
        }
    }
}

// ---------- Oberfläche ----------

/** Eintrag oben in den Einstellungen. */
@Composable
fun ParentEntry(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("👪", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Eltern-Bereich", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (ParentStore.hasPin(ctx)) "Mit PIN geschützt" else "PIN festlegen, Aufgaben stellen, Woche ansehen",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onOpen) { Text("Öffnen") }
        }
    }
}

/** Hinweis statt gesperrter Einstellungen. */
@Composable
fun LockedByParent(title: String, onUnlock: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔒"); Spacer(Modifier.width(10.dp))
            Text("$title ist durch den Eltern-PIN geschützt.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onUnlock) { Text("Entsperren") }
        }
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { v -> onChange(v.filter { it.isDigit() }.take(4)) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) }, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

/** Vollbild: PIN festlegen / eingeben, danach der Eltern-Bereich. onChanged = Einstellungen neu zeichnen. */
@Composable
fun ParentDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { version++ }
    val familyOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var wasUnlocked = ParentStore.isUnlocked()
        while (true) {
            kotlinx.coroutines.delay(500)
            val unlocked = ParentStore.isUnlocked()
            if (wasUnlocked && !unlocked) version++
            wasUnlocked = unlocked
        }
    }
    androidx.compose.runtime.DisposableEffect(familyOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { ParentStore.lockNow(); version++ }
        }
        familyOwner.lifecycle.addObserver(observer)
        onDispose { familyOwner.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👪 Eltern-Bereich", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClose) { Text("Fertig") }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    key(version) {
                        when {
                            !ParentStore.hasPin(ctx) -> PinSetup(refresh)
                            !ParentStore.isUnlocked() -> PinEntry(refresh)
                            else -> ParentContent(refresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinSetup(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Text("Lege einen 4-stelligen PIN fest. Damit schützt du Ziele, Schlafenszeit und das Löschen der Daten. Den PIN kennt nur ihr Eltern.",
        style = MaterialTheme.typography.bodyMedium)
    PinField(a, "PIN") { a = it; err = null }
    PinField(b, "PIN wiederholen") { b = it; err = null }
    err?.let { Text(it, color = Color(0xFFD9667A), style = MaterialTheme.typography.bodySmall) }
    Button(onClick = {
        err = when {
            !ParentLock.isValidPin(a) -> "Bitte genau 4 Ziffern."
            ParentLock.isTooSimple(a) -> "Zu einfach (z. B. 1111 oder 1234). Bitte einen anderen PIN."
            a != b -> "Die beiden PINs sind verschieden."
            else -> null
        }
        if (err == null) { ParentStore.setPin(ctx, a); onDone() }
    }, enabled = a.length == 4 && b.length == 4) { Text("PIN speichern") }
}

@Composable
private fun PinEntry(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    Text("PIN eingeben", style = MaterialTheme.typography.titleMedium)
    PinField(pin, "PIN") { pin = it; msg = null }
    msg?.let { Text(it, color = Color(0xFFD9667A), style = MaterialTheme.typography.bodySmall) }
    Button(onClick = {
        when (val r = ParentStore.tryUnlock(ctx, pin)) {
            ParentStore.Result.Ok -> onDone()
            is ParentStore.Result.Wrong -> msg = "Falscher PIN. Noch ${r.left} Versuche."
            is ParentStore.Result.Wait -> msg = "Zu viele Versuche. Bitte ${r.seconds} Sekunden warten."
        }
        pin = ""
    }, enabled = pin.length == 4) { Text("Entsperren") }
    Text("PIN vergessen? Über „Alle Daten löschen“ auf dem Gerät (Systemeinstellungen → Apps → OS-Mon → Speicher) wird alles zurückgesetzt.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ParentContent(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.load(ctx).filter { it.fromParent }) }

    // Wochenübersicht
    Text("Diese Woche", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val week = remember { ParentStore.week(ctx) }
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { r ->
                val name = LocalDate.ofEpochDay(r.day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "$name: Wohlbefinden ${r.wellbeing?.toInt() ?: "keine Messung"}, Aufgaben ${r.tasksDone} von ${r.tasksDue}" +
                        (if (r.goodnight) ", Gute Nacht gesagt" else "") + (if (r.rough > 0) ", ${r.rough}× grob" else "")
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.width(36.dp), fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { (r.wellbeing ?: 0f) / 100f }, modifier = Modifier.weight(1f),
                        color = when { r.wellbeing == null -> Color.Gray; r.wellbeing >= 65 -> Color(0xFF2E9E5B); r.wellbeing >= 40 -> Color(0xFFE08A1E); else -> Color(0xFFD9667A) })
                    Spacer(Modifier.width(8.dp))
                    Text("${r.wellbeing?.toInt() ?: "–"}", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
                    Text(if (r.tasksDue > 0) "✅ ${r.tasksDone}/${r.tasksDue}" else "", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    Text((if (r.goodnight) "🌙" else "") + (if (r.rough > 0) " ✋${r.rough}" else ""), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("Balken = Wohlbefinden im Tagesmittel · ✅ Aufgaben · 🌙 Gute Nacht gesagt · ✋ grob behandelt",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Aufgaben fürs Kind
    Text("Aufgaben fürs Kind", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(TaskRepeat.DAILY) }
    OutlinedTextField(title, { title = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("z. B. Zähne putzen") })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(TaskRepeat.ONCE to "Einmal", TaskRepeat.DAILY to "Täglich", TaskRepeat.WEEKDAYS to "Mo–Fr", TaskRepeat.WEEKEND to "Wochenende").forEach { (r, l) ->
            if (r == repeat) Button(onClick = {}) { Text(l) } else OutlinedButton(onClick = { repeat = r }) { Text(l) }
        }
    }
    Button(onClick = {
        if (ParentStore.addTask(ctx, title, "👪", repeat)) { title = ""; tasks = TaskStore.load(ctx).filter { it.fromParent } }
    }, enabled = title.isNotBlank()) { Text("Aufgabe stellen") }
    tasks.forEach { t ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("👪  ${t.title}", Modifier.weight(1f))
            Text(when (t.repeat) { TaskRepeat.DAILY -> "täglich"; TaskRepeat.WEEKDAYS -> "Mo–Fr"; TaskRepeat.WEEKEND -> "Wochenende"; else -> "einmal" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { ParentStore.removeTask(ctx, t); tasks = TaskStore.load(ctx).filter { it.fromParent } },
                modifier = Modifier.semantics { contentDescription = "${t.title} löschen" }) { Text("✕") }
        }
    }
    Text("Das Kind sieht diese Aufgaben mit „von den Eltern“, kann sie abhaken, aber nicht löschen.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Mini-Spiele: Runden pro Tag
    Text("Mini-Spiele", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    var limit by remember { mutableIntStateOf(GameStore.limit(ctx)) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..com.example.osmon.engine.MiniGames.MAX_LIMIT).forEach { n ->
            val l = if (n == 0) "Aus" else "$n× täglich"
            if (n == limit) Button(onClick = {}) { Text(l) } else OutlinedButton(onClick = { limit = n; GameStore.setLimit(ctx, n) }) { Text(l) }
        }
    }
    Text("Jede Runde dauert 30 Sekunden. Standard: 2 Runden am Tag.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    FamilyChildCard()

    // Premium (Einmalkauf, nur hier hinter dem PIN)
    Text("Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    PremiumCard()

    // Schutz
    Text("Schutz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    var lock by remember { mutableStateOf(ParentStore.lockSettings(ctx)) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Einstellungen sperren (Ziele, Schlafenszeit, Daten löschen)", Modifier.weight(1f))
        Switch(lock, { lock = it; ParentStore.setLockSettings(ctx, it); onChanged() })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { ParentStore.removePin(ctx); onChanged() }) { Text("PIN entfernen") }
        Button(onClick = { ParentStore.lockNow(); onChanged() }) { Text("Jetzt sperren") }
    }
}
