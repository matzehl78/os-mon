// GoldenVectorsTest.kt: prüft PetCore.kt gegen golden_vectors.json (Parität mit Swift und Python)
//
// JVM-Test (liest eine Datei): Ablage in src/test/kotlin (Android-/JVM-Modul) bzw. jvmTest (KMP).
//
// Gradle (Versionen prüfen):
//   dependencies {
//       testImplementation(kotlin("test"))
//       testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
//   }
//   // Es genügt die Laufzeitbibliothek, das serialization-Compiler-Plugin wird NICHT benötigt:
//   // geparst wird nur über JsonElement.
//
// Vektoren bereitstellen (eine der beiden Möglichkeiten):
//   a) golden_vectors.json nach src/test/resources/ kopieren, z. B. per Gradle-Task:
//        tasks.register<Copy>("syncGolden") { from("../golden_vectors.json"); into("src/test/resources") }
//        tasks.named("processTestResources") { dependsOn("syncGolden") }
//   b) Pfad per Systemproperty übergeben:  -Dgolden.vectors=/pfad/zu/golden_vectors.json
//
// Vektoren neu erzeugen: python3 golden_reference.py

package com.example.osmon.engine

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoldenVectorsTest {

    // ---------- Laden ----------

    private val root: JsonObject by lazy {
        val text = GoldenVectorsTest::class.java.classLoader?.getResource("golden_vectors.json")?.readText()
            ?: System.getProperty("golden.vectors")?.let { File(it).readText() }
            ?: error(
                "golden_vectors.json nicht gefunden: nach src/test/resources kopieren " +
                    "oder -Dgolden.vectors=<Pfad> setzen"
            )
        Json.parseToJsonElement(text).jsonObject
    }

    private val tol: Float get() = root.getValue("tolerance").jsonPrimitive.float

    private fun cases(section: String): JsonArray = root.getValue(section).jsonArray

    // ---------- JSON -> Engine-Typen ----------

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this

    private fun JsonObject.float(key: String): Float? = this[key].orNull()?.jsonPrimitive?.float
    private fun JsonObject.int(key: String): Int? = this[key].orNull()?.jsonPrimitive?.int
    private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean
    private fun JsonObject.str(key: String): String? = this[key].orNull()?.jsonPrimitive?.content
    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.toInputs() = Inputs(
        batteryPct = float("batteryPct"),
        charging = bool("charging"),
        lowPower = bool("lowPower"),
        freeStorageRatio = float("freeStorageRatio"),
        screenMinutes = float("screenMinutes"),
        nightMinutes = float("nightMinutes"),
        pickups = int("pickups"),
        steps = int("steps"),
        pats = int("pats")!!,
        focusSessions = int("focusSessions")!!
    )

    private fun JsonObject.toConfig() = Config(
        screenGoalMin = float("screenGoalMin")!!,
        stepGoal = int("stepGoal")!!,
        pickupRef = float("pickupRef")!!
    )

    private fun JsonObject.toStats() = PetStats(float("e"), float("r"), float("f"), float("v"), float("a"))

    private fun JsonObject.optStats(key: String): PetStats? = this[key].orNull()?.jsonObject?.toStats()

    private fun String.toMood() = Mood.valueOf(uppercase())
    private fun String.toOverlay() = Overlay.valueOf(uppercase())
    private fun String.toForm() = Form.valueOf(uppercase())
    private fun String.toBranch() = Branch.valueOf(uppercase())

    // ---------- Abweichungen sammeln (alle Fälle in einem Lauf melden) ----------

    private class Diff {
        val items = mutableListOf<String>()

        fun num(name: String, label: String, got: Float?, want: Float?, tol: Float) {
            val ok = when {
                got == null && want == null -> true
                got != null && want != null -> !got.isNaN() && abs(got - want) <= tol
                else -> false
            }
            if (!ok) items += "$name / $label: erhalten $got, erwartet $want"
        }

        fun stats(name: String, got: PetStats, want: PetStats, tol: Float) {
            num(name, "e", got.e, want.e, tol)
            num(name, "r", got.r, want.r, tol)
            num(name, "f", got.f, want.f, tol)
            num(name, "v", got.v, want.v, tol)
            num(name, "a", got.a, want.a, tol)
        }

        fun <T> same(name: String, label: String, got: T, want: T) {
            if (got != want) items += "$name / $label: erhalten $got, erwartet $want"
        }

        fun assertClean(section: String, count: Int) {
            assertTrue(count > 0, "$section: keine Fälle in der Vektordatei")
            assertTrue(items.isEmpty(), "$section: ${items.size} Abweichung(en) in $count Fällen\n" +
                items.joinToString("\n"))
        }
    }

    // ---------- Tests ----------

    @Test fun vectorFileHasExpectedVersion() {
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
    }

    @Test fun targetsMatch() {
        val d = Diff()
        for (c in cases("targets").map { it.jsonObject }) {
            val name = c.str("name")!!
            val got = PetCore.targets(c.obj("inputs").toInputs(), c.obj("config").toConfig())
            d.stats(name, got, c.obj("expect").toStats(), tol)
        }
        d.assertClean("targets", cases("targets").size)
    }

    @Test fun stepsMatch() {
        val d = Diff()
        for (c in cases("step").map { it.jsonObject }) {
            val name = c.str("name")!!
            val got = PetCore.step(c.optStats("prev"), c.obj("target").toStats(), c.getValue("dtHours").jsonPrimitive.float)
            d.stats(name, got, c.obj("expect").toStats(), tol)
        }
        d.assertClean("step", cases("step").size)
    }

    @Test fun wellbeingMatches() {
        val d = Diff()
        for (c in cases("wellbeing").map { it.jsonObject }) {
            val name = c.str("name")!!
            d.num(name, "H", PetCore.wellbeing(c.obj("stats").toStats()), c.float("expect"), tol)
        }
        d.assertClean("wellbeing", cases("wellbeing").size)
    }

    @Test fun moodHysteresisMatches() {
        val d = Diff()
        for (c in cases("mood").map { it.jsonObject }) {
            val name = c.str("name")!!
            val got = PetCore.mood(c.float("h")!!, c.str("prev")?.toMood())
            d.same(name, "mood", got, c.str("expect")!!.toMood())
        }
        d.assertClean("mood", cases("mood").size)
    }

    @Test fun overlaysMatch() {
        val d = Diff()
        for (c in cases("overlays").map { it.jsonObject }) {
            val name = c.str("name")!!
            val want = c.getValue("expect").jsonArray.map { it.jsonPrimitive.content.toOverlay() }.toSet()
            d.same(name, "overlays", PetCore.overlays(c.obj("stats").toStats()), want)
        }
        d.assertClean("overlays", cases("overlays").size)
    }

    @Test fun sequencesMatch() {
        val d = Diff()
        for (seq in cases("sequences").map { it.jsonObject }) {
            val cfg = seq.obj("config").toConfig()
            var prev: Snapshot? = null
            seq.getValue("steps").jsonArray.map { it.jsonObject }.forEachIndexed { k, s ->
                val label = "${seq.str("name")} [Schritt $k]"
                val snap = PetCore.tick(prev, s.obj("inputs").toInputs(), cfg, s.getValue("dtHours").jsonPrimitive.float)
                val want = s.obj("expect")
                d.stats(label, snap.stats, want.obj("stats").toStats(), tol)
                d.num(label, "H", snap.wellbeing, want.float("wellbeing"), tol)
                d.same(label, "mood", snap.mood, want.str("mood")!!.toMood())
                d.same(
                    label, "overlays", snap.overlays,
                    want.getValue("overlays").jsonArray.map { it.jsonPrimitive.content.toOverlay() }.toSet()
                )
                prev = snap
            }
        }
        d.assertClean("sequences", cases("sequences").size)
    }

    @Test fun evolutionMatches() {
        val d = Diff()
        for (c in cases("evolve").map { it.jsonObject }) {
            val name = c.str("name")!!
            val shares = c.obj("shares").entries.associate {
                AppGroup.valueOf(it.key.uppercase()) to it.value.jsonPrimitive.float
            }
            val got = PetCore.evolve(
                shares = shares,
                avgDailyMinutes = c.float("avgDailyMinutes")!!,
                avgWellbeing14 = c.float("avgWellbeing14")!!,
                c = c.obj("config").toConfig(),
                outdoorShare = c.float("outdoorShare")!!
            )
            val want = c.obj("expect")
            d.same(name, "form", got.form, want.str("form")!!.toForm())
            d.same(name, "hybrid", got.hybrid, want.str("hybrid")?.toForm())
            d.same(name, "branch", got.branch, want.str("branch")!!.toBranch())
        }
        d.assertClean("evolve", cases("evolve").size)
    }
}
