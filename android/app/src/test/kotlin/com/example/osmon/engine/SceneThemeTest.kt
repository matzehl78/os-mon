// SceneThemeTest.kt: Jahreszeiten und Requisiten (gleiche Fälle wie SceneThemeTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneThemeTest {
    private fun d(y: Int, m: Int, dd: Int) = Festivals.epochDay(y, m, dd)

    @Test fun seasons() {
        assertEquals(Season.WINTER, SceneTheme.season(d(2026, 2, 28)))
        assertEquals(Season.SPRING, SceneTheme.season(d(2026, 3, 1)))
        assertEquals(Season.SUMMER, SceneTheme.season(d(2026, 6, 1)))
        assertEquals(Season.AUTUMN, SceneTheme.season(d(2026, 11, 2)))
        assertEquals(Season.WINTER, SceneTheme.season(d(2026, 12, 1)))
        assertEquals("Herbst", SceneTheme.title(Season.AUTUMN))
    }

    @Test fun everyFestivalHasProps() {
        FestivalId.values().forEach { assertTrue(SceneTheme.festivalProps(it).isNotEmpty(), it.name) }
        val xmas = SceneTheme.festivalProps(FestivalId.CHRISTMAS)
        assertTrue(xmas.any { it.kind == PropKind.EMOJI && it.text == "🎄" })
        assertEquals("3", SceneTheme.festivalProps(FestivalId.ADVENT, 3).first { it.kind == PropKind.CANDLES }.text)
        assertEquals("4", SceneTheme.festivalProps(FestivalId.ADVENT, 9).first { it.kind == PropKind.CANDLES }.text)
    }

    @Test fun propsStayInside() {
        val all = FestivalId.values().flatMap { SceneTheme.festivalProps(it) } + Season.values().flatMap { SceneTheme.seasonProps(it) }
        all.forEach { assertTrue(it.x in 0f..1f && it.size > 0f && it.dy >= 0f, it.toString()) }
    }

    @Test fun combine() {
        val xmas = Festivals.today(d(2026, 12, 24), null, null, "Lumo")
        val p = SceneTheme.props(xmas, Season.WINTER, seasons = true)
        assertTrue(p.any { it.text == "🎄" } && p.any { it.kind == PropKind.SNOW_GROUND })
        assertTrue(SceneTheme.props(null, Season.WINTER, seasons = false).isEmpty())
        assertEquals(Decor.SNOW, SceneTheme.particles(xmas, Season.WINTER, true))
        assertEquals(Decor.LEAVES, SceneTheme.particles(null, Season.AUTUMN, true))
        assertEquals(Decor.PUMPKINS, SceneTheme.particles(Festivals.today(d(2026, 10, 31), null, null, "Lumo"), Season.AUTUMN, false))
        assertNull(SceneTheme.particles(null, Season.SUMMER, false))
    }
}
