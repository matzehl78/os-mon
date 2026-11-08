// Growth.kt: Wachsen vom Ei bis zum Erwachsenen (reines Kotlin).
// Gegenstück: Growth.swift (gleiche Regeln, gleiche Tests).
//
// Ei → (antippen bis es schlüpft) → Baby → Kind → Jugend → Erwachsen.
// Wachstumstage je abgeschlossenem Tag (mittleres Wohlbefinden): gut (>= 40) +1, sonst 0 (Pause).
// Grobes Verhalten (wildes Antippen, Schütteln) ab ROUGH_LIMIT Aktionen am Tag: der Tag zählt nicht (0).
// Aufstieg, sobald die Grenze der nächsten Stufe erreicht ist.
// Kein Rückfall: Schlechte Tage pausieren nur das Wachsen (keine Verlustangst, keine Bestrafung von Kindern).
// Das Wesen zeigt schlechte Tage über Stimmung und Müdigkeit, nie über eine niedrigere Stufe.
// Bei lauter guten Tagen ist das Wesen nach 12 Wochen erwachsen, realistisch nach etwa 3 Monaten.
// Die Wesensart (Form, EvolutionTracker) ist davon unabhängig und erscheint ab Tag 11 als Abzeichen.

package com.example.osmon.engine

enum class GrowthStage(val minDays: Int, val title: String) {
    EGG(-1, "Ei"), BABY(0, "Baby"), CHILD(14, "Kind"), TEEN(42, "Jugend"), ADULT(84, "Erwachsen");

    val next: GrowthStage? get() = entries.getOrNull(ordinal + 1)
    val previous: GrowthStage? get() = if (this == EGG || this == BABY) null else entries[ordinal - 1]
}

/** Proportionen fürs Zeichnen. sx/sy = Größe der ganzen Figur (um die Füße), face = Gesicht, ant = Antennen, head = Kopf (Lumo-Bauweise). */
data class GrowthLook(val sx: Float, val sy: Float, val face: Float, val ant: Float, val head: Float)

data class GrowthState(
    val hatched: Boolean,
    val goodDays: Int = 0,                   // Wachstumstage (gute minus schlechte, nie unter 0)
    val lastDay: Long,                       // letzter ausgewerteter (abgeschlossener) Tag
    val hatchedDay: Long? = null,
    val pendingStage: GrowthStage? = null,   // Wechsel, der noch gezeigt werden soll
    val stage: GrowthStage? = null,          // aktuelle Stufe (null = aus goodDays ableiten, alte Stände)
    val pendingDown: Boolean = false         // der Wechsel war ein Rückfall
)

/** Gefahr eines Rückfalls: so viele schlechte Tage noch bis zur Stufe darunter. */
data class GrowthRisk(val badDaysLeft: Int, val to: GrowthStage)

object Growth {
    const val GOOD_MIN = 40f         // ab hier „nicht kränklich“ (Mood.SICK liegt darunter)
    const val ROUGH_LIMIT = 3        // grobe Aktionen pro Tag, ab denen der Tag schlecht zählt
    const val EGG_TAPS = 5
    const val MAX_CATCH_UP = 120

    fun stageFor(goodDays: Int): GrowthStage =
        GrowthStage.entries.last { it != GrowthStage.EGG && goodDays >= it.minDays }

    fun stage(s: GrowthState): GrowthStage = if (!s.hatched) GrowthStage.EGG else s.stage ?: stageFor(s.goodDays)

    fun isGood(avgWellbeing: Float?) = avgWellbeing != null && avgWellbeing >= GOOD_MIN

    /** Änderung der Wachstumstage für einen Tag (rough = grobe Aktionen an diesem Tag). */
    fun delta(avgWellbeing: Float?, rough: Int = 0): Int =
        if (isGood(avgWellbeing) && rough < ROUGH_LIMIT) 1 else 0

    /** Tag abschließen (aufsteigend). Bereits ausgewertete Tage und Tage vor dem Schlüpfen werden ignoriert. Nie abwärts. */
    fun closeDay(s: GrowthState, day: Long, avgWellbeing: Float?, rough: Int = 0): GrowthState {
        if (!s.hatched || day <= s.lastDay) return s
        val cur = stage(s)
        val days = s.goodDays + delta(avgWellbeing, rough)
        val nx = cur.next
        return if (nx != null && days >= nx.minDays) s.copy(goodDays = days, lastDay = day, stage = nx, pendingStage = nx, pendingDown = false)
        else s.copy(goodDays = days, lastDay = day, stage = cur)
    }

    /** Schlüpfen (nach EGG_TAPS Tippern). Der Schlüpftag selbst zählt noch nicht. */
    fun hatch(s: GrowthState, today: Long): GrowthState =
        if (s.hatched) s else GrowthState(hatched = true, goodDays = 0, lastDay = today, hatchedDay = today,
            pendingStage = GrowthStage.BABY, stage = GrowthStage.BABY)

    /** Wer OS-Mon schon vor dem Wachsen hatte: geschlüpft, jeder bisherige Tag zählt als gut (niemand wird zurückgestuft). */
    fun migrated(hatchDay: Long, today: Long): GrowthState {
        val days = (today - hatchDay).toInt().coerceAtLeast(0)
        return GrowthState(hatched = true, goodDays = days, lastDay = today - 1, hatchedDay = hatchDay, stage = stageFor(days))
    }

    /** Fortschritt zur nächsten Stufe 0..1 und fehlende Wachstumstage (null = erwachsen bzw. Ei). */
    fun progress(s: GrowthState): Pair<Float, Int>? {
        if (!s.hatched) return null
        val st = stage(s); val nx = st.next ?: return null
        val span = (nx.minDays - st.minDays).toFloat()
        return ((s.goodDays - st.minDays) / span).coerceIn(0f, 1f) to (nx.minDays - s.goodDays)
    }

    /** Früher: Warnung vor einem Rückfall. Es gibt keinen Rückfall mehr, daher immer null (Oberfläche zeigt keine Warnung). */
    @Suppress("UNUSED_PARAMETER")
    fun risk(s: GrowthState): GrowthRisk? = null

    fun look(stage: GrowthStage?): GrowthLook = when (stage) {
        GrowthStage.BABY -> GrowthLook(0.66f, 0.60f, 1.25f, 0.62f, 1.18f)
        GrowthStage.CHILD -> GrowthLook(0.78f, 0.74f, 1.14f, 0.78f, 1.10f)
        GrowthStage.TEEN -> GrowthLook(0.90f, 0.89f, 1.05f, 0.92f, 1.04f)
        else -> GrowthLook(1f, 1f, 1f, 1f, 1f)
    }

    /** Risse im Ei (0..4) nach Anzahl Tipper. */
    fun cracks(taps: Int): Int = (taps * 4 / EGG_TAPS).coerceIn(0, 4)
}
