// PetFigureCore.kt: Modell und Zeichnung des Wesens auf android.graphics.Canvas (ohne App-Abhängigkeiten).
//
// Gemeinsam genutzt von Handy-App (Compose, Glance-Widget, Live-Hintergrund) und Wear-OS-App
// (wear/build.gradle.kts bindet diesen Ordner ein). Auswahl und Compose-Bausteine: PetFigure.kt.
// Gegenstück zu PetFigure.swift; Daten: PetFigureData.kt (erzeugt von gen_figures.mjs).

package com.example.osmon.figure

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.Branch
import com.example.osmon.engine.Glasses
import com.example.osmon.engine.Form
import com.example.osmon.engine.Growth
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.Snapshot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Abzeichen der Entwicklungsstufe (gleiche Zeichen wie formBadge() in der App). */
private fun badge(f: Form) = when (f) {
    Form.CHATTERLING -> "💬"
    Form.BINGEON -> "📺"
    Form.PIXELO -> "🎮"
    Form.ZENFUCHS -> "🦊"
    Form.WANDERO -> "🍃"
}

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
    val reactU: Float = -1f,                 // Fortschritt 0..1 (außerhalb = keine Reaktion)
    val accessory: Accessory? = null,        // Kopf: Fest-Zubehör oder Mütze aus dem Kleiderschrank, PetAccessory.kt
    val stage: GrowthStage? = null,          // Wachstumsstufe (Baby … Erwachsen); null = erwachsen. Ei: drawEgg()
    val glasses: Glasses? = null             // Brille aus dem Kleiderschrank
) {
    companion object {
        fun of(s: Snapshot, form: Form? = null, branch: Branch? = null, charging: Boolean = false, petted: Boolean = false) =
            PetPose(s.mood, s.overlays, form, branch, s.stats.e, s.stats.r, charging, petted)

        val Preview = PetPose(Mood.CONTENT, energy = 80f, rest = 80f)
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

    /** Ei als Standbild (Widget, Complication). */
    fun eggBitmap(figure: PetFigure, cracks: Int, sizePx: Int = 360): Bitmap {
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawEgg(Canvas(b), sizePx.toFloat(), sizePx.toFloat(), figure, cracks, null)
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
        // Wachstum: kleiner und gedrungener, Kopf/Gesicht größer, Antennen kürzer (um die Füße skaliert)
        val L = Growth.look(p.stage)
        if (L.sx != 1f || L.sy != 1f) c.scale(L.sx, L.sy, 110f, 212f)

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

        // Herz mit Abstand zum Mund (auch zum gähnenden, auch wenn Kopf/Gesicht beim Wachsen größer sind):
        // erst höchstens 10 tiefer, den Rest durch Verkleinern bei fester Unterkante
        val heartXYS: Triple<Float, Float, Float> = run {
            val H = S.heart
            var mb = ey + (my + 18f - ey) * L.face                       // Unterkante Gähn-Mund
            if (S.lumo) { val hb = S.head?.let { it.cy + it.ry } ?: (ey + 40f); mb = hb + (mb - hb) * L.head }
            val need = mb + 3f - (H.y - 0.82f * H.size)
            if (need <= 0f) return@run Triple(H.x, H.y, H.size)
            val down = minOf(10f, need); var y = H.y + down; var s = H.size
            if (need > down) { val bottom = y + 0.9f * s; val target = mb + 3f; s = maxOf(H.size * 0.55f, (bottom - target) / 1.72f); y = bottom - 0.9f * s }
            Triple(H.x, y, s)
        }

        fun heart() {
            val (hx, hy, hs) = heartXYS
            val hp = svg(HEART_D)
            c.save()
            c.translate(hx, hy)
            c.scale(hs, hs)
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
            c.drawPath(hp, stroke(line, sw / hs))
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
                    oval(cx, my + 6, 10f, 12f, line)                     // Gähn-Mund (Unterkante my + 18)
                    oval(cx, my + 12.5f, 6.5f, 4f, C.tongue)
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
        fun antG() { c.save(); c.scale(L.ant, L.ant, 110f, S.ant.y + 8f); antennas(); c.restore() }
        fun faceG() { c.save(); c.scale(L.face, L.face, cx, ey); face(); p.glasses?.let { PetAccessory.drawGlasses(c, it, cx, ey, dx, r, line) }; c.restore() }
        val headBase = S.head?.let { it.cy + it.ry } ?: (ey + 40f)
        fun headScale() { if (L.head != 1f) c.scale(L.head, L.head, 110f, headBase) }
        if (S.lumo) {
            bodyParts(); heart()
            c.save(); tilt(); headScale(); antG(); head(); glint(); faceG(); c.restore()
        } else {
            c.save(); tilt(); antG(); c.restore()
            bodyParts(); head(); glint(); heart()
            c.save(); tilt(); faceG(); c.restore()
        }
        p.accessory?.let { c.save(); tilt(); if (S.lumo) headScale(); PetAccessory.draw(c, it, S.ant.y, line); c.restore() }
        c.restore()

        // Außerhalb des atmenden Körpers
        if (stress) {
            val x = S.sweat.x; val y = S.sweat.y
            val d = Path().apply { moveTo(x, y); quadTo(x - 8, y + 12, x, y + 18); quadTo(x + 8, y + 12, x, y); close() }
            c.drawPath(d, fill(C.sweat)); c.drawPath(d, stroke(line, 2f))
        }
        if (yawn || md == Mood.HIBERNATION) text(c, "z Z", 20f, line, S.zz.x, S.zz.y + zzOff, bold = true)
        p.form?.let { text(c, badge(it), 28f, line, 182f, 22f) }
        if (p.branch == Branch.RADIANT) text(c, "✨", 20f, line, 4f, 78f)
        if (p.branch == Branch.WILD) text(c, "🍂", 20f, line, 18f, 212f)
        c.restore()
    }

    /**
     * Das Ei in den Farben der Figur: Grundfarbe = Körper, Tupfen = Antennenfarbe. cracks 0..4 (Growth.cracks),
     * shake 0..1 = Wackeln nach dem Antippen, t = Zeit (leichtes Schaukeln); null = Standbild.
     */
    fun drawEgg(c: Canvas, w: Float, h: Float, F: PetFigure, cracks: Int, t: Double?, shake: Float = 0f) {
        val C = F.colors; val line = C.line
        val s = min(w, h) / VB_W
        c.save()
        c.translate((w - VB_W * s) / 2f, (h - VB_W * s) / 2f)
        c.scale(s, s)
        c.translate(-VB_X, -VB_Y)
        // Schatten
        c.drawOval(RectF(60f, 206f, 160f, 220f), fill(0xFF000000.toInt(), 0.16f))
        val tt = t ?: 0.0
        val rock = (2.0 * sin(tt * 2.4) + shake * 9.0 * sin(tt * 38.0)).toFloat()
        c.rotate(rock, 110f, 212f)
        val egg = Path().apply {
            moveTo(110f, 52f)
            cubicTo(146f, 52f, 170f, 112f, 170f, 152f)
            cubicTo(170f, 190f, 144f, 212f, 110f, 212f)
            cubicTo(76f, 212f, 50f, 190f, 50f, 152f)
            cubicTo(50f, 112f, 74f, 52f, 110f, 52f)
            close()
        }
        c.drawPath(egg, fill(C.body))
        c.save(); c.clipPath(egg)
        for ((x, y, r) in listOf(Triple(84f, 150f, 14f), Triple(132f, 120f, 10f), Triple(122f, 180f, 12f), Triple(98f, 92f, 7f), Triple(146f, 162f, 6f))) {
            c.drawCircle(x, y, r, fill(C.ant, 0.85f))
        }
        c.drawOval(RectF(72f, 84f, 100f, 126f), fill(WHITE, 0.38f))
        c.restore()
        c.drawPath(egg, stroke(line, 4f))
        // Risse: wachsen mit jedem Tippen
        val crackPaths = listOf(
            floatArrayOf(96f, 102f, 104f, 112f, 98f, 120f, 108f, 130f),
            floatArrayOf(108f, 130f, 120f, 124f, 126f, 136f, 138f, 130f),
            floatArrayOf(72f, 136f, 82f, 130f, 90f, 140f, 98f, 134f, 108f, 130f),
            floatArrayOf(138f, 130f, 146f, 140f, 152f, 132f)
        )
        crackPaths.take(cracks.coerceIn(0, 4)).forEach { pts ->
            val path = Path().apply { moveTo(pts[0], pts[1]); var i = 2; while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 } }
            c.drawPath(path, stroke(line, 3f))
        }
        c.restore()
    }

    private fun text(c: Canvas, s: String, size: Float, color: Int, x: Float, y: Float, bold: Boolean = false) {
        textP.textSize = size
        textP.color = color
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y, textP)                       // y = Grundlinie wie in SVG
    }
}

