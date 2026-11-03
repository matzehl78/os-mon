// AchievementsTest.kt: Liste, Freischalten, Fortschritt, Höchstwerte (gleiche Fälle wie AchievementsTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {
    @Test fun listIsConsistent() {
        assertEquals(25, Achievements.ALL.size)
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
        Achievements.ALL.forEach { assertTrue(it.target > 0 && it.title.isNotBlank() && it.hint.isNotBlank(), it.id) }
    }

    @Test fun unlocksOnlyNewOnes() {
        val m = mapOf(Metric.STAGE to 2, Metric.TASKS to 12, Metric.KIND to 1)
        val ids = Achievements.newlyUnlocked(m, setOf("hatched")).map { it.id }
        assertEquals(listOf("child", "task1", "task10", "kind1"), ids)
        assertEquals(emptyList(), Achievements.newlyUnlocked(emptyMap(), emptySet()))
    }

    @Test fun progressAndMerge() {
        val a = Achievements.byId("task50")!!
        assertEquals(0.5f, Achievements.progress(a, mapOf(Metric.TASKS to 25)))
        assertEquals(1f, Achievements.progress(a, mapOf(Metric.TASKS to 80)))
        assertEquals(0f, Achievements.progress(a, emptyMap()))
        val merged = Achievements.merge(mapOf(Metric.STREAK to 9, Metric.TASKS to 3), mapOf(Metric.STREAK to 2, Metric.TASKS to 4))
        assertEquals(mapOf(Metric.STREAK to 9, Metric.TASKS to 4), merged)
    }
}
