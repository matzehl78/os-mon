// NightRoutineTest.kt: Phasen, Nacht-Tag, Bewertung (gleiche Fälle wie NightRoutineTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightRoutineTest {
    private val c = SleepConfig(bedMin = 22 * 60, wakeMin = 7 * 60)
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    @Test fun windowAcrossMidnight() {
        assertTrue(NightRoutine.inWindow(at(23), at(22), at(7)))
        assertTrue(NightRoutine.inWindow(at(3), at(22), at(7)))
        assertFalse(NightRoutine.inWindow(at(7), at(22), at(7)))
        assertFalse(NightRoutine.inWindow(at(12), at(22), at(7)))
        assertTrue(NightRoutine.inWindow(at(8), at(7), at(10)))
        assertFalse(NightRoutine.inWindow(at(8), at(7), at(7)))
    }

    @Test fun phases() {
        assertEquals(DayPhase.DAY, NightRoutine.phase(at(21, 29), c))
        assertEquals(DayPhase.WIND_DOWN, NightRoutine.phase(at(21, 30), c))
        assertEquals(DayPhase.NIGHT, NightRoutine.phase(at(22), c))
        assertEquals(DayPhase.NIGHT, NightRoutine.phase(at(6, 59), c))
        assertEquals(DayPhase.MORNING, NightRoutine.phase(at(7), c))
        assertEquals(DayPhase.MORNING, NightRoutine.phase(at(9, 59), c))
        assertEquals(DayPhase.DAY, NightRoutine.phase(at(10), c))
        assertEquals(DayPhase.DAY, NightRoutine.phase(at(23), c.copy(enabled = false)))
    }

    @Test fun lateBedtimeAfterMidnight() {
        val late = SleepConfig(bedMin = at(0, 30), wakeMin = at(8))
        assertEquals(DayPhase.WIND_DOWN, NightRoutine.phase(at(0, 10), late))
        assertEquals(DayPhase.WIND_DOWN, NightRoutine.phase(at(0), late))
        assertEquals(DayPhase.NIGHT, NightRoutine.phase(at(1), late))
        assertEquals(DayPhase.DAY, NightRoutine.phase(at(23, 59), late))
        assertEquals(100L, NightRoutine.nightDay(100, at(0, 10), late))
    }

    @Test fun nightDayBelongsToMorning() {
        assertEquals(101L, NightRoutine.nightDay(100, at(21, 45), c))   // Abendphase
        assertEquals(101L, NightRoutine.nightDay(100, at(23), c))
        assertEquals(100L, NightRoutine.nightDay(100, at(2), c))
        assertEquals(100L, NightRoutine.nightDay(100, at(8), c))
        assertEquals(100L, NightRoutine.nightDay(100, at(21), c))
    }

    @Test fun ratingAndReport() {
        assertEquals(NightRating.GREAT, NightRoutine.rating(0f, true, 0))
        assertEquals(NightRating.GREAT, NightRoutine.rating(null, true, 2))
        assertEquals(NightRating.OK, NightRoutine.rating(null, false, 1))
        assertEquals(NightRating.OK, NightRoutine.rating(25f, true, 0))
        assertEquals(NightRating.RESTLESS, NightRoutine.rating(25f, false, 0))
        assertEquals("Nach Mitternacht kein Blick aufs Handy.", NightRoutine.morningReport(0f, true, 0).detail)
        assertEquals("Nach Mitternacht 42 Min am Handy.", NightRoutine.morningReport(42.3f, true, 0).detail)
        assertEquals("Nach dem Gute-Nacht-Sagen blieb die App zu.", NightRoutine.morningReport(null, true, 0).detail)
        assertEquals("Die App wurde nachts 3× geöffnet.", NightRoutine.morningReport(null, true, 3).detail)
        assertEquals("Gestern gab es kein Gute-Nacht-Sagen.", NightRoutine.morningReport(null, false, 0).detail)
    }

    @Test fun pickAndClock() {
        assertEquals(NightRoutine.pickIndex(8, 20759), NightRoutine.pickIndex(8, 20759))
        assertTrue((0 until 8).contains(NightRoutine.pickIndex(8, -5, 3)))
        assertEquals(0, NightRoutine.pickIndex(0, 5))
        assertEquals("22:05", NightRoutine.clock(22 * 60 + 5))
        assertEquals("00:30", NightRoutine.clock(-30 + 24 * 60 + 60))
    }
}
