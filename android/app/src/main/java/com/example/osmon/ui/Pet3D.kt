// Pet3D.kt: Das Wesen in 3D (WebView mit assets/pet3d/pet3d.html, gleicher Renderer wie iOS und die Filme).
// Durchsichtig über dem Szenenbild. Antippen kommt als "tap" zurück, Ziehen dreht das Wesen.
// Schlägt WebGL fehl ("error"), merkt sich die App das und zeigt wieder 2D (Pet3D.failed).
// Inhalte: assets/pet3d/ (Kopie von tools/pet3d/).

package com.example.osmon.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.example.osmon.PetSync
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.Branch
import com.example.osmon.engine.Form
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.figure.ActiveReaction
import com.example.osmon.figure.ReactionKind
import com.example.osmon.moodColor

object Pet3D {
    const val KEY = "pet.3d"                  // Einstellung „3D-Wesen“ (Standard an)
    const val FAILED = "pet.3d.failed"
    fun enabled(ctx: Context): Boolean {
        val p = PetSync.prefs(ctx)
        return p.getBoolean(KEY, true) && !p.getBoolean(FAILED, false)
    }
}

/** Was die 3D-Ansicht zeigen soll. */
data class Pet3DState(
    val figureId: String,
    val accessory: Accessory?,
    val mood: Mood,
    val overlays: Set<Overlay> = emptySet(),
    val energy: Float?,
    val rest: Float?,
    val form: Form? = null,
    val branch: Branch? = null,
    val stage: GrowthStage? = null,
    val eggCracks: Int? = null,               // nicht null = Ei
    val platform: Boolean = true              // Leuchtring (ohne Hintergrundbild)
)

private class Pet3DController(val web: WebView) {
    var ready = false
    var state: Pet3DState? = null
    var sent = ""
    var reacting: ReactionKind? = null
    var lastReaction = -1L
    var lastShake = -1L
    var onTap: () -> Unit = {}
    var onError: () -> Unit = {}
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun post(m: String) {
        main.post {
            when (m) {
                "ready" -> { ready = true; sent = ""; state?.let { apply(it) } }
                "tap" -> onTap()
                "error" -> onError()
            }
        }
    }

    fun apply(s: Pet3DState, shake: Boolean = false) {
        state = s
        if (!ready) return
        val js = script(s, shake)
        if (js == sent && !shake) return
        sent = js
        web.evaluateJavascript(js, null)
    }

    fun react(r: ActiveReaction) {
        reacting = r.kind
        web.evaluateJavascript("OSPet.react('${r.kind.name.lowercase()}', ${r.kind.durMs})", null)
        state?.let { apply(it) }
        val start = r.startMs
        main.postDelayed({ if (lastReaction == start) { reacting = null; state?.let { apply(it) } } }, r.kind.durMs.toLong())
    }

    private fun script(s: Pet3DState, shake: Boolean): String {
        fun q(v: String?) = v?.let { "'$it'" } ?: "null"
        fun n(v: Float?) = v?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "null"
        val ov = s.overlays.map { "'${it.name.lowercase()}'" }.sorted().joinToString(",")
        val color = "#%06x".format(moodColor(s.mood).toArgb() and 0xFFFFFF)
        val acc = s.accessory?.let { accName(it) }
        return "OSPet.setFigure(${q(s.figureId)}, ${q(acc)});" +
            "OSPet.update({mood:'${s.mood.name.lowercase()}',ov:[$ov],energy:${n(s.energy)},rest:${n(s.rest)}," +
            "form:${q(s.form?.name?.lowercase())},branch:${q(s.branch?.name?.lowercase())},stage:${q(s.stage?.name?.let { stageName(it) })}," +
            "egg:${s.eggCracks ?: "null"},shake:${if (shake) 1 else 0},color:'$color',platform:${s.platform}," +
            "react:${q(reacting?.name?.lowercase())}})"
    }

    // Namen wie im Swift-Teil (pet3d.html kennt partyHat/santaHat/witchHat bzw. baby/child/teen/adult)
    private fun accName(a: Accessory) = when (a) {
        Accessory.PARTY_HAT -> "partyHat"; Accessory.SANTA_HAT -> "santaHat"; Accessory.WITCH_HAT -> "witchHat"; else -> a.name
    }
    private fun stageName(s: String) = s.lowercase()
}

/** Das Wesen in 3D. reaction: laufende Antipp-Reaktion; eggShakeAtMs: letztes Antippen des Eis. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Pet3DView(
    state: Pet3DState,
    modifier: Modifier = Modifier,
    reaction: ActiveReaction? = null,
    eggShakeAtMs: Long? = null,
    onTap: () -> Unit = {}
) {
    val holder = remember { arrayOfNulls<Pet3DController>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val c = Pet3DController(this)
                c.onError = { PetSync.prefs(ctx).edit().putBoolean(Pet3D.FAILED, true).apply() }
                addJavascriptInterface(c, "OSPetBridge")
                holder[0] = c
                loadUrl("file:///android_asset/pet3d/pet3d.html")
            }
        },
        update = {
            val c = holder[0] ?: return@AndroidView
            c.onTap = onTap
            c.apply(state)
            if (reaction != null && reaction.startMs != c.lastReaction) { c.lastReaction = reaction.startMs; c.react(reaction) }
            if (eggShakeAtMs != null && eggShakeAtMs != c.lastShake) { c.lastShake = eggShakeAtMs; c.apply(state, shake = true) }
        }
    )
    DisposableEffect(Unit) { onDispose { holder[0]?.web?.destroy() } }
}
