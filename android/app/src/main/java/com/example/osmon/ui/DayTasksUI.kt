// DayTasksUI.kt: Aufgabenliste mit Motivation und „Miteinander“ (Verhaltenstipps). Gegenstück: DayTasksUI.swift.
//
// Startseite: Karte „Meine Aufgaben“ (Fortschritt, Satz vom Wesen, bis zu 4 Aufgaben zum Abhaken) und Karte
// „Miteinander“ (Tipp des Tages, „Hab ich gemacht“, als Aufgabe übernehmen). Vollbild-Dialoge zum Verwalten
// und für alle Tipps. Regeln: engine/DayTasks.kt, Texte: res/raw/tasks.json (gleiche Datei wie iOS).
// Gespeichert wird nur auf dem Gerät (SharedPreferences, JSON).

package com.example.osmon.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.osmon.PetSync
import com.example.osmon.PhoneWearBridge
import com.example.osmon.R
import com.example.osmon.engine.DayTask
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.TaskProgress
import com.example.osmon.engine.TaskRepeat
import com.example.osmon.engine.TaskStage
import com.example.osmon.figure.PetFigures
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

// ---------- Inhalte ----------

data class TaskIdea(val emoji: String, val title: String)
data class IdeaGroup(val title: String, val items: List<TaskIdea>)
data class KindTip(val tip: String, val task: String)
data class KindCategory(val id: String, val emoji: String, val title: String, val tips: List<KindTip>)

class TaskContent(
    val suggestions: List<IdeaGroup>, val motivation: Map<TaskStage, List<String>>, val done: List<String>,
    val kindness: List<KindCategory>
) {
    val allTips: List<Pair<KindCategory, KindTip>> get() = kindness.flatMap { c -> c.tips.map { c to it } }
    fun tipOfTheDay(day: Long) = DayTasks.pick(allTips, day, 5)
    fun motivation(p: TaskProgress, day: Long, pet: String) =
        DayTasks.pick(motivation[p.stage].orEmpty(), day, p.done)?.let { DayTasks.fill(it, pet) } ?: ""

    companion object {
        @Volatile private var cached: TaskContent? = null
        fun get(ctx: Context): TaskContent = cached ?: synchronized(this) { cached ?: load(ctx).also { cached = it } }

        private fun load(ctx: Context): TaskContent {
            val o = JSONObject(ctx.resources.openRawResource(R.raw.tasks).bufferedReader().use { it.readText() })
            fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }
            fun JSONArray.strs() = (0 until length()).map { getString(it) }
            val sug = o.getJSONArray("suggestions").objs().map { g ->
                IdeaGroup(g.getString("group"), g.getJSONArray("items").objs().map { TaskIdea(it.getString("emoji"), it.getString("title")) })
            }
            val m = o.getJSONObject("motivation")
            val mot = TaskStage.entries.associateWith { m.optJSONArray(it.name.lowercase())?.strs().orEmpty() }
            val kind = o.getJSONArray("kindness").objs().map { c ->
                KindCategory(c.getString("id"), c.getString("emoji"), c.getString("title"),
                    c.getJSONArray("tips").objs().map { KindTip(it.getString("tip"), it.getString("task")) })
            }
            return TaskContent(sug, mot, o.getJSONArray("done").strs(), kind)
        }
    }
}

// ---------- Speicher ----------

object TaskStore {
    private const val KEY = "tasks.list"
    private fun p(ctx: Context) = PetSync.prefs(ctx)
    fun today() = LocalDate.now().toEpochDay()

    fun load(ctx: Context): List<DayTask> {
        val raw = p(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                val d = o.getJSONArray("done")
                DayTask(o.getString("id"), o.getString("title"), o.optString("emoji", "⭐"),
                    runCatching { TaskRepeat.valueOf(o.getString("repeat")) }.getOrDefault(TaskRepeat.ONCE),
                    o.getLong("created"), (0 until d.length()).map { d.getLong(it) }.toSet(), o.optBoolean("parent"))
            }
        }.getOrDefault(emptyList()).let { DayTasks.cleanup(it, today()) }
    }

    fun save(ctx: Context, list: List<DayTask>) {
        val a = JSONArray()
        list.forEach { t ->
            a.put(JSONObject().put("id", t.id).put("title", t.title).put("emoji", t.emoji).put("repeat", t.repeat.name)
                .put("created", t.createdDay).put("done", JSONArray(t.doneDays.sorted())).put("parent", t.fromParent))
        }
        p(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun newTask(title: String, emoji: String, repeat: TaskRepeat) =
        DayTask(UUID.randomUUID().toString(), title.trim().take(40), emoji, repeat, today())

    // Miteinander: Tipp des Tages ausprobiert (pro Tag) und Anzahl insgesamt
    fun kindDone(ctx: Context, day: Long) = p(ctx).getBoolean("kind.done.$day", false)
    fun kindCount(ctx: Context) = p(ctx).getInt("kind.count", 0)
    fun markKind(ctx: Context, day: Long) {
        if (kindDone(ctx, day)) return
        p(ctx).edit().putBoolean("kind.done.$day", true).putInt("kind.count", kindCount(ctx) + 1).apply()
    }
}

/** Aufgaben wurden außerhalb der Oberfläche geändert (z. B. auf der Uhr abgehakt). */
object TaskEvents {
    val version = MutableStateFlow(0)
    fun changed() { version.value++ }
}

/** Gemeinsamer Zustand für beide Karten und die Dialoge. */
class TaskModel(private val ctx: Context) {
    var tasks by mutableStateOf(TaskStore.load(ctx))
        private set
    var cheer by mutableStateOf<String?>(null)          // kurzer Satz nach dem Abhaken
    val day get() = TaskStore.today()

    fun update(list: List<DayTask>) { tasks = list; TaskStore.save(ctx, list); PhoneWearBridge.push(ctx) }
    fun reload() { tasks = TaskStore.load(ctx) }
    fun add(title: String, emoji: String, repeat: TaskRepeat): Boolean {
        if (!DayTasks.canAdd(tasks, day, title)) return false
        update(tasks + TaskStore.newTask(title, emoji, repeat)); return true
    }
    fun remove(t: DayTask) = update(tasks.filterNot { it.id == t.id })
    /** true, wenn damit alles erledigt ist. */
    fun toggle(t: DayTask): Boolean {
        val wasDone = DayTasks.isDone(t, day)
        if (!wasDone) AchievementStore.addTask(ctx)          // Sammelalbum
        update(tasks.map { if (it.id == t.id) DayTasks.toggle(it, day) else it })
        return !wasDone && DayTasks.progress(tasks, day).stage == TaskStage.ALL
    }
}

// ---------- Startseite ----------

/**
 * Beide Karten der Startseite. onCheer(alles erledigt, Satz) lässt das Wesen reagieren (Hüpfer bzw. Drehung mit Konfetti).
 */
@Composable
fun TasksSection(onCheer: (Boolean, String) -> Unit, demoSeed: Boolean = false) {
    val ctx = LocalContext.current
    val model = remember { if (demoSeed) TaskDemo.seed(ctx); TaskModel(ctx) }
    val content = remember { TaskContent.get(ctx) }
    val pet = remember { PetFigures.current(ctx).name }
    var showAll by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showKind by remember { mutableStateOf(false) }
    val view = LocalView.current
    val changes by TaskEvents.version.collectAsState()
    LaunchedEffect(changes) { if (changes > 0) model.reload() }

    val onToggle: (DayTask) -> Unit = { t ->
        val nowDone = !DayTasks.isDone(t, model.day)
        val all = model.toggle(t)
        if (nowDone) {
            val line = if (all) content.motivation(DayTasks.progress(model.tasks, model.day), model.day, pet)
                else DayTasks.pick(content.done, model.day, model.tasks.sumOf { it.doneDays.size })?.let { DayTasks.fill(it, pet, t.title) } ?: ""
            model.cheer = line
            view.performHapticFeedback(if (all) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
            onCheer(all, line)
        } else model.cheer = null
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TasksCard(model, content, pet, onToggle, onShowAll = { showAll = true }, onAdd = { showAdd = true })
        KindnessCard(model, content, pet, onCheer = { onCheer(false, it) }, onShowAll = { showKind = true })
    }

    if (showAll || showAdd) TasksDialog(model, content, startWithAdd = showAdd, onToggle = onToggle) { showAll = false; showAdd = false }
    if (showKind) KindnessDialog(model, content) { showKind = false }
}

@Composable
private fun TasksCard(model: TaskModel, content: TaskContent, pet: String, onToggle: (DayTask) -> Unit,
                      onShowAll: () -> Unit, onAdd: () -> Unit) {
    val day = model.day
    val list = DayTasks.forDay(model.tasks, day)
    val p = DayTasks.progress(model.tasks, day)
    val streak = DayTasks.streak(model.tasks, day)
    val accent = Color(0xFF1C9A9A)
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✅ Meine Aufgaben", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (streak >= 2) Text("🔥 $streak Tage", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE08A1E),
                    modifier = Modifier.semantics { contentDescription = "$streak Tage am Stück alles geschafft" })
                if (p.total > 0) Text("  ${p.done}/${p.total}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            // Satz vom Wesen
            Text("$pet: " + (model.cheer ?: content.motivation(p, day, pet)), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.total > 0) LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth(),
                color = if (p.stage == TaskStage.ALL) Color(0xFF2E9E5B) else accent)
            list.take(4).forEach { TaskRow(it, day, onToggle) }
            if (list.size > 4) Text("+ ${list.size - 4} weitere", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAdd) { Text("＋ Aufgabe") }
                if (model.tasks.isNotEmpty()) TextButton(onClick = onShowAll) { Text("Alle verwalten") }
            }
        }
    }
}

@Composable
private fun TaskRow(t: DayTask, day: Long, onToggle: (DayTask) -> Unit, onDelete: ((DayTask) -> Unit)? = null) {
    val done = DayTasks.isDone(t, day)
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Checkbox) { onToggle(t) }
            .semantics(mergeDescendants = true) { stateDescription = if (done) "erledigt" else "offen" }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (done) "✅" else "⬜", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(t.emoji)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            val sub = when {
                DayTasks.isCarried(t, day) -> "von gestern"
                t.fromParent -> "👪 von den Eltern"
                t.repeat == TaskRepeat.DAILY -> "täglich"
                t.repeat == TaskRepeat.WEEKDAYS -> "Mo–Fr"
                t.repeat == TaskRepeat.WEEKEND -> "am Wochenende"
                else -> null
            }
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (it == "von gestern") Color(0xFFE08A1E) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        onDelete?.let { del ->
            TextButton(onClick = { del(t) }, modifier = Modifier.semantics { contentDescription = "${t.title} löschen" }) { Text("✕") }
        }
    }
}

@Composable
private fun KindnessCard(model: TaskModel, content: TaskContent, pet: String, onCheer: (String) -> Unit, onShowAll: () -> Unit) {
    val ctx = LocalContext.current
    val day = model.day
    val (cat, tip) = content.tipOfTheDay(day) ?: return
    var done by remember { mutableStateOf(TaskStore.kindDone(ctx, day)) }
    var count by remember { mutableStateOf(TaskStore.kindCount(ctx)) }
    var added by remember { mutableStateOf(model.tasks.any { it.title == tip.task && DayTasks.isDue(it, day) }) }
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE91E63).copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💛 Miteinander", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Color(0xFFC2185B))
                Text("${cat.emoji} ${cat.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(tip.tip, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (count > 0) Text("$count gute Taten bisher – $pet ist stolz auf dich.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (done) Text("✅ Heute ausprobiert", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2E9E5B))
                else Button(onClick = {
                    TaskStore.markKind(ctx, day); done = true; count = TaskStore.kindCount(ctx)
                    onCheer("Danke! Die Welt ist ein bisschen netter geworden. 💛")
                }) { Text("Hab ich gemacht") }
                if (!added && !done) TextButton(onClick = { added = model.add(tip.task, "💛", TaskRepeat.ONCE) }) { Text("Als Aufgabe") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onShowAll) { Text("Alle Tipps") }
            }
        }
    }
}

// ---------- Dialoge ----------

private val EMOJIS = listOf("⭐", "📚", "🧹", "🪥", "💧", "🍎", "🌳", "🏃", "🎒", "🐾", "🎨", "🎵", "📞", "💛", "🧘", "📵")

@Composable
private fun FullDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClose) { Text("Fertig") }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick)
            .semantics { if (selected) stateDescription = "ausgewählt" }
    ) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun TasksDialog(model: TaskModel, content: TaskContent, startWithAdd: Boolean, onToggle: (DayTask) -> Unit, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("⭐") }
    var repeat by remember { mutableStateOf(TaskRepeat.ONCE) }
    var full by remember { mutableStateOf(false) }
    val day = model.day
    FullDialog(if (startWithAdd) "Neue Aufgabe" else "Meine Aufgaben", onClose) {
        // Neue Aufgabe
        OutlinedTextField(title, { title = it.take(40); full = false }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Was möchtest du heute schaffen?") })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EMOJIS.forEach { e -> Chip(e, e == emoji) { emoji = e } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(TaskRepeat.ONCE to "Einmal", TaskRepeat.DAILY to "Täglich", TaskRepeat.WEEKDAYS to "Mo–Fr", TaskRepeat.WEEKEND to "Wochenende")
                .forEach { (r, l) -> Chip(l, r == repeat) { repeat = r } }
        }
        Button(onClick = {
            if (model.add(title, emoji, repeat)) { title = ""; emoji = "⭐"; repeat = TaskRepeat.ONCE } else full = title.isNotBlank()
        }, enabled = title.isNotBlank()) { Text("Hinzufügen") }
        if (full) Text("Höchstens ${DayTasks.MAX_TASKS} Aufgaben pro Tag – lieber weniger und dafür geschafft!",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFE08A1E))

        // Heute
        val list = DayTasks.forDay(model.tasks, day)
        if (list.isNotEmpty()) {
            Text("Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            list.forEach { TaskRow(it, day, onToggle, onDelete = if (it.fromParent) null else model::remove) }
        }
        // Wiederkehrende, die heute nicht dran sind (z. B. Wochenende)
        val later = model.tasks.filter { !DayTasks.isDue(it, day) && it.repeat != TaskRepeat.ONCE }
        if (later.isNotEmpty()) {
            Text("An anderen Tagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            later.forEach { TaskRow(it, day, {}, onDelete = if (it.fromParent) null else model::remove) }
        }

        // Ideen
        Text("Ideen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content.suggestions.forEach { g ->
            Text(g.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                g.items.forEach { i -> Chip("${i.emoji} ${i.title}") { title = i.title; emoji = i.emoji } }
            }
        }
    }
}

@Composable
private fun KindnessDialog(model: TaskModel, content: TaskContent, onClose: () -> Unit) {
    var open by remember { mutableStateOf(content.kindness.firstOrNull()?.id) }
    var added by remember { mutableStateOf(setOf<String>()) }
    val day = model.day
    FullDialog("Miteinander", onClose) {
        Text("Kleine Regeln, die das Zusammenleben schöner machen. Such dir einen Tipp aus und probier ihn heute aus.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content.kindness.forEach { c ->
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { open = if (open == c.id) null else c.id }, verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(c.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (open == c.id) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (open == c.id) c.tips.forEach { t ->
                        val has = t.task in added || model.tasks.any { it.title == t.task && DayTasks.isDue(it, day) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("• ${t.tip}", style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                if (has) Text("✓ in deinen Aufgaben", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E9E5B),
                                    modifier = Modifier.padding(vertical = 6.dp))
                                else TextButton(onClick = { if (model.add(t.task, "💛", TaskRepeat.ONCE)) added = added + t.task }) {
                                    Text("＋ Als Aufgabe für heute", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Vorführmodus (Screenshots) ----------

object TaskDemo {
    fun seed(ctx: Context) {
        val d = TaskStore.today()
        fun t(title: String, e: String, r: TaskRepeat, created: Long, done: Boolean) =
            DayTask(UUID.randomUUID().toString(), title, e, r, created, if (done) setOf(d) else emptySet())
        val daily = DayTask(UUID.randomUUID().toString(), "Zähne putzen", "🪥", TaskRepeat.DAILY, d - 4, setOf(d, d - 1, d - 2, d - 3, d - 4))
        TaskStore.save(ctx, listOf(
            daily,
            t("Hausaufgaben machen", "📚", TaskRepeat.WEEKDAYS, d - 4, true),
            t("Zimmer aufräumen", "🧹", TaskRepeat.ONCE, d - 1, false),
            t("Eine Stunde nach draußen", "🌳", TaskRepeat.ONCE, d, false),
            t("Oma anrufen", "📞", TaskRepeat.ONCE, d, false)
        ))
    }
}
