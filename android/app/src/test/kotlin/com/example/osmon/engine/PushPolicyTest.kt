// PushPolicyTest.kt: Unit-Tests (commonTest bzw. src/test), kotlin.test

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushPolicyTest {
    private val DAY = 24 * 3_600_000L

    /** Uhr: Tag 0 = "d0" usw., Minute des Tages als Uhrzeit. */
    private fun clk(day: Int, hour: Int, minute: Int = 0): PushClock {
        val m = hour * 60 + minute
        return PushClock(day * DAY + m * 60_000L, m, "d$day")
    }

    private fun send(s: PushState, type: PushType, c: PushClock) = PushPolicy.onSent(s, type, c)

    // ---------- Trigger ----------

    @Test fun batteryLowFiresOnlyWhenLowAndNotCharging() {
        val c = clk(0, 14)
        assertEquals(PushType.BATTERY_LOW, PushPolicy.evaluate(PushState(), PushSignals(batteryPct = 14f), c)?.type)
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(batteryPct = 14f, charging = true), c))
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(batteryPct = 16f), c))
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(), c))        // keine Daten, keine Push
    }

    @Test fun storageFullBelowEightPercent() {
        val m = PushPolicy.evaluate(PushState(), PushSignals(freeStorageRatio = 0.06f), clk(0, 12))
        assertEquals(PushType.STORAGE_FULL, m?.type)
        assertTrue(m!!.body.contains("6 %"))
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(freeStorageRatio = 0.09f), clk(0, 12)))
    }

    @Test fun screenGoalMessageShowsDuration() {
        val m = PushPolicy.evaluate(PushState(), PushSignals(screenMinutes = 180f), clk(0, 18))
        assertEquals(PushType.SCREEN_GOAL, m?.type)
        assertTrue(m!!.body.startsWith("3 Std."))
    }

    @Test fun stepsPraiseAtEightyPercent() {
        val c = clk(0, 17)
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(steps = 5599), c))
        val m = PushPolicy.evaluate(PushState(), PushSignals(steps = 5600), c)
        assertEquals(PushType.STEPS_PRAISE, m?.type)
        assertTrue(m!!.body.startsWith("5.600 Schritte"))
        val done = PushPolicy.evaluate(PushState(), PushSignals(steps = 7200), c)
        assertTrue(done!!.body.contains("Ziel geschafft"))
    }

    // ---------- Ruhezeit ----------

    @Test fun quietHoursBlockEverythingExceptNightUse() {
        val low = PushSignals(batteryPct = 5f)
        assertNull(PushPolicy.evaluate(PushState(), low, clk(0, 23)))
        assertNull(PushPolicy.evaluate(PushState(), low, clk(1, 7, 30)))
        assertNotNull(PushPolicy.evaluate(PushState(), low, clk(1, 8)))      // ab 08:00 wieder erlaubt
    }

    @Test fun nightUseFiresOnlyWithActiveUseInNightWindow() {
        val active = PushSignals(nightActiveMinutes = 12f)
        val m = PushPolicy.evaluate(PushState(), active, clk(0, 23, 47))
        assertEquals(PushType.NIGHT_USE, m?.type)
        assertTrue(m!!.body.contains("23:47"))
        assertNull(PushPolicy.evaluate(PushState(), PushSignals(nightActiveMinutes = 5f), clk(0, 23, 47)))
        assertNull(PushPolicy.evaluate(PushState(), active, clk(0, 22, 45)))   // vor 23:30
        assertNull(PushPolicy.evaluate(PushState(), active, clk(0, 14)))       // außerhalb des Fensters
        assertEquals(PushType.NIGHT_USE, PushPolicy.evaluate(PushState(), active, clk(1, 0, 20))?.type)
    }

    // ---------- Priorität, Abstand, Cooldown ----------

    @Test fun priorityThenFallbackToNextEligibleType() {
        val sig = PushSignals(batteryPct = 10f, screenMinutes = 400f)
        val c1 = clk(0, 14)
        assertEquals(PushType.BATTERY_LOW, PushPolicy.evaluate(PushState(), sig, c1)?.type)
        val s = send(PushState(), PushType.BATTERY_LOW, c1)
        // 2,5 h später: Akku-Cooldown (6 h) läuft noch, Bildschirmziel kommt dran
        assertEquals(PushType.SCREEN_GOAL, PushPolicy.evaluate(s, sig, clk(0, 16, 30))?.type)
    }

    @Test fun minimumGapBetweenPushes() {
        val sig = PushSignals(batteryPct = 10f, screenMinutes = 400f)
        val s = send(PushState(), PushType.BATTERY_LOW, clk(0, 14))
        assertNull(PushPolicy.evaluate(s, sig, clk(0, 15, 30)))               // nur 1,5 h
        assertNotNull(PushPolicy.evaluate(s, sig, clk(0, 16)))                // genau 2 h
    }

    @Test fun typeCooldowns() {
        val low = PushSignals(batteryPct = 10f)
        val s = send(PushState(), PushType.BATTERY_LOW, clk(0, 9))
        assertNull(PushPolicy.evaluate(s, low, clk(0, 14)))                   // 5 h < 6 h
        assertEquals(PushType.BATTERY_LOW, PushPolicy.evaluate(s, low, clk(0, 15))?.type)  // 6 h

        val full = PushSignals(freeStorageRatio = 0.05f)
        val t = send(PushState(), PushType.STORAGE_FULL, clk(0, 12))
        assertNull(PushPolicy.evaluate(t, full, clk(3, 12)))                  // nach 3 Tagen noch gesperrt
        assertEquals(PushType.STORAGE_FULL, PushPolicy.evaluate(t, full, clk(7, 12))?.type)
    }

    // ---------- Tageslimit & Lernen ----------

    @Test fun dailyCapThreeAndRollover() {
        var s = PushState()
        s = send(s, PushType.SCREEN_GOAL, clk(0, 9))
        s = send(s, PushType.STEPS_PRAISE, clk(0, 12))
        s = send(s, PushType.STORAGE_FULL, clk(0, 15))
        val low = PushSignals(batteryPct = 5f)
        assertNull(PushPolicy.evaluate(s, low, clk(0, 18)))                   // Limit 3 erreicht
        assertNotNull(PushPolicy.evaluate(s, low, clk(1, 10)))                // neuer Tag
    }

    @Test fun ignoringLowersCapAndEngagementResets() {
        var s = PushState()
        assertEquals(3, PushPolicy.dailyCap(s))
        repeat(3) { s = PushPolicy.onIgnored(s) }
        assertEquals(2, PushPolicy.dailyCap(s))
        repeat(3) { s = PushPolicy.onIgnored(s) }
        assertEquals(1, PushPolicy.dailyCap(s))
        repeat(9) { s = PushPolicy.onIgnored(s) }
        assertEquals(1, PushPolicy.dailyCap(s))                               // Minimum 1
        s = PushPolicy.onEngaged(s)
        assertEquals(3, PushPolicy.dailyCap(s))
    }

    @Test fun reducedCapIsEnforced() {
        var s = PushState(ignoredStreak = 6)                                  // Limit 1
        s = send(s, PushType.SCREEN_GOAL, clk(0, 9))
        assertNull(PushPolicy.evaluate(s, PushSignals(batteryPct = 5f), clk(0, 14)))
    }
}
