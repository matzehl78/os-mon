// RealFigureArt.kt: Realistische Figurenbilder (Naturdoku-Stil) je Figur und Wachstumsstufe.
// Gegenstück: RealFigureArt.swift (gleiche Namen, gleiche Regeln, gleiche Tests).
//
// Bilder: res/drawable-nodpi/real_<figur>_<stufe>.webp, 512×512, transparent. Alle Figuren und Stufen
// haben denselben Maßstab und dieselbe Bodenlinie (Füße 20 px über dem unteren Rand), damit ein Wechsel
// der Stufe ohne Springen funktioniert: das Bild einfach unten mittig ausrichten.
// Erzeugt mit tools/real_figures/export_real_figures.py.
//
// Sechs Bildstufen, fünf Wachstumsstufen: Der Schlüpfling erscheint am Schlüpftag selbst,
// ab dem nächsten Tag zeigt die Stufe BABY das Baby-Bild.

package com.example.osmon.figure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.example.osmon.engine.GrowthStage

enum class RealArtStage(val key: String) {
    EGG("egg"), HATCHLING("hatchling"), BABY("baby"), CHILD("child"), TEEN("teen"), ADULT("adult")
}

object RealFigureArt {
    /** Figuren mit realistischen Bildern (gleiche IDs wie PetFigureData). */
    val FIGURE_IDS = listOf("lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki")

    /** Abstand der Füße zum unteren Bildrand, relativ zur Bildhöhe (20 von 512 px). */
    const val FLOOR_INSET = 20f / 512f

    /** Bildstufe aus Wachstumsstufe. hatchedDay/today als Epoch-Tage; am Schlüpftag gibt es den Schlüpfling. */
    fun stageFor(growth: GrowthStage, hatchedDay: Long? = null, today: Long? = null): RealArtStage = when (growth) {
        GrowthStage.EGG -> RealArtStage.EGG
        GrowthStage.BABY -> if (hatchedDay != null && today != null && today <= hatchedDay) RealArtStage.HATCHLING else RealArtStage.BABY
        GrowthStage.CHILD -> RealArtStage.CHILD
        GrowthStage.TEEN -> RealArtStage.TEEN
        GrowthStage.ADULT -> RealArtStage.ADULT
    }

    fun has(figureId: String) = figureId in FIGURE_IDS

    /** Ressourcenname ohne Endung, z. B. real_lumo_baby. Unbekannte Figuren fallen auf Lumo zurück. */
    fun resourceName(figureId: String, stage: RealArtStage): String =
        "real_${if (has(figureId)) figureId else "lumo"}_${stage.key}"

    /** Drawable-ID (0, falls die Ressource fehlt). */
    @DrawableRes
    fun resId(context: Context, figureId: String, stage: RealArtStage): Int =
        context.resources.getIdentifier(resourceName(figureId, stage), "drawable", context.packageName)

    /** Bild als Bitmap (z. B. fürs Glance-Widget), optional verkleinert auf maxPx Kantenlänge. */
    fun bitmap(context: Context, figureId: String, stage: RealArtStage, maxPx: Int = 512): Bitmap? {
        val id = resId(context, figureId, stage)
        if (id == 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { 512 / (it * 2) < maxPx }
        }
        return BitmapFactory.decodeResource(context.resources, id, opts)
    }
}
