// PetSync.kt: gemeinsamer Kern für Home-Screen, Worker und Glance-Widget (Android)
//
// Ein Zustand, eine Formelquelle: Alle Stellen rechnen über PetSync.tick -> PetCore.tick und lesen/schreiben
// denselben Snapshot in den SharedPreferences "home" (dort liegen auch die Feature-Flags und Tageszähler).
// Die alte Energie-Formel aus OSMonWidget.kt (PetState/PetPrefs/PetEngine, Prefs "pet") ist entfallen.
//
// Hierher verschoben: Anzeige-Zuordnungen und PetCopy (vorher private in HomeScreen.kt), Akku-/Speicher-
// Sensoren (vorher PetEngine), Streicheln und Fokus (vorher HomeViewModel).
//
// Nebenläufigkeit: Worker, Widget-Aktionen und ViewModel laufen im selben Prozess; tick/pat sind
// @Synchronized auf diesem Objekt.

package com.example.osmon

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import com.example.osmon.engine.Config
import com.example.osmon.engine.Form
import com.example.osmon.engine.Inputs
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.PetCore
import com.example.osmon.engine.PetStats
import com.example.osmon.engine.Snapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

// ---------- Anzeige-Zuordnungen (Home-Screen und Widget) ----------

fun moodTitle(m: Mood) = when (m) {
    Mood.HIBERNATION -> "Winterschlaf"
    Mood.SICK -> "Kränklich"
    Mood.TIRED -> "Müde"
    Mood.CONTENT -> "Zufrieden"
    Mood.RADIANT -> "Strahlend"
}

/** Kompakte Darstellung fürs Widget (im Home-Screen zeichnet PetAvatar das Wesen). */
fun moodEmoji(m: Mood) = when (m) {
    Mood.HIBERNATION -> "😴"
    Mood.SICK -> "🤒"
    Mood.TIRED -> "🥱"
    Mood.CONTENT -> "🙂"
    Mood.RADIANT -> "😄"
}

fun moodColor(m: Mood) = when (m) {
    Mood.HIBERNATION -> Color(0xFF4A56B5)
    Mood.SICK -> Color(0xFFD9667A)
    Mood.TIRED -> Color(0xFFE08A1E)
    Mood.CONTENT -> Color(0xFF1C9A9A)
    Mood.RADIANT -> Color(0xFF2E9E5B)
}

fun formBadge(f: Form) = when (f) {
    Form.CHATTERLING -> "💬"
    Form.BINGEON -> "📺"
    Form.PIXELO -> "🎮"
    Form.ZENFUCHS -> "🦊"
    Form.WANDERO -> "🍃"
}

fun formTitle(f: Form) = when (f) {
    Form.CHATTERLING -> "Chatterling"
    Form.BINGEON -> "Bingeon"
    Form.PIXELO -> "Pixelo"
    Form.ZENFUCHS -> "Zenfuchs"
    Form.WANDERO -> "Wandero"
}

/** Was das Wesen sagt: Overlays haben Vorrang vor der Grundstimmung (identisch zur iOS-Fassung). */
object PetCopy {
    fun line(s: Snapshot): String = when {
        Overlay.YAWNING in s.overlays -> "Gähn … mir geht die Energie aus."
        Overlay.STRESSED in s.overlays -> "Puh, ganz schön viel Bildschirm heute."
        Overlay.CHUBBY in s.overlays -> "Mir ist so eng … schaffen wir Platz?"
        Overlay.SLUGGISH in s.overlays -> "Ich könnte mal ein paar Schritte vertragen."
        else -> when (s.mood) {
            Mood.RADIANT -> "Mir geht’s super!"
            Mood.CONTENT -> "Alles gut bei uns."
            Mood.TIRED -> "Ich bin heute ein bisschen müde."
            Mood.SICK -> "Mir ist nicht so gut …"
            Mood.HIBERNATION -> "Zzz …"
        }
    }
}

// ---------- Ergebnis eines Ticks ----------

data class PetTick(
    val snapshot: Snapshot,
    val inputs: Inputs,
    val atMs: Long,
    val screenUsable: Boolean            // Flag gesetzt UND Nutzungszugriff (noch) erteilt
)

// ---------- Kern ----------

object PetSync {
    /** Ziele aus den Einstellungen (Prefs "home"), sonst PRD-Startwerte. */
    fun config(ctx: Context): Config = Config(
        screenGoalMin = prefs(ctx).getInt("goal.screenMin", 180).toFloat(),
        stepGoal = prefs(ctx).getInt("goal.steps", 7000)
    )

    val SCREEN_GOAL_RANGE = 90..240                // Minuten, Schritt 15 (PRD: Z begrenzt auf 90..240)
    val STEP_GOAL_RANGE = 3000..15000              // Schritt 500

    fun setGoals(ctx: Context, screenMin: Int, steps: Int) {
        prefs(ctx).edit()
            .putInt("goal.screenMin", screenMin.coerceIn(SCREEN_GOAL_RANGE))
            .putInt("goal.steps", steps.coerceIn(STEP_GOAL_RANGE))
            .apply()
    }
    const val PAT_COOLDOWN_MS = 3 * 3_600_000L
    /** Bildschirmzeit-Auswertung ist der teuerste Schritt: Ergebnis so lange wiederverwenden. */
    const val SCREEN_CACHE_MS = 10 * 60_000L

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)

    private fun dayKey(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun flag(ctx: Context, key: String) = prefs(ctx).getBoolean(key, false)

    fun screenUsable(ctx: Context) = flag(ctx, "feature.screentime") && ScreenTime.hasAccess(ctx)

    // ---- Tageszähler (Streicheln, Fokus)

    fun counter(ctx: Context, name: String, nowMs: Long = System.currentTimeMillis()) =
        prefs(ctx).getInt("$name.${dayKey(nowMs)}", 0)

    private fun bump(ctx: Context, name: String, by: Int = 1, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putInt("$name.${dayKey(nowMs)}", counter(ctx, name, nowMs) + by).apply()
    }

    // ---- Tick: Sensoren -> PetCore -> Speicher

    /**
     * Ein vollständiger Update-Zyklus. maxScreenAgeMs = 0 erzwingt eine frische Bildschirmzeit-Auswertung
     * (Worker); Home-Screen und Widget nutzen den Cache.
     */
    @Synchronized
    fun tick(ctx: Context, nowMs: Long = System.currentTimeMillis(), maxScreenAgeMs: Long = SCREEN_CACHE_MS): PetTick {
        completeFocusIfDue(ctx, nowMs)
        val screenOn = screenUsable(ctx)
        val inputs = liveInputs(ctx, nowMs, screenOn, maxScreenAgeMs)

        val prev = lastSnapshot(ctx)
        val dt = prev?.let { ((nowMs - it.second) / 3_600_000f).coerceIn(0f, 24f) } ?: 0.25f
        val snap = PetCore.tick(prev?.first, inputs, config(ctx), dt)
        saveSnapshot(ctx, snap, nowMs)
        DayLog.record(ctx, snap.wellbeing, nowMs)
        return PetTick(snap, inputs, nowMs, screenOn)
    }

    // ---- Widget nur bei spürbarer Änderung neu zeichnen

    /**
     * Vergleicht mit dem zuletzt im Widget gezeigten Zustand, nicht mit dem letzten Tick:
     * So summieren sich viele kleine Änderungen und lösen irgendwann doch ein Update aus.
     */
    fun widgetNeedsUpdate(ctx: Context, s: Snapshot): Boolean {
        val p = prefs(ctx)
        if (!p.contains("widget.h")) return true
        return p.getString("widget.mood", null) != s.mood.name ||
            p.getStringSet("widget.overlays", emptySet()) != s.overlays.map { it.name }.toSet() ||
            abs(p.getFloat("widget.h", 0f) - s.wellbeing) >= 3f
    }

    fun markWidgetRendered(ctx: Context, s: Snapshot) {
        prefs(ctx).edit()
            .putString("widget.mood", s.mood.name)
            .putStringSet("widget.overlays", s.overlays.map { it.name }.toSet())
            .putFloat("widget.h", s.wellbeing)
            .apply()
    }

    /** force = true z. B. nach dem Streicheln (Button-Zustand ändert sich, Stimmung nicht unbedingt). */
    suspend fun updateWidgetIfNeeded(ctx: Context, s: Snapshot, force: Boolean = false) {
        if (!force && !widgetNeedsUpdate(ctx, s)) return
        markWidgetRendered(ctx, s)
        PetWidget().updateAll(ctx)
    }

    // ---- Streicheln (wirkt laut PRD nur auf die Zuneigung, nicht auf die Energie)

    fun patReadyAt(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long? {
        val last = prefs(ctx).getLong("lastPat", 0L)
        if (last == 0L) return null
        val ready = last + PAT_COOLDOWN_MS
        return ready.takeIf { it > nowMs }
    }

    /** true, wenn das Streicheln gezählt wurde (höchstens 1× pro 3 h). */
    @Synchronized
    fun pat(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (patReadyAt(ctx, nowMs) != null) return false
        prefs(ctx).edit().putLong("lastPat", nowMs).apply()
        bump(ctx, "pats", nowMs = nowMs)
        return true
    }

    // ---- Fokus-Session (zählt erst, wenn sie bis zum Ende durchläuft)

    fun focusEndsAt(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long? =
        prefs(ctx).getLong("focusEnds", 0L).takeIf { it > nowMs }

    fun startFocus(ctx: Context, minutes: Int, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong("focusEnds", nowMs + minutes * 60_000L).putInt("focusLen", minutes).apply()
    }

    fun cancelFocus(ctx: Context) {
        prefs(ctx).edit().remove("focusEnds").apply()
    }

    private fun completeFocusIfDue(ctx: Context, nowMs: Long) {
        val p = prefs(ctx)
        val end = p.getLong("focusEnds", 0L)
        if (end == 0L || end > nowMs) return
        // Der Session-Tag ist der Tag, an dem sie endete.
        bump(ctx, "focusSessions", nowMs = end)
        bump(ctx, "focusMinutes", p.getInt("focusLen", 0), nowMs = end)
        p.edit().remove("focusEnds").apply()
    }

    // ---- Sensoren

    /** Sticky-Broadcast: liefert den letzten Akku-Zustand ohne dauerhaften Receiver. */
    fun readBattery(ctx: Context): Pair<Float, Boolean>? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return level * 100f / scale to charging
    }

    fun isPowerSave(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun freeStorageRatio(): Float? = try {
        val s = StatFs(Environment.getDataDirectory().path)
        s.availableBytes.toFloat() / s.totalBytes
    } catch (e: Exception) {
        null
    }

    private data class ScreenValues(val total: Float, val night: Float, val pickups: Int)

    private fun screenValues(ctx: Context, nowMs: Long, maxAgeMs: Long): ScreenValues {
        val p = prefs(ctx)
        val today = dayKey(nowMs)
        val cachedAt = p.getLong("screen.at", 0L)
        val fresh = p.getString("screen.day", null) == today && nowMs - cachedAt in 0..maxAgeMs
        if (fresh) {
            return ScreenValues(p.getFloat("screen.total", 0f), p.getFloat("screen.night", 0f), p.getInt("screen.pickups", 0))
        }
        val d = ScreenTime.dayStats(ctx, nowMs)
        p.edit()
            .putString("screen.day", today).putLong("screen.at", nowMs)
            .putFloat("screen.total", d.totalMin).putFloat("screen.night", d.nightMin).putInt("screen.pickups", d.pickups)
            .apply()
        return ScreenValues(d.totalMin, d.nightMin, d.pickups)
    }

    private fun liveInputs(ctx: Context, nowMs: Long, screenOn: Boolean, maxScreenAgeMs: Long): Inputs {
        val battery = readBattery(ctx)
        val screen = if (screenOn) screenValues(ctx, nowMs, maxScreenAgeMs) else null
        return Inputs(
            batteryPct = battery?.first,
            charging = battery?.second ?: false,
            lowPower = isPowerSave(ctx),
            freeStorageRatio = freeStorageRatio(),
            screenMinutes = screen?.total,
            nightMinutes = screen?.night,
            pickups = screen?.pickups,
            steps = if (flag(ctx, "feature.steps")) StepsPrefs.today(ctx).toInt() else null,
            pats = counter(ctx, "pats", nowMs),
            focusSessions = counter(ctx, "focusSessions", nowMs)
        )
    }

    // ---- Snapshot-Speicher

    fun lastSnapshot(ctx: Context): Pair<Snapshot, Long>? {
        val sp = prefs(ctx)
        if (!sp.contains("snap.ts")) return null
        fun f(k: String): Float? = if (sp.contains("snap.$k")) sp.getFloat("snap.$k", 0f) else null
        val mood = sp.getString("snap.mood", null)?.let { runCatching { Mood.valueOf(it) }.getOrNull() } ?: return null
        val overlays = (sp.getStringSet("snap.overlays", emptySet()) ?: emptySet())
            .mapNotNull { runCatching { Overlay.valueOf(it) }.getOrNull() }.toSet()
        val snap = Snapshot(PetStats(f("e"), f("r"), f("f"), f("v"), f("a")), sp.getFloat("snap.h", 50f), mood, overlays)
        return snap to sp.getLong("snap.ts", 0L)
    }

    private fun saveSnapshot(ctx: Context, s: Snapshot, ts: Long) {
        prefs(ctx).edit().apply {
            fun put(k: String, v: Float?) { if (v == null) remove("snap.$k") else putFloat("snap.$k", v) }
            put("e", s.stats.e); put("r", s.stats.r); put("f", s.stats.f); put("v", s.stats.v); put("a", s.stats.a)
            putFloat("snap.h", s.wellbeing)
            putString("snap.mood", s.mood.name)
            putStringSet("snap.overlays", s.overlays.map { it.name }.toSet())
            putLong("snap.ts", ts)
        }.apply()
    }
}
