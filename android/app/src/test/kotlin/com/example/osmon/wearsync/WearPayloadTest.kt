// WearPayloadTest.kt: Handy → Uhr und zurück verliert nichts (JSON-Rundreise).

package com.example.osmon.wearsync

import com.example.osmon.engine.DayTask
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.PetStats
import com.example.osmon.engine.Snapshot
import com.example.osmon.engine.TaskRepeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearPayloadTest {
    private val day = 20759L

    @Test fun roundTrip() {
        val p = WearPayload(
            Snapshot(PetStats(e = 90f, r = 85f, f = null, v = 70f), 86f, Mood.RADIANT, setOf(Overlay.YAWNING)),
            1_700_000_000_000L, "lila", "Lila", "SANTA_HAT", "CHATTERLING", day,
            listOf(DayTask("a", "Zähne putzen", "🪥", TaskRepeat.DAILY, day - 3, setOf(day, day - 1)),
                DayTask("b", "Oma „anrufen“", "📞", TaskRepeat.ONCE, day)),
            "Halbzeit!", "Halte die Tür auf.", "🚌 Unterwegs", true)
        val back = WearPayload.fromJson(p.toJson())
        assertEquals(p, back)
        assertEquals(1, back!!.progress.done)
    }

    @Test fun optionalFieldsAndGarbage() {
        val p = WearPayload(Snapshot(PetStats(), 40f, Mood.TIRED, emptySet()), 1L, "lumo", "Lumo", null, null, day,
            emptyList(), "", null, null, false)
        assertEquals(p, WearPayload.fromJson(p.toJson()))
        assertNull(WearPayload.fromJson("kein json"))
        assertNull(WearPayload.fromJson(null))
    }
}
