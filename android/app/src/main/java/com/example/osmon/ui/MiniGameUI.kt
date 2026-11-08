// MiniGameUI.kt: Mini-Spiele (30 Sekunden, wenige Runden pro Tag). Gegenstück: MiniGameUI.swift.
// Regeln, Zufall und Punkte: engine/MiniGames.kt. Das Wesen schaut zu und freut sich mit; Belohnung:
// Erfolge im Sammelalbum („Spielkind“, „Spiele-Fan“) und eine Freude-Reaktion auf der Startseite.

package com.example.osmon.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.osmon.PetSync
import com.example.osmon.engine.GameTarget
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.MiniGame
import com.example.osmon.engine.MiniGameSim
import com.example.osmon.engine.MiniGames
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Sfx
import com.example.osmon.engine.TargetKind
import com.example.osmon.figure.ActiveReaction
import com.example.osmon.figure.PetFigureCanvas
import com.example.osmon.figure.PetPose
import com.example.osmon.figure.ReactionKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object GameStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)
    fun limit(ctx: Context) = p(ctx).getInt("games.limit", MiniGames.DEFAULT_LIMIT)
    fun setLimit(ctx: Context, n: Int) { p(ctx).edit().putInt("games.limit", n.coerceIn(0, MiniGames.MAX_LIMIT)).apply(); ExtrasEvents.bump() }
    fun left(ctx: Context): Int {
        val last = p(ctx).getLong("games.day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        return MiniGames.left(last, p(ctx).getInt("games.played", 0), TaskStore.today(), limit(ctx))
    }
    /** Beim Start einer Runde: zählt sofort fürs Tageslimit (Abbrechen gibt keine Runde zurück). */
    fun recordStart(ctx: Context) {
        val last = p(ctx).getLong("games.day", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        val today = TaskStore.today()
        p(ctx).edit().putLong("games.day", MiniGames.dayAfterPlay(last, today))
            .putInt("games.played", MiniGames.afterPlay(last, p(ctx).getInt("games.played", 0), today)).commit()
        ExtrasEvents.bump()
    }

    /** Nach einer zu Ende gespielten Runde: nur noch fürs Sammelalbum. */
    fun recordFinish(ctx: Context) {
        p(ctx).edit().putInt("ach.games", p(ctx).getInt("ach.games", 0) + 1).apply()
        ExtrasEvents.bump()
    }
}

/** Knopf auf der Startseite: „🎮 Spielen (2 übrig)“; ausgegraut, wenn für heute alles gespielt ist. */
@Composable
fun GameButton(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val v = ExtrasEvents.version.intValue
    val left = remember(v) { GameStore.left(ctx) }
    val limit = remember(v) { GameStore.limit(ctx) }
    if (limit == 0) return                                   // von den Eltern ausgeschaltet
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Mini-Spiele, heute noch $left Runden" }) {
        Text("🎮 Spielen · $left/$limit", style = MaterialTheme.typography.labelLarge)
    }
}

private enum class Phase { CHOOSE, PLAY, RESULT }

/** Spiel-Fenster: Auswahl → 30 Sekunden spielen → Ergebnis. onDone(stars, cheer) für die Freude auf der Startseite. */
@Composable
fun GameDialog(figureName: String, stage: GrowthStage?, onClose: (Int?, String?) -> Unit) {
    val ctx = LocalContext.current
    val premium = remember { PremiumStore.isPremium(ctx) }
    var phase by remember { mutableStateOf(Phase.CHOOSE) }
    var game by remember { mutableStateOf(MiniGame.STARS) }
    var result by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(GameStore.left(ctx)) }
    val outfit = remember { WardrobeStore.outfit(ctx) }
    fun close() = onClose(if (phase == Phase.RESULT) MiniGames.stars(result) else null, if (phase == Phase.RESULT) MiniGames.cheer(MiniGames.stars(result), figureName) else null)

    Dialog(onDismissRequest = { if (phase != Phase.PLAY) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101433)) {
            Column(Modifier.systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (phase) {
                    Phase.CHOOSE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎮 Mini-Spiele", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { close() }) { Text("Fertig") }
                        }
                        Text(if (left > 0) "Heute noch $left ${if (left == 1) "Runde" else "Runden"} · je 30 Sekunden"
                            else "Für heute ist Schluss – $figureName braucht jetzt Pause. Morgen geht’s weiter! 🌙",
                            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                        MiniGame.entries.forEach { g ->
                            val ok = !g.premium || premium
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = if (ok) 0.12f else 0.05f), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(g.emoji, fontSize = 32.sp); Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(g.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(if (ok) g.hint else "💎 Premium (Eltern-Bereich)", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = {
                                        if (GameStore.left(ctx) > 0) { GameStore.recordStart(ctx); left = GameStore.left(ctx); game = g; phase = Phase.PLAY }
                                    }, enabled = ok && left > 0) { Text("Los") }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PetFigureCanvas(PetPose(Mood.RADIANT, accessory = outfit.hat, glasses = outfit.glasses, stage = stage), Modifier.size(150.dp))
                        }
                    }
                    Phase.PLAY -> GamePlay(game, stage) { score ->
                        result = score
                        GameStore.recordFinish(ctx)
                        SoundPlayer.play(ctx, Sfx.GAME_END)
                        phase = Phase.RESULT
                    }
                    Phase.RESULT -> {
                        val st = MiniGames.stars(result)
                        Spacer(Modifier.height(24.dp))
                        Text(game.emoji + " " + game.title, Modifier.fillMaxWidth(), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                        Text("$result Punkte", Modifier.fillMaxWidth(), color = Color.White, textAlign = TextAlign.Center, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text("⭐".repeat(st) + "☆".repeat(3 - st), Modifier.fillMaxWidth().semantics { contentDescription = "$st von 3 Sternen" },
                            color = Color(0xFFFFD54F), textAlign = TextAlign.Center, fontSize = 40.sp)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val start = remember { SystemClock.uptimeMillis() }
                            PetFigureCanvas(PetPose(Mood.RADIANT, accessory = outfit.hat, glasses = outfit.glasses, stage = stage), Modifier.size(180.dp),
                                reaction = ActiveReaction(if (st >= 2) ReactionKind.SPIN else ReactionKind.GIGGLE, start, ""))
                        }
                        Text(MiniGames.cheer(st, figureName), Modifier.fillMaxWidth(), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                            if (left > 0) Button(onClick = { phase = Phase.CHOOSE }) { Text("Noch eine Runde ($left)") }
                            Button(onClick = { close() }) { Text("Fertig") }
                        }
                    }
                }
            }
        }
    }
}

private data class Pop(val x: Float, val y: Float, val text: String, val at: Float)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GamePlay(game: MiniGame, stage: GrowthStage?, onEnd: (Int) -> Unit) {
    val ctx = LocalContext.current
    val sim = remember { MiniGameSim(game, System.nanoTime()) }
    var frame by remember { mutableIntStateOf(0) }
    val pops = remember { mutableStateListOf<Pop>() }
    var size by remember { mutableStateOf(Offset(1f, 1f)) }
    LaunchedEffect(sim) {
        var last = 0L
        while (!sim.done) {
            withFrameNanos { t -> if (last != 0L) sim.step(((t - last) / 1e9f).coerceAtMost(0.1f)); last = t }
            pops.removeAll { sim.time - it.at > 0.8f }
            frame++
        }
        onEnd(sim.score)
    }
    val f = frame                                            // jedes Bild neu zeichnen
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${game.emoji} ${sim.score}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "${sim.score} Punkte" })
        Spacer(Modifier.width(14.dp))
        LinearProgressIndicator(progress = { sim.left / MiniGameSim.DURATION }, Modifier.weight(1f).height(10.dp), color = Color(0xFFFFD54F), trackColor = Color.White.copy(alpha = 0.15f))
        Spacer(Modifier.width(10.dp))
        Text("${sim.left.toInt() + if (sim.left % 1f > 0f) 1 else 0} s", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
    Box(Modifier.fillMaxWidth().weight(1f)
        .background(Brush.verticalGradient(when (game) {
            MiniGame.STARS -> listOf(Color(0xFF1A1F5C), Color(0xFF0B0E2A))
            MiniGame.BUBBLES -> listOf(Color(0xFF4FC3F7), Color(0xFF1C6E9A))
            MiniGame.FIREFLIES -> listOf(Color(0xFF0E2A1E), Color(0xFF06140E))
        }), RoundedCornerShape(20.dp))
        .pointerInput(sim) {
            detectTapGestures { o ->
                val w = size.x; val h = size.y
                val hit = sim.tap(o.x / w, o.y / h, h / w)
                if (hit != null) {
                    pops += Pop(hit.x, hit.y, if (hit.points > 0) "+${hit.points}" else if (hit.kind == TargetKind.CLOUD) "−1" else "0", sim.time)
                    SoundPlayer.play(ctx, if (hit.kind == TargetKind.CLOUD) Sfx.OOPS else Sfx.POP)
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (f < 0) return@Canvas
            size = Offset(this.size.width, this.size.height)
            val w = this.size.width; val h = this.size.height
            sim.targets.forEach { drawTarget(it, sim.time, w, h) }
        }
        // Punkte-Einblendungen
        pops.forEach { p ->
            val a = (1f - (sim.time - p.at) / 0.8f).coerceIn(0f, 1f)
            Text(p.text, Modifier.padding(start = (p.x * size.x / ctx.resources.displayMetrics.density).dp, top = ((p.y - 0.06f - (sim.time - p.at) * 0.08f) * size.y / ctx.resources.displayMetrics.density).coerceAtLeast(0f).dp),
                color = Color.White.copy(alpha = a), fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            PetFigureCanvas(PetPose(Mood.RADIANT, stage = stage), Modifier.size(70.dp))
        }
    }
}

private fun DrawScope.drawTarget(t: GameTarget, time: Float, w: Float, h: Float) {
    val a = t.alpha(time)
    val c = Offset(t.x * w, t.y * h)
    val r = t.r * w
    when (t.kind) {
        TargetKind.STAR, TargetKind.GOLD -> {
            val col = if (t.kind == TargetKind.GOLD) Color(0xFFFFC107) else Color(0xFFFFF59D)
            val spin = t.age(time) * 1.5f
            drawCircle(col.copy(alpha = 0.25f * a), r * 1.5f, c)
            drawPath(starPath(c, r, spin), col.copy(alpha = a))
            drawPath(starPath(c, r, spin), Color(0xFF1B1E4B).copy(alpha = a), style = Stroke(r * 0.08f))
        }
        TargetKind.BUBBLE -> {
            drawCircle(Color.White.copy(alpha = 0.18f * a), r, c)
            drawCircle(Color.White.copy(alpha = 0.85f * a), r, c, style = Stroke(r * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.8f * a), r * 0.22f, Offset(c.x - r * 0.4f, c.y - r * 0.4f))
        }
        TargetKind.CLOUD -> {
            val g = Color(0xFF546E7A).copy(alpha = a)
            drawCircle(g, r * 0.55f, Offset(c.x - r * 0.45f, c.y + r * 0.1f)); drawCircle(g, r * 0.7f, Offset(c.x, c.y - r * 0.15f))
            drawCircle(g, r * 0.5f, Offset(c.x + r * 0.5f, c.y + r * 0.12f))
            val bolt = Path().apply { moveTo(c.x + r * 0.05f, c.y + r * 0.2f); lineTo(c.x - r * 0.2f, c.y + r * 0.7f); lineTo(c.x + r * 0.05f, c.y + r * 0.62f); lineTo(c.x - r * 0.08f, c.y + r * 1.05f); lineTo(c.x + r * 0.28f, c.y + r * 0.5f); lineTo(c.x + r * 0.05f, c.y + r * 0.55f); close() }
            drawPath(bolt, Color(0xFFFFEB3B).copy(alpha = a))
        }
        TargetKind.FLY -> {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF176).copy(alpha = 0.7f * a), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
            drawCircle(Color(0xFFFFFDE7).copy(alpha = a), r * 0.35f, c)
        }
    }
}

private fun starPath(c: Offset, r: Float, rot: Float) = Path().apply {
    for (k in 0 until 10) {
        val ang = rot + (-PI / 2 + k * PI / 5).toFloat(); val rr = if (k % 2 == 0) r else r * 0.45f
        val x = c.x + rr * cos(ang); val y = c.y + rr * sin(ang)
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
