// PetAccessory.kt: Fest-Zubehör auf der Figur (Partyhut, Weihnachtsmütze, Hasenohren …). Gegenstück: PetAccessory.swift.
//
// Zeichen-Einheiten wie die Figur (viewBox −21.5 −31.8 263 263). Anker: Mitte x = 110, Oberkante Kopf = top
// (Ansatz der Antennen, FigureShape.ant.y). Wird im Renderer im atmenden, geneigten Kopf-Teil gezeichnet,
// bewegt sich also mit Atmen, Hüpfen und Reaktionen.

package com.example.osmon.figure

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.osmon.engine.Accessory
import com.example.osmon.engine.Glasses

object PetAccessory {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    private fun f(c: Int) = fill.apply { color = c }
    private fun s(c: Int, w: Float = 3f) = stroke.apply { color = c; strokeWidth = w }

    fun draw(c: Canvas, a: Accessory, top: Float, line: Int) {
        val cx = 110f
        val b = top + 8f                      // Hutrand sitzt leicht im Kopf
        when (a) {
            Accessory.PARTY_HAT -> {
                val hat = Path().apply { moveTo(cx - 24, b); lineTo(cx + 4, b - 60); lineTo(cx + 26, b); close() }
                c.drawPath(hat, f(0xFFFFD54F.toInt()))
                c.save(); c.clipPath(hat)
                for (k in 0..4) {
                    val y = b - 8 - k * 13
                    c.drawPath(Path().apply { moveTo(cx - 40, y + 6); lineTo(cx + 40, y - 10); lineTo(cx + 40, y - 4); lineTo(cx - 40, y + 12); close() },
                        f(if (k % 2 == 0) 0xFFFF4FA8.toInt() else 0xFF4FC3F7.toInt()))
                }
                c.restore()
                c.drawPath(hat, s(line))
                c.drawCircle(cx + 4, b - 62, 7f, f(0xFFFF4FA8.toInt())); c.drawCircle(cx + 4, b - 62, 7f, s(line, 2.5f))
            }
            Accessory.SANTA_HAT -> {
                val cap = Path().apply {
                    moveTo(cx - 28, b - 6); cubicTo(cx - 24, b - 44, cx + 10, b - 62, cx + 44, b - 40)
                    cubicTo(cx + 30, b - 40, cx + 24, b - 30, cx + 28, b - 6); close()
                }
                c.drawPath(cap, f(0xFFE53935.toInt())); c.drawPath(cap, s(line))
                val band = RectF(cx - 34, b - 12, cx + 34, b + 4)
                c.drawRoundRect(band, 8f, 8f, f(0xFFFFFFFF.toInt())); c.drawRoundRect(band, 8f, 8f, s(line))
                c.drawCircle(cx + 46, b - 40, 8f, f(0xFFFFFFFF.toInt())); c.drawCircle(cx + 46, b - 40, 8f, s(line, 2.5f))
            }
            Accessory.BUNNY_EARS -> {
                for (side in listOf(-1f, 1f)) {
                    c.save(); c.rotate(side * 14f, cx + side * 14, b)
                    val outer = RectF(cx + side * 14 - 10, b - 58, cx + side * 14 + 10, b + 2)
                    c.drawOval(outer, f(0xFFFFFFFF.toInt())); c.drawOval(outer, s(line))
                    c.drawOval(RectF(cx + side * 14 - 5, b - 50, cx + side * 14 + 5, b - 8), f(0xFFFFA8C8.toInt()))
                    c.restore()
                }
            }
            Accessory.WITCH_HAT -> {
                val brim = RectF(cx - 44, b - 8, cx + 44, b + 6)
                val cone = Path().apply { moveTo(cx - 24, b - 2); cubicTo(cx - 14, b - 40, cx + 2, b - 58, cx + 30, b - 70); cubicTo(cx + 14, b - 52, cx + 20, b - 26, cx + 24, b - 2); close() }
                c.drawOval(brim, f(0xFF4A2C6E.toInt())); c.drawOval(brim, s(line))
                c.drawPath(cone, f(0xFF5E35B1.toInt())); c.drawPath(cone, s(line))
                c.drawRect(cx - 22, b - 14, cx + 23, b - 6, f(0xFFFF9800.toInt())); c.drawRect(cx - 22, b - 14, cx + 23, b - 6, s(line, 2f))
            }
            Accessory.CROWN -> {
                val crown = Path().apply {
                    moveTo(cx - 26, b); lineTo(cx - 30, b - 30); lineTo(cx - 14, b - 16); lineTo(cx, b - 36)
                    lineTo(cx + 14, b - 16); lineTo(cx + 30, b - 30); lineTo(cx + 26, b); close()
                }
                c.drawPath(crown, f(0xFFFFC107.toInt())); c.drawPath(crown, s(line))
                c.drawCircle(cx, b - 8, 4.5f, f(0xFFE53935.toInt())); c.drawCircle(cx - 16, b - 7, 3.5f, f(0xFF42A5F5.toInt())); c.drawCircle(cx + 16, b - 7, 3.5f, f(0xFF66BB6A.toInt()))
                for (x in listOf(cx - 30, cx, cx + 30)) { val y = if (x == cx) b - 36 else b - 30; c.drawCircle(x, y, 3.5f, f(0xFFFFF59D.toInt())); c.drawCircle(x, y, 3.5f, s(line, 2f)) }
            }
            Accessory.FLOWERS -> {
                val cols = listOf(0xFFFF80AB.toInt(), 0xFFFFEB3B.toInt(), 0xFFB388FF.toInt())
                listOf(-24f, 0f, 24f).forEachIndexed { i, dx ->
                    val x = cx + dx; val y = b - 4 - (if (i == 1) 6 else 0)
                    for (k in 0 until 5) {
                        val ang = Math.toRadians(k * 72.0)
                        val px = x + 7 * Math.cos(ang).toFloat(); val py = y + 7 * Math.sin(ang).toFloat()
                        c.drawCircle(px, py, 5.5f, f(cols[i])); c.drawCircle(px, py, 5.5f, s(line, 1.8f))
                    }
                    c.drawCircle(x, y, 4.5f, f(0xFFFFFFFF.toInt())); c.drawCircle(x, y, 4.5f, s(line, 1.8f))
                }
            }
            Accessory.LANTERN -> {                // Laterne an einem Stab neben der Figur (St. Martin)
                c.drawLine(196f, 168f, 186f, 70f, s(0xFF6D4C41.toInt(), 4f))
                c.drawLine(186f, 70f, 200f, 82f, s(line, 2f))
                val lan = RectF(186f, 82f, 214f, 116f)
                c.drawOval(RectF(178f, 74f, 222f, 124f), f(0x55FFD54F))
                c.drawRoundRect(lan, 10f, 10f, f(0xFFFFB300.toInt())); c.drawRoundRect(lan, 10f, 10f, s(line))
                c.drawCircle(200f, 99f, 6f, f(0xFFFFF59D.toInt()))
            }
            Accessory.JESTER -> {
                val cap = Path().apply {
                    moveTo(cx - 30, b); cubicTo(cx - 40, b - 30, cx - 54, b - 44, cx - 50, b - 52)
                    cubicTo(cx - 30, b - 44, cx - 10, b - 30, cx, b - 26)
                    cubicTo(cx + 10, b - 30, cx + 30, b - 44, cx + 50, b - 52)
                    cubicTo(cx + 54, b - 44, cx + 40, b - 30, cx + 30, b); close()
                }
                c.drawPath(cap, f(0xFF7E57C2.toInt()))
                c.save(); c.clipPath(cap); c.drawRect(cx, b - 60, cx + 60, b + 4, f(0xFF26A69A.toInt())); c.restore()
                c.drawPath(cap, s(line))
                for (x in listOf(cx - 50, cx + 50)) { c.drawCircle(x, b - 52, 6f, f(0xFFFFD54F.toInt())); c.drawCircle(x, b - 52, 6f, s(line, 2f)) }
            }
            // ---------- Kleiderschrank (Wardrobe.kt) ----------
            Accessory.BEANIE -> {                 // Wollmütze mit Rippen und Bommel
                val dome = Path().apply { moveTo(cx - 34, b); cubicTo(cx - 34, b - 44, cx + 34, b - 44, cx + 34, b); close() }
                c.drawPath(dome, f(0xFFEF5350.toInt()))
                c.save(); c.clipPath(dome)
                for (k in -3..3) c.drawLine(cx + k * 9f, b, cx + k * 6f, b - 40, s(0xFFC62828.toInt(), 2.5f))
                c.restore()
                c.drawPath(dome, s(line))
                val band = RectF(cx - 37, b - 10, cx + 37, b + 4)
                c.drawRoundRect(band, 7f, 7f, f(0xFFFFCDD2.toInt())); c.drawRoundRect(band, 7f, 7f, s(line))
                c.drawCircle(cx, b - 38, 9f, f(0xFFFFFFFF.toInt())); c.drawCircle(cx, b - 38, 9f, s(line, 2.5f))
            }
            Accessory.CAP -> {                    // Kappe mit Schirm nach rechts
                val dome = Path().apply { moveTo(cx - 31, b); cubicTo(cx - 31, b - 38, cx + 31, b - 38, cx + 31, b); close() }
                val brim = Path().apply { moveTo(cx + 18, b - 6); quadTo(cx + 54, b - 10, cx + 60, b + 2); quadTo(cx + 40, b + 6, cx + 18, b + 2); close() }
                c.drawPath(brim, f(0xFF1E88E5.toInt())); c.drawPath(brim, s(line))
                c.drawPath(dome, f(0xFF42A5F5.toInt())); c.drawPath(dome, s(line))
                c.drawLine(cx, b - 28, cx, b, s(0xFF1E88E5.toInt(), 2.5f))
                c.drawCircle(cx, b - 29, 4f, f(0xFF1E88E5.toInt())); c.drawCircle(cx, b - 29, 4f, s(line, 2f))
            }
            Accessory.BOW -> {                    // Schleife oben rechts am Kopf
                val x = cx + 24; val y = b - 6
                for (sd in listOf(-1f, 1f)) {
                    val loop = Path().apply { moveTo(x, y); cubicTo(x + sd * 10, y - 20, x + sd * 30, y - 16, x + sd * 26, y); cubicTo(x + sd * 30, y + 16, x + sd * 10, y + 20, x, y); close() }
                    c.drawPath(loop, f(0xFFFF4FA8.toInt())); c.drawPath(loop, s(line))
                }
                c.drawCircle(x, y, 7f, f(0xFFFF80C0.toInt())); c.drawCircle(x, y, 7f, s(line, 2.5f))
            }
            Accessory.TOP_HAT -> {                // Zylinder mit rotem Band
                val brim = RectF(cx - 40, b - 9, cx + 40, b + 5)
                c.drawOval(brim, f(0xFF263238.toInt())); c.drawOval(brim, s(line))
                val crown = RectF(cx - 24, b - 52, cx + 24, b - 2)
                c.drawRoundRect(crown, 5f, 5f, f(0xFF37474F.toInt())); c.drawRoundRect(crown, 5f, 5f, s(line))
                c.drawRect(cx - 24, b - 16, cx + 24, b - 8, f(0xFFE53935.toInt())); c.drawRect(cx - 24, b - 16, cx + 24, b - 8, s(line, 2f))
                c.drawLine(cx - 15, b - 46, cx - 15, b - 22, s(0x55FFFFFF, 4f))
            }
            Accessory.WIZARD_HAT -> {             // Zauberhut (Premium): blau mit Sternen
                val brim = RectF(cx - 44, b - 8, cx + 44, b + 6)
                val cone = Path().apply { moveTo(cx - 26, b - 2); cubicTo(cx - 16, b - 44, cx - 4, b - 66, cx - 26, b - 80); cubicTo(cx + 4, b - 70, cx + 18, b - 40, cx + 26, b - 2); close() }
                c.drawOval(brim, f(0xFF283593.toInt())); c.drawOval(brim, s(line))
                c.drawPath(cone, f(0xFF3949AB.toInt())); c.drawPath(cone, s(line))
                for ((x, y, r) in listOf(Triple(cx - 6, b - 22, 5f), Triple(cx + 10, b - 40, 4f), Triple(cx - 10, b - 52, 3.5f))) star(c, x, y, r, 0xFFFFEB3B.toInt())
            }
            Accessory.PIRATE_HAT -> {             // Piratenhut (Premium): Dreispitz mit Totenkopf
                val hat = Path().apply {
                    moveTo(cx - 46, b - 2); cubicTo(cx - 40, b - 26, cx - 24, b - 44, cx, b - 44)
                    cubicTo(cx + 24, b - 44, cx + 40, b - 26, cx + 46, b - 2); quadTo(cx, b - 14, cx - 46, b - 2); close()
                }
                c.drawPath(hat, f(0xFF212121.toInt())); c.drawPath(hat, s(line))
                c.drawPath(Path().apply { moveTo(cx - 44, b - 4); quadTo(cx, b - 16, cx + 44, b - 4) }, s(0xFFFFC107.toInt(), 3f))
                c.drawCircle(cx, b - 30, 7f, f(0xFFFFFFFF.toInt()))
                c.drawCircle(cx - 2.5f, b - 31, 1.8f, f(0xFF212121.toInt())); c.drawCircle(cx + 2.5f, b - 31, 1.8f, f(0xFF212121.toInt()))
                c.drawLine(cx - 8, b - 20, cx + 8, b - 16, s(0xFFFFFFFF.toInt(), 2.5f)); c.drawLine(cx - 8, b - 16, cx + 8, b - 20, s(0xFFFFFFFF.toInt(), 2.5f))
            }
        }
    }

    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, line: Int? = null, w: Float = 2f) {
        val p = Path()
        for (k in 0 until 10) {
            val a = Math.toRadians(-90.0 + k * 36.0); val rr = if (k % 2 == 0) r else r * 0.45f
            val px = x + rr * Math.cos(a).toFloat(); val py = y + rr * Math.sin(a).toFloat()
            if (k == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        c.drawPath(p, f(color)); line?.let { c.drawPath(p, s(it, w)) }
    }

    private fun heartPath(x: Float, y: Float, r: Float) = Path().apply {
        moveTo(x, y + r * 0.95f)
        cubicTo(x - r * 1.5f, y - r * 0.05f, x - r * 0.75f, y - r * 1.25f, x, y - r * 0.45f)
        cubicTo(x + r * 0.75f, y - r * 1.25f, x + r * 1.5f, y - r * 0.05f, x, y + r * 0.95f); close()
    }

    /**
     * Brille über den Augen (Kleiderschrank). Augen bei (cx ± dx, ey), Radius r; wird im Gesichts-Teil gezeichnet
     * (wächst mit dem Gesicht, neigt sich mit dem Kopf).
     */
    fun drawGlasses(c: Canvas, g: Glasses, cx: Float, ey: Float, dx: Float, r: Float, line: Int) {
        val R = r + 4f
        val eyes = listOf(cx - dx, cx + dx)
        fun temples(col: Int) { for (sd in listOf(-1f, 1f)) c.drawLine(cx + sd * (dx + R), ey - 2, cx + sd * (dx + R + 10), ey - 6, s(col, 3f)) }
        fun bridge(col: Int) { c.drawPath(Path().apply { moveTo(cx - dx + R * 0.85f, ey - 3); quadTo(cx, ey - 10, cx + dx - R * 0.85f, ey - 3) }, s(col, 3f)) }
        when (g) {
            Glasses.ROUND -> {
                temples(line); bridge(line)
                for (x in eyes) { c.drawCircle(x, ey, R, f(0x26FFFFFF)); c.drawCircle(x, ey, R, s(0xFF5D4037.toInt(), 3.5f)) }
            }
            Glasses.SUN -> {
                temples(line); bridge(line)
                for (x in eyes) {
                    val rc = RectF(x - R - 1, ey - R * 0.8f, x + R + 1, ey + R * 0.75f)
                    c.drawRoundRect(rc, R * 0.5f, R * 0.5f, f(0xF0263238.toInt())); c.drawRoundRect(rc, R * 0.5f, R * 0.5f, s(line, 3f))
                    c.drawLine(x - R * 0.5f, ey - R * 0.35f, x - R * 0.1f, ey - R * 0.6f, s(0x99FFFFFF.toInt(), 3f))
                }
            }
            Glasses.HEART -> {
                temples(0xFFD81B60.toInt()); bridge(0xFFD81B60.toInt())
                for (x in eyes) { val h = heartPath(x, ey, R * 0.95f); c.drawPath(h, f(0x66FF80AB)); c.drawPath(h, s(0xFFD81B60.toInt(), 3.5f)) }
            }
            Glasses.STAR -> {
                temples(0xFFF9A825.toInt()); bridge(0xFFF9A825.toInt())
                for (x in eyes) star(c, x, ey + 1, R * 1.25f, 0x55FFEB3B, 0xFFF9A825.toInt(), 3.5f)
            }
            Glasses.MONOCLE -> {
                val x = cx + dx
                c.drawPath(Path().apply { moveTo(x + R * 0.3f, ey + R); quadTo(x + R * 1.2f, ey + R * 2.2f, x + R * 0.6f, ey + R * 3.2f) }, s(0xFFFFB300.toInt(), 2f))
                c.drawCircle(x, ey, R, f(0x26FFFFFF)); c.drawCircle(x, ey, R, s(0xFFFFB300.toInt(), 3.5f))
            }
        }
    }
}
