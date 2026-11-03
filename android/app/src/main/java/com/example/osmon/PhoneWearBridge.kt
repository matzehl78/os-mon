// PhoneWearBridge.kt: Handy-Seite der Wear-OS-Verbindung (Wearable Data Layer).
//
// Nach jedem Update (HomeViewModel.publish, Aufgabe geändert) wird der aktuelle Stand als DataItem
// /osmon/state veröffentlicht; die Uhr bekommt ihn auch, wenn sie gerade nicht verbunden war.
// Aktionen der Uhr (Aufgabe abhaken, Miteinander-Tipp ausprobiert) kommen als Nachricht /osmon/action an
// (PhoneWearListener) und lösen danach ein neues DataItem aus.
// Ohne Google-Play-Dienste oder ohne Uhr passiert einfach nichts.

package com.example.osmon

import android.content.Context
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.Form
import com.example.osmon.engine.Snapshot
import com.example.osmon.figure.PetFigures
import com.example.osmon.ui.TaskContent
import com.example.osmon.ui.TaskEvents
import com.example.osmon.ui.TaskStore
import com.example.osmon.wearsync.WearPayload
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

object PhoneWearBridge {
    private const val K_ACC = "wear.accessory"
    private const val K_FORM = "wear.form"

    /** Stand an die Uhr. Mit updateExtras werden Fest-Zubehör und Entwicklungsstufe gemerkt (für spätere Aufrufe ohne). */
    fun push(ctx: Context, snapshot: Snapshot? = null, accessory: Accessory? = null, form: Form? = null, updateExtras: Boolean = false) {
        val app = ctx.applicationContext
        val prefs = PetSync.prefs(app)
        if (updateExtras) prefs.edit().putString(K_ACC, accessory?.name).putString(K_FORM, form?.name).apply()
        val p = payload(app, snapshot, prefs.getString(K_ACC, null), prefs.getString(K_FORM, null)) ?: return
        runCatching {
            val req = PutDataMapRequest.create(WearPayload.PATH_STATE).apply { dataMap.putString(WearPayload.KEY_JSON, p.toJson()) }
            Wearable.getDataClient(app).putDataItem(req.asPutDataRequest().setUrgent())
        }
    }

    fun payload(ctx: Context, snapshot: Snapshot?, accessory: String?, form: String?): WearPayload? {
        val snap = snapshot ?: PetSync.lastSnapshot(ctx)?.first ?: return null
        val day = TaskStore.today()
        val tasks = TaskStore.load(ctx)
        val content = TaskContent.get(ctx)
        val fig = PetFigures.current(ctx)
        val tip = content.tipOfTheDay(day)
        return WearPayload(
            snap, System.currentTimeMillis(), fig.id, fig.name, accessory, form, day,
            DayTasks.forDay(tasks, day), content.motivation(DayTasks.progress(tasks, day), day, fig.name),
            tip?.second?.tip, tip?.first?.let { "${it.emoji} ${it.title}" }, TaskStore.kindDone(ctx, day),
            com.example.osmon.ui.GrowthStore.stage(ctx).name)
    }

    /** Aktion der Uhr ausführen. */
    fun handle(ctx: Context, action: String) {
        val day = TaskStore.today()
        when {
            action.startsWith(WearPayload.ACTION_TOGGLE + ":") -> {
                val id = action.substringAfter(":")
                TaskStore.save(ctx, TaskStore.load(ctx).map { if (it.id == id) DayTasks.toggle(it, day) else it })
                TaskEvents.changed()
            }
            action == WearPayload.ACTION_KIND -> { TaskStore.markKind(ctx, day); TaskEvents.changed() }
        }
        push(ctx)
    }
}

/** Empfängt Nachrichten der Uhr (auch wenn die App nicht offen ist). Manifest: intent-filter MESSAGE_RECEIVED. */
class PhoneWearListener : WearableListenerService() {
    override fun onMessageReceived(e: MessageEvent) {
        if (e.path != WearPayload.PATH_ACTION) return
        PhoneWearBridge.handle(applicationContext, String(e.data, Charsets.UTF_8))
    }
}
