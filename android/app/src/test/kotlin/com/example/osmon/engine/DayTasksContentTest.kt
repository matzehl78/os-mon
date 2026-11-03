// DayTasksContentTest.kt: prüft res/raw/tasks.json (gleiche Datei wie iOS): Vorschläge, Motivation, Miteinander-Tipps.

package com.example.osmon.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DayTasksContentTest {
    private val root = Json.parseToJsonElement(File("src/main/res/raw/tasks.json").readText()).jsonObject

    @Test fun suggestions() {
        val groups = root["suggestions"]!!.jsonArray.map { it.jsonObject }
        assertTrue(groups.size >= 4)
        groups.flatMap { it["items"]!!.jsonArray }.map { it.jsonObject }.forEach {
            assertTrue(it["emoji"]!!.jsonPrimitive.content.isNotBlank())
            assertTrue(it["title"]!!.jsonPrimitive.content.length in 3..32, "Titel zu lang: ${it["title"]}")
        }
    }

    @Test fun motivationForEveryStage() {
        val m = root["motivation"]!!.jsonObject
        TaskStage.entries.forEach { assertTrue(m[it.name.lowercase()]!!.jsonArray.size >= 3, it.name) }
        assertTrue(root["done"]!!.jsonArray.size >= 3)
    }

    @Test fun kindnessTips() {
        val cats = root["kindness"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cats.size >= 5)
        assertTrue(cats.map { it["id"]!!.jsonPrimitive.content }.toSet().size == cats.size)
        cats.forEach { c ->
            val tips = c["tips"]!!.jsonArray.map { it.jsonObject }
            assertTrue(tips.size >= 4, c["id"].toString())
            tips.forEach {
                assertTrue(it["tip"]!!.jsonPrimitive.content.length >= 20)
                assertTrue(it["task"]!!.jsonPrimitive.content.length in 3..32, "Aufgabe zu lang: ${it["task"]}")
            }
        }
    }
}
