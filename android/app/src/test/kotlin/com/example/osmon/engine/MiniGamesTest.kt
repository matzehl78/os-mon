// MiniGamesTest.kt: Mini-Spiele – Tageslimit, Sterne, Ablauf, gleicher Zufall wie iOS (gleiche Fälle wie MiniGamesTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniGamesTest {
    @Test fun dailyLimit() {
        assertEquals(2, MiniGames.left(null, 0, 100, 2))
        assertEquals(1, MiniGames.left(100, 1, 100, 2))
        assertEquals(0, MiniGames.left(100, 2, 100, 2))
        assertEquals(2, MiniGames.left(99, 2, 100, 2))          // neuer Tag
        assertEquals(3, MiniGames.left(null, 0, 100, 9))        // höchstens 3
        assertEquals(0, MiniGames.left(null, 0, 100, 0))        // von Eltern ausgeschaltet
        assertEquals(2, MiniGames.afterPlay(100, 1, 100))
        assertEquals(1, MiniGames.afterPlay(99, 2, 100))
    }

    @Test fun clockRollbackDoesNotResetLimit() {
        // Datum vorgestellt (Tag 101), zwei Runden gespielt, Datum zurück auf Tag 100: keine neuen Runden
        assertEquals(0, MiniGames.left(101, 2, 100, 2))
        assertEquals(2, MiniGames.afterPlay(101, 1, 100))
        assertEquals(101L, MiniGames.dayAfterPlay(101, 100))
        assertEquals(100L, MiniGames.dayAfterPlay(99, 100))
        assertEquals(100L, MiniGames.dayAfterPlay(null, 100))
    }

    @Test fun starsAndGames() {
        assertEquals(listOf(0, 1, 2, 3), listOf(7, 8, 15, 24).map { MiniGames.stars(it) })
        assertEquals(listOf(MiniGame.STARS, MiniGame.BUBBLES), MiniGames.available(false))
        assertEquals(3, MiniGames.available(true).size)
    }

    @Test fun randomIsPortable() {
        val r = GameRandom(42)
        val v = List(3) { r.next() }
        assertEquals(listOf(0.25234514f, 0.08812499f, 0.5772812f), v)
    }

    @Test fun gameRunsThirtySeconds() {
        for (g in MiniGame.entries) {
            val s = MiniGameSim(g, 7)
            var maxT = 0
            repeat(400) { s.step(0.1f); maxT = maxOf(maxT, s.targets.size) }
            assertTrue(s.done && s.left == 0f, g.name)
            assertTrue(maxT in 1..MiniGameSim.MAX_TARGETS, g.name)
            assertEquals(null, s.tap(0.5f, 0.5f))                // vorbei: nichts zählt mehr
        }
    }

    @Test fun tappingScores() {
        val s = MiniGameSim(MiniGame.STARS, 1)
        var taps = 0
        while (!s.done) {
            s.step(0.05f)
            s.targets.firstOrNull { it.alpha(s.time) > 0.5f }?.let { t -> s.tap(t.x, t.y)?.let { taps++ } }
        }
        assertTrue(taps >= 20 && s.score >= taps && s.hits == taps)
        assertTrue(MiniGames.stars(s.score) == 3)
        // daneben tippen zählt nicht
        val m = MiniGameSim(MiniGame.STARS, 1); m.step(1f)
        assertEquals(null, m.tap(-1f, -1f))
    }

    @Test fun cloudsCostAPointButNeverBelowZero() {
        val s = MiniGameSim(MiniGame.BUBBLES, 3)
        var cloud: GameHit? = null
        while (!s.done && cloud == null) {
            s.step(0.05f)
            s.targets.firstOrNull { it.kind == TargetKind.CLOUD && it.alpha(s.time) > 0.5f && it.y < 0.95f }?.let { cloud = s.tap(it.x, it.y) }
        }
        assertEquals(TargetKind.CLOUD, cloud?.kind)
        assertEquals(0, s.score)
        assertEquals(0, cloud?.points)
    }
}
