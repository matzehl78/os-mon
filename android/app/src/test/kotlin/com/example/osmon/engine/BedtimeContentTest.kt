// BedtimeContentTest.kt: prüft res/raw/bedtime.json (gleiche Datei wie iOS) und die Lied-Dateien.

package com.example.osmon.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BedtimeContentTest {
    private val raw = File("src/main/res/raw")
    private val root = Json.parseToJsonElement(File(raw, "bedtime.json").readText()).jsonObject

    @Test fun storiesComplete() {
        val stories = root["stories"]!!.jsonArray.map { it.jsonObject }
        assertTrue(stories.size >= 8)
        val figures = setOf("lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki")
        stories.forEach {
            assertTrue(it["title"]!!.jsonPrimitive.content.isNotBlank())
            assertTrue(it["figure"]!!.jsonPrimitive.content in figures, "Figur unbekannt: ${it["figure"]}")
            assertTrue(it["text"]!!.jsonPrimitive.content.split(" ").size in 80..400, "Länge: ${it["id"]}")
        }
        assertTrue(stories.map { it["id"]!!.jsonPrimitive.content }.toSet().size == stories.size)
    }

    @Test fun songsHaveAudio() {
        val songs = root["songs"]!!.jsonArray.map { it.jsonObject }
        assertTrue(songs.isNotEmpty())
        songs.forEach {
            val f = File(raw, it["audio"]!!.jsonPrimitive.content + ".m4a")
            assertTrue(f.exists() && f.length() > 50_000, "Audio fehlt: $f")
            assertTrue(it["lyrics"]!!.jsonPrimitive.content.isNotBlank())
        }
    }

    @Test fun textLists() {
        listOf("goodnight", "sleep", "tips").forEach { assertTrue(root[it]!!.jsonArray.size >= 3, it) }
        val m = root["morning"]!!.jsonObject
        listOf("great", "ok", "restless").forEach { assertTrue(m[it]!!.jsonArray.isNotEmpty(), it) }
    }
}
