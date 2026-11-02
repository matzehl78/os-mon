// PetCoreTest.kt: Unit-Tests (commonTest bzw. src/test)
// Gradle: testImplementation(kotlin("test"))

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetCoreTest {
    private val c = Config()          // Ziel 180 min, 7000 Schritte, Pickups-Ref 50
    private val eps = 0.1f

    // ---------- Einzelwerte ----------

    @Test fun energyFollowsBattery() {
        assertEquals(50f, PetCore.energy(Inputs(batteryPct = 50f))!!, eps)
        assertEquals(42.5f, PetCore.energy(Inputs(batteryPct = 50f, lowPower = true))!!, eps)
        assertEquals(60f, PetCore.energy(Inputs(batteryPct = 50f, charging = true))!!, eps)
        assertEquals(100f, PetCore.energy(Inputs(batteryPct = 95f, charging = true))!!, eps)  // geklemmt
        assertNull(PetCore.energy(Inputs()))
    }

    @Test fun fitnessFromFreeStorage() {
        assertEquals(100f, PetCore.fitness(Inputs(freeStorageRatio = 0.30f))!!, eps)
        assertEquals(100f, PetCore.fitness(Inputs(freeStorageRatio = 0.60f))!!, eps)
        assertEquals(0f, PetCore.fitness(Inputs(freeStorageRatio = 0.05f))!!, eps)
        assertEquals(57.4f, PetCore.fitness(Inputs(freeStorageRatio = 0.175f))!!, eps)
    }

    @Test fun stressWithoutPickups() {          // iOS: 0,72 / 0,28
        val i = Inputs(screenMinutes = 360f, nightMinutes = 0f)
        assertEquals(72f, PetCore.stress(i, c)!!, eps)
        assertEquals(28f, PetCore.rest(i, c)!!, eps)
        val j = Inputs(screenMinutes = 180f, nightMinutes = 45f)
        assertEquals(64f, PetCore.stress(j, c)!!, eps)
    }

    @Test fun stressWithPickups() {             // Android: 0,65 / 0,25 / 0,10
        val i = Inputs(screenMinutes = 360f, nightMinutes = 0f, pickups = 0)
        assertEquals(65f, PetCore.stress(i, c)!!, eps)
        val max = Inputs(screenMinutes = 2000f, nightMinutes = 500f, pickups = 1000)
        assertEquals(100f, PetCore.stress(max, c)!!, eps)                                // geklemmt
    }

    @Test fun vitalityCurve() {
        assertEquals(100f, PetCore.vitality(Inputs(steps = 7000), c)!!, eps)
        assertEquals(100f, PetCore.vitality(Inputs(steps = 20000), c)!!, eps)
        assertEquals(61.6f, PetCore.vitality(Inputs(steps = 3500), c)!!, eps)
        assertEquals(0f, PetCore.vitality(Inputs(steps = 0), c)!!, eps)
    }

    @Test fun affectionMixesRestAndInteraction() {
        // R=100, 1 Fokus-Session (50) + 2 Streicheln (50) -> Interaktion 100 -> A = 70 + 30
        assertEquals(100f, PetCore.affection(100f, Inputs(pats = 2, focusSessions = 1)), eps)
        assertEquals(70f, PetCore.affection(100f, Inputs()), eps)
        assertEquals(25f, PetCore.affection(null, Inputs(pats = 1)), eps)
    }

    // ---------- Gesamtwert ----------

    @Test fun wellbeingAllPerfect() {
        assertEquals(100f, PetCore.wellbeing(PetStats(100f, 100f, 100f, 100f, 100f)), eps)
    }

    @Test fun weakestLinkPenalty() {
        // E=0, Rest 100: H_roh = 70, Faktor 0,6 -> 42
        assertEquals(42f, PetCore.wellbeing(PetStats(e = 0f, r = 100f, f = 100f, v = 100f, a = 100f)), eps)
    }

    @Test fun missingSensorsRenormalizeWeights() {
        // Nur E=50: H_roh = 50, Faktor 0,6 + 0,4*0,5 = 0,8 -> 40
        assertEquals(40f, PetCore.wellbeing(PetStats(e = 50f)), eps)
        assertEquals(50f, PetCore.wellbeing(PetStats()), eps)
    }

    // ---------- Trägheit ----------

    @Test fun smoothingMovesTowardTarget() {
        val s = PetCore.step(PetStats(e = 100f), PetStats(e = 0f), 0.25f)
        assertEquals(60.7f, s.e!!, eps)                       // 100 * exp(-0,25/0,5)
        assertEquals(0f, PetCore.step(null, PetStats(e = 0f), 1f).e!!, eps)   // ohne Vorwert: direkt
        assertNull(PetCore.step(PetStats(e = 50f), PetStats(), 1f).e)         // Sensor weg: ausgeblendet
        assertEquals(50f, PetCore.step(PetStats(e = 50f), PetStats(e = 0f), -1f).e!!, eps)   // dt < 0 ignoriert
    }

    // ---------- Zustände ----------

    @Test fun moodInitialClassification() {
        assertEquals(Mood.RADIANT, PetCore.mood(90f, null))
        assertEquals(Mood.CONTENT, PetCore.mood(70f, null))
        assertEquals(Mood.TIRED, PetCore.mood(50f, null))
        assertEquals(Mood.SICK, PetCore.mood(25f, null))
        assertEquals(Mood.HIBERNATION, PetCore.mood(5f, null))
    }

    @Test fun moodHysteresis() {
        assertEquals(Mood.CONTENT, PetCore.mood(86f, Mood.CONTENT))    // erst ab 90 aufwärts
        assertEquals(Mood.RADIANT, PetCore.mood(90f, Mood.CONTENT))
        assertEquals(Mood.RADIANT, PetCore.mood(82f, Mood.RADIANT))    // erst unter 80 abwärts
        assertEquals(Mood.CONTENT, PetCore.mood(79f, Mood.RADIANT))
    }

    @Test fun overlaysTriggerAtThresholds() {
        val o = PetCore.overlays(PetStats(e = 10f, r = 20f, f = 10f, v = 10f))
        assertEquals(setOf(Overlay.YAWNING, Overlay.CHUBBY, Overlay.STRESSED, Overlay.SLUGGISH), o)
        assertTrue(PetCore.overlays(PetStats(e = 80f, r = 80f, f = 80f, v = 80f)).isEmpty())
        assertTrue(PetCore.overlays(PetStats()).isEmpty())            // fehlende Werte lösen nichts aus
    }

    @Test fun tickRunsFullPipeline() {
        val i = Inputs(batteryPct = 100f, freeStorageRatio = 0.4f, screenMinutes = 60f, steps = 7000)
        val first = PetCore.tick(null, i, c, 0.25f)
        assertEquals(Mood.RADIANT, first.mood)
        // Akku bricht ein: Energie sinkt träge, nicht sprunghaft
        val second = PetCore.tick(first, i.copy(batteryPct = 10f), c, 0.25f)
        assertTrue(second.stats.e!! < first.stats.e!!)
        assertTrue(second.stats.e!! > 10f)
    }

    // ---------- Evolution ----------

    @Test fun evolutionPicksDominantCategory() {
        val shares = mapOf(AppGroup.SOCIAL to 0.5f, AppGroup.ENTERTAINMENT to 0.1f, AppGroup.OTHER to 0.4f)
        val e = PetCore.evolve(shares, 200f, 70f, c)
        assertEquals(Form.CHATTERLING, e.form)
        assertNull(e.hybrid)
        assertEquals(Branch.RADIANT, e.branch)
    }

    @Test fun evolutionHybridWhenClose() {
        val shares = mapOf(AppGroup.SOCIAL to 0.40f, AppGroup.ENTERTAINMENT to 0.37f, AppGroup.OTHER to 0.23f)
        val e = PetCore.evolve(shares, 200f, 50f, c)
        assertEquals(Form.CHATTERLING, e.form)
        assertEquals(Form.BINGEON, e.hybrid)
        assertEquals(Branch.STANDARD, e.branch)
    }

    @Test fun evolutionBalancedBecomesWandero() {
        val shares = mapOf(
            AppGroup.SOCIAL to 0.25f, AppGroup.ENTERTAINMENT to 0.25f,
            AppGroup.GAMES to 0.2f, AppGroup.PRODUCTIVITY to 0.2f, AppGroup.OTHER to 0.1f
        )
        assertEquals(Form.WANDERO, PetCore.evolve(shares, 150f, 30f, c).form)
        assertEquals(Branch.WILD, PetCore.evolve(shares, 150f, 30f, c).branch)
        // zu viel Bildschirmzeit: kein Balance-Fall -> nächstliegende Form
        assertEquals(Form.CHATTERLING, PetCore.evolve(shares, 400f, 50f, c).form)
    }
}
