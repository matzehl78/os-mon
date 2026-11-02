// NightRoutine.kt: Abend- und Morgenablauf (reines Kotlin, keine Android-Imports). Gegenstück: NightRoutine.swift.
//
// Phasen über den Tag (Minuten seit Mitternacht, lokale Zeit):
//   WIND_DOWN  30 Min vor der Schlafenszeit: Wesen wird müde, Gute-Nacht-Routine wird angeboten
//   NIGHT      Schlafenszeit bis Aufstehzeit (auch über Mitternacht)
//   MORNING    3 Std. ab Aufstehzeit: Guten-Morgen-Karte mit Bericht über die Nacht
//   DAY        sonst
// Eine Nacht gehört zum Morgen danach („Nacht-Tag“), damit Abend und Morgen zusammen ausgewertet werden.

package com.example.osmon.engine

enum class DayPhase { DAY, WIND_DOWN, NIGHT, MORNING }

data class SleepConfig(
    val enabled: Boolean = true,
    val bedMin: Int = 22 * 60,           // Schlafenszeit
    val wakeMin: Int = 7 * 60            // Aufstehzeit
)

enum class NightRating { GREAT, OK, RESTLESS }

data class MorningReport(
    val rating: NightRating,
    val detail: String,                  // ein Satz über die Nacht
    val saidGoodnight: Boolean
)

object NightRoutine {
    const val WIND_DOWN_MIN = 30
    const val MORNING_MIN = 180
    private const val DAY_MIN = 24 * 60

    private fun norm(m: Int) = Math.floorMod(m, DAY_MIN)

    /** true, wenn m im Fenster [start, end) liegt (Fenster darf über Mitternacht gehen). */
    fun inWindow(m: Int, start: Int, end: Int): Boolean {
        val a = norm(start); val b = norm(end); val x = norm(m)
        return if (a == b) false else if (a < b) x in a until b else x >= a || x < b
    }

    fun phase(minuteOfDay: Int, c: SleepConfig): DayPhase = when {
        !c.enabled -> DayPhase.DAY
        inWindow(minuteOfDay, c.bedMin, c.wakeMin) -> DayPhase.NIGHT
        inWindow(minuteOfDay, c.bedMin - WIND_DOWN_MIN, c.bedMin) -> DayPhase.WIND_DOWN
        inWindow(minuteOfDay, c.wakeMin, c.wakeMin + MORNING_MIN) -> DayPhase.MORNING
        else -> DayPhase.DAY
    }

    /** Nacht-Tag: der Tag des Morgens, zu dem dieser Zeitpunkt (Abend/Nacht) gehört. */
    fun nightDay(epochDay: Long, minuteOfDay: Int, c: SleepConfig): Long {
        val m = norm(minuteOfDay)
        // nach der Schlafenszeit (vor Mitternacht) und in der Abendphase gehört die Nacht schon zum nächsten Morgen
        val evening = c.bedMin > c.wakeMin && m >= norm(c.bedMin - WIND_DOWN_MIN)
        return if (evening) epochDay + 1 else epochDay
    }

    /**
     * Bewertung der Nacht. nightMinutes = Bildschirmminuten nach Mitternacht (null = keine Messung),
     * nightOpens = wie oft die App nach dem Gute-Nacht-Sagen geöffnet wurde.
     */
    fun rating(nightMinutes: Float?, saidGoodnight: Boolean, nightOpens: Int): NightRating {
        var score = (nightMinutes ?: 0f) + 5f * nightOpens
        if (!saidGoodnight) score += 10f
        return when {
            score <= 10f -> NightRating.GREAT
            score <= 30f -> NightRating.OK
            else -> NightRating.RESTLESS
        }
    }

    fun morningReport(nightMinutes: Float?, saidGoodnight: Boolean, nightOpens: Int): MorningReport {
        val detail = when {
            nightMinutes != null && nightMinutes < 1f -> "Nach Mitternacht kein Blick aufs Handy."
            nightMinutes != null -> "Nach Mitternacht ${Math.round(nightMinutes)} Min am Handy."
            nightOpens == 0 && saidGoodnight -> "Nach dem Gute-Nacht-Sagen blieb die App zu."
            nightOpens > 0 -> "Die App wurde nachts ${nightOpens}× geöffnet."
            else -> "Gestern gab es kein Gute-Nacht-Sagen."
        }
        return MorningReport(rating(nightMinutes, saidGoodnight, nightOpens), detail, saidGoodnight)
    }

    /** Feste Auswahl pro Tag (gleich auf allen Geräten), salt trennt verschiedene Listen. */
    fun pickIndex(size: Int, epochDay: Long, salt: Int = 0): Int =
        if (size <= 0) 0 else Math.floorMod(epochDay * 31 + salt, size.toLong()).toInt()

    /** „22:30“ */
    fun clock(min: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", norm(min) / 60, norm(min) % 60)
}
