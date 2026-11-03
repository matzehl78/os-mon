// Growth.kt: Wachsen vom Ei bis zum Erwachsenen (reines Kotlin). Gegenstück: Growth.swift (gleiche Regeln).
//
// Ei → (antippen bis es schlüpft) → Baby → Kind → Jugend → Erwachsen.
// Gezählt werden nur „gute Tage“: abgeschlossene Tage mit mittlerem Wohlbefinden >= 40 (nicht kränklich).
// Schlechte Tage und Tage ohne Messung bremsen, werfen aber nie zurück. Bei lauter guten Tagen ist das Wesen
// nach 12 Wochen erwachsen, realistisch nach etwa 3 Monaten.
// Die Wesensart (Form, EvolutionTracker) ist davon unabhängig und erscheint ab Tag 11 als Abzeichen.

package com.example.osmon.engine

enum class GrowthStage(val minDays: Int, val title: String) {
    EGG(-1, "Ei"), BABY(0, "Baby"), CHILD(14, "Kind"), TEEN(42, "Jugend"), ADULT(84, "Erwachsen");

    val next: GrowthStage? get() = entries.getOrNull(ordinal + 1)
}

/** Proportionen fürs Zeichnen. sx/sy = Größe der ganzen Figur (um die Füße), face = Gesicht, ant = Antennen, head = Kopf (Lumo-Bauweise). */
data class GrowthLook(val sx: Float, val sy: Float, val face: Float, val ant: Float, val head: Float)

data class GrowthState(
    val hatched: Boolean,
    val goodDays: Int = 0,
    val lastDay: Long,               // letzter ausgewerteter (abgeschlossener) Tag
    val hatchedDay: Long? = null,
    val pendingStage: GrowthStage? = null   // Aufstieg, der noch gefeiert werden soll
)

object Growth {
    const val GOOD_MIN = 40f         // ab hier „nicht kränklich“ (Mood.SICK liegt darunter)
    const val EGG_TAPS = 5
    const val MAX_CATCH_UP = 120

    fun stageFor(goodDays: Int): GrowthStage =
        GrowthStage.entries.last { it != GrowthStage.EGG && goodDays >= it.minDays }

    fun stage(s: GrowthState): GrowthStage = if (!s.hatched) GrowthStage.EGG else stageFor(s.goodDays)

    fun isGood(avgWellbeing: Float?) = avgWellbeing != null && avgWellbeing >= GOOD_MIN

    /** Tag abschließen (aufsteigend). Bereits ausgewertete Tage und Tage vor dem Schlüpfen werden ignoriert. */
    fun closeDay(s: GrowthState, day: Long, avgWellbeing: Float?): GrowthState {
        if (!s.hatched || day <= s.lastDay) return s
        val before = stageFor(s.goodDays)
        val days = s.goodDays + if (isGood(avgWellbeing)) 1 else 0
        val after = stageFor(days)
        return s.copy(goodDays = days, lastDay = day, pendingStage = if (after != before) after else s.pendingStage)
    }

    /** Schlüpfen (nach EGG_TAPS Tippern). Der Schlüpftag selbst zählt noch nicht. */
    fun hatch(s: GrowthState, today: Long): GrowthState =
        if (s.hatched) s else s.copy(hatched = true, goodDays = 0, lastDay = today, hatchedDay = today, pendingStage = GrowthStage.BABY)

    /** Wer OS-Mon schon vor dem Wachsen hatte: geschlüpft, jeder bisherige Tag zählt als gut (niemand wird zurückgestuft). */
    fun migrated(hatchDay: Long, today: Long): GrowthState =
        GrowthState(hatched = true, goodDays = (today - hatchDay).toInt().coerceAtLeast(0), lastDay = today - 1, hatchedDay = hatchDay)

    /** Fortschritt zur nächsten Stufe 0..1 und fehlende gute Tage (null = erwachsen bzw. Ei). */
    fun progress(s: GrowthState): Pair<Float, Int>? {
        if (!s.hatched) return null
        val st = stageFor(s.goodDays); val nx = st.next ?: return null
        val span = (nx.minDays - st.minDays).toFloat()
        return ((s.goodDays - st.minDays) / span).coerceIn(0f, 1f) to (nx.minDays - s.goodDays)
    }

    fun look(stage: GrowthStage?): GrowthLook = when (stage) {
        GrowthStage.BABY -> GrowthLook(0.66f, 0.60f, 1.25f, 0.62f, 1.18f)
        GrowthStage.CHILD -> GrowthLook(0.78f, 0.74f, 1.14f, 0.78f, 1.10f)
        GrowthStage.TEEN -> GrowthLook(0.90f, 0.89f, 1.05f, 0.92f, 1.04f)
        else -> GrowthLook(1f, 1f, 1f, 1f, 1f)
    }

    /** Risse im Ei (0..4) nach Anzahl Tipper. */
    fun cracks(taps: Int): Int = (taps * 4 / EGG_TAPS).coerceIn(0, 4)
}
