// PushGoldenTest.kt: prüft PushPolicy.kt gegen golden_push.json (Parität mit Swift und Python)
//
// JVM-Test, Einrichtung wie bei GoldenVectorsTest.kt:
//   golden_push.json nach src/test/resources/ kopieren (Gradle-Sync-Task analog) oder
//   -Dgolden.push=/pfad/zu/golden_push.json setzen.
// Erzeugen: python3 golden_push.py

package com.example.osmon.engine

import java.io.File
import kotlin.test.Test
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

class PushGoldenTest {

    private val root: JsonObject by lazy {
        val text = PushGoldenTest::class.java.classLoader?.getResource("golden_push.json")?.readText()
            ?: System.getProperty("golden.push")?.let { File(it).readText() }
            ?: error("golden_push.json nicht gefunden: nach src/test/resources kopieren oder -Dgolden.push=<Pfad> setzen")
        Json.parseToJsonElement(text).jsonObject
    }

    // ---------- JSON -> Typen ----------

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this
    private fun JsonObject.float(k: String): Float? = this[k].orNull()?.jsonPrimitive?.float
    private fun JsonObject.int(k: String): Int? = this[k].orNull()?.jsonPrimitive?.int
    private fun JsonObject.long(k: String): Long? = this[k].orNull()?.jsonPrimitive?.long
    private fun JsonObject.str(k: String): String? = this[k].orNull()?.jsonPrimitive?.content
    private fun JsonObject.obj(k: String): JsonObject? = this[k].orNull()?.jsonObject

    private fun JsonObject.toSignals() = PushSignals(
        batteryPct = float("batteryPct"),
        charging = getValue("charging").jsonPrimitive.boolean,
        freeStorageRatio = float("freeStorageRatio"),
        screenMinutes = float("screenMinutes"),
        screenGoalMin = float("screenGoalMin")!!,
        nightActiveMinutes = float("nightActiveMinutes"),
        steps = int("steps"),
        stepGoal = int("stepGoal")!!
    )

    private fun JsonObject.toClock() = PushClock(long("nowMs")!!, int("minuteOfDay")!!, str("dayKey")!!)

    private fun JsonObject.toState() = PushState(
        dayKey = str("dayKey")!!,
        sentToday = int("sentToday")!!,
        lastSentMs = obj("lastSentMs")!!.entries.associate { PushType.valueOf(it.key) to it.value.jsonPrimitive.long },
        lastAnyMs = long("lastAnyMs"),
        ignoredStreak = int("ignoredStreak")!!
    )

    private fun JsonObject.toMessage() = PushMessage(
        type = PushType.valueOf(str("type")!!),
        title = str("title")!!,
        body = str("body")!!,
        actions = getValue("actions").jsonArray.map { it.jsonPrimitive.content }
    )

    // ---------- Test ----------

    @Test fun scenariosMatch() {
        val scenarios = root.getValue("scenarios").jsonArray.map { it.jsonObject }
        assertTrue(scenarios.isNotEmpty(), "keine Abläufe in der Vektordatei")
        val diffs = mutableListOf<String>()
        var evaluated = 0

        for (sc in scenarios) {
            var state = sc.obj("initial")!!.toState()
            sc.getValue("steps").jsonArray.map { it.jsonObject }.forEachIndexed { k, st ->
                val label = "${sc.str("name")} [Schritt $k, ${st.str("op")}]"
                when (st.str("op")) {
                    "evaluate" -> {
                        evaluated++
                        val got = PushPolicy.evaluate(state, st.obj("signals")!!.toSignals(), st.obj("clock")!!.toClock())
                        val want = st.obj("expectMessage")?.toMessage()
                        if (got != want) diffs += "$label / message: erhalten $got, erwartet $want"
                    }
                    "sent" -> state = PushPolicy.onSent(state, PushType.valueOf(st.str("type")!!), st.obj("clock")!!.toClock())
                    "ignored" -> state = PushPolicy.onIgnored(state)
                    "engaged" -> state = PushPolicy.onEngaged(state)
                    else -> diffs += "$label: unbekannte Operation"
                }
                val wantState = st.obj("expectState")!!.toState()
                if (state != wantState) diffs += "$label / state: erhalten $state, erwartet $wantState"
                val cap = PushPolicy.dailyCap(state)
                if (cap != st.int("expectCap")) diffs += "$label / cap: erhalten $cap, erwartet ${st.int("expectCap")}"
            }
        }
        assertTrue(evaluated > 0, "keine evaluate-Schritte")
        assertTrue(diffs.isEmpty(), "${diffs.size} Abweichung(en)\n" + diffs.take(40).joinToString("\n"))
    }
}
