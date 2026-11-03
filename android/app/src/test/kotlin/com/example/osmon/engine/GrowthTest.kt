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

    @Test fun onlyGoodDaysCount() {
        var s = Growth.hatch(GrowthState(hatched = false, lastDay = d0), d0)
        assertEquals(GrowthStage.BABY, s.pendingStage)
        s = s.copy(pendingStage = null)
        s = Growth.closeDay(s, d0, 90f)                    // Schlüpftag selbst zählt nicht
        assertEquals(0, s.goodDays)
        s = Growth.closeDay(s, d0 + 1, 39.9f)              // kränklich
        s = Growth.closeDay(s, d0 + 2, null)               // keine Messung
        s = Growth.closeDay(s, d0 + 3, 40f)                // gut
        s = Growth.closeDay(s, d0 + 3, 80f)                // doppelt → ignoriert
        assertEquals(1, s.goodDays)
        assertEquals(d0 + 3, s.lastDay)
    }

    @Test fun eggDoesNotGrow() {
        val egg = GrowthState(hatched = false, lastDay = d0)
        assertEquals(egg, Growth.closeDay(egg, d0 + 1, 90f))
    }

    @Test fun stageUpIsPending() {
        var s = GrowthState(hatched = true, goodDays = 13, lastDay = d0)
        s = Growth.closeDay(s, d0 + 1, 70f)
        assertEquals(GrowthStage.CHILD, s.pendingStage)
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
    }

    @Test fun cracksAndLook() {
        assertEquals(0, Growth.cracks(0)); assertEquals(1, Growth.cracks(2)); assertEquals(4, Growth.cracks(5)); assertEquals(4, Growth.cracks(9))
        assertEquals(1f, Growth.look(GrowthStage.ADULT).sx); assertEquals(1f, Growth.look(null).face)
        assertEquals(true, Growth.look(GrowthStage.BABY).sx < Growth.look(GrowthStage.CHILD).sx)
    }
}
