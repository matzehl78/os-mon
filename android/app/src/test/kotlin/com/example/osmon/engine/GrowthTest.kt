// GrowthTest.kt: Stufen, gute Tage, Schlüpfen, Übernahme (gleiche Fälle wie GrowthTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrowthTest {
    private val d0 = 20759L

    @Test fun stagesByGoodDays() {
        assertEquals(GrowthStage.BABY, Growth.stageFor(0))
        assertEquals(GrowthStage.BABY, Growth.stageFor(13))
        assertEquals(GrowthStage.CHILD, Growth.stageFor(14))
        assertEquals(GrowthStage.TEEN, Growth.stageFor(42))
        assertEquals(GrowthStage.ADULT, Growth.stageFor(84))
        assertEquals(GrowthStage.ADULT, Growth.stageFor(500))
        assertEquals(GrowthStage.EGG, Growth.stage(GrowthState(hatched = false, lastDay = d0)))
    }

    @Test fun goodAndBadDays() {
        var s = Growth.hatch(GrowthState(hatched = false, lastDay = d0), d0)
        assertEquals(GrowthStage.BABY, s.pendingStage)
        s = s.copy(pendingStage = null)
        s = Growth.closeDay(s, d0, 90f)                    // Schlüpftag selbst zählt nicht
        assertEquals(0, s.goodDays)
        s = Growth.closeDay(s, d0 + 1, 40f)                // gut +1
        s = Growth.closeDay(s, d0 + 2, 80f)                // gut +1
        s = Growth.closeDay(s, d0 + 3, null)               // keine Messung 0
        s = Growth.closeDay(s, d0 + 3, 80f)                // doppelt → ignoriert
        assertEquals(2, s.goodDays)
        s = Growth.closeDay(s, d0 + 4, 39.9f)              // kränklich: Pause
        assertEquals(2, s.goodDays)
        s = Growth.closeDay(s, d0 + 5, 10f)                // Winterschlaf: ebenfalls nur Pause
        assertEquals(2, s.goodDays)
        assertEquals(d0 + 5, s.lastDay)
        assertEquals(GrowthStage.BABY, Growth.stage(s))
        assertEquals(listOf(0, 1, 0, 0), listOf(null, 40f, 39f, 19f).map { Growth.delta(it) })
    }

    @Test fun badDaysPauseButNeverDrop() {
        var s = GrowthState(hatched = true, goodDays = 14, lastDay = d0, stage = GrowthStage.CHILD)
        for (i in 1..30) s = Growth.closeDay(s, d0 + i, 10f)
        assertEquals(14, s.goodDays); assertEquals(GrowthStage.CHILD, Growth.stage(s))
        assertNull(s.pendingStage); assertEquals(false, s.pendingDown); assertNull(Growth.risk(s))
        for (i in 31..58) s = Growth.closeDay(s, d0 + i, 70f)     // 28 gute Tage → 42 = Jugend
        assertEquals(GrowthStage.TEEN, Growth.stage(s)); assertEquals(GrowthStage.TEEN, s.pendingStage)
    }

    @Test fun adultStaysAdult() {
        var s = GrowthState(hatched = true, goodDays = 84, lastDay = d0, stage = GrowthStage.ADULT)
        assertNull(Growth.progress(s))
        for (i in 1..10) s = Growth.closeDay(s, d0 + i, 10f)
        assertEquals(GrowthStage.ADULT, Growth.stage(s)); assertEquals(false, s.pendingDown)
    }

    @Test fun oldStateBelowStageMinimumKeepsStage() {
        // Aus der Zeit mit Rückfall-Puffer: weniger Tage als die Stufe verlangt, Stufe bleibt
        val s = Growth.closeDay(GrowthState(hatched = true, goodDays = 12, lastDay = d0, stage = GrowthStage.CHILD), d0 + 1, 10f)
        assertEquals(GrowthStage.CHILD, Growth.stage(s)); assertNull(Growth.risk(s))
    }

    @Test fun roughBehaviour() {
        assertEquals(1, Growth.delta(80f, 2))              // 2× grob: zählt noch
        assertEquals(0, Growth.delta(80f, 3))              // ab 3× grob: Tag zählt nicht
        assertEquals(0, Growth.delta(null, 3))
        assertEquals(0, Growth.delta(10f, 5))
        var s = GrowthState(hatched = true, goodDays = 5, lastDay = d0, stage = GrowthStage.BABY)
        s = Growth.closeDay(s, d0 + 1, 80f, rough = 4)
        assertEquals(5, s.goodDays)
    }

    @Test fun eggDoesNotGrow() {
        val egg = GrowthState(hatched = false, lastDay = d0)
        assertEquals(egg, Growth.closeDay(egg, d0 + 1, 90f))
    }

    @Test fun stageUpIsPending() {
        var s = GrowthState(hatched = true, goodDays = 13, lastDay = d0)
        s = Growth.closeDay(s, d0 + 1, 70f)
        assertEquals(GrowthStage.CHILD, s.pendingStage)
        assertEquals(false, s.pendingDown)
        s = Growth.closeDay(s.copy(pendingStage = null), d0 + 2, 70f)
        assertNull(s.pendingStage)
    }

    @Test fun progressAndMigration() {
        assertEquals(0.5f to 7, Growth.progress(GrowthState(true, 7, d0)))
        assertEquals(0f to 28, Growth.progress(GrowthState(true, 14, d0)))
        assertNull(Growth.progress(GrowthState(true, 84, d0)))
        assertNull(Growth.progress(GrowthState(false, 0, d0)))
        val m = Growth.migrated(d0 - 20, d0)
        assertEquals(20, m.goodDays); assertEquals(GrowthStage.CHILD, Growth.stage(m)); assertEquals(d0 - 1, m.lastDay)
        // alte Stände ohne gespeicherte Stufe: aus den Tagen ableiten
        assertEquals(GrowthStage.TEEN, Growth.stage(GrowthState(true, 50, d0)))
    }

    @Test fun cracksAndLook() {
        assertEquals(0, Growth.cracks(0)); assertEquals(1, Growth.cracks(2)); assertEquals(4, Growth.cracks(5)); assertEquals(4, Growth.cracks(9))
        assertEquals(1f, Growth.look(GrowthStage.ADULT).sx); assertEquals(1f, Growth.look(null).face)
        assertEquals(true, Growth.look(GrowthStage.BABY).sx < Growth.look(GrowthStage.CHILD).sx)
    }
}
