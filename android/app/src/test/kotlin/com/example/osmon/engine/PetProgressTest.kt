// PetProgressTest.kt: Wochenrückblick und Freischalt-Regeln (gleiche Fälle wie PetProgressTests.swift)
// Ablage: src/test/kotlin/com/example/osmon/engine/

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetProgressTest {
    // 2026-11-02 = epochDay 20759, ein Montag
    private val MON = 20759L

    private fun unlocked(l: List<SceneUnlock>, id: String) = l.first { it.sceneId == id }.unlocked
    private fun progress(l: List<SceneUnlock>, id: String) = l.first { it.sceneId == id }.progress

    @Test fun weekStartIsMonday() {
        assertEquals(MON, PetProgress.weekStart(MON))
        assertEquals(MON, PetProgress.weekStart(MON + 6))      // Sonntag
        assertEquals(MON - 7, PetProgress.weekStart(MON - 1))
        assertEquals(-3L, PetProgress.weekStart(0))           // 1970-01-01 (Do) -> Mo 1969-12-29
    }

    @Test fun reviewNeedsThreeDays() {
        val days = listOf(DayStat(MON - 7, avgWellbeing = 70f), DayStat(MON - 6, steps = 4000))
        assertNull(PetProgress.weeklyReview(days, MON + 2))
    }

    @Test fun reviewScreenDropHeadline() {
        val prev = (MON - 14 until MON - 7).map { DayStat(it, avgWellbeing = 55f, screenMin = 200f) }
        val week = (MON - 7 until MON).map { DayStat(it, avgWellbeing = 60f, screenMin = 170f, steps = 3000, pats = 1) }
        val r = assertNotNull(PetProgress.weeklyReview(prev + week, MON + 3))
        assertEquals(MON - 7, r.weekStart)
        assertEquals(7, r.daysWithData)
        assertEquals(30f, r.screenPerDayPrev!! - r.screenPerDay!!, 0.01f)
        assertEquals("30 Min weniger Bildschirm pro Tag!", r.headline)
        assertEquals(21000, r.steps)
        assertEquals(7, r.pats)
        assertEquals(
            listOf(
                "Bildschirm: 30 Min weniger pro Tag als in der Vorwoche",
                "Zufrieden an 0 von 7 Tagen",
                "21.000 Schritte, bester Tag 3.000",
                "7× gestreichelt"
            ), r.lines
        )
    }

    @Test fun reviewHappyHeadlineAndFocusSingular() {
        val week = (MON - 7 until MON).map { DayStat(it, avgWellbeing = if (it < MON - 2) 70f else 50f, focus = if (it == MON - 7) 1 else 0) }
        val r = assertNotNull(PetProgress.weeklyReview(week, MON))
        assertEquals(5, r.happyDays)
        assertEquals("5 Tage zufrieden – starke Woche!", r.headline)
        assertTrue("1 Fokus-Session geschafft" in r.lines)
        assertNull(r.screenPerDay)
    }

    @Test fun reviewIgnoresCurrentWeek() {
        val cur = (MON until MON + 5).map { DayStat(it, avgWellbeing = 90f) }
        assertNull(PetProgress.weeklyReview(cur, MON + 5))
    }

    @Test fun freeScenesAlwaysOpen() {
        val l = PetProgress.unlocks(emptyList(), age = 0, evolved = false, screenGoal = 180f)
        assertEquals(11, l.size)
        listOf("none", "neonstadt", "zimmer", "garteninsel").forEach { assertTrue(unlocked(l, it)) }
        listOf("raumstation", "zauberwald", "unterwasser", "suess", "mondbasis", "zen", "studio").forEach { assertFalse(unlocked(l, it)) }
    }

    @Test fun stepsWindowAndGaps() {
        // 6 Tage × 8000 + Lücke + 1 Tag 9000: Fenster über die Lücke = 5×8000 + 9000 = 49.000 < 50.000
        val d = (0 until 6).map { DayStat(MON + it, steps = 8000) } + DayStat(MON + 7, steps = 9000)
        val l = PetProgress.unlocks(d, 0, false, 180f)
        assertFalse(unlocked(l, "raumstation"))
        assertEquals(0.98f, progress(l, "raumstation"), 0.001f)
        val l2 = PetProgress.unlocks(d + DayStat(MON + 6, steps = 2000), 0, false, 180f)
        assertTrue(unlocked(l2, "raumstation"))
    }

    @Test fun happyRunNeedsConsecutiveDays() {
        val d = listOf(0, 1, 2, 3, 5, 6).map { DayStat(MON + it, avgWellbeing = 70f) }
        val l = PetProgress.unlocks(d, 0, false, 180f)
        assertFalse(unlocked(l, "zauberwald"))
        assertEquals(0.8f, progress(l, "zauberwald"), 0.001f)
        val l2 = PetProgress.unlocks(d + DayStat(MON + 4, avgWellbeing = 65f), 0, false, 180f)
        assertTrue(unlocked(l2, "zauberwald"))
    }

    @Test fun screenRunUsesGoal() {
        val d = (0 until 7).map { DayStat(MON + it, screenMin = if (it == 3) 181f else 120f) }
        assertFalse(unlocked(PetProgress.unlocks(d, 0, false, 180f), "zen"))
        assertTrue(unlocked(PetProgress.unlocks(d, 0, false, 190f), "zen"))
    }

    @Test fun countersAgeEvolution() {
        val d = listOf(DayStat(MON, focus = 2, pats = 6), DayStat(MON + 9, focus = 1, pats = 4))
        val l = PetProgress.unlocks(d, age = 14, evolved = true, screenGoal = 180f)
        assertTrue(unlocked(l, "unterwasser"))
        assertTrue(unlocked(l, "suess"))
        assertTrue(unlocked(l, "mondbasis"))
        assertTrue(unlocked(l, "studio"))
        val l2 = PetProgress.unlocks(d, age = 7, evolved = false, screenGoal = 180f)
        assertEquals(0.5f, progress(l2, "mondbasis"), 0.001f)
        assertFalse(unlocked(l2, "studio"))
    }

    @Test fun alreadyUnlockedStaysOpen() {
        val l = PetProgress.unlocks(emptyList(), 0, false, 180f, already = setOf("zen"))
        assertTrue(unlocked(l, "zen"))
        assertEquals(1f, progress(l, "zen"))
    }
}
