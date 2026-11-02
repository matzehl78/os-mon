// PetFigureTest.kt: prüft PetFigureData.kt gegen figures.json (gleiche Quelle wie iOS)
//
// JVM-Test: Ablage in src/test/kotlin. Abhängigkeiten wie GoldenVectorsTest.kt
// (kotlin("test"), kotlinx-serialization-json). figures.json nach src/test/resources kopieren
// (z. B. im selben syncGolden-Task) oder Pfad per -Dfigures.json=/pfad/figures.json übergeben.
// Daten neu erzeugen: node gen_figures.mjs

package com.example.osmon.figure

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PetFigureTest {

    private fun load(): List<JsonObject> {
        val text = System.getProperty("figures.json")?.let { File(it).readText() }
            ?: javaClass.classLoader!!.getResource("figures.json")!!.readText()
        return Json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun JsonObject.obj(k: String) = this[k]!!.jsonObject
    private fun color(s: String) = (0xFF000000 or s.drop(1).toLong(16)).toInt()

    @Test
    fun catalogMatchesReference() {
        val ref = load()
        assertEquals(ref.map { it.str("id") }, ALL_FIGURES.map { it.id })
        assertEquals(ALL_FIGURES.size, ALL_FIGURES.map { it.id }.toSet().size, "IDs müssen eindeutig sein")
        ALL_FIGURES.zip(ref).forEach { (f, r) ->
            val c = r.obj("colors"); val s = r.obj("shape")
            assertEquals(r.str("name"), f.name)
            assertEquals(color(c.str("body")), f.colors.body, f.id)
            assertEquals(color(c.str("bodySick")), f.colors.bodySick, f.id)
            assertEquals(color(c.str("ant")), f.colors.ant, f.id)
            assertEquals(color(c.str("off")), f.colors.off, f.id)
            assertEquals(color(c.str("heart")), f.colors.heart, f.id)
            assertEquals(color(c.str("iris")), f.colors.iris, f.id)
            assertEquals(color(c.str("lid")), f.colors.lid, f.id)
            assertEquals(color(c.str("line")), f.colors.line, f.id)
            assertEquals(color(c.str("cheek")), f.colors.cheek, f.id)
            assertEquals(s.str("path"), f.shape.path, f.id)
            assertEquals(s.obj("face")["ey"]!!.jsonPrimitive.float, f.shape.face.ey, f.id)
            assertEquals(s.obj("face")["my"]!!.jsonPrimitive.float, f.shape.face.my, f.id)
            assertEquals(s["heart"]!!.jsonArray[2].jsonPrimitive.float, f.shape.heart.size, f.id)
            assertEquals(3, f.shape.ant.beads.size, "${f.id}: 3 Antennenkugeln (Ruhe 0–3)")
        }
    }

    @Test
    fun selectionFallsBackToDefault() {
        assertEquals(PetFigures.DEFAULT_ID, PetFigures.byId("gibt-es-nicht").id)
        assertEquals(PetFigures.DEFAULT_ID, PetFigures.byId(null).id)
        assertEquals("Glut", PetFigures.byId("glut").name)
        assertTrue(ALL_FIGURES.first().id == PetFigures.DEFAULT_ID)
    }
}
