// AchievementsUI.kt: Sammelalbum – Kennzahlen sammeln, freischalten, Album und Banner. Gegenstück: AchievementsUI.swift.
// Regeln und Liste: engine/Achievements.kt. Alles nur auf dem Gerät.

package com.example.osmon.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.osmon.PetSync
import com.example.osmon.engine.Achievement
import com.example.osmon.engine.Achievements
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.Metric
import org.json.JSONObject

/** Stand des Albums für die Oberfläche. */
data class AlbumInfo(val metrics: Map<Metric, Int>, val unlocked: Set<String>, val pending: List<Achievement>) {
    val count get() = unlocked.size
}

object AchievementStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)

    // Zähler (werden bei Ereignissen erhöht)
    fun addTask(ctx: Context) = inc(ctx, "ach.tasks")
    fun addComeback(ctx: Context) = inc(ctx, "ach.comeback")
    /** Gute Nacht gesagt (einmal pro Nacht zählen). */
    fun addGoodnight(ctx: Context, night: Long) {
        if (p(ctx).getLong("ach.night.last", Long.MIN_VALUE) == night) return
        p(ctx).edit().putLong("ach.night.last", night).apply(); inc(ctx, "ach.goodnights")
    }
    /** Fest gefeiert (verschiedene Feste zählen). */
    fun addFestival(ctx: Context, id: String) {
        val s = p(ctx).getStringSet("ach.festivals", emptySet())!!.toMutableSet()
        if (s.add(id)) p(ctx).edit().putStringSet("ach.festivals", s).apply()
    }
    private fun inc(ctx: Context, k: String) { p(ctx).edit().putInt(k, p(ctx).getInt(k, 0) + 1).apply() }

    private fun loadMetrics(ctx: Context): Map<Metric, Int> = runCatching {
        val o = JSONObject(p(ctx).getString("ach.metrics", "{}")!!)
        Metric.entries.filter { o.has(it.name) }.associateWith { o.getInt(it.name) }
    }.getOrDefault(emptyMap())

    fun unlocked(ctx: Context): Set<String> = p(ctx).getStringSet("ach.unlocked", emptySet())!!.toSet()
    private fun pending(ctx: Context): List<String> = p(ctx).getString("ach.pending", "")!!.split(",").filter { it.isNotEmpty() }

    /** Kennzahlen einsammeln (Höchstwerte bleiben), neu Erreichtes freischalten und zum Zeigen vormerken. */
    fun refresh(ctx: Context, growth: GrowthInfo?, scenesUnlocked: Int): AlbumInfo {
        val day = TaskStore.today()
        val now = mapOf(
            Metric.STAGE to (growth?.stage?.ordinal ?: 0),
            Metric.TASKS to p(ctx).getInt("ach.tasks", 0),
            Metric.STREAK to DayTasks.streak(TaskStore.load(ctx), day),
            Metric.KIND to TaskStore.kindCount(ctx),
            Metric.GOODNIGHT to p(ctx).getInt("ach.goodnights", 0),
            Metric.FESTIVALS to (p(ctx).getStringSet("ach.festivals", emptySet())?.size ?: 0),
            Metric.SCENES to scenesUnlocked,
            Metric.COMEBACK to p(ctx).getInt("ach.comeback", 0),
            Metric.DAYS to (growth?.ageDays ?: 0)
        )
        val m = Achievements.merge(loadMetrics(ctx), now)
        val un = unlocked(ctx)
        val fresh = Achievements.newlyUnlocked(m, un)
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.name, v) }
        val pend = (pending(ctx) + fresh.map { it.id }).distinct()
        p(ctx).edit().putString("ach.metrics", o.toString()).putStringSet("ach.unlocked", un + fresh.map { it.id })
            .putString("ach.pending", pend.joinToString(",")).apply()
        return AlbumInfo(m, un + fresh.map { it.id }, pend.mapNotNull { Achievements.byId(it) })
    }

    /** Ersten vorgemerkten Erfolg als gesehen markieren. */
    fun ackFirst(ctx: Context) {
        val rest = pending(ctx).drop(1)
        p(ctx).edit().putString("ach.pending", rest.joinToString(",")).apply()
    }

    fun info(ctx: Context) = AlbumInfo(loadMetrics(ctx), unlocked(ctx), pending(ctx).mapNotNull { Achievements.byId(it) })
}

// ---------- Bausteine ----------

/** Banner: „Neuer Erfolg: 🔥 Drei am Stück“ (unten eingeblendet). */
@Composable
fun AchievementBanner(a: Achievement, onOpen: () -> Unit, onAck: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF3C4), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Neuer Erfolg!", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8D6E00))
                Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF3E2C00))
            }
            TextButton(onClick = onOpen) { Text("Album") }
            Button(onClick = onAck) { Text("Super!") }
        }
    }
}

/** Kleiner Knopf auf der Startseite: „🏆 Sammelalbum 5/23“. */
@Composable
fun AlbumButton(album: AlbumInfo, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Sammelalbum, ${album.count} von ${Achievements.ALL.size} Erfolgen" }) {
        Text("🏆 Sammelalbum ${album.count}/${Achievements.ALL.size}", style = MaterialTheme.typography.labelLarge)
    }
}

/** Das Album als Vollbild: erreichte Erfolge farbig, offene blass mit Fortschritt. */
@Composable
fun AlbumDialog(album: AlbumInfo, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sammelalbum", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClose) { Text("Fertig") }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${album.count} von ${Achievements.ALL.size} Erfolgen gesammelt", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { album.count.toFloat() / Achievements.ALL.size }, modifier = Modifier.fillMaxWidth())
                    Achievements.ALL.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { a -> Badge(a, a.id in album.unlocked, Achievements.progress(a, album.metrics), Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(a: Achievement, done: Boolean, progress: Float, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = if (done) 3.dp else 0.dp,
        color = if (done) Color(0xFFFFF3C4) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${a.title}: ${if (done) "erreicht" else "noch offen, ${(progress * 100).toInt()} Prozent"}. ${a.hint}"
        }) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.emoji, fontSize = 32.sp, modifier = Modifier.alpha(if (done) 1f else 0.35f))
            Text(a.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            Text(a.hint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, modifier = Modifier.height(44.dp))
            if (!done) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
