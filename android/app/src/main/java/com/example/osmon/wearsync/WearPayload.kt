// WearPayload.kt: Was das Handy der Wear-OS-Uhr schickt (Data Layer, DataItem) und welche Aktionen die Uhr
// zurückmeldet (MessageClient). Gemeinsam genutzt von app und wear (wear/build.gradle.kts bindet den Ordner ein).
// Gegenstück zu WatchPayload.swift (Apple Watch). Format: JSON (org.json, auf beiden Seiten vorhanden).

package com.example.osmon.wearsync

import com.example.osmon.engine.DayTask
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.PetStats
import com.example.osmon.engine.Snapshot
import com.example.osmon.engine.TaskProgress
import com.example.osmon.engine.TaskRepeat
import org.json.JSONArray
import org.json.JSONObject

data class WearPayload(
    val snapshot: Snapshot,
    val updatedAtMs: Long,
    val figureId: String,
    val petName: String,
    val accessory: String?,          // Accessory.name (Fest-Zubehör)
    val form: String?,               // Form.name (Entwicklungsstufe)
    val day: Long,                   // epochDay, für den die Aufgaben gelten
    val tasks: List<DayTask>,
    val motivation: String,
    val kindTip: String?,
    val kindCategory: String?,
    val kindDone: Boolean
) {
    val progress: TaskProgress get() = DayTasks.progress(tasks, day)

    fun toJson(): String = JSONObject().apply {
        val s = snapshot
        put("stats", JSONObject().apply {
            s.stats.e?.let { put("e", it.toDouble()) }; s.stats.r?.let { put("r", it.toDouble()) }
            s.stats.f?.let { put("f", it.toDouble()) }; s.stats.v?.let { put("v", it.toDouble()) }
        })
        put("wellbeing", s.wellbeing.toDouble()); put("mood", s.mood.name)
        put("overlays", JSONArray(s.overlays.map { it.name }))
        put("updatedAt", updatedAtMs); put("figure", figureId); put("pet", petName)
        accessory?.let { put("accessory", it) }; form?.let { put("form", it) }
        put("day", day)
        put("tasks", JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().put("id", t.id).put("title", t.title).put("emoji", t.emoji).put("repeat", t.repeat.name)
                    .put("created", t.createdDay).put("done", JSONArray(t.doneDays.sorted())))
            }
        })
        put("motivation", motivation)
        kindTip?.let { put("kindTip", it) }; kindCategory?.let { put("kindCategory", it) }
        put("kindDone", kindDone)
    }.toString()

    companion object {
        const val PATH_STATE = "/osmon/state"          // DataItem: aktueller Stand
        const val PATH_ACTION = "/osmon/action"        // Message: Aktion der Uhr
        const val KEY_JSON = "json"
        const val ACTION_TOGGLE = "toggleTask"         // Nachricht: "toggleTask:<id>"
        const val ACTION_KIND = "kindDone"
        const val ACTION_UPDATE = "update"

        fun fromJson(json: String?): WearPayload? = json?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

        private fun parse(o: JSONObject): WearPayload {
            val st = o.getJSONObject("stats")
            fun f(k: String): Float? = if (st.has(k)) st.getDouble(k).toFloat() else null
            val ov = o.optJSONArray("overlays")
            val overlays = (0 until (ov?.length() ?: 0)).mapNotNull { i -> runCatching { Overlay.valueOf(ov!!.getString(i)) }.getOrNull() }.toSet()
            val snap = Snapshot(PetStats(e = f("e"), r = f("r"), f = f("f"), v = f("v")),
                o.getDouble("wellbeing").toFloat(), Mood.valueOf(o.getString("mood")), overlays)
            val ta = o.getJSONArray("tasks")
            val tasks = (0 until ta.length()).map { ta.getJSONObject(it) }.map { t ->
                val d = t.getJSONArray("done")
                DayTask(t.getString("id"), t.getString("title"), t.optString("emoji", "⭐"),
                    runCatching { TaskRepeat.valueOf(t.getString("repeat")) }.getOrDefault(TaskRepeat.ONCE),
                    t.getLong("created"), (0 until d.length()).map { d.getLong(it) }.toSet())
            }
            return WearPayload(
                snap, o.getLong("updatedAt"), o.getString("figure"), o.getString("pet"),
                o.optString("accessory").ifEmpty { null }, o.optString("form").ifEmpty { null },
                o.getLong("day"), tasks, o.optString("motivation"),
                o.optString("kindTip").ifEmpty { null }, o.optString("kindCategory").ifEmpty { null },
                o.optBoolean("kindDone"))
        }
    }
}
