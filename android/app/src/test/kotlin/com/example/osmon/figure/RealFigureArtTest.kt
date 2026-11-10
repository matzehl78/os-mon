// RealFigureArtTest.kt: gleiche Fälle wie RealFigureArtTests.swift, dazu Prüfung, dass alle 66 Bilder vorhanden sind.

package com.example.osmon.figure

import com.example.osmon.engine.GrowthStage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealFigureArtTest {
    @Test fun stages() {
        assertEquals(RealArtStage.EGG, RealFigureArt.stageFor(GrowthStage.EGG))
        assertEquals(RealArtStage.HATCHLING, RealFigureArt.stageFor(GrowthStage.BABY, hatchedDay = 100, today = 100))
        assertEquals(RealArtStage.BABY, RealFigureArt.stageFor(GrowthStage.BABY, hatchedDay = 100, today = 101))
        assertEquals(RealArtStage.BABY, RealFigureArt.stageFor(GrowthStage.BABY))
        assertEquals(RealArtStage.CHILD, RealFigureArt.stageFor(GrowthStage.CHILD))
        assertEquals(RealArtStage.TEEN, RealFigureArt.stageFor(GrowthStage.TEEN))
        assertEquals(RealArtStage.ADULT, RealFigureArt.stageFor(GrowthStage.ADULT, hatchedDay = 1, today = 1))
    }

    @Test fun names() {
        assertEquals("real_sunni_hatchling", RealFigureArt.resourceName("sunni", RealArtStage.HATCHLING))
        assertEquals("real_lumo_adult", RealFigureArt.resourceName("unbekannt", RealArtStage.ADULT))
        assertEquals(11, RealFigureArt.FIGURE_IDS.size)
    }

    @Test fun allImagesExist() {
        val dir = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi").map(::File).first { it.isDirectory }
        val missing = RealFigureArt.FIGURE_IDS.flatMap { f -> RealArtStage.entries.map { RealFigureArt.resourceName(f, it) } }
            .filterNot { File(dir, "$it.webp").isFile }
        assertTrue(missing.isEmpty(), "Fehlende Bilder: $missing")
    }
}

class PetStyleTest {
    @Test fun resolve() {
        assertEquals(PetStyle.REAL, PetStyle.resolve("real", true))
        assertEquals(PetStyle.FLAT, PetStyle.resolve("2d", true))
        assertEquals(PetStyle.THREE_D, PetStyle.resolve(null, null))          // Standard wie bisher: 3D an
        assertEquals(PetStyle.FLAT, PetStyle.resolve("", false))              // frühere Einstellung „3D aus“
        assertEquals(PetStyle.THREE_D, PetStyle.resolve("kaputt", true))
    }
}
