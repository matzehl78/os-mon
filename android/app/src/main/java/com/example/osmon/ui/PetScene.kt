// PetScene.kt: Hintergrund-Szenen (Auswahl, Freischalten, Darstellung) und Wochenrückblick, Gegenstück zu PetScene.swift
//
// Ablage: app/src/main/java/com/example/osmon/ui/PetScene.kt
// Bilder: res/drawable-nodpi/scene_<id>.jpg (1024×1536, Hochformat). „floor“ = Höhe der Plattform im Bild (0..1),
// dort stehen die Füße der Figur. Werte 1:1 aus dem Prototyp.
//
// Speicher (Prefs "home"):
//   "pet.scene"            gewählte Szene
//   "pet.scenes.unlocked"  dauerhaft freigeschaltete Szenen (StringSet)
//   "review.seen.<Mo>"     Wochenrückblick dieser Woche weggeklickt
// Tagesdaten für Rückblick/Freischalten: EvoState.history (Bildschirm, Wohlbefinden) + Zähler pats/focusSessions
// (Prefs "home") + Schritte count.<Datum> (Prefs "steps").

package com.example.osmon.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.osmon.EvolutionStore
import com.example.osmon.PetSync
import com.example.osmon.R
import com.example.osmon.engine.DayStat
import com.example.osmon.engine.Mood
import com.example.osmon.engine.PetProgress
import com.example.osmon.engine.SceneUnlock
import com.example.osmon.engine.WeeklyReview
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

// ---------- Katalog ----------

data class Scene(val id: String, val name: String, @DrawableRes val res: Int?, val floor: Float)

object Scenes {
    val ALL = listOf(
        Scene("none", "Keine", null, 0.7f),
        Scene("neonstadt", "Neonstadt", R.drawable.scene_neonstadt, 0.69f),
        Scene("zimmer", "Zukunftszimmer", R.drawable.scene_zimmer, 0.61f),
        Scene("garteninsel", "Garteninsel", R.drawable.scene_garteninsel, 0.62f),
        Scene("raumstation", "Raumstation", R.drawable.scene_raumstation, 0.64f),
        Scene("zauberwald", "Zauberwald", R.drawable.scene_zauberwald, 0.645f),
        Scene("unterwasser", "Unterwasserkuppel", R.drawable.scene_unterwasser, 0.64f),
        Scene("suess", "Süßigkeitenwelt", R.drawable.scene_suess, 0.655f),
        Scene("mondbasis", "Mondbasis", R.drawable.scene_mondbasis, 0.68f),
        Scene("zen", "Zen-Garten", R.drawable.scene_zen, 0.72f),
        Scene("studio", "Neon-Studio", R.drawable.scene_studio, 0.665f),
    )
    const val DEFAULT_ID = "neonstadt"
    fun byId(id: String?): Scene = ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == DEFAULT_ID }
}

// ---------- Speicher ----------

object SceneStore {
    const val KEY = "pet.scene"
    const val UNLOCKED = "pet.scenes.unlocked"

    fun current(ctx: Context): Scene = Scenes.byId(PetSync.prefs(ctx).getString(KEY, null))
    fun unlocked(ctx: Context): Set<String> = PetSync.prefs(ctx).getStringSet(UNLOCKED, emptySet())!!.toSet()

    /** Nur freigeschaltete Szenen lassen sich wählen. */
    fun select(ctx: Context, id: String, status: List<SceneUnlock>): Boolean {
        if (status.none { it.sceneId == id && it.unlocked }) return false
        PetSync.prefs(ctx).edit().putString(KEY, id).apply()
        return true
    }

    fun addUnlocked(ctx: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        PetSync.prefs(ctx).edit().putStringSet(UNLOCKED, unlocked(ctx) + ids).apply()
    }

    fun reviewSeen(ctx: Context, weekStart: Long) = PetSync.prefs(ctx).getBoolean("review.seen.$weekStart", false)
    fun markReviewSeen(ctx: Context, weekStart: Long) = PetSync.prefs(ctx).edit().putBoolean("review.seen.$weekStart", true).apply()
}

// ---------- Tagesdaten -> PetProgress ----------

data class ProgressResult(
    val review: WeeklyReview?,               // null = keine Daten oder schon weggeklickt
    val status: List<SceneUnlock>,
    val newlyUnlocked: List<Scene>           // seit dem letzten Aufruf neu freigeschaltet (Banner)
)

object ProgressData {
    /** Abgeschlossene Tage (bis 28) plus heute (nur Zähler/Schritte, für zeitnahes Freischalten). */
    fun days(ctx: Context, today: Long = LocalDate.now().toEpochDay()): List<DayStat> {
        val home = PetSync.prefs(ctx)
        val steps = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
        val stepsOn = PetSync.flag(ctx, "feature.steps")
        fun stat(day: Long, screen: Float?, avg: Float?): DayStat {
            val d = LocalDate.ofEpochDay(day).toString()
            val st = if (stepsOn && steps.contains("count.$d")) steps.getLong("count.$d", 0L).toInt() else null
            return DayStat(day, avg, screen, st, home.getInt("pats.$d", 0), home.getInt("focusSessions.$d", 0))
        }
        val hist = EvolutionStore.load(ctx)?.history.orEmpty()
        return hist.filter { it.epochDay < today }.map { stat(it.epochDay, it.totalMinutes, it.avgWellbeing) } +
            stat(today, null, null)
    }

    /** Einmal pro Home-Aktualisierung: Rückblick + Freischalt-Stand; neu Freigeschaltetes wird dauerhaft gespeichert. */
    fun refresh(ctx: Context, today: Long = LocalDate.now().toEpochDay()): ProgressResult {
        val evo = EvolutionStore.load(ctx)
        val days = days(ctx, today)
        val already = SceneStore.unlocked(ctx)
        val status = PetProgress.unlocks(
            days, age = evo?.let { today - it.hatchDay } ?: 0L, evolved = evo?.current != null,
            screenGoal = PetSync.config(ctx).screenGoalMin, already = already
        )
        val fresh = status.filter { it.unlocked && it.sceneId !in PetProgress.FREE && it.sceneId !in already }.map { it.sceneId }
        SceneStore.addUnlocked(ctx, fresh)
        val review = PetProgress.weeklyReview(days, today)?.takeUnless { SceneStore.reviewSeen(ctx, it.weekStart) }
        return ProgressResult(review, status, fresh.map(Scenes::byId))
    }
}

/** Gewählte Szene; aktualisiert sich bei Änderung in den Einstellungen. */
@Composable
fun rememberCurrentScene(): Scene {
    val ctx = LocalContext.current.applicationContext
    val prefs = remember { PetSync.prefs(ctx) }
    var id by remember { mutableStateOf(prefs.getString(SceneStore.KEY, null)) }
    DisposableEffect(prefs) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { sp, key -> if (key == SceneStore.KEY) id = sp.getString(key, null) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return Scenes.byId(id)
}

// ---------- Stimmung färbt die Szene (wie CSS brightness → saturate → sepia im Prototyp) ----------

object SceneMood {
    private data class M(val b: Float, val s: Float, val sep: Float)

    private fun of(m: Mood) = when (m) {
        Mood.RADIANT -> M(1.08f, 1.18f, 0f)
        Mood.CONTENT -> M(1f, 1f, 0f)
        Mood.TIRED -> M(0.8f, 0.85f, 0f)
        Mood.SICK -> M(0.88f, 0.45f, 0.18f)
        Mood.HIBERNATION -> M(0.5f, 0.6f, 0f)
    }

    /** Abends/nachts dunkler: 22–6 Uhr × 0,72, 20–22 und 6–7 Uhr × 0,86. */
    fun night(hour: Float) = if (hour >= 22 || hour < 6) 0.72f else if (hour >= 20 || hour < 7) 0.86f else 1f

    /** 3×3-Farbmatrix (Zeilen R, G, B), Reihenfolge wie CSS: erst Helligkeit, dann Sättigung, dann Sepia. */
    fun matrix3(mood: Mood, hour: Float): FloatArray {
        val m = of(mood); val n = night(hour)
        val b = m.b * n; val s = m.s * (if (n < 1f) 0.85f else 1f); val p = m.sep
        val sat = floatArrayOf(
            0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s,
            0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s,
            0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s
        )
        val sep = floatArrayOf(
            0.393f + 0.607f * (1 - p), 0.769f - 0.769f * (1 - p), 0.189f - 0.189f * (1 - p),
            0.349f - 0.349f * (1 - p), 0.686f + 0.314f * (1 - p), 0.168f - 0.168f * (1 - p),
            0.272f - 0.272f * (1 - p), 0.534f - 0.534f * (1 - p), 0.131f + 0.869f * (1 - p)
        )
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = (0..2).sumOf { k -> (sep[i * 3 + k] * sat[k * 3 + j]).toDouble() }.toFloat() * b
        return r
    }

    fun colorFilter(mood: Mood, hour: Float): ColorFilter {
        val m = matrix3(mood, hour)
        return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            m[0], m[1], m[2], 0f, 0f,
            m[3], m[4], m[5], 0f, 0f,
            m[6], m[7], m[8], 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }
}

// ---------- Darstellung ----------

/**
 * Bildlage wie layoutScene() im Prototyp: Das Bild füllt die Ebene (Breite × Höhe), die Plattform (floor)
 * liegt genau unter den Füßen (feetY). Rückgabe: Höhe und Oberkante des Bildes, Breite folgt aus dem Seitenverhältnis.
 */
fun sceneFit(layerW: Float, layerH: Float, feetY: Float, floor: Float, aspect: Float = 2f / 3f): Pair<Float, Float> {
    val h = maxOf(layerH, feetY / floor, (layerH - feetY) / (1 - floor), layerW / aspect)
    return h to (feetY - floor * h)
}

/**
 * Hintergrund von oben bis unter die Statusringe. Größe setzt der Aufrufer (volle Breite, Höhe = layerH).
 * feetY: Füße der Figur in Pixeln ab Oberkante dieser Ebene. fadePx: weicher Auslauf unten in surface.
 */
@Composable
fun SceneBackground(scene: Scene, mood: Mood, hour: Float, feetY: Float, fadePx: Float, modifier: Modifier = Modifier) {
    val res = scene.res ?: return
    val img: ImageBitmap = ImageBitmap.imageResource(res)
    val surface = MaterialTheme.colorScheme.background
    val filter = remember(mood, (hour * 4).toInt()) { SceneMood.colorFilter(mood, hour) }
    Canvas(modifier) {
        val (h, top) = sceneFit(size.width, size.height, feetY, scene.floor, img.width.toFloat() / img.height)
        val w = h * img.width / img.height
        drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), top.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()), colorFilter = filter
        )
        // oben abdunkeln (Lesbarkeit), unten in den Bildschirm auslaufen
        drawRect(Brush.verticalGradient(listOf(Color(0x9E0A0C28), Color(0x000A0C28)), 0f, size.height * 0.34f),
            size = Size(size.width, size.height * 0.34f))
        val f = max(1f, fadePx)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, surface), size.height - f, size.height),
            topLeft = Offset(0f, size.height - f), size = Size(size.width, f))
    }
}

/** Auswahl-Raster: gesperrte Szenen zeigen Schloss, Regel und Fortschritt. */
@Composable
fun ScenePicker(status: List<SceneUnlock>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val current = rememberCurrentScene()
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Scenes.ALL.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sc ->
                    val st = status.firstOrNull { it.sceneId == sc.id }
                    val open = st?.unlocked ?: (sc.id in PetProgress.FREE)
                    val selected = sc.id == current.id
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.Gray.copy(alpha = 0.3f), shape)
                            .clickable(enabled = open, role = Role.RadioButton) { onSelect(sc.id) }
                            .semantics {
                                contentDescription = if (open) sc.name else "${sc.name}, gesperrt: ${st?.rule.orEmpty()}, ${((st?.progress ?: 0f) * 100).roundToInt()} Prozent"
                                this.selected = selected
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) {
                            if (sc.res != null) {
                                Image(painterResource(sc.res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    colorFilter = if (open) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
                            } else Text("ohne", style = MaterialTheme.typography.labelSmall)
                            if (!open) {
                                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                        Text("🔒")
                                        Text(st?.rule.orEmpty(), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Text(sc.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                        if (!open && st != null) LinearProgressIndicator(progress = { st.progress }, Modifier.fillMaxWidth().height(3.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Wochenrückblick (montags bis zum Wegklicken). */
@Composable
fun WeeklyReviewCard(r: WeeklyReview, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📅 Deine Woche", style = MaterialTheme.typography.labelLarge)
            Text(r.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            r.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Row { Spacer(Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Schön!") } }
        }
    }
}

/** Hinweis auf neu freigeschaltete Szenen. */
@Composable
fun SceneUnlockBanner(scenes: List<Scene>, onUse: (Scene) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val s = scenes.firstOrNull() ?: return
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉 Neuer Hintergrund: ${s.name}" + if (scenes.size > 1) " (+${scenes.size - 1})" else "",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onUse(s) }) { Text("Nutzen") }
            TextButton(onClick = onDismiss) { Text("Später") }
        }
    }
}
