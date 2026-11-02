// FestivalsTest.kt: Kalender, Ostern/Advent/Muttertag, Vorrang (gleiche Fälle wie FestivalsTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FestivalsTest {
    private fun d(y: Int, m: Int, dd: Int) = Festivals.epochDay(y, m, dd)
    private fun id(day: Long, bday: Pair<Int, Int>? = null, hatch: Long? = null) = Festivals.today(day, bday, hatch, "Lumo")?.id

    @Test fun calendar() {
        assertEquals(0L, d(1970, 1, 1))
        assertEquals(20759L, d(2026, 11, 2))
        assertEquals(Ymd(2026, 11, 2), Festivals.ymd(20759))
        assertEquals(Ymd(2024, 2, 29), Festivals.ymd(d(2024, 2, 29)))
        assertEquals(Ymd(1969, 12, 31), Festivals.ymd(-1))
        assertEquals(1, Festivals.weekday(d(2026, 11, 2)))      // Montag
        assertEquals(4, Festivals.weekday(0))                    // Donnerstag
    }

    @Test fun movableFeasts() {
        assertEquals(d(2025, 4, 20), Festivals.easter(2025))
        assertEquals(d(2026, 4, 5), Festivals.easter(2026))
        assertEquals(d(2027, 3, 28), Festivals.easter(2027))
        assertEquals(d(2026, 11, 29), Festivals.firstAdvent(2026))
        assertEquals(d(2027, 11, 28), Festivals.firstAdvent(2027))
        assertEquals(d(2025, 11, 30), Festivals.firstAdvent(2025))
        assertEquals(d(2026, 5, 10), Festivals.mothersDay(2026))
        assertEquals(d(2027, 5, 9), Festivals.mothersDay(2027))
    }

    @Test fun fixedDays() {
        assertEquals(FestivalId.CHRISTMAS, id(d(2026, 12, 24)))
        assertEquals(FestivalId.CHRISTMAS, id(d(2026, 12, 26)))
        assertEquals(FestivalId.SILVESTER, id(d(2026, 12, 31)))
        assertEquals(FestivalId.NEW_YEAR, id(d(2027, 1, 1)))
        assertEquals(FestivalId.NIKOLAUS, id(d(2026, 12, 6)))
        assertEquals(FestivalId.ST_MARTIN, id(d(2026, 11, 11)))
        assertEquals(FestivalId.HALLOWEEN, id(d(2026, 10, 31)))
        assertEquals(FestivalId.VALENTINE, id(d(2027, 2, 14)))
        assertNull(id(d(2026, 11, 2)))
        assertNull(id(d(2026, 7, 15)))
    }

    @Test fun movableDays() {
        assertEquals(FestivalId.EASTER, id(d(2026, 4, 3)))       // Karfreitag
        assertEquals(FestivalId.EASTER, id(d(2026, 4, 6)))       // Ostermontag
        assertNull(id(d(2026, 4, 7)))
        assertEquals(FestivalId.CARNIVAL, id(d(2026, 2, 12)))    // Weiberfastnacht
        assertEquals(FestivalId.CARNIVAL, id(d(2026, 2, 17)))    // Faschingsdienstag
        assertNull(id(d(2026, 2, 18)))                           // Aschermittwoch
        assertEquals(FestivalId.MOTHERS_DAY, id(d(2026, 5, 10)))
        assertEquals(FestivalId.FATHERS_DAY, id(d(2026, 5, 14)))
        assertEquals(FestivalId.ADVENT, id(d(2026, 11, 29)))
        assertEquals(FestivalId.ADVENT, id(d(2026, 12, 23)))
        assertNull(id(d(2026, 11, 28)))
        assertEquals("4. Advent", Festivals.today(d(2026, 12, 20), null, null, "Lumo")!!.title)
        assertEquals("1. Advent", Festivals.today(d(2026, 12, 1), null, null, "Lumo")!!.title)
    }

    @Test fun personalDaysWin() {
        val xmas = d(2026, 12, 24)
        assertEquals(FestivalId.BIRTHDAY, id(xmas, bday = 12 to 24))
        val hatch = d(2025, 12, 24)
        assertEquals(FestivalId.HATCHDAY, id(xmas, hatch = hatch))
        assertEquals("Heute vor einem Jahr bin ich geschlüpft. Danke, dass du da bist!", Festivals.today(xmas, null, hatch, "Lumo")!!.greeting)
        assertEquals("Lumo hat Schlüpftag!", Festivals.today(xmas, null, hatch, "Lumo")!!.title)
        assertEquals(FestivalId.MILESTONE, id(d(2026, 7, 15), hatch = d(2026, 7, 15) - 100))
        assertEquals(FestivalId.MILESTONE, id(d(2026, 7, 15), hatch = d(2026, 7, 15) - 7))
        assertNull(id(d(2026, 7, 15), hatch = d(2026, 7, 15) - 8))
        assertNull(id(d(2026, 7, 15), hatch = d(2026, 7, 15)))  // Tag des Schlüpfens selbst
        assertEquals(FestivalId.BIRTHDAY, id(d(2028, 2, 29), bday = 2 to 29))
    }
}
