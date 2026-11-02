// EvolutionTracker.kt: tägliche Evolutions-Logik (reines Kotlin, KMP-tauglich, keine Plattform-Imports)
//
// Wird einmal pro abgeschlossenem Tag aufgerufen (closeDay). Entscheidet:
//   1. Erste Evolution ab Tag 11 nach dem Schlüpfen (mit >= 5 Tagen Nutzungsdaten im 14-Tage-Fenster,
//      spätestens ab Tag 21 auch ohne Daten; dann greift der Balance-Fall "Wandero").
//   2. Metamorphose: eine andere Form führt 14 Tage in Folge, seit der letzten Evolution sind >= 21 Tage
//      vergangen, und (bei zwei kategoriebasierten Formen) der Anteilsvorsprung beträgt >= 10 Prozentpunkte.
//   3. Sonst nur: Gesundheits-Zweig (Branch) nachziehen, bei gleicher Form auch das Hybrid-Merkmal.
//
// Parität: golden_evolution.py (Referenz) erzeugt golden_evolution.json; EvolutionGoldenTest.kt und
// EvolutionGoldenTests.swift prüfen beide Ports dagegen. Formeländerungen immer in allen drei Fassungen.

package com.example.osmon.engine

import kotlin.math.pow

enum class EvoKind { EVOLVED, METAMORPHOSIS }

/** Ereignis für die Oberfläche (Animation/Banner). Bleibt gespeichert, bis acknowledge() aufgerufen wird. */
data class EvoEvent(val kind: EvoKind, val from: Form?, val to: Form, val epochDay: Long)

/**
 * Ein abgeschlossener Tag. epochDay = Tage seit 1970-01-01 (lokales Datum).
 * shares: Kategorienanteile des Tages (Summe ca. 1), null = keine Nutzungsdaten (Feature aus / Zugriff fehlt).
 * avgWellbeing: Mittel der H-Messungen des Tages, null = keine Messung.
 */
data class DayRecord(
    val epochDay: Long,
    val shares: Map<AppGroup, Float>?,
    val totalMinutes: Float?,
    val avgWellbeing: Float?
)

data class EvoState(
    val hatchDay: Long,
    val history: List<DayRecord> = emptyList(),          // abgeschlossene Tage, aufsteigend, max. 28
    val blended: Map<AppGroup, Float>? = null,           // exponentiell gewichtete Anteile (Halbwertszeit 5 Tage)
    val current: Evolution? = null,                      // null = noch Ei/Baby
    val lastEvolutionDay: Long? = null,
    val challenger: Form? = null,                        // Form, die gerade führt
    val challengerSince: Long? = null,
    val pendingEvent: EvoEvent? = null
)

object EvolutionTracker {
    const val EVOLVE_AGE = 11
    const val MIN_USABLE_DAYS = 5
    const val FALLBACK_AGE = 21
    const val WINDOW = 14
    const val HISTORY_MAX = 28
    const val META_INTERVAL = 21
    const val CHALLENGER_DAYS = 14
    const val MARGIN = 0.10f

    private val KEEP = 0.5f.pow(1f / 5f)                 // Gewicht der Vergangenheit pro Tag

    private val formGroup = mapOf(
        Form.CHATTERLING to AppGroup.SOCIAL,
        Form.BINGEON to AppGroup.ENTERTAINMENT,
        Form.PIXELO to AppGroup.GAMES,
        Form.ZENFUCHS to AppGroup.PRODUCTIVITY
    )                                                    // WANDERO hat keine Kategorie

    fun isUsable(r: DayRecord) = r.shares != null && (r.totalMinutes ?: 0f) > 0f

    fun blend(prev: Map<AppGroup, Float>?, today: Map<AppGroup, Float>): Map<AppGroup, Float> =
        AppGroup.values().associateWith { g ->
            val t = today[g] ?: 0f
            if (prev == null) t else KEEP * (prev[g] ?: 0f) + (1f - KEEP) * t
        }

    private fun groupMargin(blended: Map<AppGroup, Float>?, from: Form, to: Form): Float? {
        val fg = formGroup[from]
        val tg = formGroup[to]
        if (fg == null || tg == null || blended == null) return null
        return (blended[tg] ?: 0f) - (blended[fg] ?: 0f)
    }

    /** Tage müssen in aufsteigender Reihenfolge abgeschlossen werden; ein bereits abgeschlossener Tag wird ignoriert. */
    fun closeDay(s: EvoState, r: DayRecord, config: Config): EvoState {
        val last = s.history.lastOrNull()?.epochDay
        if (last != null && r.epochDay <= last) return s

        val history = (s.history + r).takeLast(HISTORY_MAX)
        val blended = if (isUsable(r)) blend(s.blended, r.shares!!) else s.blended

        val window = history.takeLast(WINDOW)
        val usableWindow = window.filter { isUsable(it) }
        val avgDaily = if (usableWindow.isEmpty()) 0f else usableWindow.map { it.totalMinutes!! }.sum() / usableWindow.size
        val wellbeing = window.mapNotNull { it.avgWellbeing }
        val avgW = if (wellbeing.isEmpty()) 50f else wellbeing.sum() / wellbeing.size
        val cand = PetCore.evolve(blended ?: emptyMap(), avgDaily, avgW, config)

        val age = r.epochDay - s.hatchDay
        var current = s.current
        var lastEvo = s.lastEvolutionDay
        var challenger = s.challenger
        var since = s.challengerSince
        var event = s.pendingEvent

        if (current == null) {
            val ready = age >= EVOLVE_AGE && (usableWindow.size >= MIN_USABLE_DAYS || age >= FALLBACK_AGE)
            if (ready) {
                current = cand
                lastEvo = r.epochDay
                event = EvoEvent(EvoKind.EVOLVED, null, cand.form, r.epochDay)
            }
        } else if (cand.form == current.form) {
            current = cand                               // Hybrid und Branch nachziehen
            challenger = null
            since = null
        } else {
            val cur: Evolution = current
            current = cur.copy(branch = cand.branch)     // nur den Gesundheits-Zweig aktualisieren
            if (challenger != cand.form) {
                challenger = cand.form
                since = r.epochDay
            }
            val stable = since != null && r.epochDay - since >= CHALLENGER_DAYS - 1
            val intervalOk = lastEvo == null || r.epochDay - lastEvo >= META_INTERVAL
            val margin = groupMargin(blended, cur.form, cand.form)
            if (stable && intervalOk && (margin == null || margin >= MARGIN)) {
                event = EvoEvent(EvoKind.METAMORPHOSIS, cur.form, cand.form, r.epochDay)
                current = cand
                lastEvo = r.epochDay
                challenger = null
                since = null
            }
        }

        return s.copy(
            history = history, blended = blended, current = current, lastEvolutionDay = lastEvo,
            challenger = challenger, challengerSince = since, pendingEvent = event
        )
    }

    /** Die Oberfläche hat das Ereignis gezeigt. */
    fun acknowledge(s: EvoState) = s.copy(pendingEvent = null)
}
