// PetFigure.kt: Figuren-Katalog, Auswahl und Zeichnung des Wesens (Android, minSdk 29)
//
// Gegenstück zu PetFigure.swift. Die Zeichnung ist eine 1:1-Übertragung von lumoSVG() aus dem Prototyp
// (gleiche Zeichenfläche viewBox -21.5 -31.8 263 263, gleiche Bausteine, Reihenfolge und Farben).
// Gezeichnet wird auf android.graphics.Canvas, damit derselbe Code für den Home-Screen (Compose) und
// das Glance-Widget (Bitmap) gilt. Daten: PetFigureData.kt (erzeugt von gen_figures.mjs).
//
// Gradle: implementation("androidx.core:core-ktx:1.13.1")   // PathParser für SVG-Pfade
//
// Bedeutung: Herz-Füllung = Energie · leuchtende Antennenkugeln = Ruhe (0–3) · Augen/Mund = Stimmung.
// Auswahl: SharedPreferences "home", Schlüssel "pet.figure" (gleiche Prefs wie PetSync, also auch im Widget).

package com.example.osmon.figure

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.glance.appwidget.updateAll
import com.example.osmon.PetSync
import com.example.osmon.PetWidget
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.Branch
import com.example.osmon.engine.Form
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.Snapshot
import com.example.osmon.formBadge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Auswahl (App + Widget) ----------

object PetFigures {
    const val KEY = "pet.figure"
    const val DEFAULT_ID = "lumo"
    val all: List<PetFigure> get() = ALL_FIGURES

    fun byId(id: String?): PetFigure = ALL_FIGURES.firstOrNull { it.id == id } ?: ALL_FIGURES[0]

    fun current(ctx: Context): PetFigure = byId(PetSync.prefs(ctx).getString(KEY, null))

    /** Speichert die Wahl und zeichnet das Widget neu. */
    suspend fun select(ctx: Context, id: String) {
        if (ALL_FIGURES.none { it.id == id }) return
        withContext(Dispatchers.IO) { PetSync.prefs(ctx).edit().putString(KEY, id).commit() }
        runCatching { PetWidget().updateAll(ctx) }
    }
}

// ---------- Compose ----------

/** Aktuelle Figur; aktualisiert sich, sobald in den Einstellungen eine andere gewählt wird. */
@Composable
fun rememberCurrentFigure(): PetFigure {
    val ctx = LocalContext.current.applicationContext
    val prefs = remember { PetSync.prefs(ctx) }
    var id by remember { mutableStateOf(prefs.getString(PetFigures.KEY, null)) }
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == PetFigures.KEY) id = sp.getString(key, null)
        }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return PetFigures.byId(id)
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Das Wesen. figure = null: aktuelle Auswahl. Quadratisch; Größe setzt der Aufrufer. */
@Composable
fun PetFigureCanvas(
    pose: PetPose,
    modifier: Modifier = Modifier,
    figure: PetFigure? = null,
    animated: Boolean = true,
    reaction: ActiveReaction? = null              // Antippen-Reaktion (Gesicht, Arme/Beine, Bewegung)
) {
    val fig = figure ?: rememberCurrentFigure()
    val reduce = rememberReduceMotion()
    val animate = animated && !reduce
    var t by remember { mutableStateOf<Double?>(null) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now -> t = (now - start) / 1e9 }
        }
    }
    ComposeCanvas(modifier.aspectRatio(1f)) {
        val now = SystemClock.uptimeMillis()
        val p = if (reaction == null) pose else pose.copy(
            reaction = reaction.kind,
            // Bewegung reduziert: u ≈ 0 -> nur das Gesicht, Arme/Beine/Körper in Ruhe
            reactU = if (animate) reaction.progress(now) else if (reaction.isActive(now)) 0.001f else -1f
        )
        drawIntoCanvas { PetFigureRenderer.draw(it.nativeCanvas, size.width, size.height, fig, p, if (animate) t ?: 0.0 else null) }
    }
}

/** Auswahl-Raster für Einstellungen und Onboarding. */
@Composable
fun PetFigurePicker(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val current = rememberCurrentFigure()
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PetFigures.all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f ->
                    val selected = f.id == current.id
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(if (selected) 2.dp else 1.dp, if (selected) accent else ComposeColor.Gray.copy(alpha = 0.3f), shape)
                            .clickable(role = Role.RadioButton) { scope.launch { PetFigures.select(ctx, f.id) } }
                            .semantics { contentDescription = f.name; this.selected = selected }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PetFigureCanvas(PetPose.Preview, Modifier.size(60.dp), figure = f, animated = false)
                        Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
