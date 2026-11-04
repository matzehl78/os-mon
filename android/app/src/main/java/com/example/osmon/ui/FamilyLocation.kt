package com.example.osmon.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.osmon.family.*
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FamilyLocation {
    var sharing by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("Keine Hintergrundortung. Nur der letzte Standort wird geteilt.")
    fun allowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun measure(ctx: Context): Location = withContext(Dispatchers.Main.immediate) {
        check(allowed(ctx)) { "Standortzugriff nicht erlaubt." }
        val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else error("Standortdienste nicht verfügbar.")
        withTimeout(30000) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Standortdienst deaktiviert."))
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                continuation.invokeOnCancellation { manager.removeUpdates(callback) }
                try { manager.requestSingleUpdate(provider, callback, Looper.getMainLooper()) }
                catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        }
    }
    suspend fun send(ctx: Context, c: FamilyConnection, requestId: String? = null) {
        if (busy) return
        busy = true
        try {
            val location = measure(ctx)
            check(location.hasAccuracy() && location.accuracy >= 0 && kotlin.math.abs(System.currentTimeMillis() - location.time) <= 120000) { "Kein aktueller Standort verfügbar." }
            val body = JSONObject().put("latitude", location.latitude).put("longitude", location.longitude).put("accuracy", location.accuracy.toDouble()).put("measured", location.time / 1000.0)
            if (requestId != null) body.put("requestId", requestId)
            FamilyApi(c).call("POST", "/v1/device/location", body)
            message = "Standort mit Elternzugang geteilt. Keine laufende Ortung."
        } catch (e: TimeoutCancellationException) {
            message = "Kein Standort innerhalb von 30 Sekunden verfügbar."
            if (requestId != null) runCatching { FamilyApi(c).call("POST", "/v1/device/location-result", JSONObject().put("requestId", requestId).put("state", "unavailable")) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            message = e.message ?: "Standort nicht verfügbar."
            if (requestId != null) runCatching { FamilyApi(c).call("POST", "/v1/device/location-result", JSONObject().put("requestId", requestId).put("state", if (allowed(ctx)) "unavailable" else "permissionDenied")) }
        } finally { busy = false }
    }
    suspend fun handle(ctx: Context, c: FamilyConnection, device: JSONObject) {
        sharing = device.optBoolean("locationSharing", false)
        val request = device.optJSONObject("locationRequest") ?: return
        if (sharing && request.optString("state") == "pending" && request.optDouble("expires") > System.currentTimeMillis()/1000.0) send(ctx, c, request.getString("id"))
    }
}

@Composable
fun FamilyLocationCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    fun run(action: suspend (FamilyConnection) -> Unit) {
        if (working || FamilyLocation.busy) return
        working = true; scope.launch {
            try { action(FamilyVault.load(ctx) ?: error("Kein Familienzugang.")) }
            catch (e: Exception) { FamilyLocation.message = e.message ?: "Verbindung fehlgeschlagen." }
            finally { working = false }
        }
    }
    fun enable() = run { c ->
        check(FamilyLocation.allowed(ctx)) { "Standortberechtigung fehlt." }
        FamilyApi(c).call("PUT", "/v1/device/location-sharing", JSONObject().put("enabled",true))
        FamilyLocation.sharing = true; FamilyLocation.message = "Eltern dürfen bei geöffneter OS-Mon-App Standorte anfragen."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) enable() else FamilyLocation.message = "Standortzugriff nicht erlaubt. Nichts geteilt."
    }
    LaunchedEffect(Unit) { run { c -> FamilyLocation.sharing = FamilyApi(c).call("GET", "/v1/device").optBoolean("locationSharing", false) } }
    Column {
        Text("Standortfreigabe", style = MaterialTheme.typography.titleMedium)
        Text("Nach einmaliger Freigabe darf der verbundene Elternzugang Messungen anfragen, solange OS-Mon geöffnet ist. Keine Hintergrundortung.")
        Text(if (FamilyLocation.sharing) "Standortanfragen: freigegeben" else "Standortanfragen: aus")
        Button(enabled = !working && !FamilyLocation.busy, onClick = {
            if (FamilyLocation.sharing) run { c -> FamilyApi(c).call("PUT", "/v1/device/location-sharing", JSONObject().put("enabled",false)); FamilyLocation.sharing = false; FamilyLocation.message = "Freigabe deaktiviert und Serverstandort gelöscht." }
            else if (FamilyLocation.allowed(ctx)) enable()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }) { Text(if (FamilyLocation.sharing) "Standortanfragen deaktivieren" else "Standortanfragen erlauben") }
        OutlinedButton(enabled = !working && !FamilyLocation.busy, onClick = { run { c -> FamilyLocation.send(ctx, c) } }) { Text("Aktuellen Standort einmal teilen") }
        TextButton(enabled = !working && !FamilyLocation.busy, onClick = { run { c -> FamilyApi(c).call("DELETE", "/v1/device/location"); FamilyLocation.message = "Geteilten Standort gelöscht." } }) { Text("Geteilten Standort löschen") }
        Text(FamilyLocation.message)
    }
}
