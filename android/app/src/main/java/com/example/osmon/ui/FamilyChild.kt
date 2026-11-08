package com.example.osmon.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.osmon.PhoneWearBridge
import com.example.osmon.engine.DayTask
import com.example.osmon.engine.DayTasks
import com.example.osmon.engine.TaskRepeat
import com.example.osmon.family.*
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

/** Lob von den Eltern ist angekommen: (Sticker-ID, Zeitpunkt); die Startseite lässt das Wesen sich freuen. */
object PraiseEvents { val latest = mutableStateOf<Pair<String, Long>?>(null) }

object FamilyChild {
    private const val PRAISE_KEY = "pet.praise.last"   // zuletzt gezeigtes Lob (Datei „pet“, wird mit „Alle Daten löschen“ geleert)
    private val mutex = Mutex()
    suspend fun sync(ctx: Context) {
        if (!mutex.tryLock()) return
        try {
            val c = FamilyVault.load(ctx) ?: return
            val api = FamilyApi(c); val device = api.call("GET", "/v1/device")
            if (!device.getBoolean("paired")) return
            FamilyLocation.handle(ctx, c, device)
            // Lob von den Eltern: einmal zeigen (Startseite: Wesen freut sich), dann als gesehen melden
            device.optJSONObject("praise")?.let { p ->
                val id = p.optString("id"); val prefs = com.example.osmon.PetSync.prefs(ctx)
                if (id.isNotEmpty() && !p.optBoolean("seen", true) && prefs.getString(PRAISE_KEY, null) != id) {
                    prefs.edit().putString(PRAISE_KEY, id).apply()
                    PraiseEvents.latest.value = p.optString("sticker") to System.currentTimeMillis()
                    runCatching { api.call("POST", "/v1/device/praise-seen", JSONObject().put("id", id)) }
                }
            }
            val config = device.getJSONObject("config"); val a = config.getJSONArray("tasks")
            val old = TaskStore.load(ctx); val local = old.filterNot { it.id.startsWith("family:") }
            check(local.size + a.length() <= DayTasks.MAX_TASKS) { "Lokal und aus der Eltern-App zusammen sind höchstens 12 Aufgaben möglich." }
            val remote = (0 until a.length()).map { i ->
                val t = a.getJSONObject(i); val id = "family:" + t.getString("id")
                val previous = old.firstOrNull { it.id == id } ?: DayTask(id, t.getString("title"), "👪", TaskRepeat.DAILY, TaskStore.today())
                previous.copy(title = t.getString("title"), repeat = TaskRepeat.valueOf(t.getString("repeat").uppercase()), fromParent = true)
            }
            if (old != local + remote) { TaskStore.save(ctx, local + remote); TaskEvents.changed() }
            if (GameStore.limit(ctx) != config.getInt("limit")) GameStore.setLimit(ctx, config.getInt("limit"))
            val done = JSONArray(remote.filter { DayTasks.isDone(it, TaskStore.today()) }.map { it.id.removePrefix("family:") })
            api.call("POST", "/v1/device/ack", JSONObject().put("version",device.getInt("version")).put("completed",done).put("day",TaskStore.today())
                .put("measuring", measuring(ctx)))
            PhoneWearBridge.push(ctx)
        } finally { mutex.unlock() }
    }

    /** Ob Bildschirmzeit und Schritte gerade gemessen werden (nur an/aus, keine Werte), damit Eltern ein Abschalten sehen. */
    private suspend fun measuring(ctx: Context): JSONObject {
        val steps = com.example.osmon.PetSync.flag(ctx, "feature.steps") &&
            (runCatching { com.example.osmon.Steps.client(ctx)?.let { com.example.osmon.Steps.hasPermission(it) } }.getOrNull() ?: false)
        return JSONObject().put("screen", com.example.osmon.PetSync.screenUsable(ctx)).put("steps", steps)
    }
}

@Composable
fun FamilyChildCard() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(FamilyVault.load(ctx)?.server.orEmpty()) }
    var name by remember { mutableStateOf("Mein OS-Mon") }; var code by remember { mutableStateOf("") }
    var linked by remember { mutableStateOf(FamilyVault.load(ctx) != null) }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (!ParentStore.isUnlocked()) { message = "Eltern-Bereich erneut mit PIN entsperren."; return }
        if (busy) return
        busy = true; scope.launch { try { action() } catch (e: Exception) { message = e.message ?: "Verbindung fehlgeschlagen." } finally { busy = false } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mit OS-Mon Eltern verbinden", style = MaterialTheme.typography.titleMedium)
        Text("Optional: Aufgaben, Spielelimit, Erledigt-Status und ob Bildschirmzeit und Schritte gemessen werden (nur an/aus) werden über euren Familienserver geteilt. Standort nur nach ausdrücklichem Teilen; keine Gesundheitsdaten. Beide Apps brauchen denselben HTTPS-Server.")
        if (!linked) {
            OutlinedTextField(server, { server = it }, label = { Text("HTTPS-Serveradresse") }, enabled = !busy)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Gerätename (ohne echten Kindernamen)") }, enabled = !busy)
            Button(onClick = { run {
                val base = FamilyApi.server(server)
                val pair = FamilyApi(FamilyConnection(base, "")).call("POST", "/v1/pairings", JSONObject().put("name",name).put("config",JSONObject().put("limit",GameStore.limit(ctx)).put("tasks",JSONArray())))
                FamilyVault.save(ctx, FamilyConnection(base,pair.getString("token"))); code = pair.getString("code"); linked = true; message = "Code zehn Minuten gültig. Nur in eurer Eltern-App eingeben."
            } }, enabled = !busy) { Text("Freigeben und Code erstellen") }
        } else {
            if (code.isNotEmpty()) Text(code, style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { run { FamilyChild.sync(ctx); message = "Verbindung abgefragt. Übernahme erfolgt, sobald gekoppelt." } }, enabled = !busy) { Text("Verbindung prüfen") }
            FamilyLocationCard()
            TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Verbindung entfernen") }
            Text("Synchronisierung bei geöffneter OS-Mon-App. Nach Trennen bleiben zuletzt übernommene Aufgaben und Limits lokal erhalten.")
        }
        if (busy) CircularProgressIndicator()
        if (message.isNotEmpty()) Text(message)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Verbindung entfernen?") }, text = { Text("Online trennen löscht die Verbindung auf dem Server. Nur lokal trennen entfernt den Zugang von diesem Gerät; Serverdaten danach in der Eltern-App löschen.") }, confirmButton = {
        TextButton(onClick = { confirm = false; run {
            FamilyVault.load(ctx)?.let { FamilyApi(it).call("DELETE", "/v1/device") }
            FamilyVault.clear(ctx); linked = false; code = ""; message = "Verbindung entfernt."
        } }) { Text("Online trennen") }
    }, dismissButton = {
        TextButton(onClick = { confirm = false; if (ParentStore.isUnlocked()) { FamilyVault.clear(ctx); linked = false; code = ""; message = "Lokal getrennt. Serverdaten bitte in der Eltern-App löschen." } }) { Text("Nur lokal trennen") }
    })
}
