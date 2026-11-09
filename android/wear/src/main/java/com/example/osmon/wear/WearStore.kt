// WearStore.kt: Uhr-Seite der Verbindung. Hält das letzte Paket vom Handy (auch gespeichert, damit Complication
// und Neustart es haben) und schickt Aktionen ans Handy. Abhaken wirkt sofort; das Handy bestätigt mit neuem Stand.

package com.example.osmon.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.example.osmon.engine.DayTask
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.TaskStage
import com.example.osmon.wearsync.WearPayload
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object WearStore {
    private const val PREFS = "wear"
    private const val KEY = "payload"
    private const val KEY_RESET = "resetAt"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _payload = MutableStateFlow<WearPayload?>(null)
    val payload: StateFlow<WearPayload?> get() = _payload
    val cheer = MutableStateFlow<String?>(null)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): WearPayload? = WearPayload.fromJson(prefs(ctx).getString(KEY, null))

    fun init(ctx: Context) {
        if (_payload.value == null) _payload.value = load(ctx)
        // Beim Start den aktuellen Stand aus dem Data Layer holen (falls verpasst)
        scope.launch {
            runCatching {
                val buf = Wearable.getDataClient(ctx).dataItems.await()
                try {
                    // Erst ein verpasstes Löschen anwenden, dann den Stand (der nach dem Löschen neu sein kann)
                    buf.firstOrNull { it.uri.path == WearPayload.PATH_RESET }?.let {
                        reset(ctx, DataMapItem.fromDataItem(it).dataMap.getLong(WearPayload.KEY_AT))
                    }
                    buf.firstOrNull { it.uri.path == WearPayload.PATH_STATE }?.let {
                        DataMapItem.fromDataItem(it).dataMap.getString(WearPayload.KEY_JSON)
                    }
                } finally { buf.release() }
            }.getOrNull()?.let { apply(ctx, it) }
            send(ctx, WearPayload.ACTION_UPDATE)
        }
    }

    fun apply(ctx: Context, json: String) {
        val p = WearPayload.fromJson(json) ?: return
        val cur = _payload.value
        if (cur != null && cur.updatedAtMs > p.updatedAtMs) return
        if (p.updatedAtMs <= prefs(ctx).getLong(KEY_RESET, 0L)) return        // Stand von vor dem Löschen
        save(ctx, p)
    }

    /** Handy hat „Alle Daten löschen“ ausgeführt (Zeitpunkt at): gespeicherten Stand verwerfen, Complication leeren. */
    fun reset(ctx: Context, at: Long) {
        val pr = prefs(ctx)
        if (at <= pr.getLong(KEY_RESET, 0L)) return
        pr.edit().putLong(KEY_RESET, at).apply()
        val cur = _payload.value ?: load(ctx)
        if (cur != null && cur.updatedAtMs > at) return                       // schon neuer Stand nach dem Löschen
        _payload.value = null
        cheer.value = null
        pr.edit().remove(KEY).apply()
        runCatching {
            ComplicationDataSourceUpdateRequester.create(ctx.applicationContext,
                ComponentName(ctx.applicationContext, PetComplicationService::class.java)).requestUpdateAll()
        }
    }

    private fun save(ctx: Context, p: WearPayload) {
        _payload.value = p
        prefs(ctx).edit().putString(KEY, p.toJson()).apply()
        runCatching {
            ComplicationDataSourceUpdateRequester.create(ctx.applicationContext,
                ComponentName(ctx.applicationContext, PetComplicationService::class.java)).requestUpdateAll()
        }
    }

    // ---------- Aktionen ----------

    fun toggle(ctx: Context, t: DayTask) {
        val p = _payload.value ?: return
        val nowDone = !DayTasks.isDone(t, p.day)
        val next = p.copy(tasks = p.tasks.map { if (it.id == t.id) DayTasks.toggle(it, p.day) else it })
        cheer.value = if (!nowDone) null else if (next.progress.stage == TaskStage.ALL) "Alles geschafft! 🎉" else "Super, erledigt!"
        save(ctx, next)
        send(ctx, "${WearPayload.ACTION_TOGGLE}:${t.id}")
    }

    fun kindDone(ctx: Context) {
        val p = _payload.value ?: return
        if (p.kindDone) return
        save(ctx, p.copy(kindDone = true))
        send(ctx, WearPayload.ACTION_KIND)
    }

    private fun send(ctx: Context, action: String) {
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
                nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, WearPayload.PATH_ACTION, action.toByteArray()).await() }
            }
        }
    }
}

/** Neuer Stand vom Handy (Manifest: DATA_CHANGED /osmon/state). */
class WearDataListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearPayload.PATH_STATE) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString(WearPayload.KEY_JSON)?.let { WearStore.apply(applicationContext, it) }
            }
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearPayload.PATH_RESET) {
                WearStore.reset(applicationContext, DataMapItem.fromDataItem(e.dataItem).dataMap.getLong(WearPayload.KEY_AT))
            }
        }
    }
}
