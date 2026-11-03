// DayTasksTest.kt: Fälligkeit, Übernahme, Fortschritt, Serie (gleiche Fälle wie DayTasksTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DayTasksTest {
    // 2026-11-02 ist ein Montag: epochDay 20759
    private val mon = 20759L

    private fun t(id: String, r: TaskRepeat = TaskRepeat.ONCE, created: Long = mon, done: Set<Long> = emptySet()) =
        DayTask(id, id, "⭐", r, created, done)

    @Test fun weekday() {
        assertEquals(3, DayTasks.weekday(0))          // 1970-01-01 Donnerstag
        assertEquals(0, DayTasks.weekday(mon))
        assertEquals(5, DayTasks.weekday(mon + 5))    // Samstag
        assertEquals(6, DayTasks.weekday(mon + 6))
    }

    @Test fun onceCarriesOverUntilDone() {
        val a = t("a")
        assertFalse(DayTasks.isDue(a, mon - 1))
        assertTrue(DayTasks.isDue(a, mon))
        assertTrue(DayTasks.isDue(a, mon + 3))
        assertTrue(DayTasks.isCarried(a, mon + 1))
        assertFalse(DayTasks.isCarried(a, mon))
        val done = DayTasks.toggle(a, mon + 2)
        assertTrue(DayTasks.isDue(done, mon + 2))
        assertTrue(DayTasks.isDone(done, mon + 2))
        assertFalse(DayTasks.isCarried(done, mon + 2))
        assertFalse(DayTasks.isDue(done, mon + 3))
    }

    @Test fun repeatRules() {
        val wd = t("w", TaskRepeat.WEEKDAYS); val we = t("e", TaskRepeat.WEEKEND); val d = t("d", TaskRepeat.DAILY)
        assertTrue(DayTasks.isDue(wd, mon + 4)); assertFalse(DayTasks.isDue(wd, mon + 5))
        assertFalse(DayTasks.isDue(we, mon + 4)); assertTrue(DayTasks.isDue(we, mon + 6))
        assertTrue(DayTasks.isDue(d, mon + 10)); assertFalse(DayTasks.isDue(d, mon - 1))
        val x = DayTasks.toggle(d, mon)
        assertTrue(DayTasks.isDone(x, mon)); assertFalse(DayTasks.isDone(x, mon + 1))
        assertFalse(DayTasks.isDone(DayTasks.toggle(x, mon), mon))
    }

    @Test fun progressAndStages() {
        assertEquals(TaskStage.EMPTY, DayTasks.progress(emptyList(), mon).stage)
        val list = listOf(t("a"), t("b"), t("c"), t("d"))
        assertEquals(TaskProgress(0, 4), DayTasks.progress(list, mon))
        assertEquals(TaskStage.START, DayTasks.progress(list, mon).stage)
        val one = list.mapIndexed { i, x -> if (i == 0) DayTasks.toggle(x, mon) else x }
        assertEquals(TaskStage.GOING, DayTasks.progress(one, mon).stage)
        val two = one.mapIndexed { i, x -> if (i == 1) DayTasks.toggle(x, mon) else x }
        assertEquals(TaskStage.HALF, DayTasks.progress(two, mon).stage)
        val all = two.map { if (DayTasks.isDone(it, mon)) it else DayTasks.toggle(it, mon) }
        assertEquals(TaskStage.ALL, DayTasks.progress(all, mon).stage)
        assertEquals(1f, DayTasks.progress(all, mon).fraction)
    }

    @Test fun orderOpenCarriedFirst() {
        val old = t("alt", created = mon - 1)
        val done = DayTasks.toggle(t("fertig"), mon)
        val open = t("neu")
        assertEquals(listOf("alt", "neu", "fertig"), DayTasks.forDay(listOf(done, open, old), mon).map { it.id })
    }

    @Test fun streak() {
        val d = t("d", TaskRepeat.DAILY, created = mon - 5, done = setOf(mon - 3, mon - 2, mon - 1))
        assertEquals(3, DayTasks.streak(listOf(d), mon))               // heute noch offen → bis gestern
        assertEquals(4, DayTasks.streak(listOf(DayTasks.toggle(d, mon)), mon))
        // Tage ohne Aufgaben unterbrechen nicht
        val we = t("we", TaskRepeat.WEEKDAYS, created = mon - 7, done = setOf(mon - 7, mon - 6, mon - 5, mon - 4, mon - 3))
        assertEquals(5, DayTasks.streak(listOf(we), mon))
        assertEquals(0, DayTasks.streak(emptyList(), mon))
    }

    @Test fun limitsAndCleanup() {
        val many = (1..DayTasks.MAX_TASKS).map { t("t$it") }
        assertFalse(DayTasks.canAdd(many, mon, "noch eine"))
        assertTrue(DayTasks.canAdd(many.drop(1), mon, "noch eine"))
        assertFalse(DayTasks.canAdd(emptyList(), mon, "   "))
        val oldDone = t("x", created = mon - 100, done = setOf(mon - 90))
        assertEquals(emptyList(), DayTasks.cleanup(listOf(oldDone), mon))
        assertEquals(1, DayTasks.cleanup(listOf(t("offen", created = mon - 100)), mon).size)
    }

    @Test fun pickAndFill() {
        assertEquals("a", DayTasks.pick(listOf("a", "b", "c"), 1, 2))   // (31 + 2) % 3 = 0
        assertEquals("b", DayTasks.pick(listOf("a", "b", "c"), 2, 2))   // (62 + 2) % 3 = 1
        assertEquals(null, DayTasks.pick(emptyList<String>(), 1, 0))
        assertEquals("Lumo freut sich über Zähne putzen!", DayTasks.fill("{name} freut sich über {task}!", "Lumo", "Zähne putzen"))
    }
}
