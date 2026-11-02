// PushDelivery.kt: Zustellung der Haustier-Benachrichtigungen (Android 10+, minSdk 29)
//
// Aufgerufen vom PetWorker (alle 15 Min) direkt nach PetSync.tick: Signale bauen, PushPolicy fragen,
// höchstens eine Meldung zeigen, Zustand speichern. Reaktionen (Tipp, Aktionen, Wegwischen) fließen über
// PushActionReceiver bzw. handleOpen zurück in den Ignorier-Zähler der PushPolicy.
//
// Einrichtung:
//   Manifest:
//     <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>      (Android 13+, Onboarding fragt)
//     <receiver android:name=".PushActionReceiver" android:exported="false"/>
//   Ressource: res/drawable/ic_stat_osmon.xml (einfarbiges Vektor-Icon für die Statusleiste)
//   Launcher-Activity (onCreate und onNewIntent):
//     PushDelivery.handleOpen(this, intent)
//
// Reaktionen:
//   Tipp auf die Meldung, "Später"        -> engagiert (Ignorier-Zähler zurück)
//   "Fokus 15 Min"                        -> engagiert + Fokus-Session starten
//   Wegwischen / "Alle löschen"           -> ignoriert (deleteIntent)
//   nach 2 h unbeachtet noch sichtbar     -> ignoriert und entfernt (sweepUnattended)

package com.example.osmon

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.osmon.engine.PushClock
import com.example.osmon.engine.PushMessage
import com.example.osmon.engine.PushPolicy
import com.example.osmon.engine.PushSignals
import com.example.osmon.engine.PushState
import com.example.osmon.engine.PushType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

// ---------- Speicher ----------

object PushStore {
    private const val KEY = "state"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun load(ctx: Context): PushState {
        val o = sp(ctx).getString(KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return PushState()
        val last = o.optJSONObject("lastSentMs")
        return PushState(
            dayKey = o.optString("dayKey", ""),
            sentToday = o.optInt("sentToday", 0),
            lastSentMs = last?.keys()?.asSequence()
                ?.mapNotNull { k -> runCatching { PushType.valueOf(k) to last.getLong(k) }.getOrNull() }
                ?.toMap() ?: emptyMap(),
            lastAnyMs = if (o.has("lastAnyMs")) o.getLong("lastAnyMs") else null,
            ignoredStreak = o.optInt("ignoredStreak", 0)
        )
    }

    fun save(ctx: Context, s: PushState) {
        val o = JSONObject()
            .put("dayKey", s.dayKey)
            .put("sentToday", s.sentToday)
            .put("lastSentMs", JSONObject().apply { s.lastSentMs.forEach { (t, ms) -> put(t.name, ms) } })
            .put("ignoredStreak", s.ignoredStreak)
        s.lastAnyMs?.let { o.put("lastAnyMs", it) }
        sp(ctx).edit().putString(KEY, o.toString()).apply()
    }

    @Synchronized
    fun update(ctx: Context, change: (PushState) -> PushState) = save(ctx, change(load(ctx)))

    // Offene (noch sichtbare) Meldungen: Typ -> Zustellzeit, für die 2-h-Regel.
    fun markPending(ctx: Context, type: PushType, ms: Long) = sp(ctx).edit().putLong("pending.${type.name}", ms).apply()
    fun clearPending(ctx: Context, type: PushType) = sp(ctx).edit().remove("pending.${type.name}").apply()
    fun pending(ctx: Context): Map<PushType, Long> = PushType.values()
        .mapNotNull { t -> sp(ctx).getLong("pending.${t.name}", 0L).takeIf { it > 0L }?.let { t to it } }
        .toMap()
}

// ---------- Zustellung ----------

object PushDelivery {
    const val CHANNEL_ID = "osmon_pet"
    const val EXTRA_TYPE = "com.example.osmon.push.TYPE"
    const val ACTION_FOCUS = "com.example.osmon.push.FOCUS"
    const val ACTION_LATER = "com.example.osmon.push.LATER"
    const val ACTION_DISMISSED = "com.example.osmon.push.DISMISSED"
    private const val UNATTENDED_MS = 2 * 3_600_000L

    fun notificationId(t: PushType) = 4100 + t.ordinal

    fun clock(nowMs: Long): PushClock {
        val z = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        return PushClock(nowMs, z.hour * 60 + z.minute, z.toLocalDate().toString())
    }

    private fun inNightWindow(minuteOfDay: Int) = minuteOfDay >= 23 * 60 + 30 || minuteOfDay < 3 * 60

    /** Beginn des Nachtfensters (23:30, bei Uhrzeiten nach Mitternacht der Vortag). */
    private fun nightStartMs(nowMs: Long): Long {
        val z = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        val day = if (z.hour * 60 + z.minute >= 23 * 60 + 30) z.toLocalDate() else z.toLocalDate().minusDays(1)
        return day.atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun signals(ctx: Context, t: PetTick, c: PushClock): PushSignals {
        val night = if (t.screenUsable && inNightWindow(c.minuteOfDay)) {
            runCatching { ScreenTime.activeMinutes(ctx, nightStartMs(c.nowMs), c.nowMs) }.getOrNull()
        } else null
        return PushSignals(
            batteryPct = t.inputs.batteryPct,
            charging = t.inputs.charging,
            freeStorageRatio = t.inputs.freeStorageRatio,
            screenMinutes = t.inputs.screenMinutes,
            screenGoalMin = PetSync.config(ctx).screenGoalMin,
            nightActiveMinutes = night,
            steps = t.inputs.steps,
            stepGoal = PetSync.config(ctx).stepGoal
        )
    }

    /** Schalter "Meldungen vom Wesen" in den Einstellungen (Opt-out, Standard: an). */
    fun optedOut(ctx: Context) = PetSync.prefs(ctx).getBoolean("push.optOut", false)

    fun setOptedOut(ctx: Context, value: Boolean) = PetSync.prefs(ctx).edit().putBoolean("push.optOut", value).apply()

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Ein Durchlauf nach dem Tick: Unbeachtetes auswerten, Regeln prüfen, höchstens eine Meldung zeigen. */
    @Synchronized
    fun run(ctx: Context, t: PetTick, nowMs: Long = t.atMs): PushType? {
        if (!canPost(ctx) || optedOut(ctx)) return null
        sweepUnattended(ctx, nowMs)
        val c = clock(nowMs)
        val msg = PushPolicy.evaluate(PushStore.load(ctx), signals(ctx, t, c), c) ?: return null
        show(ctx, msg)
        PushStore.update(ctx) { PushPolicy.onSent(it, msg.type, c) }
        PushStore.markPending(ctx, msg.type, nowMs)
        return msg.type
    }

    /** Sichtbare Meldungen älter als 2 h: entfernen und als ignoriert zählen. Verschwundene austragen. */
    fun sweepUnattended(ctx: Context, nowMs: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val active = nm.activeNotifications.map { it.id }.toSet()
        for ((type, sentMs) in PushStore.pending(ctx)) {
            val id = notificationId(type)
            when {
                id !in active -> PushStore.clearPending(ctx, type)          // schon beantwortet oder weggewischt
                nowMs - sentMs > UNATTENDED_MS -> {
                    nm.cancel(id)                                            // cancel() löst keinen deleteIntent aus
                    PushStore.clearPending(ctx, type)
                    PushStore.update(ctx) { PushPolicy.onIgnored(it) }
                }
            }
        }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Dein Wesen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Höchstens drei Meldungen am Tag, nie in der Nachtruhe (außer bei nächtlicher Nutzung)."
            }
        )
    }

    private fun broadcast(ctx: Context, action: String, type: PushType): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, notificationId(type) * 10 + action.hashCode().mod(10),
            Intent(ctx, PushActionReceiver::class.java).setAction(action).putExtra(EXTRA_TYPE, type.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun show(ctx: Context, msg: PushMessage) {
        ensureChannel(ctx)
        // Tipp öffnet die App direkt (kein Trampolin über einen Receiver, das blockiert Android 12+).
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?.putExtra(EXTRA_TYPE, msg.type.name)
        val content = open?.let {
            PendingIntent.getActivity(ctx, notificationId(msg.type), it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_osmon)
            .setContentTitle(msg.title)
            .setContentText(msg.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(content)
            .setDeleteIntent(broadcast(ctx, ACTION_DISMISSED, msg.type))
            .addAction(0, msg.actions.getOrElse(0) { "Fokus 15 Min" }, broadcast(ctx, ACTION_FOCUS, msg.type))
            .addAction(0, msg.actions.getOrElse(1) { "Später" }, broadcast(ctx, ACTION_LATER, msg.type))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notificationId(msg.type), n)
        } catch (e: SecurityException) {
            // Erlaubnis zwischen canPost und notify entzogen: still ignorieren.
        }
    }

    /** Aus der Launcher-Activity aufrufen (onCreate/onNewIntent): Tipp auf eine Meldung = engagiert. */
    fun handleOpen(ctx: Context, intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE)?.let { runCatching { PushType.valueOf(it) }.getOrNull() } ?: return
        intent.removeExtra(EXTRA_TYPE)                                   // nicht bei Rotation erneut zählen
        PushStore.clearPending(ctx, type)
        PushStore.update(ctx) { PushPolicy.onEngaged(it) }
    }
}

// ---------- Reaktionen ----------

class PushActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val type = intent.getStringExtra(PushDelivery.EXTRA_TYPE)?.let { runCatching { PushType.valueOf(it) }.getOrNull() }
            ?: return
        PushStore.clearPending(ctx, type)
        when (intent.action) {
            PushDelivery.ACTION_DISMISSED -> PushStore.update(ctx) { PushPolicy.onIgnored(it) }
            PushDelivery.ACTION_LATER -> {
                PushStore.update(ctx) { PushPolicy.onEngaged(it) }
                NotificationManagerCompat.from(ctx).cancel(PushDelivery.notificationId(type))
            }
            PushDelivery.ACTION_FOCUS -> {
                PushStore.update(ctx) { PushPolicy.onEngaged(it) }
                NotificationManagerCompat.from(ctx).cancel(PushDelivery.notificationId(type))
                PetSync.startFocus(ctx, 15)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PetSync.updateWidgetIfNeeded(ctx, PetSync.tick(ctx).snapshot)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
