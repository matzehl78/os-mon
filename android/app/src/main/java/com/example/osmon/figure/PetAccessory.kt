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
        }
    }
}
