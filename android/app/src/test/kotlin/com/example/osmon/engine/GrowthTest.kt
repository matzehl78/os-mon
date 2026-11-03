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
        s = Growth.closeDay(s, d0 + 4, 39.9f)              // kränklich −1
        assertEquals(1, s.goodDays)
        s = Growth.closeDay(s, d0 + 5, 10f)                // Winterschlaf −2, nie unter 0
        assertEquals(0, s.goodDays)
        assertEquals(d0 + 5, s.lastDay)
        assertEquals(GrowthStage.BABY, Growth.stage(s))    // Baby ist das Minimum
        assertEquals(listOf(0, 1, -1, -2), listOf(null, 40f, 39f, 19f).map { Growth.delta(it) })
    }

    @Test fun dropsBackWithBuffer() {
        var s = GrowthState(hatched = true, goodDays = 14, lastDay = d0, stage = GrowthStage.CHILD)
        s = Growth.closeDay(s, d0 + 1, 30f)                // 13: unter der Grenze, aber im Puffer
        assertEquals(GrowthStage.CHILD, Growth.stage(s)); assertNull(s.pendingStage)
        assertEquals(GrowthRisk(3, GrowthStage.BABY), Growth.risk(s))
        s = Growth.closeDay(s, d0 + 2, 30f)                // 12
        s = Growth.closeDay(s, d0 + 3, 30f)                // 11 = Grenze − 3, noch Kind
        assertEquals(GrowthStage.CHILD, Growth.stage(s)); assertEquals(1, Growth.risk(s)!!.badDaysLeft)
        s = Growth.closeDay(s, d0 + 4, 30f)                // 10 → Rückfall
        assertEquals(GrowthStage.BABY, Growth.stage(s))
        assertEquals(GrowthStage.BABY, s.pendingStage); assertEquals(true, s.pendingDown)
        assertNull(Growth.risk(s))                         // Baby kann nicht weiter fallen
        // Wieder wachsen: bis 14 gute Tage
        s = s.copy(pendingStage = null, pendingDown = false)
        for (i in 5..8) s = Growth.closeDay(s, d0 + i, 70f)
        assertEquals(14, s.goodDays); assertEquals(GrowthStage.CHILD, Growth.stage(s)); assertEquals(false, s.pendingDown)
    }

    @Test fun adultCanFallBackToTeen() {
        var s = GrowthState(hatched = true, goodDays = 84, lastDay = d0, stage = GrowthStage.ADULT)
        assertNull(Growth.progress(s))
        for (i in 1..2) s = Growth.closeDay(s, d0 + i, 10f)   // −4 → 80 < 81
        assertEquals(GrowthStage.TEEN, Growth.stage(s)); assertEquals(true, s.pendingDown)
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
