// WardrobeTest.kt: Kleiderschrank – Freischalten, Anziehen, Premium, Fest-Vorrang (gleiche Fälle wie WardrobeTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WardrobeTest {
    @Test fun listIsConsistent() {
        assertEquals(11, Wardrobe.ALL.size)
        assertEquals(Wardrobe.ALL.size, Wardrobe.ALL.map { it.id }.toSet().size)
        Wardrobe.ALL.forEach { assertTrue((it.slot == Slot.HAT) == (it.hat != null) && (it.slot == Slot.GLASSES) == (it.glasses != null), it.id) }
        // durch gute Tage alles innerhalb der ca. 3 Monate bis zum Erwachsenwerden erreichbar
        assertTrue(Wardrobe.ALL.filter { !it.premium }.all { it.goodDays in 1..GrowthStage.ADULT.minDays })
    }

    @Test fun unlockByGoodDaysAndPremium() {
        assertEquals(listOf("beanie", "round"), Wardrobe.newlyUnlocked(5, false, emptySet()).map { it.id })
        assertEquals(listOf("cap"), Wardrobe.newlyUnlocked(10, false, setOf("beanie", "round")).map { it.id })
        assertEquals(listOf("wizard", "pirate", "monocle"), Wardrobe.newlyUnlocked(0, true, emptySet()).map { it.id })
        assertEquals(3, Wardrobe.missing(Wardrobe.byId("cap")!!, 7, false))
        assertEquals(null, Wardrobe.missing(Wardrobe.byId("wizard")!!, 99, false))
        assertEquals("cap", Wardrobe.next(5)?.id)
        assertEquals(null, Wardrobe.next(60))
    }

    @Test fun toggleAndOutfit() {
        var e = Wardrobe.toggle(emptyList(), "beanie")
        e = Wardrobe.toggle(e, "round")
        e = Wardrobe.toggle(e, "cap")                 // ersetzt die Mütze
        assertEquals(listOf("round", "cap"), e)
        assertEquals(Outfit(Accessory.CAP, Glasses.ROUND), Wardrobe.outfit(e, 20, false))
        assertEquals(Outfit(null, Glasses.ROUND), Wardrobe.outfit(e, 6, false))     // Kappe noch gesperrt
        assertEquals(listOf("cap"), Wardrobe.toggle(e, "round"))                   // aus
        assertEquals(Outfit(), Wardrobe.outfit(listOf("wizard"), 99, false))       // Premium weg
        assertEquals(Outfit(Accessory.WIZARD_HAT), Wardrobe.outfit(listOf("wizard"), 0, true))
    }

    @Test fun festivalWins() {
        val o = Outfit(Accessory.BEANIE, Glasses.SUN)
        assertEquals(Accessory.SANTA_HAT, Wardrobe.hat(Accessory.SANTA_HAT, o))
        assertEquals(Accessory.BEANIE, Wardrobe.hat(null, o))
    }
}
