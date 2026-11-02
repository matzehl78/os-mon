// PetProgress.kt: Wochenrückblick und freischaltbare Hintergründe (reines Kotlin, keine Android-Imports)
//
// Gegenstück: PetProgress.swift. Beide rechnen aus derselben Tagesliste (DayStat) und liefern
// dieselben Ergebnisse; die gemeinsamen Testfälle stehen in PetProgressTest.kt / PetProgressTests.swift.
//
// Grundidee: Belohnt wird gesundes Verhalten (weniger Bildschirm, Bewegung, Pausen, Zuwendung),
// nie mehr Nutzung. Freigeschaltete Hintergründe bleiben freigeschaltet (Speicher im Adapter).
//
// Tage: epochDay = Tage seit 1970-01-01 (lokales Datum). 1970-01-01 war ein Donnerstag.

package com.example.osmon.engine

/** Ein abgeschlossener Tag. null = keine Daten (Sensor aus oder nicht erlaubt). */
data class DayStat(
    val day: Long,
    val avgWellbeing: Float? = null,
    val screenMin: Float? = null,
    val steps: Int? = null,
    val pats: Int = 0,
    val focus: Int = 0
)

data class WeeklyReview(
    val weekStart: Long,                 // Montag der ausgewerteten Woche
    val daysWithData: Int,
    val avgWellbeing: Float?,
    val happyDays: Int,                  // Tage mit Mittel >= 65 (mindestens „Zufrieden“)
    val screenPerDay: Float?,            // Minuten pro Tag (nur Tage mit Messung)
    val screenPerDayPrev: Float?,
    val steps: Int,
    val bestStepsDay: Int,
    val focus: Int,
    val pats: Int,
    val headline: String,
    val lines: List<String>
)

data class SceneUnlock(val sceneId: String, val unlocked: Boolean, val progress: Float, val rule: String)

object PetProgress {
    const val HAPPY = 65f

    /** Montag der Woche, in der `day` liegt. */
    fun weekStart(day: Long): Long = day - Math.floorMod(day + 3, 7L)

    /**
     * Rückblick auf die letzte abgeschlossene Woche (Mo–So vor der aktuellen Woche).
     * null, wenn weniger als 3 Tage mit Daten vorliegen.
     */
    fun weeklyReview(days: List<DayStat>, today: Long): WeeklyReview? {
        val ws = weekStart(today) - 7
        val week = days.filter { it.day in ws..(ws + 6) }
        val prev = days.filter { it.day in (ws - 7)..(ws - 1) }
        val withData = week.count { it.avgWellbeing != null || it.screenMin != null || it.steps != null }
        if (withData < 3) return null

        val hs = week.mapNotNull { it.avgWellbeing }
        val avgH = if (hs.isEmpty()) null else hs.average().toFloat()
        val happy = hs.count { it >= HAPPY }
        fun perDay(l: List<DayStat>): Float? {
            val m = l.mapNotNull { it.screenMin }
            return if (m.size < 4) null else m.average().toFloat()
        }
        val sNow = perDay(week); val sPrev = perDay(prev)
        val steps = week.sumOf { it.steps ?: 0 }
        val best = week.maxOfOrNull { it.steps ?: 0 } ?: 0
        val focus = week.sumOf { it.focus }
        val pats = week.sumOf { it.pats }

        val lines = mutableListOf<String>()
        val drop = if (sNow != null && sPrev != null) sPrev - sNow else null
        if (drop != null) {
            val d = Math.round(kotlin.math.abs(drop))
            lines += if (drop >= 0) "Bildschirm: $d Min weniger pro Tag als in der Vorwoche"
                     else "Bildschirm: $d Min mehr pro Tag als in der Vorwoche"
        } else if (sNow != null) lines += "Bildschirm: im Schnitt ${Math.round(sNow)} Min pro Tag"
        if (hs.isNotEmpty()) lines += "Zufrieden an $happy von ${hs.size} Tagen"
        if (steps > 0) lines += "${fmt(steps)} Schritte, bester Tag ${fmt(best)}"
        if (focus > 0) lines += "$focus Fokus-Session${if (focus == 1) "" else "s"} geschafft"
        if (pats > 0) lines += "$pats× gestreichelt"

        val headline = when {
            drop != null && drop >= 15f -> "${Math.round(drop)} Min weniger Bildschirm pro Tag!"
            happy >= 5 -> "$happy Tage zufrieden – starke Woche!"
            steps >= 35_000 -> "${fmt(steps)} Schritte diese Woche!"
            focus >= 3 -> "$focus Fokus-Sessions – sehr konzentriert!"
            else -> "Deine Woche mit deinem Wesen"
        }
        return WeeklyReview(ws, withData, avgH, happy, sNow, sPrev, steps, best, focus, pats, headline, lines)
    }

    // ---------------------------------------------------------------- Freischaltbare Hintergründe

    val FREE = setOf("none", "neonstadt", "zimmer", "garteninsel")

    /**
     * Stand aller Hintergründe. `already` = schon früher freigeschaltet (bleibt frei).
     * age = Tage seit dem Schlüpfen, evolved = erste Entwicklung erreicht.
     */
    fun unlocks(days: List<DayStat>, age: Long, evolved: Boolean, screenGoal: Float, already: Set<String> = emptySet()): List<SceneUnlock> {
        val d = days.sortedBy { it.day }
        val steps7 = rollingMax(d, 7) { (it.steps ?: 0).toFloat() }
        val happyRun = longestRun(d) { (it.avgWellbeing ?: -1f) >= HAPPY }
        val screenRun = longestRun(d) { it.screenMin != null && it.screenMin <= screenGoal }
        val focus = d.sumOf { it.focus }
        val pats = d.sumOf { it.pats }
        fun u(id: String, p: Float, rule: String) = SceneUnlock(id, id in FREE || id in already || p >= 1f, if (id in already) 1f else p.coerceIn(0f, 1f), rule)
        return listOf(
            u("none", 1f, ""), u("neonstadt", 1f, ""), u("zimmer", 1f, ""), u("garteninsel", 1f, ""),
            u("raumstation", steps7 / 50_000f, "50.000 Schritte in 7 Tagen"),
            u("zauberwald", happyRun / 5f, "5 Tage am Stück zufrieden"),
            u("unterwasser", focus / 3f, "3 Fokus-Sessions abschließen"),
            u("suess", pats / 10f, "10× streicheln"),
            u("mondbasis", age / 14f, "14 Tage zusammen"),
            u("zen", screenRun / 7f, "7 Tage am Stück unter dem Bildschirmziel"),
            u("studio", if (evolved) 1f else 0f, "Erste Entwicklung erleben"),
        )
    }

    /** Größte Summe über `n` aufeinanderfolgende Kalendertage (fehlende Tage zählen 0). */
    private fun rollingMax(d: List<DayStat>, n: Int, v: (DayStat) -> Float): Float {
        if (d.isEmpty()) return 0f
        val byDay = d.associateBy { it.day }
        var best = 0f
        for (start in d.first().day..d.last().day) {
            var s = 0f
            for (k in 0 until n) byDay[start + k]?.let { s += v(it) }
            if (s > best) best = s
        }
        return best
    }

    /** Längste Folge aufeinanderfolgender Kalendertage, an denen `ok` gilt. */
    private fun longestRun(d: List<DayStat>, ok: (DayStat) -> Boolean): Int {
        var best = 0; var run = 0; var last: Long? = null
        for (x in d) {
            run = if (ok(x) && last != null && x.day == last + 1 && run > 0) run + 1 else if (ok(x)) 1 else 0
            last = x.day
            if (run > best) best = run
        }
        return best
    }

    private fun fmt(n: Int): String = String.format(java.util.Locale.ROOT, "%,d", n).replace(',', '.')
}
