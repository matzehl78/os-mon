// PetWallpaper.kt: Lebendes Hintergrundbild (Sperr- und Startbildschirm) mit dem Wesen auf seiner Szene.
//
// Zeigt die gewählte Figur in ihrer aktuellen Stimmung (letzter gespeicherter Snapshot) auf dem gewählten
// Hintergrund, mit Fest-Zubehör. Animiert nur, solange das Hintergrundbild sichtbar ist (atmen, blinzeln),
// 15 Bilder/s – akkuschonend. Ohne Szene: Verlauf in der Stimmungsfarbe.
// Setzen: Einstellungen → „Als Hintergrundbild“ (öffnet die System-Vorschau, siehe PetWallpaper.intent()).

package com.example.osmon

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.osmon.engine.Mood
import com.example.osmon.figure.PetFigureRenderer
import com.example.osmon.figure.PetFigures
import com.example.osmon.figure.PetPose
import com.example.osmon.ui.FestivalStore
import com.example.osmon.ui.SceneStore
import com.example.osmon.ui.GrowthStore
import com.example.osmon.engine.GrowthStage

class PetWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PetEngine()

    inner class PetEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val start = SystemClock.uptimeMillis()
        private var bg: Bitmap? = null
        private var bgRes = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val tick = object : Runnable {
            override fun run() { drawFrame(); if (visible) handler.postDelayed(this, 1000L / 15) }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(tick)
            if (v) handler.post(tick)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false; handler.removeCallbacks(tick); super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() { handler.removeCallbacks(tick); bg?.recycle(); super.onDestroy() }

        private fun drawFrame() {
            val holder = surfaceHolder
            val c = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try { PetWallpaper.render(applicationContext, c, (SystemClock.uptimeMillis() - start) / 1000.0, ::background) }
            finally { runCatching { holder.unlockCanvasAndPost(c) } }
        }

        /** Szenenbild einmal laden (auf ~1080 px Breite verkleinert). */
        private fun background(res: Int): Bitmap? {
            if (res == bgRes && bg != null) return bg
            bg?.recycle()
            bg = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = 1 })
            bgRes = res
            return bg
        }
    }
}

object PetWallpaper {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Intent für die System-Vorschau „Hintergrund festlegen“. */
    fun intent(ctx: Context) = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, PetWallpaperService::class.java))

    /** Ist unser Hintergrundbild gerade aktiv? */
    fun isActive(ctx: Context) = WallpaperManager.getInstance(ctx).wallpaperInfo?.packageName == ctx.packageName

    /**
     * Zeichnet Szene + Wesen. Die Plattform liegt bei 72 % der Höhe (Uhr und Benachrichtigungen bleiben oben frei),
     * die Figur ist 62 % der Breite groß.
     */
    fun render(ctx: Context, c: Canvas, t: Double?, loadBg: (Int) -> Bitmap?) {
        val w = c.width.toFloat(); val h = c.height.toFloat()
        val snap = PetSync.lastSnapshot(ctx)?.first
        val mood = snap?.mood ?: Mood.CONTENT
        val scene = SceneStore.current(ctx)
        val feetY = h * 0.72f
        val res = scene.res
        val bmp = res?.let(loadBg)
        if (bmp != null) {
            // wie sceneFit(): Plattform (floor) unter die Füße, Bild füllt den Bildschirm
            val aspect = bmp.width.toFloat() / bmp.height
            val ih = maxOf(h, feetY / scene.floor, (h - feetY) / (1 - scene.floor), w / aspect)
            val top = feetY - scene.floor * ih; val iw = ih * aspect
            c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF((w - iw) / 2, top, (w + iw) / 2, top + ih), paint)
            if (mood == Mood.HIBERNATION || mood == Mood.TIRED) c.drawColor(Color.argb(if (mood == Mood.HIBERNATION) 110 else 50, 10, 12, 40))
        } else {
            val top = when (mood) { Mood.RADIANT -> 0xFF2E9E5B.toInt(); Mood.CONTENT -> 0xFF1C9A9A.toInt(); Mood.TIRED -> 0xFF5C6BC0.toInt()
                Mood.SICK -> 0xFF8D6E63.toInt(); Mood.HIBERNATION -> 0xFF263238.toInt() }
            paint.shader = LinearGradient(0f, 0f, 0f, h, top, 0xFF0E1230.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint); paint.shader = null
        }
        val pose = (snap?.let { PetPose.of(it) } ?: PetPose(Mood.CONTENT, energy = 80f, rest = 80f))
            .let { val o = com.example.osmon.ui.WardrobeStore.outfit(ctx)
                it.copy(accessory = com.example.osmon.engine.Wardrobe.hat(FestivalStore.today(ctx, withHatchDay = false)?.accessory, o), glasses = o.glasses) }
        val size = w * 0.62f
        c.save()
        c.translate((w - size) / 2f, feetY - size * 0.92f)
        val stage = GrowthStore.stage(ctx)
        if (stage == GrowthStage.EGG) PetFigureRenderer.drawEgg(c, size, size, PetFigures.current(ctx), GrowthStore.cracks(ctx), t)
        else PetFigureRenderer.draw(c, size, size, PetFigures.current(ctx), pose.copy(stage = stage), t)
        c.restore()
    }

    /** Standbild in Bildschirmgröße (für „Als Bild speichern“). */
    fun bitmap(ctx: Context, width: Int, height: Int): Bitmap {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(ctx, Canvas(b), null) { BitmapFactory.decodeResource(ctx.resources, it) }
        return b
    }
}
