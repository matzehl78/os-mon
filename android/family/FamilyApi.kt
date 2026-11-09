package com.example.osmon.family

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FamilyConnection(val server: String, val token: String)

/** Lob von den Eltern: feste Sticker (kein Freitext). Gleiche Sticker wie Webportal und iOS. */
object FamilyPraise {
    val stickers = mapOf("star" to ("⭐" to "einen Stern"), "heart" to ("💖" to "ein Herz"), "thumbs" to ("👍" to "einen Daumen hoch"),
        "trophy" to ("🏆" to "einen Pokal"), "rainbow" to ("🌈" to "einen Regenbogen"))
    fun line(sticker: String, pet: String): String {
        val (emoji, label) = stickers[sticker] ?: ("⭐" to "ein Lob")
        return "$emoji Deine Eltern schicken dir $label! $pet freut sich riesig."
    }
    /** Reihenfolge der Sticker-Knöpfe (wie im Webportal und auf dem Server) */
    val order = listOf("star", "heart", "thumbs", "trophy", "rainbow")
    /** Status für die Eltern: zuletzt gesendet und ob das Kindergerät es schon gezeigt hat. */
    fun status(praise: JSONObject?): String {
        if (praise == null) return "Noch kein Lob gesendet."
        val s = stickers[praise.optString("sticker")] ?: return "Noch kein Lob gesendet."
        return "Zuletzt gesendet: ${s.first} · " + (if (praise.optBoolean("seen")) "angekommen ✓" else "wartet auf das Kindergerät")
    }
}

/** Text für die Eltern: werden Bildschirmzeit und Schritte am Kindergerät gemessen? Gleiche Texte wie Webportal und iOS. */
object FamilyStatus {
    /** null = das Kindergerät meldet es (noch) nicht (ältere App-Version). */
    fun measuringText(screen: Boolean?, steps: Boolean?): String {
        if (screen == null || steps == null) return "Messung noch unbekannt (ältere OS-Mon-Version auf dem Kindergerät)"
        val off = listOfNotNull("Bildschirmzeit".takeIf { !screen }, "Schritte".takeIf { !steps })
        return if (off.isEmpty()) "Bildschirmzeit und Schritte werden gemessen"
        else off.joinToString(" und ") + (if (off == listOf("Bildschirmzeit")) " wird" else " werden") + " nicht gemessen – am Kindergerät in OS-Mon oder den Systemeinstellungen wieder erlauben"
    }
    fun measuringOff(screen: Boolean?, steps: Boolean?) = screen == false || steps == false

    fun measuringText(device: JSONObject): String = device.optJSONObject("measuring").let { m ->
        measuringText(m?.takeIf { it.has("screen") }?.optBoolean("screen"), m?.takeIf { it.has("steps") }?.optBoolean("steps"))
    }
    fun measuringOff(device: JSONObject): Boolean = device.optJSONObject("measuring").let { m ->
        m != null && (!m.optBoolean("screen", true) || !m.optBoolean("steps", true))
    }
}

object FamilyVault {
    private const val ALIAS = "osmon.family.connection"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(ctx: Context, value: FamilyConnection) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val raw = JSONObject().put("server", value.server).put("token", value.token).toString().toByteArray(Charsets.UTF_8)
        val encrypted = c.iv + c.doFinal(raw)
        check(ctx.getSharedPreferences("family_secure", Context.MODE_PRIVATE).edit().putString("connection", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "Zugang konnte nicht gespeichert werden." }
    }
    fun load(ctx: Context): FamilyConnection? = runCatching {
        val raw = ctx.getSharedPreferences("family_secure", Context.MODE_PRIVATE).getString("connection", null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))) }
        val o = JSONObject(String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        FamilyConnection(o.getString("server"), o.getString("token"))
    }.getOrNull()
    fun clear(ctx: Context) { ctx.getSharedPreferences("family_secure", Context.MODE_PRIVATE).edit().clear().commit() }
}

class FamilyApi(val connection: FamilyConnection) {
    companion object {
        fun server(text: String): String {
            val t = text.trim().trimEnd('/'); val u = URI(t)
            require(u.scheme == "https" && !u.host.isNullOrEmpty() && u.userInfo == null && u.query == null && u.fragment == null && u.path.orEmpty().isEmpty()) { "Bitte eine HTTPS-Serveradresse ohne Pfad angeben." }
            return t
        }
    }
    suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(server(connection.server) + path).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false; c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 15000
            c.useCaches = false; c.setRequestProperty("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${connection.token}")
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            check(status in 200..299) { result.optString("error", "Verbindung fehlgeschlagen ($status).") }
            result
        } finally { c.disconnect() }
    }
}
