// Achievements.kt: Sammelalbum mit Erfolgen (reines Kotlin). Gegenstück: Achievements.swift (gleiche Liste, gleiche Tests).
//
// Jeder Erfolg hängt an einer Kennzahl (Metric) und einer Zielzahl. Kennzahlen sammelt die App (AchievementStore):
// Zähler wie erledigte Aufgaben und Höchstwerte wie die längste Serie. Freigeschaltet bleibt freigeschaltet.

package com.example.osmon.engine

enum class Metric { STAGE, TASKS, STREAK, KIND, GOODNIGHT, FESTIVALS, SCENES, COMEBACK, DAYS, GAMES, GOODDAYS }

data class Achievement(val id: String, val emoji: String, val title: String, val hint: String, val metric: Metric, val target: Int)

object Achievements {
    val ALL = listOf(
        Achievement("hatched", "🐣", "Hallo Welt!", "Lass dein Ei schlüpfen.", Metric.STAGE, 1),
        Achievement("child", "🎒", "Schulkind", "Dein Wesen wird ein Kind.", Metric.STAGE, 2),
        Achievement("teen", "🛹", "Fast groß", "Dein Wesen wird jugendlich.", Metric.STAGE, 3),
        Achievement("adult", "🏆", "Erwachsen", "Pflege dein Wesen, bis es erwachsen ist.", Metric.STAGE, 4),
        Achievement("task1", "✅", "Erster Haken", "Erledige deine erste Aufgabe.", Metric.TASKS, 1),
        Achievement("task10", "📋", "Fleißig", "Erledige 10 Aufgaben.", Metric.TASKS, 10),
        Achievement("task50", "🌟", "Aufgaben-Profi", "Erledige 50 Aufgaben.", Metric.TASKS, 50),
        Achievement("task200", "🚀", "Unaufhaltsam", "Erledige 200 Aufgaben.", Metric.TASKS, 200),
        Achievement("streak3", "🔥", "Drei am Stück", "Schaffe 3 Tage hintereinander alle Aufgaben.", Metric.STREAK, 3),
        Achievement("streak7", "🗓️", "Eine Woche stark", "Schaffe 7 Tage hintereinander alle Aufgaben.", Metric.STREAK, 7),
        Achievement("streak30", "🏅", "Monats-Held", "Schaffe 30 Tage hintereinander alle Aufgaben.", Metric.STREAK, 30),
        Achievement("kind1", "💛", "Gute Tat", "Probiere einen Miteinander-Tipp aus.", Metric.KIND, 1),
        Achievement("kind10", "💖", "Herzensmensch", "Probiere 10 Miteinander-Tipps aus.", Metric.KIND, 10),
        Achievement("kind30", "😇", "Engel", "Probiere 30 Miteinander-Tipps aus.", Metric.KIND, 30),
        Achievement("night1", "🌙", "Gute Nacht", "Sag deinem Wesen zum ersten Mal Gute Nacht.", Metric.GOODNIGHT, 1),
        Achievement("night7", "😴", "Schlafprofi", "Sag 7-mal Gute Nacht.", Metric.GOODNIGHT, 7),
        Achievement("night30", "✨", "Traumfänger", "Sag 30-mal Gute Nacht.", Metric.GOODNIGHT, 30),
        Achievement("fest1", "🎉", "Erstes Fest", "Feiere ein Fest mit deinem Wesen.", Metric.FESTIVALS, 1),
        Achievement("fest5", "🎊", "Feierbiest", "Feiere 5 verschiedene Feste.", Metric.FESTIVALS, 5),
        Achievement("scene3", "🗺️", "Entdecker", "Schalte 3 Hintergründe frei.", Metric.SCENES, 3),
        Achievement("scene10", "🌍", "Weltreisender", "Schalte alle 10 Hintergründe frei.", Metric.SCENES, 10),
        Achievement("comeback", "🩹", "Wieder aufgepäppelt", "Lass dein Wesen nach einer Pause wieder eine Stufe wachsen.", Metric.COMEBACK, 1),
        Achievement("days100", "💯", "100 Tage zusammen", "Seid 100 Tage zusammen.", Metric.DAYS, 100),
        Achievement("game1", "🎮", "Spielkind", "Spiel ein Mini-Spiel mit deinem Wesen.", Metric.GAMES, 1),
        Achievement("game20", "🕹️", "Spiele-Fan", "Spiel 20 Mini-Spiele.", Metric.GAMES, 20)
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    fun isReached(a: Achievement, m: Map<Metric, Int>) = (m[a.metric] ?: 0) >= a.target

    /** Fortschritt 0..1 (für gesperrte Erfolge im Album). */
    fun progress(a: Achievement, m: Map<Metric, Int>) = ((m[a.metric] ?: 0).toFloat() / a.target).coerceIn(0f, 1f)

    /** Neu erreichte Erfolge (in Listen-Reihenfolge), bereits freigeschaltete ausgenommen. */
    fun newlyUnlocked(m: Map<Metric, Int>, unlocked: Set<String>): List<Achievement> =
        ALL.filter { it.id !in unlocked && isReached(it, m) }

    /** Kennzahlen zusammenführen: Höchstwerte bleiben erhalten (nichts geht verloren, wenn z. B. eine Serie reißt). */
    fun merge(old: Map<Metric, Int>, now: Map<Metric, Int>): Map<Metric, Int> =
        Metric.entries.associateWith { maxOf(old[it] ?: 0, now[it] ?: 0) }.filterValues { it > 0 }
}
