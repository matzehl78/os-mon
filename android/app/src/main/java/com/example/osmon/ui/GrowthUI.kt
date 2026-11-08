// GrowthUI.kt: Wachsen vom Ei bis zum Erwachsenen – Speicher und Bausteine. Gegenstück: GrowthUI.swift.
// Regeln: engine/Growth.kt. Ei auf der Startseite antippen (5×, mit Rissen) → schlüpft als Baby.
// Gute Tage werden beim Aktualisieren nachgetragen (DayLog-Mittel der abgeschlossenen Tage).

package com.example.osmon.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.osmon.DayLog
import com.example.osmon.EvolutionStore
import com.example.osmon.PetSync
import com.example.osmon.engine.Growth
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.GrowthState
import com.example.osmon.figure.PetFigure
import com.example.osmon.figure.PetFigureRenderer
import java.time.LocalDate
import org.json.JSONObject

/** Was die Oberfläche braucht. */
data class GrowthInfo(
    val state: GrowthState,
    val stage: GrowthStage,
    val eggTaps: Int,
    val ageDays: Int,                         // Tage seit dem Schlüpfen
    val progress: Pair<Float, Int>?,          // (Anteil, fehlende gute Tage) bis zur nächsten Stufe
    val roughToday: Int = 0                   // grobe Aktionen heute (wildes Antippen, Schütteln)
) {
    val pending: GrowthStage? get() = state.pendingStage
    val pendingDown: Boolean get() = state.pendingDown
    val risk get() = Growth.risk(state)
}

object GrowthStore {
    private const val KEY = "growth.state"
    private const val TAPS = "growth.eggTaps"
    private fun p(ctx: Context) = PetSync.prefs(ctx)
    private fun today() = LocalDate.now().toEpochDay()

    fun load(ctx: Context): GrowthState {
        p(ctx).getString(KEY, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                fun st(k: String) = o.optString(k).takeIf { it.isNotEmpty() }?.let { runCatching { GrowthStage.valueOf(it) }.getOrNull() }
                return GrowthState(o.getBoolean("hatched"), o.getInt("good"), o.getLong("last"),
                    if (o.has("hatchedDay")) o.getLong("hatchedDay") else null,
                    st("pending"), st("stage"), o.optBoolean("down"))
            }
        }
        // Erster Aufruf: wer OS-Mon schon hatte, ist geschlüpft (nichts geht verloren); sonst liegt ein Ei da.
        val t = today()
        val s = if (p(ctx).getBoolean("onboarding.done", false)) {
            Growth.migrated(EvolutionStore.load(ctx)?.hatchDay ?: t, t)
        } else GrowthState(hatched = false, lastDay = t)
        save(ctx, s)
        return s
    }

    /** Neues Wesen (Ende des Onboardings): Ei, noch nicht geschlüpft. Vorhandener Stand bleibt. */
    fun startEgg(ctx: Context) {
        if (p(ctx).contains(KEY)) return
        p(ctx).edit().putInt(TAPS, 0).apply()
        save(ctx, GrowthState(hatched = false, lastDay = today()))
    }

    fun save(ctx: Context, s: GrowthState) {
        val o = JSONObject().put("hatched", s.hatched).put("good", s.goodDays).put("last", s.lastDay)
        s.hatchedDay?.let { o.put("hatchedDay", it) }
        s.pendingStage?.let { o.put("pending", it.name) }
        s.stage?.let { o.put("stage", it.name) }
        if (s.pendingDown) o.put("down", true)
        p(ctx).edit().putString(KEY, o.toString()).apply()
    }

    /** Gute Tage nachtragen (bis gestern) und aktuellen Stand liefern. */
    fun refresh(ctx: Context): GrowthInfo {
        var s = load(ctx)
        val t = today()
        if (s.hatched && s.lastDay < t - 1) {
            val from = maxOf(s.lastDay + 1, t - Growth.MAX_CATCH_UP)
            for (d in from until t) {
                val avg = DayLog.average(ctx, d); val r = rough(ctx, d)
                // Für „Wieder aufgepäppelt“: es gab einen gemessenen Tag, an dem das Wachsen pausiert hat
                if (avg != null && Growth.delta(avg, r) == 0) p(ctx).edit().putBoolean("growth.dropped", true).apply()
                s = Growth.closeDay(s, d, avg, r)
            }
            save(ctx, s)
        }
        return info(ctx, s)
    }

    fun info(ctx: Context, s: GrowthState = load(ctx)) = GrowthInfo(
        s, Growth.stage(s), p(ctx).getInt(TAPS, 0),
        s.hatchedDay?.let { (today() - it).toInt() } ?: 0, Growth.progress(s), rough(ctx, today()))

    /** Grobe Aktionen eines Tages (wildes Antippen, Schütteln). */
    fun rough(ctx: Context, day: Long) = p(ctx).getInt("growth.rough.$day", 0)

    /** Grobe Aktion zählen (nur nach dem Schlüpfen). */
    fun addRough(ctx: Context): GrowthInfo {
        val s = load(ctx)
        if (s.hatched) { val d = today(); p(ctx).edit().putInt("growth.rough.$d", rough(ctx, d) + 1).apply() }
        return info(ctx, s)
    }

    /** Ei antippen. Gibt den neuen Stand zurück (nach Growth.EGG_TAPS geschlüpft). */
    fun tapEgg(ctx: Context): GrowthInfo {
        var s = load(ctx)
        if (s.hatched) return info(ctx, s)
        val taps = p(ctx).getInt(TAPS, 0) + 1
        p(ctx).edit().putInt(TAPS, taps).apply()
        if (taps >= Growth.EGG_TAPS) { s = Growth.hatch(s, today()); save(ctx, s) }
        return info(ctx, s)
    }

    fun ackStage(ctx: Context) {
        val s = load(ctx)
        // Sammelalbum „Wieder aufgepäppelt“: nach einer Wachstumspause wieder eine Stufe gewachsen
        // (Schlüssel heißt aus Kompatibilität noch growth.dropped; einen Rückfall gibt es nicht mehr)
        if (s.pendingStage != null && p(ctx).getBoolean("growth.dropped", false)) {
            p(ctx).edit().putBoolean("growth.dropped", false).apply(); AchievementStore.addComeback(ctx)
        }
        save(ctx, s.copy(pendingStage = null, pendingDown = false))
    }

    /** Stufe für Widget, Hintergrundbild und Uhr (ohne Nachtragen). */
    fun stage(ctx: Context): GrowthStage = Growth.stage(load(ctx))
    fun cracks(ctx: Context) = Growth.cracks(p(ctx).getInt(TAPS, 0))
}

// ---------- Bausteine ----------

/** Das Ei (schaukelt; shakeAtMs = letztes Antippen → kräftiges Wackeln). */
@Composable
fun EggCanvas(figure: PetFigure, cracks: Int, shakeAtMs: Long?, modifier: Modifier = Modifier) {
    var t by remember { mutableStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t = (now - start) / 1e9 }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val since = shakeAtMs?.let { (SystemClock.uptimeMillis() - it) / 1000f } ?: 99f
        drawIntoCanvas { PetFigureRenderer.drawEgg(it.nativeCanvas, size.width, size.height, figure, cracks, t, (1f - since / 0.5f).coerceAtLeast(0f)) }
    }
}

/** Zeile unter dem Namen: Fortschritt bis zur nächsten Stufe, oder Warnung vor dem Rückfall. */
@Composable
fun GrowthProgress(g: GrowthInfo, onScene: Boolean) {
    val p = g.progress ?: return
    val next = g.stage.next ?: return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        LinearProgressIndicator(progress = { p.first }, modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF2E9E5B), trackColor = Color.White.copy(alpha = if (onScene) 0.25f else 0.6f))
        Text("Noch ${p.second} gute ${if (p.second == 1) "Tag" else "Tage"} bis ${next.title}",
            style = MaterialTheme.typography.labelSmall,
            color = if (onScene) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Gibt es Warnungen (grob behandelt, Rückfall droht)? */
fun hasGrowthAlerts(g: GrowthInfo?) = g != null && g.stage != GrowthStage.EGG && (g.roughToday > 0 || g.risk != null)

/** Warnungen als eigene Zeile unter der Kopfzeile. */
@Composable
fun GrowthAlerts(g: GrowthInfo, onScene: Boolean) {
    val textColor = if (onScene) Color.White else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (g.roughToday > 0) Row {
            Text("✋", color = Color(0xFFD9667A)); Spacer(Modifier.width(8.dp))
            Text(if (g.roughToday >= Growth.ROUGH_LIMIT) "Heute zu grob behandelt – der Tag zählt als schlecht."
                else "Heute ${g.roughToday}× grob behandelt. Ab ${Growth.ROUGH_LIMIT}× zählt der Tag als schlecht.",
                style = MaterialTheme.typography.bodySmall, color = textColor)
        }
        g.risk?.let { r ->
            Row {
                Text("⚠️"); Spacer(Modifier.width(8.dp))
                Text("Noch ${r.badDaysLeft} schlechte ${if (r.badDaysLeft == 1) "Tag" else "Tage"}, dann wird dein Wesen wieder ${article(r.to)}.",
                    style = MaterialTheme.typography.bodySmall, color = textColor)
            }
        }
    }
}

/** Feier beim Aufstieg (unten eingeblendet, wie das Entwicklungs-Banner). */
@Composable
fun GrowthBanner(name: String, stage: GrowthStage, down: Boolean, onAck: () -> Unit) {
    val text = if (down) "$name ist wieder ${article(stage)} geworden. Mit guter Pflege wächst es wieder." else when (stage) {
        GrowthStage.BABY -> "$name ist geschlüpft! Willkommen, kleines Baby."
        GrowthStage.CHILD -> "$name ist jetzt ein Kind! Größer, mutiger, neugieriger."
        GrowthStage.TEEN -> "$name ist jetzt jugendlich! Fast schon groß."
        GrowthStage.ADULT -> "$name ist erwachsen! Danke, dass du so gut aufgepasst hast."
        GrowthStage.EGG -> ""
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (down) "🥺" else "🎉", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAck) { Text(if (down) "Ich pass auf" else "Juhu!") }
        }
    }
}

/** „ein Baby“, „ein Kind“, „jugendlich“, „erwachsen“ */
fun article(s: GrowthStage) = when (s) {
    GrowthStage.BABY -> "ein Baby"; GrowthStage.CHILD -> "ein Kind"; GrowthStage.TEEN -> "jugendlich"
    GrowthStage.ADULT -> "erwachsen"; GrowthStage.EGG -> "ein Ei"
}

/** Kopfzeile: „Lumo · Kind · Tag 9“. */
fun growthLabel(name: String, g: GrowthInfo): String = when (g.stage) {
    GrowthStage.EGG -> "$name · Ei"
    else -> "$name · ${g.stage.title} · Tag ${g.ageDays + 1}"
}

/**
 * Erkennt kräftiges Schütteln (ab ca. 2,6 g, höchstens einmal pro 1,5 s), nur solange der Bildschirm sichtbar ist.
 * Ohne Beschleunigungssensor passiert nichts.
 */
@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onShake)
    androidx.compose.runtime.DisposableEffect(owner) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val acc = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                val g = kotlin.math.sqrt(x * x + y * y + z * z) / android.hardware.SensorManager.GRAVITY_EARTH
                val now = SystemClock.uptimeMillis()
                if (g > 2.6f && now - last > 1500) { last = now; latest.value() }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            when (ev) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> acc?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> sm?.unregisterListener(listener)
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); sm?.unregisterListener(listener) }
    }
}
