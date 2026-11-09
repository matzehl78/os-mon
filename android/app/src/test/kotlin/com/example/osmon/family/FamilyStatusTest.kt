// FamilyStatusTest.kt: Elternhinweis zum Messstatus (gleiche Texte wie Webportal und iOS-Eltern-App)

package com.example.osmon.family

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilyStatusTest {
    @Test fun texts() {
        assertTrue(FamilyStatus.measuringText(null, null).startsWith("Messung noch unbekannt"))
        assertEquals("Bildschirmzeit und Schritte werden gemessen", FamilyStatus.measuringText(true, true))
        assertTrue(FamilyStatus.measuringText(false, true).startsWith("Bildschirmzeit wird nicht gemessen"))
        assertTrue(FamilyStatus.measuringText(true, false).startsWith("Schritte werden nicht gemessen"))
        assertTrue(FamilyStatus.measuringText(false, false).startsWith("Bildschirmzeit und Schritte werden nicht gemessen"))
    }

    @Test fun praiseButtonsCoverAllStickers() {
        // Eltern-App zeigt die Knöpfe in dieser Reihenfolge; jeder Sticker braucht Emoji und Text (Server: FamilyStore::STICKERS)
        assertEquals(listOf("star", "heart", "thumbs", "trophy", "rainbow"), FamilyPraise.order)
        assertEquals(FamilyPraise.order.toSet(), FamilyPraise.stickers.keys)
    }

    @Test fun offOnlyWhenReported() {
        assertFalse(FamilyStatus.measuringOff(null, null))
        assertFalse(FamilyStatus.measuringOff(true, true))
        assertTrue(FamilyStatus.measuringOff(false, true))
        assertTrue(FamilyStatus.measuringOff(true, false))
    }
}
