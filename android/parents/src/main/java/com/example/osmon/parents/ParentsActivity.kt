package com.example.osmon.parents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.osmon.family.*
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ParentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ParentsHome() } } }
    }
}

@Composable
private fun ParentsHome() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var connection by remember { mutableStateOf(FamilyVault.load(ctx)) }
    var webCode by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("https://parents.ulmatti-studios.com") }; var code by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var children by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    suspend fun refresh() {
        connection?.let { c ->
            val a = FamilyApi(c).call("GET", "/v1/children").getJSONArray("children")
            children = (0 until a.length()).map { a.getJSONObject(it) }
        }
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; scope.launch { try { action() } catch (e: Exception) { message = e.message ?: "Verbindungsfehler." } finally { busy = false } }
    }
    LaunchedEffect(connection) { run { refresh() } }
    Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OS-Mon Eltern", style = MaterialTheme.typography.headlineMedium)
        Text("Aufgaben und Spielelimit verwalten. Geteilt werden nur diese Einstellungen und der Übernahmestatus – auf Wunsch ein ausdrücklich geteilter Standort; keine Gesundheitsdaten.")
        if (connection == null) {
            OutlinedTextField(server, { server = it }, label = { Text("HTTPS-Serveradresse") }, enabled = !busy)
            Text("Verwende denselben Familienserver wie auf dem Kindergerät. Der Zugang wird sicher auf diesem Elternhandy gespeichert.")
            OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, enabled = !busy, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
            Button(onClick = { val pass = password; password = ""; run {
                val base = FamilyApi.server(server)
                val result = FamilyApi(FamilyConnection(base, "")).call("POST", "/v1/account/login", JSONObject().put("email", email).put("password", pass))
                val c = FamilyConnection(base, result.getString("token")); FamilyVault.save(ctx, c); connection = c; refresh(); message = "Angemeldet."
            } }, enabled = !busy) { Text("Anmelden") }
            OutlinedButton(onClick = { run {
                FamilyApi(FamilyConnection(FamilyApi.server(server), "")).call("POST", "/v1/account/register", JSONObject().put("email", email))
                message = "Falls möglich wurde eine Bestätigung gesendet. Im Postfach den Link öffnen und Passwort festlegen. Bestehende Familie zuerst über ihren Web-Anmeldecode im Portal mit einem Konto verknüpfen."
            } }, enabled = !busy) { Text("Mit E-Mail registrieren") }
            TextButton(onClick = { run {
                FamilyApi(FamilyConnection(FamilyApi.server(server), "")).call("POST", "/v1/account/forgot", JSONObject().put("email", email)); message = "Falls möglich erhältst du einen Link zum Zurücksetzen. Bitte auch Spam prüfen."
            } }, enabled = !busy) { Text("Passwort vergessen") }
            TextButton(onClick = { run {
                FamilyApi(FamilyConnection(FamilyApi.server(server), "")).call("POST", "/v1/account/resend", JSONObject().put("email", email)); message = "Falls möglich erhältst du eine neue Bestätigung. Bitte auch Spam prüfen."
            } }, enabled = !busy) { Text("Bestätigung erneut senden") }
            Text("Anmeldung gilt höchstens sieben Tage. Passwörter werden nicht gespeichert. Die E-Mail-Bestätigung und Passwortwahl erfolgen im Webportal.")
        } else if (selected == null) {
            OutlinedTextField(code, { code = it.uppercase().take(12) }, label = { Text("Kopplungscode vom Kindergerät") }, enabled = !busy)
            Text("Den zehn Minuten gültigen Code im PIN-geschützten Eltern-Bereich auf dem Kindergerät erstellen.")
            Button(onClick = { run {
                FamilyApi(connection!!).call("POST", "/v1/claims", JSONObject().put("code", code)); code = ""; refresh(); message = "Gerät verbunden."
            } }, enabled = !busy) { Text("Verbinden") }
            HorizontalDivider()
            Text("Elternkonto für bestehende Familie", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(email, { email = it }, label = { Text("E-Mail zur Kontoverknüpfung") }, enabled = !busy, singleLine = true)
            OutlinedButton(onClick = { run {
                FamilyApi(connection!!).call("POST", "/v1/account/register", JSONObject().put("email", email))
                message = "Falls möglich erhältst du eine Bestätigung. Dort Passwort festlegen und anschließend hier neu anmelden. Geräte und Aufgaben bleiben erhalten. Bei bestehendem Konto: Webportal verwenden."
            } }, enabled = !busy) { Text("Diese Familie mit E-Mail absichern") }
            TextButton(onClick = { run {
                val old = connection!!
                try { FamilyApi(old).call("DELETE", "/v1/browser-session") } finally { server = old.server; password = ""; FamilyVault.clear(ctx); connection = null; children = emptyList(); webCode = "" }
            } }, enabled = !busy) { Text("Abmelden / neu anmelden") }
            Text("Auch im Browser", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { run { webCode = FamilyApi(connection!!).call("POST", "/v1/web-codes").getString("code") } }, enabled = !busy) { Text("Web-Anmeldecode erstellen") }
            if (webCode.isNotEmpty()) Text(webCode, style = MaterialTheme.typography.headlineSmall)
            Text("Fünf Minuten gültig, einmal verwendbar. Nur auf eurer eigenen Eltern-Webseite eingeben. App und Browser verwenden dieselben Geräte.")
            HorizontalDivider()
            Text("Deine Geräte", style = MaterialTheme.typography.titleLarge)
            if (children.isEmpty()) Text("Noch kein Gerät verbunden.")
            children.forEach { c -> OutlinedButton(onClick = { selected = c }, enabled = !busy) { Text(c.getString("name")) } }
            Button(onClick = { run { refresh() } }, enabled = !busy) { Text("Aktualisieren") }
            Text("Bestehende Zugänge ohne E-Mail-Konto gelten weiterhin 90 Tage. Verknüpfe sie mit einer E-Mail, bevor sie ablaufen. Ein Konto je Familie; mehrere Anmeldungen sind möglich.")
            TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Zugang und alle Verbindungen löschen") }
        } else {
            DeviceEditor(selected!!, connection!!, onClose = { selected = null; run { refresh() } })
        }
        if (busy) CircularProgressIndicator()
        if (message.isNotEmpty()) Text(message)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Zugang löschen?") }, text = { Text("Alle Geräte werden getrennt.") }, confirmButton = {
        TextButton(onClick = { confirm = false; run { FamilyApi(connection!!).call("DELETE", "/v1/parents"); FamilyVault.clear(ctx); connection = null; children = emptyList() } }) { Text("Löschen") }
    }, dismissButton = {
        Column {
            TextButton(onClick = { confirm = false; FamilyVault.clear(ctx); connection = null; children = emptyList(); message = "Lokaler Zugang entfernt. Serverdaten werden dadurch nicht gelöscht." }) { Text("Nur lokalen Zugang entfernen") }
            TextButton(onClick = { confirm = false }) { Text("Abbrechen") }
        }
    })
}

private data class TaskDraft(val id: String, val title: String, val repeat: String)

@Composable
private fun DeviceEditor(device: JSONObject, connection: FamilyConnection, onClose: () -> Unit) {
    val config = remember(device) { device.getJSONObject("config") }
    var limit by remember(device) { mutableIntStateOf(config.getInt("limit")) }
    val tasks = remember(device) {
        mutableStateListOf<TaskDraft>().apply { val a = config.getJSONArray("tasks"); for (i in 0 until a.length()) { val t = a.getJSONObject(i); add(TaskDraft(t.getString("id"), t.getString("title"), t.getString("repeat"))) } }
    }
    var title by remember { mutableStateOf("") }; var rule by remember { mutableStateOf("daily") }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val completed = remember(device) { device.getJSONArray("completed").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }
    Text(device.getString("name"), style = MaterialTheme.typography.titleLarge)
    Text(if (device.getInt("applied") == device.getInt("version")) "Letzter gespeicherter Stand übernommen" else "Wartet auf Übernahme am Kindergerät")
    Text("Übernahme bei geöffneter Kinder-App. Ohne Internet bleiben die zuletzt übernommenen Einstellungen gültig.")
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var sharing by remember(device) { mutableStateOf(device.optBoolean("locationSharing", false)) }
    var locationRequest by remember(device) { mutableStateOf(device.optJSONObject("locationRequest")) }
    var location by remember(device) { mutableStateOf(device.optJSONObject("location")) }
    suspend fun refreshLocation() {
        val a = FamilyApi(connection).call("GET", "/v1/children").getJSONArray("children")
        val updated = (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("id") == device.getString("id") }
        location = updated?.optJSONObject("location"); sharing = updated?.optBoolean("locationSharing", false) == true; locationRequest = updated?.optJSONObject("locationRequest")
    }
    LaunchedEffect(device) {
        while (true) {
            try { refreshLocation() } catch (e: Exception) { message = e.message ?: "Standortabruf fehlgeschlagen." }
            kotlinx.coroutines.delay(5000)
        }
    }
    Text("Zuletzt geteilter Standort", style = MaterialTheme.typography.titleMedium)
    Text(if (sharing) "Standortanfragen freigegeben" else "Am Kindergerät noch nicht freigegeben")
    locationRequest?.let { req -> Text(when (req.optString("state")) {
        "pending" -> if (req.optDouble("expires") > System.currentTimeMillis()/1000.0) "Anfrage läuft …" else "Keine Antwort innerhalb von 90 Sekunden. Gerät möglicherweise nicht erreichbar."
        "received" -> "Standort für die letzte Anfrage empfangen."
        "permissionDenied" -> "Standortberechtigung am Kindergerät fehlt."
        "unavailable" -> "Kindergerät konnte keinen aktuellen Standort ermitteln."
        else -> "Anfrage abgelaufen. Gerät möglicherweise nicht erreichbar."
    }) }
    Button(enabled = sharing && !busy, onClick = { busy = true; scope.launch {
        try { FamilyApi(connection).call("POST", "/v1/children/${device.getString("id")}/location-request", JSONObject()); refreshLocation() }
        catch (e: Exception) { message = e.message ?: "Standortanfrage fehlgeschlagen." } finally { busy = false }
    } }) { Text("Neuen Standort anfragen") }
    Text("Kinder-App muss geöffnet sein. Anfrage läuft nach 90 Sekunden ab. Der angezeigte Standort kann aus einer früheren Messung stammen.")
    val loc = location
    if (loc != null && System.currentTimeMillis() / 1000.0 - loc.getDouble("received") < 86400) {
        Text("Gemessen: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date((loc.getDouble("measured") * 1000).toLong())))
        Text("Genauigkeit ungefähr ${loc.getDouble("accuracy").toInt()} m. Keine Live-Ortung.")
        OutlinedButton(onClick = { uriHandler.openUri("geo:${loc.getDouble("latitude")},${loc.getDouble("longitude")}?q=${loc.getDouble("latitude")},${loc.getDouble("longitude")}") }, enabled = !busy) { Text("In Karten-App anzeigen") }
    } else Text("Kein geteilter Standort. Auf dem Kindergerät ausdrücklich teilen.")
    OutlinedButton(onClick = { busy = true; scope.launch {
        try {
            val a = FamilyApi(connection).call("GET", "/v1/children").getJSONArray("children")
            location = (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("id") == device.getString("id") }?.optJSONObject("location")
        } catch (e: Exception) { message = e.message ?: "Abruf fehlgeschlagen." } finally { busy = false }
    } }, enabled = !busy) { Text("Anzeige neu laden") }
    Text("Runden pro Tag")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (n in 0..3) {
        FilterChip(selected = limit == n, onClick = { limit = n }, label = { Text(if (n == 0) "Aus" else "$n") }, enabled = !busy)
    } }
    tasks.toList().forEach { task ->
        Text(task.title + if (task.id in completed) " · als erledigt gemeldet" else " · offen/noch nicht gemeldet")
        TextButton(onClick = { tasks.remove(task) }, enabled = !busy) { Text("Aufgabe entfernen") }
    }
    OutlinedTextField(title, { title = it.take(40) }, label = { Text("Neue Aufgabe") }, enabled = !busy)
    for ((r, label) in listOf("once" to "Einmal", "daily" to "Täglich", "weekdays" to "Mo–Fr", "weekend" to "Wochenende")) {
        Row { RadioButton(selected = rule == r, onClick = { rule = r }, enabled = !busy); Text(label) }
    }
    OutlinedButton(onClick = { if (title.trim().isNotEmpty() && tasks.size < 12) { tasks.add(TaskDraft(UUID.randomUUID().toString(), title.trim(), rule)); title = "" } }, enabled = !busy && tasks.size < 12) { Text("Aufgabe hinzufügen") }
    Button(onClick = {
        busy = true; scope.launch {
            try {
                val a = JSONArray(); tasks.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("repeat", it.repeat)) }
                FamilyApi(connection).call("PUT", "/v1/children/${device.getString("id")}/config", JSONObject().put("version",device.getInt("version")).put("config", JSONObject().put("limit",limit).put("tasks",a)))
                onClose()
            } catch (e: Exception) { message = e.message ?: "Fehler beim Senden." } finally { busy = false }
        }
    }, enabled = !busy) { Text("Änderungen senden") }
    TextButton(onClick = onClose, enabled = !busy) { Text("Zurück ohne Speichern") }
    TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Gerät trennen") }
    if (message.isNotEmpty()) Text(message)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Gerät trennen?") }, text = { Text("Zuletzt übernommene Einstellungen bleiben auf dem Kindergerät erhalten.") }, confirmButton = {
        TextButton(onClick = { confirm = false; busy = true; scope.launch {
            try { FamilyApi(connection).call("DELETE", "/v1/children/${device.getString("id")}"); onClose() }
            catch (e: Exception) { message = e.message ?: "Trennen fehlgeschlagen." }; busy = false
        } }) { Text("Trennen") }
    }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } })
}
