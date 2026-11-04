package com.example.osmon.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.osmon.family.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@Suppress("DEPRECATION", "MissingPermission")
@Composable
fun FamilyLocationCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var listener by remember { mutableStateOf<LocationListener?>(null) }
    var message by remember { mutableStateOf("Kein automatisches Tracking. Standort wird spätestens nach 24 Stunden gelöscht.") }
    var busy by remember { mutableStateOf(false) }
    fun stop() { listener?.let { manager.removeUpdates(it) }; listener = null; handler.removeCallbacksAndMessages(null) }
    fun request() {
        if (busy) return
        busy = true
        try {
            val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            check(fine || coarse) { "Standortzugriff wurde nicht erlaubt." }
            val provider = if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else error("Standortdienste nicht verfügbar.")
            val callback = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (listener !== this) return
                    stop()
                    if (!location.hasAccuracy() || location.accuracy < 0 || kotlin.math.abs(System.currentTimeMillis() - location.time) > 120000) { busy = false; message = "Kein aktueller Standort verfügbar."; return }
                    scope.launch {
                        try {
                            val c = FamilyVault.load(ctx) ?: error("Kein Familienzugang.")
                            FamilyApi(c).call("POST", "/v1/device/location", JSONObject().put("latitude", location.latitude).put("longitude", location.longitude).put("accuracy", location.accuracy.toDouble()).put("measured", location.time / 1000.0))
                            message = "Standort mit verbundenem Elternzugang geteilt. Keine laufende Ortung."
                        } catch (e: Exception) { message = e.message ?: "Teilen fehlgeschlagen." } finally { busy = false }
                    }
                }
                override fun onProviderDisabled(provider: String) { stop(); busy = false; message = "Standortdienst deaktiviert." }
                override fun onProviderEnabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            listener = callback
            manager.requestSingleUpdate(provider, callback, Looper.getMainLooper())
            handler.postDelayed({ stop(); busy = false; message = "Kein Standort innerhalb von 30 Sekunden verfügbar." }, 30000)
        } catch (e: Exception) { stop(); busy = false; message = e.message ?: "Standort nicht verfügbar." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) request() else message = "Standortzugriff nicht erlaubt. Es wurde nichts geteilt."
    }
    DisposableEffect(Unit) { onDispose { stop() } }
    Column {
        Text("Standort teilen", style = MaterialTheme.typography.titleMedium)
        Text("Nur nach diesem Tippen wird der aktuelle Standort an den verbundenen Elternzugang gesendet. Sichtbar in Eltern-App und Webseite.")
        Button(enabled = !busy, onClick = {
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) request()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }) { Text("Aktuellen Standort einmal teilen") }
        TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
            try { val c = FamilyVault.load(ctx) ?: error("Kein Familienzugang."); FamilyApi(c).call("DELETE", "/v1/device/location"); message = "Geteilter Standort vom Server gelöscht." }
            catch (e: Exception) { message = "Löschen fehlgeschlagen: " + e.message } finally { busy = false }
        } }) { Text("Geteilten Standort löschen") }
        Text(message)
    }
}
