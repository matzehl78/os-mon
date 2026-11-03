// WearActivity.kt: OS-Mon auf Wear OS. Eine drehbare Liste (Krone/Wischen): Wesen (antippen = Reaktion),
// Stimmung und Werte, Aufgaben zum Abhaken, Miteinander-Tipp. Alle Daten kommen vom Handy (WearStore).

package com.example.osmon.wear

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.Form
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.Mood
import com.example.osmon.engine.TaskStage
import com.example.osmon.figure.ActiveReaction
import com.example.osmon.figure.PetFigureRenderer
import com.example.osmon.figure.PetPose
import com.example.osmon.figure.ReactionPicker
import com.example.osmon.figure.ALL_FIGURES
import com.example.osmon.wearsync.WearPayload
import java.time.LocalDate
import java.time.LocalTime

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearStore.init(this)
        WearDemo.apply(this, intent)
        setContent { MaterialTheme { WearApp() } }
    }
}

fun moodColor(m: Mood) = when (m) {
    Mood.RADIANT -> Color(0xFF2E9E5B); Mood.CONTENT -> Color(0xFF1C9A9A); Mood.TIRED -> Color(0xFFE08A1E)
    Mood.SICK -> Color(0xFFD9667A); Mood.HIBERNATION -> Color(0xFF4A57B5)
}

fun moodTitle(m: Mood) = when (m) {
    Mood.RADIANT -> "Strahlend"; Mood.CONTENT -> "Zufrieden"; Mood.TIRED -> "Müde"
    Mood.SICK -> "Kränklich"; Mood.HIBERNATION -> "Winterschlaf"
}

val WearPayload.growth: GrowthStage? get() = stage?.let { runCatching { GrowthStage.valueOf(it) }.getOrNull() }

fun WearPayload.pose(): PetPose = PetPose.of(snapshot, form = form?.let { runCatching { Form.valueOf(it) }.getOrNull() })
    .copy(accessory = accessory?.let { runCatching { Accessory.valueOf(it) }.getOrNull() }, stage = growth,
        glasses = glasses?.let { runCatching { com.example.osmon.engine.Glasses.valueOf(it) }.getOrNull() })

fun figureOf(id: String?) = ALL_FIGURES.firstOrNull { it.id == id } ?: ALL_FIGURES[0]

@Composable
fun WearApp() {
    val ctx = LocalContext.current
    val payload by WearStore.payload.collectAsStateWithLifecycle()
    val cheer by WearStore.cheer.collectAsStateWithLifecycle()
    val list = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(list) }) {
        val p = payload
        if (p == null) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                PetCanvas(figureOf(null).id, PetPose.Preview, null, Modifier.size(80.dp))
                Text("Öffne OS-Mon auf dem Handy, damit dein Wesen hierher kommt.", textAlign = TextAlign.Center, fontSize = 13.sp)
            }
            return@Scaffold
        }
        ScalingLazyColumn(Modifier.fillMaxSize(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
            item { PetHeader(p) }
            item { Stats(p) }
            item {
                val prog = p.progress
                Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (prog.total > 0) "Aufgaben ${prog.done}/${prog.total}" else "Aufgaben", fontWeight = FontWeight.Bold)
                    Text(cheer ?: p.motivation, fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colors.onSurfaceVariant)
                }
            }
            if (p.tasks.isEmpty()) item { Text("Lege Aufgaben in der Handy-App an.", fontSize = 12.sp, textAlign = TextAlign.Center) }
            items(p.tasks, key = { it.id }) { t ->
                val done = DayTasks.isDone(t, p.day)
                val view = LocalView.current
                Chip(
                    onClick = {
                        WearStore.toggle(ctx, t)
                        view.performHapticFeedback(if (done) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONFIRM)
                    },
                    label = { Text(t.title, textDecoration = if (done) TextDecoration.LineThrough else null, maxLines = 2) },
                    icon = { Text(if (done) "✅" else t.emoji, fontSize = 18.sp) },
                    colors = if (done) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (done) "erledigt" else "offen" }
                )
            }
            p.kindTip?.let { tip ->
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💛 Miteinander", color = Color(0xFFFF7FB0), fontWeight = FontWeight.Bold)
                        p.kindCategory?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colors.onSurfaceVariant) }
                        Text(tip, fontSize = 13.sp, textAlign = TextAlign.Center)
                        if (p.kindDone) Text("✅ Heute ausprobiert", fontSize = 12.sp, color = Color(0xFF6FD08C))
                        else Chip(onClick = { WearStore.kindDone(ctx) }, label = { Text("Hab ich gemacht") },
                            colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFC2185B)), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PetHeader(p: WearPayload) {
    val view = LocalView.current
    val picker = remember { ReactionPicker() }
    var reaction by remember { mutableStateOf<ActiveReaction?>(null) }
    LaunchedEffect(reaction) { if (reaction != null) { kotlinx.coroutines.delay(2700); reaction = null } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopCenter) {
            if (p.growth == GrowthStage.EGG) {
                Canvas(Modifier.size(110.dp).semantics { contentDescription = "Ei. Auf dem Handy antippen, damit es schlüpft." }) {
                    drawIntoCanvas { PetFigureRenderer.drawEgg(it.nativeCanvas, size.width, size.height, figureOf(p.figureId), 2, null) }
                }
            } else PetCanvas(p.figureId, p.pose(), reaction,
                Modifier.size(110.dp).clickable {
                    val now = SystemClock.uptimeMillis()
                    val t = LocalTime.now()
                    val k = picker.pick(p.snapshot.mood, p.snapshot.overlays, t.hour + t.minute / 60f, LocalDate.now().toEpochDay(), now)
                    reaction = picker.start(k, now)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }.semantics { contentDescription = "${p.petName}: ${moodTitle(p.snapshot.mood)}. Antippen für eine Reaktion." })
            reaction?.let { Text(it.say, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.growth == GrowthStage.EGG) "Ei" else moodTitle(p.snapshot.mood), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("${p.snapshot.wellbeing.toInt()}", color = moodColor(p.snapshot.mood), fontWeight = FontWeight.Bold)
        }
        Text(p.petName + (p.growth?.takeIf { it != GrowthStage.EGG }?.let { " · ${it.title}" } ?: ""),
            fontSize = 11.sp, color = MaterialTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun Stats(p: WearPayload) {
    val s = p.snapshot.stats
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("⚡", s.e, Color(0xFFF2B600), "Energie")
        Stat("🌙", s.r, Color(0xFF7C8CFF), "Ruhe")
        Stat("💾", s.f, Color(0xFFE08A1E), "Fitness")
        Stat("👟", s.v, Color(0xFF2E9E5B), "Vitalität")
    }
}

@Composable
private fun Stat(icon: String, v: Float?, c: Color, title: String) {
    Box(Modifier.size(36.dp).semantics(mergeDescendants = true) { contentDescription = "$title ${v?.toInt() ?: "keine Daten"}" },
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = (v ?: 0f) / 100f, indicatorColor = c, trackColor = c.copy(alpha = 0.25f),
            strokeWidth = 3.dp, modifier = Modifier.fillMaxSize())
        Text(icon, fontSize = 13.sp)
    }
}

/** Das Wesen, animiert (Atmen, Blinzeln) wie in der Handy-App. */
@Composable
fun PetCanvas(figureId: String?, pose: PetPose, reaction: ActiveReaction?, modifier: Modifier) {
    val fig = figureOf(figureId)
    var t by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t = (now - start) / 1e9 }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val now = SystemClock.uptimeMillis()
        val p = if (reaction == null) pose else pose.copy(reaction = reaction.kind, reactU = reaction.progress(now))
        drawIntoCanvas { PetFigureRenderer.draw(it.nativeCanvas, size.width, size.height, fig, p, t) }
    }
}

/** Vorführmodus für Screenshots: adb shell am start -n …/.WearActivity --ez demo true */
object WearDemo {
    fun apply(ctx: android.content.Context, intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("demo", false) != true) return
        val d = LocalDate.now().toEpochDay()
        val json = WearPayload(
            com.example.osmon.engine.Snapshot(com.example.osmon.engine.PetStats(e = 90f, r = 85f, f = 95f, v = 70f), 86f, Mood.RADIANT, emptySet()),
            System.currentTimeMillis(), intent.getStringExtra("figure") ?: "lumo", "Lumo", null, "CHATTERLING", d,
            listOf(
                com.example.osmon.engine.DayTask("1", "Zähne putzen", "🪥", com.example.osmon.engine.TaskRepeat.DAILY, d - 3, setOf(d)),
                com.example.osmon.engine.DayTask("2", "Zimmer aufräumen", "🧹", com.example.osmon.engine.TaskRepeat.ONCE, d),
                com.example.osmon.engine.DayTask("3", "Oma anrufen", "📞", com.example.osmon.engine.TaskRepeat.ONCE, d)
            ),
            "Super Anfang! Ich spür schon, wie mein Herz leuchtet.", "Halte die Tür für die Person hinter dir auf.", "🚌 Unterwegs", false
        ).toJson()
        WearStore.apply(ctx, json)
    }
}
