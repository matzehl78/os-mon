// ScreenTime.kt: Android-Bildschirmzeit via UsageStatsManager (minSdk 29)
//
// Manifest:
//   <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
//       tools:ignore="ProtectedPermissions"/>
//   <!-- Package-Sichtbarkeit (Android 11+): sonst schlägt die Kategorie-Abfrage fremder Apps fehl.
//        Der Launcher-Filter deckt die meisten nutzerrelevanten Apps ab, ohne QUERY_ALL_PACKAGES. -->
//   <queries>
//     <intent>
//       <action android:name="android.intent.action.MAIN"/>
//       <category android:name="android.intent.category.LAUNCHER"/>
//     </intent>
//   </queries>
//
// Der Nutzer muss "Nutzungszugriff" in den Systemeinstellungen manuell erteilen
// (hasAccess / accessSettingsIntent). Play-Store: Nutzung in der Policy-Deklaration begründen.

package com.example.osmon

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class UsageGroup { SOCIAL, ENTERTAINMENT, GAMES, PRODUCTIVITY, OTHER }

data class DayStats(
    val totalMin: Float,
    val nightMin: Float,                    // 00:00-06:00 und 22:30-24:00 des heutigen Tages
    val pickups: Int,
    val byGroup: Map<UsageGroup, Float>     // Minuten je Gruppe
)

object ScreenTime {

    // ---------- Berechtigung ----------

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Öffnet die Systemseite "Nutzungszugriff"; Nutzer aktiviert die App dort manuell. */
    fun accessSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    // ---------- Kategorie-Mapping ----------

    // Viele Apps (z. B. Messenger) deklarieren keine Kategorie: eigene Tabelle zuerst.
    // Nutzer-Overrides später ergänzen (z. B. in SharedPreferences).
    private val overrides = mapOf(
        "com.whatsapp" to UsageGroup.SOCIAL,
        "org.telegram.messenger" to UsageGroup.SOCIAL,
        "org.thoughtcrime.securesms" to UsageGroup.SOCIAL,
        "com.instagram.android" to UsageGroup.SOCIAL,
        "com.zhiliaoapp.musically" to UsageGroup.ENTERTAINMENT,
        "com.google.android.youtube" to UsageGroup.ENTERTAINMENT,
        "com.netflix.mediaclient" to UsageGroup.ENTERTAINMENT,
        "com.microsoft.office.outlook" to UsageGroup.PRODUCTIVITY
    )

    fun groupOf(ctx: Context, pkg: String): UsageGroup {
        overrides[pkg]?.let { return it }
        val category = try {
            ctx.packageManager.getApplicationInfo(pkg, 0).category
        } catch (e: Exception) {
            return UsageGroup.OTHER
        }
        return when (category) {
            ApplicationInfo.CATEGORY_SOCIAL -> UsageGroup.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO -> UsageGroup.ENTERTAINMENT
            ApplicationInfo.CATEGORY_GAME -> UsageGroup.GAMES
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> UsageGroup.PRODUCTIVITY
            else -> UsageGroup.OTHER
        }
    }

    // ---------- Tagesauswertung ----------

    fun dayStats(ctx: Context, now: Long = System.currentTimeMillis()): DayStats {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val hour = 3_600_000L
        val night1 = dayStart to dayStart + 6 * hour                 // 00:00-06:00
        val night2 = dayStart + 22 * hour + hour / 2 to dayStart + 24 * hour   // 22:30-24:00

        val events = usm.queryEvents(dayStart, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()                 // Paket -> Start der Vordergrundphase
        val groupOfPkg = HashMap<String, UsageGroup>()
        val perGroupMs = EnumMap<UsageGroup, Long>(UsageGroup::class.java)
        var nightMs = 0L
        var pickups = 0

        fun overlap(s: Long, t: Long, w: Pair<Long, Long>) = max(0L, min(t, w.second) - max(s, w.first))

        fun close(pkg: String, end: Long) {
            val start = open.remove(pkg) ?: return
            if (end <= start) return
            val g = groupOfPkg.getOrPut(pkg) { groupOf(ctx, pkg) }
            perGroupMs[g] = (perGroupMs[g] ?: 0L) + (end - start)
            nightMs += overlap(start, end, night1) + overlap(start, end, night2)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (pkg != ctx.packageName) open[pkg] = e.timeStamp   // eigene App ausklammern
                UsageEvents.Event.ACTIVITY_PAUSED -> close(pkg, e.timeStamp)
                UsageEvents.Event.SCREEN_INTERACTIVE -> pickups++
                // Bildschirm aus: alle offenen Phasen beenden (verhindert "Geister-Minuten")
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    open.keys.toList().forEach { close(it, e.timeStamp) }
            }
        }
        open.keys.toList().forEach { close(it, now) }      // noch laufende Nutzung

        val byGroup = UsageGroup.values().associateWith { (perGroupMs[it] ?: 0L) / 60_000f }
        return DayStats(
            totalMin = byGroup.values.sum(),
            nightMin = nightMs / 60_000f,
            pickups = pickups,
            byGroup = byGroup
        )
    }

    /**
     * Aktive Bildschirmzeit (Minuten, alle Apps außer OS-Mon) im Fenster [fromMs, toMs].
     * Für die Nachtnutzungs-Push ("aktiv seit 23:30"). Fragt 1 h vor dem Fenster an, damit Apps,
     * die schon vorher im Vordergrund waren, mitgezählt werden; gezählt wird nur der Überlapp.
     */
    fun activeMinutes(ctx: Context, fromMs: Long, toMs: Long = System.currentTimeMillis()): Float {
        if (toMs <= fromMs) return 0f
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(fromMs - 3_600_000L, toMs)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        var ms = 0L

        fun close(pkg: String, end: Long) {
            val start = open.remove(pkg) ?: return
            ms += max(0L, min(end, toMs) - max(start, fromMs))
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (pkg != ctx.packageName) open[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> close(pkg, e.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> open.keys.toList().forEach { close(it, e.timeStamp) }
            }
        }
        open.keys.toList().forEach { close(it, toMs) }
        return ms / 60_000f
    }

    // ---------- Spielwerte ----------

    /** Stress S (0-100), Formel aus dem PRD (Android mit Pickups). Ruhe R = 100 - S. */
    fun stress(s: DayStats, goalMin: Float = 180f, pickupRef: Float = 50f): Float {
        val x = 0.65f * s.totalMin / (2f * goalMin) +
                0.25f * s.nightMin / 45f +
                0.10f * s.pickups / (2f * pickupRef)
        return (100f * x).coerceIn(0f, 100f)
    }

    /**
     * Evolutions-Dominanz: Tagesanteile mit Halbwertszeit 5 Tage in einen laufenden Mittelwert
     * einblenden. Einmal pro Tag aufrufen (z. B. um Mitternacht oder beim ersten Sync des Tages).
     */
    fun blendShares(prev: Map<UsageGroup, Float>?, today: DayStats): Map<UsageGroup, Float> {
        val total = today.totalMin
        if (total <= 0f) return prev ?: UsageGroup.values().associateWith { 0f }
        val shares = today.byGroup.mapValues { it.value / total }
        if (prev == null) return shares
        val keep = 0.5f.pow(1f / 5f)                      // Gewicht der Vergangenheit pro Tag
        return UsageGroup.values().associateWith { keep * (prev[it] ?: 0f) + (1f - keep) * (shares[it] ?: 0f) }
    }
}

// Eingebunden über PetSync.tick (PetSync.kt): Tagesminuten, Nachtminuten und Pickups gehen in PetCore ein,
// die Ruhe-Formel steht nur noch in PetCore.stress (stress() hier ist eine Altlast, siehe README).
