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

// ---------- Modell ----------

data class FigureColors(
    val body: Int, val bodySick: Int, val ant: Int, val off: Int, val heart: Int, val iris: Int,
    val lid: Int, val line: Int, val cheek: Int, val tongue: Int, val sweat: Int
)
data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float)
data class Face(val ey: Float, val dx: Float, val r: Float, val my: Float)
data class Bead(val ox: Float, val oy: Float, val r: Float)
data class Antenna(val dx: Float, val y: Float, val lean: Float, val beads: List<Bead>)
data class HeartSpot(val x: Float, val y: Float, val size: Float)
data class Pt(val x: Float, val y: Float)
data class Arm(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rot: Float)
data class Foot(val x: Float, val y: Float, val w: Float, val h: Float)
data class Glint(val x: Float, val y: Float, val rot: Float)

data class FigureShape(
    val lumo: Boolean,            // Lumo-Bauweise: Kopf über dem Rumpf (Kopf neigt sich mit)
    val path: String,             // Körper-Silhouette (SVG-Pfad)
    val head: Ellipse?,
    val face: Face,
    val ant: Antenna,
    val heart: HeartSpot,
    val tail: Pt,
    val arms: List<Arm>,
    val feet: List<Foot>,
    val glint: Glint,
    val sweat: Pt,
    val zz: Pt
)

data class PetFigure(val id: String, val name: String, val colors: FigureColors, val shape: FigureShape)

/** Was gezeichnet wird. energy = Herz-Füllung, rest = leuchtende Antennenkugeln. */
data class PetPose(
    val mood: Mood,
    val overlays: Set<Overlay> = emptySet(),
    val form: Form? = null,
    val branch: Branch? = null,
    val energy: Float? = 100f,
    val rest: Float? = 100f,
    val charging: Boolean = false,
    val petted: Boolean = false,
    val reaction: ReactionKind? = null,      // Antippen: Gesicht, Arme/Beine, Körperbewegung
    val reactU: Float = -1f                  // Fortschritt 0..1 (außerhalb = keine Reaktion)
) {
    companion object {
        fun of(s: Snapshot, form: Form? = null, branch: Branch? = null, charging: Boolean = false, petted: Boolean = false) =
            PetPose(s.mood, s.overlays, form, branch, s.stats.e, s.stats.r, charging, petted)

        val Preview = PetPose(Mood.CONTENT, energy = 80f, rest = 80f)
    }
}

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

// ---------- Zeichnung (Port von lumoSVG) ----------

object PetFigureRenderer {
    private const val VB_X = -21.5f
    private const val VB_Y = -31.8f
    private const val VB_W = 263f
    private const val HEART_D = "M0 -0.35 C -0.05 -0.78 -0.78 -0.82 -0.92 -0.3 C -1.02 0.06 -0.62 0.46 0 0.9 C 0.62 0.46 1.02 0.06 0.92 -0.3 C 0.78 -0.82 0.05 -0.78 0 -0.35 Z"
    private const val WHITE = 0xFFFFFFFF.toInt()

    private val pathCache = HashMap<String, Path>()
    private fun svg(d: String): Path = synchronized(pathCache) {
        pathCache.getOrPut(d) { PathParser.createPathFromPathData(d) }
    }

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(color: Int, alpha: Float = 1f) = fillP.apply { this.color = color; this.alpha = (alpha * 255).toInt() }
    private fun stroke(color: Int, width: Float, alpha: Float = 1f) =
        strokeP.apply { this.color = color; strokeWidth = width; this.alpha = (alpha * 255).toInt() }

    /** Fertiges Bild fürs Widget (Glance kann nicht selbst zeichnen). */
    fun bitmap(figure: PetFigure, pose: PetPose, sizePx: Int = 360): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), sizePx.toFloat(), sizePx.toFloat(), figure, pose, null)
        return b
    }

    /** t = Zeit in Sekunden (Atmen, Blinzeln, Wackeln); null = Standbild. Muss im UI-/Render-Thread laufen. */
    @Synchronized
    fun draw(c: Canvas, w: Float, h: Float, F: PetFigure, p: PetPose, t: Double?) {
        val S = F.shape
        val C = F.colors
        val md = p.mood
        val yawn = Overlay.YAWNING in p.overlays
        val stress = Overlay.STRESSED in p.overlays
        val chubby = Overlay.CHUBBY in p.overlays
        val slug = Overlay.SLUGGISH in p.overlays
        val body = if (md == Mood.SICK) C.bodySick else C.body
        val line = C.line
        val lit = p.rest?.let { if (it >= 66f) 3 else if (it >= 33f) 2 else 1 } ?: 0
        val level = (p.energy ?: 0f).coerceIn(0f, 100f) / 100f      // Herz-Füllung
        val sw = 3f
        val cx = 110f
        val ey = S.face.ey; val dx = S.face.dx; val r = S.face.r; val my = S.face.my
        val slow = slug || md == Mood.SICK || md == Mood.HIBERNATION
        val R = p.reaction?.takeIf { p.reactU >= 0f && p.reactU < 1f }
        val limbs = R?.let { ReactionMotion.limbs(it, p.reactU) }
        val bm = R?.let { ReactionMotion.body(it, p.reactU) }

        // Animation (entspricht den CSS-Keyframes im Prototyp)
        var breathe = 0f; var blink = 0f; var wobble = 0f; var pulse = 1f; var zzOff = 0f
        if (t != null) {
            val period = if (slow) 3.6 else 2.2
            breathe = (0.02 * (1 - cos(2 * PI * t / period)) / 2).toFloat()
            val ph = (t % 4.2) / 4.2
            blink = (if (ph < 0.96) 0.0 else if (ph < 0.975) (ph - 0.96) / 0.015 else (1 - ph) / 0.025).toFloat()
            if (stress) wobble = (-4 * cos(2 * PI * t / 0.5)).toFloat()
            if (p.charging) pulse = (0.725 + 0.275 * cos(2 * PI * t / 1.2)).toFloat()
            zzOff = (-4 * (1 - cos(2 * PI * t / 2)) / 2).toFloat()
        }

        val s = min(w, h) / VB_W
        c.save()
        c.translate((w - VB_W * s) / 2f, (h - VB_W * s) / 2f)
        c.scale(s, s)
        c.translate(-VB_X, -VB_Y)
        if (bm != null) {                                   // Reaktion bewegt die ganze Figur (Drehpunkt Mitte unten)
            c.translate(110f + bm.dx, 210f + bm.dy); c.rotate(bm.rot); c.scale(bm.sx, bm.sy); c.translate(-110f, -210f)
        }

        fun circle(x: Float, y: Float, rr: Float, fillColor: Int, alpha: Float = 1f, strokeW: Float = 0f) {
            c.drawCircle(x, y, rr, fill(fillColor, alpha))
            if (strokeW > 0f) c.drawCircle(x, y, rr, stroke(line, strokeW, alpha))
        }
        fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Float = 1f, rot: Float = 0f,
                 px: Float = x, py: Float = y, strokeW: Float = 0f) {
            c.save()
            if (rot != 0f) c.rotate(rot, px, py)
            val rect = RectF(x - rx, y - ry, x + rx, y + ry)
            c.drawOval(rect, fill(color, alpha))
            if (strokeW > 0f) c.drawOval(rect, stroke(line, strokeW))
            c.restore()
        }
        fun tilt() { if (slug) c.rotate(-7f, 110f, ey + 50f) }

        fun tail() {
            val tx = S.tail.x; val ty = S.tail.y
            val path = Path()
            if (slug) { path.moveTo(tx, ty + 8); path.quadTo(tx + 30, ty + 24, tx + 58, min(ty + 22, 212f)) }
            else { path.moveTo(tx, ty); path.quadTo(tx + 36, ty + 12, tx + 48, ty - 12) }
            c.drawPath(path, stroke(line, 10f))
            c.drawPath(path, stroke(body, 5f))
            val bx = if (slug) tx + 62 else tx + 52
            val by = if (slug) min(ty + 20, 210f) else ty - 18
            circle(bx, by, 10f, C.ant, pulse, sw)
            circle(bx - 3, by - 4, 2.5f, WHITE, 0.8f)
        }

        fun feet() {
            for (f in S.feet) {
                val lift = limbs?.let { if (f.x + f.w / 2 < 110f) it.fl else it.fr } ?: 0f
                val rect = RectF(f.x, f.y - lift, f.x + f.w, f.y + f.h - lift)
                val rr = min(8f, f.h / 2)
                c.drawRoundRect(rect, rr, rr, fill(body))
                c.drawRoundRect(rect, rr, rr, stroke(line, sw))
            }
        }

        fun antennas() {
            val A = S.ant
            for (sd in listOf(-1f, 1f)) {
                c.save()
                if (wobble != 0f) c.rotate(wobble, 110 + sd * A.dx, A.y + 10)
                A.beads.forEachIndexed { k, bd ->
                    val on = k < lit
                    val bx = 110 + sd * (A.dx + bd.ox * A.lean); val by = A.y + bd.oy
                    circle(bx, by, bd.r, if (on) C.ant else C.off, strokeW = sw)
                    if (on) circle(bx - bd.r * 0.35f, by - bd.r * 0.4f, bd.r * 0.28f, WHITE, 0.8f)
                }
                c.restore()
            }
        }

        fun bodyParts() {
            c.save()
            if (chubby) c.scale(1.18f, 1f, 110f, 0f)
            val sil = svg(S.path)
            c.drawPath(sil, fill(body))
            c.drawPath(sil, stroke(line, sw))
            for (a in S.arms) {
                val cy = a.cy + (if (slug) 6f else 0f)
                c.save()
                if (limbs != null) {                         // Arm um die Schulter (oberes Ende) drehen
                    val rad = Math.toRadians(a.rot.toDouble())
                    val sx = a.cx + a.ry * kotlin.math.sin(rad).toFloat(); val sy = cy - a.ry * kotlin.math.cos(rad).toFloat()
                    c.rotate(if (a.cx < 110f) limbs.l else -limbs.r, sx, sy)
                }
                oval(a.cx, cy, a.rx, a.ry, body, rot = a.rot, px = a.cx, py = a.cy, strokeW = sw)
                c.restore()
            }
            c.restore()
        }

        fun head() { S.head?.let { oval(it.cx, it.cy, it.rx, it.ry, body, strokeW = sw) } }

        fun glint() {
            val G = S.glint
            oval(G.x, G.y, max(9f, r * 0.7f), max(5f, r * 0.38f), WHITE, 0.45f, rot = G.rot)
        }

        fun heart() {
            val H = S.heart
            val hp = svg(HEART_D)
            c.save()
            c.translate(H.x, H.y)
            c.scale(H.size, H.size)
            c.drawPath(hp, fill(line))
            c.save()
            c.clipPath(hp)
            val top = 0.9f - level * 1.72f
            c.drawRect(-1.1f, top, 1.1f, 0.95f, fill(C.heart))
            c.restore()
            val bolt = Path().apply {
                moveTo(0.12f, -0.48f); lineTo(-0.26f, 0.08f); lineTo(-0.02f, 0.08f)
                lineTo(-0.14f, 0.56f); lineTo(0.26f, -0.04f); lineTo(0.02f, -0.04f); close()
            }
            c.drawPath(bolt, fill(WHITE))
            c.drawPath(hp, stroke(line, sw / H.size))
            c.restore()
        }

        fun face() {
            // Wangen
            val cheekOp = if (p.petted || (R != null && R != ReactionKind.DIZZY)) 0.95f else when (md) { Mood.RADIANT -> 0.75f; Mood.CONTENT -> 0.5f; else -> 0.3f }
            for (sd in listOf(-1f, 1f)) oval(cx + sd * (dx + r + 1), ey + r + 1, r * 0.43f, r * 0.24f, C.cheek, cheekOp)
            // Augen
            val open = when (R) {
                ReactionKind.SURPRISE, ReactionKind.STARTLE -> 1f
                ReactionKind.SLEEPY -> 0.75f; ReactionKind.WEAK -> 0.7f; ReactionKind.NIGHT -> 0.45f
                else -> when { yawn -> 0.35f; md == Mood.TIRED -> 0.5f; slug -> 0.6f; md == Mood.SICK -> 0.7f; else -> 1f }
            }
            for (ex in listOf(cx - dx, cx + dx)) {
                if (R?.happy == true) {                      // fröhlich zugekniffen: ^ ^
                    val arc = Path().apply { moveTo(ex - r * 0.72f, ey + r * 0.25f); quadTo(ex, ey - r * 0.8f, ex + r * 0.72f, ey + r * 0.25f) }
                    c.drawPath(arc, stroke(line, 4f))
                    continue
                }
                if (R == ReactionKind.DIZZY) {               // Spiralaugen
                    c.drawCircle(ex, ey, r, fill(WHITE)); c.drawCircle(ex, ey, r, stroke(line, 2.5f))
                    c.drawPath(svg("M$ex $ey m${-r * 0.08f} 0 a${r * 0.1f} ${r * 0.1f} 0 1 1 ${r * 0.2f} 0 a${r * 0.3f} ${r * 0.3f} 0 1 1 ${-r * 0.5f} 0 a${r * 0.5f} ${r * 0.5f} 0 1 1 ${r * 0.85f} 0"), stroke(line, 2.2f))
                    continue
                }
                if (md == Mood.HIBERNATION && R != ReactionKind.STARTLE) {
                    val arc = Path().apply { moveTo(ex - r * 0.67f, ey); quadTo(ex, ey + r * 0.43f, ex + r * 0.67f, ey) }
                    c.drawPath(arc, stroke(line, 3.5f))
                    continue
                }
                if (stress) oval(ex, ey + r - 1, r * 0.8f, r * 0.28f, C.lid, 0.45f)
                c.drawCircle(ex, ey, r, fill(WHITE))
                c.save()
                c.clipPath(Path().apply { addCircle(ex, ey, r, Path.Direction.CW) })
                c.drawCircle(ex, ey + 1, r * 0.67f, fill(C.iris))
                c.drawCircle(ex, ey + 1, r * 0.38f, fill(line))
                val hl = r * 0.29f
                c.drawRect(ex + r * 0.05f, ey - r * 0.38f, ex + r * 0.05f + hl, ey - r * 0.38f + hl, fill(WHITE))
                if (md == Mood.RADIANT) c.drawRect(ex - r * 0.33f, ey + r * 0.19f, ex - r * 0.33f + hl / 2, ey + r * 0.19f + hl / 2, fill(WHITE))
                c.drawRect(ex - r, ey - r, ex + r, ey - r + 2 * r * (1 - open), fill(C.lid))
                if (blink > 0f) c.drawRect(ex - r, ey - r, ex + r, ey - r + 2 * r * blink, fill(body))
                c.restore()
                c.drawCircle(ex, ey, r, stroke(line, 2.5f))
            }
            // Brauen
            val worried = (stress || md == Mood.SICK || R == ReactionKind.STARTLE) && R?.happy != true
            for ((ex, sd) in listOf(cx - dx to -1f, cx + dx to 1f)) {
                val b = ey - r - (if (R == ReactionKind.SURPRISE) 4f else 0f)
                val br = Path()
                if (worried) { br.moveTo(ex + sd * 10, b - 5); br.quadTo(ex, b - 9, ex - sd * 10, b - 13) }
                else { br.moveTo(ex - 10, b - 7); br.quadTo(ex, b - 13, ex + 10, b - 7) }
                c.drawPath(br, stroke(line, 2.5f))
            }
            // Mund
            val m = Path()
            when {
                R == ReactionKind.EVENING -> { m.moveTo(cx - 10, my); m.quadTo(cx, my + 9, cx + 10, my); c.drawPath(m, stroke(line, 3f)) }
                R == ReactionKind.NIGHT -> { m.moveTo(cx - 5, my + 4); m.quadTo(cx, my + 8, cx + 5, my + 4); c.drawPath(m, stroke(line, 3f)) }
                R == ReactionKind.SURPRISE || R == ReactionKind.STARTLE -> oval(cx, my + 6, 5f, 6.5f, line)
                R == ReactionKind.WEAK -> { m.moveTo(cx - 7, my + 2); m.quadTo(cx, my + 7, cx + 7, my + 2); c.drawPath(m, stroke(line, 3f)) }
                R == ReactionKind.GIGGLE || R == ReactionKind.SPIN || R == ReactionKind.NOON -> {
                    m.moveTo(cx - 14, my - 1); m.quadTo(cx, my + 19, cx + 14, my - 1); m.close()
                    c.drawPath(m, fill(line)); c.drawPath(m, stroke(line, 2f))
                    oval(cx, my + 9, 6f, 3.5f, C.tongue)
                }
                yawn || R == ReactionKind.SLEEPY || R == ReactionKind.MORNING -> {
                    oval(cx, my + 7, 12f, 15f, line)
                    oval(cx, my + 15, 8f, 5f, C.tongue)
                }
                stress || R == ReactionKind.DIZZY -> {
                    m.moveTo(cx - 14, my + 5)
                    m.quadTo(cx - 10.5f, my + 1, cx - 7, my + 5)
                    m.quadTo(cx - 3.5f, my + 9, cx, my + 5)
                    m.quadTo(cx + 3.5f, my + 1, cx + 7, my + 5)
                    m.quadTo(cx + 10.5f, my + 9, cx + 14, my + 5)
                    c.drawPath(m, stroke(line, 2.5f))
                }
                else -> when (md) {
                    Mood.RADIANT -> {
                        m.moveTo(cx - 14, my - 1); m.quadTo(cx, my + 19, cx + 14, my - 1); m.close()
                        c.drawPath(m, fill(line)); c.drawPath(m, stroke(line, 2f))
                        oval(cx, my + 9, 6f, 3.5f, C.tongue)
                    }
                    Mood.CONTENT -> { m.moveTo(cx - 10, my); m.quadTo(cx, my + 9, cx + 10, my); c.drawPath(m, stroke(line, 3f)) }
                    Mood.TIRED -> c.drawLine(cx - 8, my + 5, cx + 8, my + 5, stroke(line, 3f))
                    Mood.SICK -> { m.moveTo(cx - 10, my + 9); m.quadTo(cx, my, cx + 10, my + 9); c.drawPath(m, stroke(line, 3f)) }
                    Mood.HIBERNATION -> { m.moveTo(cx - 5, my + 4); m.quadTo(cx, my + 8, cx + 5, my + 4); c.drawPath(m, stroke(line, 3f)) }
                }
            }
        }

        // ---- Zusammensetzen (Reihenfolge wie im Prototyp)
        c.save()
        c.translate(0f, if (slug) 4f else 0f)
        c.scale(1 + breathe, 1 - breathe, 110f, 212f)
        tail()
        feet()
        if (S.lumo) {
            bodyParts(); heart()
            c.save(); tilt(); antennas(); head(); glint(); face(); c.restore()
        } else {
            c.save(); tilt(); antennas(); c.restore()
            bodyParts(); head(); glint(); heart()
            c.save(); tilt(); face(); c.restore()
        }
        c.restore()

        // Außerhalb des atmenden Körpers
        if (stress) {
            val x = S.sweat.x; val y = S.sweat.y
            val d = Path().apply { moveTo(x, y); quadTo(x - 8, y + 12, x, y + 18); quadTo(x + 8, y + 12, x, y); close() }
            c.drawPath(d, fill(C.sweat)); c.drawPath(d, stroke(line, 2f))
        }
        if (yawn || md == Mood.HIBERNATION) text(c, "z Z", 20f, line, S.zz.x, S.zz.y + zzOff, bold = true)
        p.form?.let { text(c, formBadge(it), 28f, line, 182f, 22f) }
        if (p.branch == Branch.RADIANT) text(c, "✨", 20f, line, 4f, 78f)
        if (p.branch == Branch.WILD) text(c, "🍂", 20f, line, 18f, 212f)
        c.restore()
    }

    private fun text(c: Canvas, s: String, size: Float, color: Int, x: Float, y: Float, bold: Boolean = false) {
        textP.textSize = size
        textP.color = color
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y, textP)                       // y = Grundlinie wie in SVG
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
