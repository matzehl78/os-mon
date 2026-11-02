// EvolutionJob.kt: Android-Anbindung des EvolutionTracker (Speicher, Tageslog, Tagesabschluss, Worker)
//
// Ablauf: Tagsüber sammeln HomeViewModel/Worker H-Messungen (DayLog). Ein Job schließt abgeschlossene
// Tage (EvolutionJob.closePastDays): Bildschirmzeit des Tages aus UsageStats, Mittel-H aus dem DayLog,
// dann EvolutionTracker.closeDay. Der Aufruf ist idempotent und holt verpasste Tage nach.
// Der Worker läuft alle 12 Stunden; zusätzlich ruft der Home-Screen closePastDays beim Öffnen auf.
//
// Grenze: UsageStats behält Ereignisse nur etwa eine Woche. Verpasste Tage werden deshalb höchstens
// 7 Tage rückwirkend nachgeholt, ältere Lücken bleiben ohne Daten (erlaubt, der Tracker kommt damit klar).

package com.example.osmon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.osmon.engine.AppGroup
import com.example.osmon.engine.Branch
import com.example.osmon.engine.Config
import com.example.osmon.engine.DayRecord
import com.example.osmon.engine.EvoEvent
import com.example.osmon.engine.EvoKind
import com.example.osmon.engine.EvoState
import com.example.osmon.engine.EvolutionTracker
import com.example.osmon.engine.Evolution
import com.example.osmon.engine.Form
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

// ---------- Speicher ----------

object EvolutionStore {
    private const val KEY = "evo.state"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("evolution", Context.MODE_PRIVATE)

    fun load(ctx: Context): EvoState? =
        sp(ctx).getString(KEY, null)?.let { runCatching { stateFrom(JSONObject(it)) }.getOrNull() }

    fun save(ctx: Context, s: EvoState) {
        sp(ctx).edit().putString(KEY, stateTo(s).toString()).apply()
    }

    /** Schlüpftag festlegen (einmalig, beim Onboarding-Abschluss bzw. beim ersten Job-Lauf). */
    fun ensureHatched(ctx: Context, today: Long = LocalDate.now().toEpochDay()): EvoState =
        load(ctx) ?: EvoState(hatchDay = today).also { save(ctx, it) }

    fun acknowledge(ctx: Context) {
        load(ctx)?.let { save(ctx, EvolutionTracker.acknowledge(it)) }
    }

    // ---- JSON (org.json, ohne Zusatzbibliothek) ----

    private fun sharesTo(m: Map<AppGroup, Float>) = JSONObject().apply { m.forEach { (k, v) -> put(k.name, v.toDouble()) } }
    private fun sharesFrom(o: JSONObject) = AppGroup.values().associateWith { o.optDouble(it.name, 0.0).toFloat() }

    private fun evoTo(e: Evolution) = JSONObject().put("form", e.form.name).put("branch", e.branch.name)
        .also { o -> e.hybrid?.let { o.put("hybrid", it.name) } }

    private fun evoFrom(o: JSONObject) = Evolution(
        Form.valueOf(o.getString("form")),
        if (o.has("hybrid")) Form.valueOf(o.getString("hybrid")) else null,
        Branch.valueOf(o.getString("branch"))
    )

    private fun eventTo(e: EvoEvent) = JSONObject().put("kind", e.kind.name).put("to", e.to.name).put("epochDay", e.epochDay)
        .also { o -> e.from?.let { o.put("from", it.name) } }

    private fun eventFrom(o: JSONObject) = EvoEvent(
        EvoKind.valueOf(o.getString("kind")),
        if (o.has("from")) Form.valueOf(o.getString("from")) else null,
        Form.valueOf(o.getString("to")), o.getLong("epochDay")
    )

    private fun recordTo(r: DayRecord) = JSONObject().put("epochDay", r.epochDay).also { o ->
        r.shares?.let { o.put("shares", sharesTo(it)) }
        r.totalMinutes?.let { o.put("totalMinutes", it.toDouble()) }
        r.avgWellbeing?.let { o.put("avgWellbeing", it.toDouble()) }
    }

    private fun recordFrom(o: JSONObject) = DayRecord(
        o.getLong("epochDay"),
        if (o.has("shares")) sharesFrom(o.getJSONObject("shares")) else null,
        if (o.has("totalMinutes")) o.getDouble("totalMinutes").toFloat() else null,
        if (o.has("avgWellbeing")) o.getDouble("avgWellbeing").toFloat() else null
    )

    private fun stateTo(s: EvoState) = JSONObject().put("hatchDay", s.hatchDay).also { o ->
        o.put("history", JSONArray().apply { s.history.forEach { put(recordTo(it)) } })
        s.blended?.let { o.put("blended", sharesTo(it)) }
        s.current?.let { o.put("current", evoTo(it)) }
        s.lastEvolutionDay?.let { o.put("lastEvolutionDay", it) }
        s.challenger?.let { o.put("challenger", it.name) }
        s.challengerSince?.let { o.put("challengerSince", it) }
        s.pendingEvent?.let { o.put("pendingEvent", eventTo(it)) }
    }

    private fun stateFrom(o: JSONObject): EvoState {
        val hist = o.getJSONArray("history")
        return EvoState(
            hatchDay = o.getLong("hatchDay"),
            history = (0 until hist.length()).map { recordFrom(hist.getJSONObject(it)) },
            blended = if (o.has("blended")) sharesFrom(o.getJSONObject("blended")) else null,
            current = if (o.has("current")) evoFrom(o.getJSONObject("current")) else null,
            lastEvolutionDay = if (o.has("lastEvolutionDay")) o.getLong("lastEvolutionDay") else null,
            challenger = if (o.has("challenger")) Form.valueOf(o.getString("challenger")) else null,
            challengerSince = if (o.has("challengerSince")) o.getLong("challengerSince") else null,
            pendingEvent = if (o.has("pendingEvent")) eventFrom(o.getJSONObject("pendingEvent")) else null
        )
    }
}

// ---------- Tageslog: Mittel des Wohlbefindens ----------

object DayLog {
    private const val MIN_GAP_MS = 30 * 60_000L          // höchstens eine Messung je 30 Minuten
    private fun sp(ctx: Context) = ctx.getSharedPreferences("daylog", Context.MODE_PRIVATE)

    /** Wird von PetSync.tick geschrieben (Home-Screen, Worker, Widget). */
    @Synchronized
    fun record(ctx: Context, wellbeing: Float, nowMs: Long = System.currentTimeMillis()) {
        val p = sp(ctx)
        if (nowMs - p.getLong("lastMs", 0L) < MIN_GAP_MS) return
        val day = java.time.Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        p.edit()
            .putFloat("sum.$day", p.getFloat("sum.$day", 0f) + wellbeing)
            .putInt("n.$day", p.getInt("n.$day", 0) + 1)
            .putLong("lastMs", nowMs)
            .apply()
    }

    fun average(ctx: Context, day: Long): Float? {
        val p = sp(ctx)
        val n = p.getInt("n.$day", 0)
        return if (n == 0) null else p.getFloat("sum.$day", 0f) / n
    }
}

// ---------- Tagesabschluss ----------

object EvolutionJob {
    private const val MAX_CATCH_UP_DAYS = 7

    private fun endOfDayMs(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay + 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    private fun record(ctx: Context, day: Long, screenOn: Boolean): DayRecord {
        val stats = if (screenOn) ScreenTime.dayStats(ctx, endOfDayMs(day)) else null
        val total = stats?.totalMin
        val shares = if (stats != null && total != null && total > 0f) {
            stats.byGroup.entries.associate { AppGroup.valueOf(it.key.name) to it.value / total }
        } else null
        return DayRecord(day, shares, if (shares != null) total else null, DayLog.average(ctx, day))
    }

    /** Schließt alle noch offenen Tage vor heute (idempotent) und speichert den Zustand. */
    @Synchronized
    fun closePastDays(ctx: Context, config: Config = PetSync.config(ctx)): EvoState {
        val today = LocalDate.now().toEpochDay()
        var s = EvolutionStore.ensureHatched(ctx, today)
        val lastClosed = s.history.lastOrNull()?.epochDay ?: (s.hatchDay - 1)
        val screenOn = ctx.getSharedPreferences("home", Context.MODE_PRIVATE).getBoolean("feature.screentime", false) &&
            ScreenTime.hasAccess(ctx)

        val days = maxOf(lastClosed + 1, today - MAX_CATCH_UP_DAYS) until today
        for (day in days) {
            s = EvolutionTracker.closeDay(s, record(ctx, day, screenOn), config)
        }
        if (!days.isEmpty()) EvolutionStore.save(ctx, s)             // nur schreiben, wenn sich etwas geändert hat
        return s
    }
}

class DailyEvolutionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        EvolutionJob.closePastDays(applicationContext)
        return Result.success()
    }
}

object EvolutionScheduler {
    fun start(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "osmon_evolution", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyEvolutionWorker>(12, TimeUnit.HOURS).build()
        )
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork("osmon_evolution")
    }
}
