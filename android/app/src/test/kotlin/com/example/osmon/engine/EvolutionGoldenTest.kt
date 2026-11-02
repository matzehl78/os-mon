// EvolutionGoldenTest.kt: prüft EvolutionTracker.kt gegen golden_evolution.json
//
// JVM-Test (liest eine Datei), Einrichtung wie bei GoldenVectorsTest.kt:
//   golden_evolution.json nach src/test/resources/ kopieren (Gradle-Sync-Task analog) oder
//   -Dgolden.evolution=/pfad/zu/golden_evolution.json setzen.
// Erzeugen: python3 golden_evolution.py

package com.example.osmon.engine

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class EvolutionGoldenTest {

    private val root: JsonObject by lazy {
        val text = EvolutionGoldenTest::class.java.classLoader?.getResource("golden_evolution.json")?.readText()
            ?: System.getProperty("golden.evolution")?.let { File(it).readText() }
            ?: error("golden_evolution.json nicht gefunden: nach src/test/resources kopieren oder -Dgolden.evolution=<Pfad> setzen")
        Json.parseToJsonElement(text).jsonObject
    }

    // ---------- JSON -> Typen ----------

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this
    private fun JsonObject.float(k: String): Float? = this[k].orNull()?.jsonPrimitive?.float
    private fun JsonObject.long(k: String): Long? = this[k].orNull()?.jsonPrimitive?.long
    private fun JsonObject.str(k: String): String? = this[k].orNull()?.jsonPrimitive?.content
    private fun JsonObject.obj(k: String): JsonObject? = this[k].orNull()?.jsonObject

    private fun JsonObject.toConfig() = Config(
        screenGoalMin = float("screenGoalMin")!!, stepGoal = this["stepGoal"]!!.jsonPrimitive.int,
        pickupRef = float("pickupRef")!!
    )

    private fun JsonObject.toShares(): Map<AppGroup, Float> =
        entries.associate { AppGroup.valueOf(it.key.uppercase()) to it.value.jsonPrimitive.float }

    private fun JsonObject.toRecord() = DayRecord(
        epochDay = long("epochDay")!!,
        shares = obj("shares")?.toShares(),
        totalMinutes = float("totalMinutes"),
        avgWellbeing = float("avgWellbeing")
    )

    private fun JsonObject.toEvolution() = Evolution(
        form = Form.valueOf(str("form")!!.uppercase()),
        hybrid = str("hybrid")?.let { Form.valueOf(it.uppercase()) },
        branch = Branch.valueOf(str("branch")!!.uppercase())
    )

    private fun JsonObject.toEvent() = EvoEvent(
        kind = EvoKind.valueOf(str("kind")!!.uppercase()),
        from = str("from")?.let { Form.valueOf(it.uppercase()) },
        to = Form.valueOf(str("to")!!.uppercase()),
        epochDay = long("epochDay")!!
    )

    // ---------- Vergleich (alle Abweichungen sammeln) ----------

    private val items = mutableListOf<String>()

    private fun <T> same(label: String, what: String, got: T, want: T) {
        if (got != want) items += "$label / $what: erhalten $got, erwartet $want"
    }

    private fun compare(label: String, s: EvoState, e: JsonObject, tol: Float) {
        same(label, "current", s.current, e.obj("current")?.toEvolution())
        same(label, "lastEvolutionDay", s.lastEvolutionDay, e.long("lastEvolutionDay"))
        same(label, "challenger", s.challenger, e.str("challenger")?.let { Form.valueOf(it.uppercase()) })
        same(label, "challengerSince", s.challengerSince, e.long("challengerSince"))
        same(label, "pendingEvent", s.pendingEvent, e.obj("pendingEvent")?.toEvent())
        same(label, "historySize", s.history.size, e["historySize"]!!.jsonPrimitive.int)

        val want = e.obj("blended")?.toShares()
        val got = s.blended
        if ((want == null) != (got == null)) {
            items += "$label / blended: erhalten $got, erwartet $want"
        } else if (want != null && got != null) {
            for (g in AppGroup.values()) {
                val a = got[g] ?: 0f
                val b = want[g] ?: 0f
                if (abs(a - b) > tol) items += "$label / blended[$g]: erhalten $a, erwartet $b"
            }
        }
    }

    // ---------- Test ----------

    @Test fun scenariosMatch() {
        val tol = root.getValue("tolerance").jsonPrimitive.float
        val scenarios = root.getValue("scenarios").jsonArray.map { it.jsonObject }
        assertTrue(scenarios.isNotEmpty(), "keine Szenarien in der Vektordatei")

        for (sc in scenarios) {
            val cfg = sc.obj("config")!!.toConfig()
            var state = EvoState(hatchDay = sc.long("hatchDay")!!)
            val steps = sc.getValue("steps").jsonArray.map { it.jsonObject }
            steps.forEachIndexed { k, st ->
                val record = st.obj("record")!!.toRecord()
                state = EvolutionTracker.closeDay(state, record, cfg)
                compare("${sc.str("name")} [Tag ${record.epochDay}, Schritt $k]", state, st.obj("expect")!!, tol)
                if (st.getValue("ack").jsonPrimitive.boolean) state = EvolutionTracker.acknowledge(state)
            }
        }
        assertTrue(items.isEmpty(), "${items.size} Abweichung(en)\n" + items.take(40).joinToString("\n"))
    }

    @Test fun ignoresAlreadyClosedDays() {
        val cfg = Config()
        val shares = mapOf(AppGroup.SOCIAL to 1f)
        val s0 = EvoState(hatchDay = 0)
        val s1 = EvolutionTracker.closeDay(s0, DayRecord(5, shares, 100f, 70f), cfg)
        assertEquals(s1, EvolutionTracker.closeDay(s1, DayRecord(5, shares, 200f, 10f), cfg))
        assertEquals(s1, EvolutionTracker.closeDay(s1, DayRecord(3, shares, 200f, 10f), cfg))
    }
}
