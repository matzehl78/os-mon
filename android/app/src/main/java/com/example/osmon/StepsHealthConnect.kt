// StepsHealthConnect.kt: Schritte via Health Connect (minSdk 29)
// Google-Fit-APIs werden abgelöst: neu nur Health Connect verwenden.
//
// Gradle:
//   implementation("androidx.health.connect:connect-client:1.1.0")   // aktuelle stabile Version prüfen
//
// Manifest:
//   <uses-permission android:name="android.permission.health.READ_STEPS"/>
//   <uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"/>
//   <queries><package android:name="com.google.android.apps.healthdata"/></queries>  <!-- Android 9-13 -->
//
//   Pflicht für die Berechtigungsanfrage: Datenschutz-Erklärung (Rationale) deklarieren:
//   <activity android:name=".PrivacyPolicyActivity" android:exported="true">
//     <intent-filter><action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"/></intent-filter>
//   </activity>
//   <activity-alias android:name="ViewPermissionUsageActivity" android:exported="true"
//       android:targetActivity=".PrivacyPolicyActivity"
//       android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
//     <intent-filter>
//       <action android:name="android.intent.action.VIEW_PERMISSION_USAGE"/>
//       <category android:name="android.intent.category.HEALTH_PERMISSIONS"/>
//     </intent-filter>
//   </activity-alias>
//
// Berechtigungsanfrage (Activity/Compose):
//   val launcher = registerForActivityResult(
//       PermissionController.createRequestPermissionResultContract()) { granted -> /* Set<String> */ }
//   launcher.launch(Steps.permissions)
//
// Play Store: Health-Connect-Berechtigungen in der Policy-Deklaration begründen.

package com.example.osmon

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

object Steps {

    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)

    /** Lesezugriff auf Schritte + Lesen im Hintergrund (für den Worker). */
    val permissions = setOf(stepsPermission, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)

    /** Android 14+: Health Connect ist Teil des Systems. Davor eigene App, evtl. installieren/aktualisieren. */
    fun client(ctx: Context): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE)
            HealthConnectClient.getOrCreate(ctx) else null

    suspend fun hasPermission(client: HealthConnectClient): Boolean =
        client.permissionController.getGrantedPermissions().contains(stepsPermission)

    /**
     * Heutige Schritte. Aggregate dedupliziert überlappende Quellen (Handy + Uhr) nach den
     * Prioritäten der Health-Connect-Einstellungen.
     * Einschränkung: Manuell eingetragene Schritte lassen sich per Aggregate nicht ausschließen
     * (anders als in der iOS-Variante).
     */
    suspend fun today(client: HealthConnectClient): Long {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
            )
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    /**
     * Changes-API: prüft billig, ob seit dem letzten Aufruf überhaupt neue Schritt-Daten vorliegen.
     * Spart die Aggregation, wenn nichts passiert ist.
     */
    suspend fun hasNewData(client: HealthConnectClient, ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
        var token = sp.getString("hc_token", null)
        if (token == null) {
            sp.edit().putString("hc_token", client.getChangesToken(ChangesTokenRequest(setOf(StepsRecord::class)))).apply()
            return true                                       // erster Lauf: einmal lesen
        }
        var changed = false
        do {
            val resp = client.getChanges(token!!)
            if (resp.changesTokenExpired) {                   // Token abgelaufen: neu starten
                sp.edit().remove("hc_token").apply()
                return true
            }
            if (resp.changes.isNotEmpty()) changed = true
            token = resp.nextChangesToken
            sp.edit().putString("hc_token", token).apply()
        } while (resp.hasMore)
        return changed
    }
}

// ---------- Speicher & Spielwert ----------

object StepsPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
    private fun key() = "count.${LocalDate.now()}"          // pro Tag, Reset geschieht von selbst

    fun today(ctx: Context): Long = sp(ctx).getLong(key(), 0L)
    fun setToday(ctx: Context, v: Long) = sp(ctx).edit().putLong(key(), v).apply()

    /** Vitalität V (0-100) laut PRD: 100 * clamp(Schritte / Ziel)^0,7 */
    fun vitality(ctx: Context, goal: Int = 7000): Float {
        val ratio = (today(ctx).toFloat() / goal).coerceIn(0f, 1f)
        return 100f * ratio.pow(0.7f)
    }
}

// ---------- Aufruf aus dem PetWorker (liefert true, wenn das Widget neu gezeichnet werden soll) ----------

suspend fun refreshSteps(ctx: Context): Boolean {
    return try {
        val client = Steps.client(ctx) ?: return false
        if (!Steps.hasPermission(client)) return false         // Degradiert: V wird ausgeblendet
        if (!Steps.hasNewData(client, ctx)) return false

        val old = StepsPrefs.today(ctx)
        val n = Steps.today(client)
        if (n < old) return false                              // Schritte steigen am Tag nur
        StepsPrefs.setToday(ctx, n)
        n - old >= 500                                         // Reload-Budget schonen
    } catch (e: Exception) {
        false                                                  // z. B. SecurityException ohne Hintergrund-Recht
    }
}

// Eingebunden in PetWorker.doWork() (OSMonWidget.kt): refreshSteps() vor PetSync.tick().
