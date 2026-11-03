// MiniGames.kt: Mini-Spiele (reines Kotlin). Gegenstück: MiniGames.swift (gleiche Regeln, gleicher Zufall, gleiche Tests).
//
// Jedes Spiel dauert 30 Sekunden. Pro Tag gibt es nur wenige Runden (Standard 2, im Eltern-Bereich 0–3), damit das
// Wesen ein Begleiter bleibt und keine Spielhalle wird. Alle Spiele sind „Antippen, was erscheint“ mit eigener Bewegung:
//  • Sternenfangen: Sterne leuchten kurz auf, goldene zählen 3.
//  • Seifenblasen: Blasen steigen auf; Gewitterwolken nicht antippen (−1).
//  • Glühwürmchen (Premium): flackern und fliegen Bögen, zählen 2.
// Koordinaten 0..1 (x: Breite, y: Höhe, y wächst nach unten). Der Zufall ist ein eigener LCG, damit beide Plattformen
// bei gleichem Startwert dasselbe Spiel erzeugen (Tests).

package com.example.osmon.engine

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

enum class MiniGame(val emoji: String, val title: String, val hint: String, val premium: Boolean = false) {
    STARS("⭐", "Sternenfangen", "Tippe die Sterne an, bevor sie verglühen. Goldene zählen 3!"),
    BUBBLES("🫧", "Seifenblasen", "Lass die Blasen platzen – aber nicht die Gewitterwolken!"),
    FIREFLIES("✨", "Glühwürmchen", "Fang die flackernden Glühwürmchen. Jedes zählt 2.", premium = true)
}

enum class TargetKind(val points: Int) { STAR(1), GOLD(3), BUBBLE(1), CLOUD(-1), FLY(2) }

data class GameTarget(
    val id: Int, val kind: TargetKind,
    var x: Float, var y: Float, val vx: Float, val vy: Float,
    val r: Float, val born: Float, val life: Float,
    val phase: Float = 0f
) {
    fun age(t: Float) = t - born
    /** Sichtbarkeit 0..1 (Ein-/Ausblenden, Glühwürmchen flackern). */
    fun alpha(t: Float): Float {
        val a = age(t)
        val fade = minOf(1f, a / 0.15f, (life - a) / 0.25f).coerceIn(0f, 1f)
        return if (kind == TargetKind.FLY) fade * (0.55f + 0.45f * sin(a * 9f + phase).let { (it + 1f) / 2f }) else fade
    }
}

/** Ein Treffer (für Punkte-Anzeige und Ton). */
data class GameHit(val kind: TargetKind, val x: Float, val y: Float, val points: Int)

/** Kleiner LCG (Numerical Recipes), auf beiden Plattformen bitgleich. */
class GameRandom(seed: Long) {
    private var s: Long = seed and 0xFFFFFFFFL
    fun next(): Float { s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL; return (s ushr 8).toFloat() / 16777216f }
    fun range(a: Float, b: Float) = a + (b - a) * next()
}

class MiniGameSim(val game: MiniGame, seed: Long) {
    companion object {
        const val DURATION = 30f
        const val MAX_TARGETS = 6
    }
    private val rnd = GameRandom(seed)
    var time = 0f; private set
    var score = 0; private set
    var hits = 0; private set
    val targets = mutableListOf<GameTarget>()
    private var nextSpawn = 0.3f
    private var ids = 0

    val done get() = time >= DURATION
    val left get() = (DURATION - time).coerceAtLeast(0f)

    /** Abstand zwischen zwei neuen Zielen: am Anfang gemütlich, gegen Ende flotter. */
    private fun interval() = 0.95f - 0.45f * (time / DURATION).coerceIn(0f, 1f)

    fun step(dt: Float) {
        if (done) return
        time = minOf(DURATION, time + dt)
        targets.forEach { it.x += it.vx * dt; it.y += it.vy * dt }
        targets.removeAll { it.age(time) >= it.life || it.y < -0.15f || it.x < -0.15f || it.x > 1.15f }
        while (time >= nextSpawn && !done) {
            if (targets.size < MAX_TARGETS) spawn()
            nextSpawn += interval()
        }
    }

    private fun spawn() {
        val id = ++ids
        val t = when (game) {
            MiniGame.STARS -> {
                val gold = rnd.next() < 0.18f
                GameTarget(id, if (gold) TargetKind.GOLD else TargetKind.STAR, rnd.range(0.12f, 0.88f), rnd.range(0.12f, 0.85f),
                    0f, 0f, if (gold) 0.075f else 0.09f, time, if (gold) 1.1f else 1.6f)
            }
            MiniGame.BUBBLES -> {
                val cloud = rnd.next() < 0.22f
                GameTarget(id, if (cloud) TargetKind.CLOUD else TargetKind.BUBBLE, rnd.range(0.12f, 0.88f), 1.08f,
                    rnd.range(-0.05f, 0.05f), -rnd.range(0.22f, 0.36f), if (cloud) 0.1f else rnd.range(0.07f, 0.1f), time, 6f)
            }
            MiniGame.FIREFLIES -> {
                val left = rnd.next() < 0.5f
                GameTarget(id, TargetKind.FLY, if (left) -0.05f else 1.05f, rnd.range(0.15f, 0.8f),
                    (if (left) 1f else -1f) * rnd.range(0.18f, 0.3f), rnd.range(-0.06f, 0.06f), 0.08f, time, 4.5f,
                    phase = rnd.range(0f, (2 * PI).toFloat()))
            }
        }
        targets += t
    }

    /**
     * Antippen bei (x, y). aspect = Höhe/Breite der Spielfläche (Abstand in echten Pixeln). Etwas großzügiger als
     * gezeichnet (Kinderfinger). Gewitterwolken ziehen 1 ab, nie unter 0.
     */
    fun tap(x: Float, y: Float, aspect: Float = 1f): GameHit? {
        if (done) return null
        val hit = targets.filter { it.alpha(time) > 0.2f }
            .map { it to sqrt((it.x - x) * (it.x - x) + ((it.y - y) * aspect) * ((it.y - y) * aspect)) }
            .filter { (t, d) -> d <= t.r * 1.35f }
            .minByOrNull { it.second }?.first ?: return null
        targets.remove(hit)
        val before = score
        score = (score + hit.kind.points).coerceAtLeast(0)
        if (hit.kind.points > 0) hits++
        return GameHit(hit.kind, hit.x, hit.y, score - before)
    }
}

object MiniGames {
    const val DEFAULT_LIMIT = 2
    const val MAX_LIMIT = 3

    /** Sterne für das Ergebnis (0–3). */
    fun stars(score: Int) = when { score >= 24 -> 3; score >= 15 -> 2; score >= 8 -> 1; else -> 0 }

    /** Runden heute noch übrig. played = (Tag, Anzahl) des letzten Spiels. */
    fun left(lastDay: Long?, playedThatDay: Int, today: Long, limit: Int): Int =
        (limit.coerceIn(0, MAX_LIMIT) - if (lastDay == today) playedThatDay else 0).coerceAtLeast(0)

    /** Nach einer Runde: neuer Zähler für heute. */
    fun afterPlay(lastDay: Long?, playedThatDay: Int, today: Long): Int = (if (lastDay == today) playedThatDay else 0) + 1

    fun available(premium: Boolean) = MiniGame.entries.filter { !it.premium || premium }

    /** Satz des Wesens zum Ergebnis. */
    fun cheer(stars: Int, name: String) = when (stars) {
        3 -> "Wahnsinn! Du bist ein Profi! 🌟"
        2 -> "Super gespielt! $name ist begeistert!"
        1 -> "Schön! Das hat Spaß gemacht!"
        else -> "Danke fürs Spielen! Nächstes Mal klappt’s besser."
    }
}
