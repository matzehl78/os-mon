// FestivalUI.kt: Feste und Geburtstag auf dem Home-Screen (Karte, Deko, Ständchen) und Einstellungen. Gegenstück: FestivalUI.swift.
// Logik: engine/Festivals.kt. Zubehör auf der Figur: figure/PetAccessory.kt.
//
// Speicher (Prefs "home"): festivals.enabled (Standard an), user.birthday.m / user.birthday.d (nur Tag und Monat, freiwillig),
// festival.hidden.<Tag> (Karte zugeklappt).

package com.example.osmon.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.osmon.EvolutionStore
import com.example.osmon.PetSync
import com.example.osmon.R
import com.example.osmon.engine.Decor
import com.example.osmon.engine.Festival
import com.example.osmon.engine.Festivals
import com.example.osmon.engine.Prop
import com.example.osmon.engine.PropKind
import com.example.osmon.figure.PetFigures
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

object FestivalStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)

    fun enabled(ctx: Context) = p(ctx).getBoolean("festivals.enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("festivals.enabled", on).apply()

    /** Jahreszeiten im Hintergrund (Tönung, Pflanzen/Schnee, Teilchen). Standard an. */
    fun seasons(ctx: Context) = p(ctx).getBoolean("seasons.enabled", true)
    fun setSeasons(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("seasons.enabled", on).apply()

    fun birthday(ctx: Context): Pair<Int, Int>? {
        val m = p(ctx).getInt("user.birthday.m", 0); val d = p(ctx).getInt("user.birthday.d", 0)
        return if (m in 1..12 && d in 1..31) m to d else null
    }
    fun setBirthday(ctx: Context, b: Pair<Int, Int>?) {
        val e = p(ctx).edit()
        if (b == null) e.remove("user.birthday.m").remove("user.birthday.d") else e.putInt("user.birthday.m", b.first).putInt("user.birthday.d", b.second)
        e.apply()
    }

    /** Fest heute (null = keins oder ausgeschaltet). */
    fun today(ctx: Context, withHatchDay: Boolean = true): Festival? {
        if (!enabled(ctx)) return null
        val hatch = if (withHatchDay) EvolutionStore.load(ctx)?.hatchDay else null
        return Festivals.today(LocalDate.now().toEpochDay(), birthday(ctx), hatch, PetFigures.current(ctx).name)
    }

    fun cardHidden(ctx: Context) = p(ctx).getBoolean("festival.hidden.${LocalDate.now().toEpochDay()}", false)
    fun hideCard(ctx: Context) = p(ctx).edit().putBoolean("festival.hidden.${LocalDate.now().toEpochDay()}", true).apply()
}

/** Karte: Titel, „Feiern“ (Reaktion + Konfetti), bei Geburtstag/Schlüpftag ein Ständchen. */
@Composable
fun FestivalCard(f: Festival, onCelebrate: () -> Unit, onHide: () -> Unit) {
    val ctx = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { mp?.release() } }
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0x2EFF4FA8), Color(0x29FFD54F))), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.emoji, fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCelebrate) { Text("Feiern 🎉") }
                    if (f.song) OutlinedButton(onClick = {
                        if (playing) { mp?.release(); mp = null; playing = false }
                        else {
                            mp = MediaPlayer.create(ctx, R.raw.song_geburtstag, AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0)
                                ?.apply { setOnCompletionListener { playing = false; release(); mp = null }; start() }
                            playing = mp != null
                        }
                    }) { Text(if (playing) "■ Stopp" else "♪ Ständchen") }
                }
            }
            TextButton(onClick = onHide, modifier = Modifier.semantics { contentDescription = "Karte ausblenden" }) { Text("✕") }
        }
    }
}

/** Schwebende Deko (Schnee, Konfetti, Herzen …). Größe setzt der Aufrufer. burstAtMs: „Feiern“ macht sie 3 s dichter. */
@Composable
fun FestivalDecor(decor: Decor, burstAtMs: Long?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduce) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val measurer = rememberTextMeasurer()
    val emojis = when (decor) {
        Decor.HEARTS -> listOf("💗", "💕", "💖"); Decor.EGGS -> listOf("🥚", "🐣", "🌷")
        Decor.PUMPKINS -> listOf("🎃", "🦇", "🍬"); Decor.STARS -> listOf("⭐", "✨", "🌟")
        Decor.LEAVES -> listOf("🍂", "🍁", "🍃"); Decor.BUTTERFLIES -> listOf("🦋", "", ""); else -> null
    }
    val colors = listOf(Color(0xFFFF4FA8), Color(0xFFFFD54F), Color(0xFF4FC3F7), Color(0xFFFF9800), Color(0xFF66BB6A), Color(0xFFB388FF))
    fun rnd(i: Int, k: Int): Float { val x = sin((i * 127 + k * 311) * 12.9898) * 43758.5453; return (x - floor(x)).toFloat() }

    Canvas(modifier) {
        val boost = burstAtMs?.let { max(0f, 1f - (System.currentTimeMillis() - it) / 3000f) } ?: 0f
        val n = 22 + (boost * 30).toInt()
        for (i in 0 until n) {
            val speed = (0.04f + 0.05f * rnd(i, 1)) * (1 + 2 * boost)
            var v = ((t * speed + rnd(i, 2)) % 1.15f) - 0.075f
            if (decor == Decor.LIGHTS) v = 1 - v
            val x = (rnd(i, 3) + 0.03f * sin(t * (0.6f + rnd(i, 4)) + i)) * size.width
            val y = v * size.height
            val s = 0.6f + 0.8f * rnd(i, 5)
            when {
                emojis != null -> if (emojis[i % 3].isNotEmpty() && x in 0f..(size.width - 4f) && y in 0f..(size.height - 4f)) drawText(   // außerhalb: Breite/Höhe negativ → Absturz
                    measurer, emojis[i % 3], Offset(x, y), TextStyle(fontSize = (14 + 10 * s).sp))
                decor == Decor.PETALS -> rotate(t * (45 + 60 * rnd(i, 6)) + i * 40, Offset(x, y)) {
                    drawOval(Color(0xFFFFB8D9), Offset(x - 5 * s * density, y - 3 * s * density), Size(10 * s * density, 6 * s * density))
                }
                decor == Decor.SNOW -> drawCircle(Color.White.copy(alpha = 0.85f), (2 + 2 * s).dp.toPx(), Offset(x, y))
                decor == Decor.LIGHTS -> {
                    drawCircle(Color(0xFFFF9800).copy(alpha = 0.18f), (8 + 8 * s).dp.toPx(), Offset(x, y))
                    drawCircle(Color(0xFFFFD966), (2 + 3 * s).dp.toPx(), Offset(x, y))
                }
                else -> rotate(t * (60 + 120 * rnd(i, 6)) + i * 40, Offset(x, y)) {
                    drawRect(colors[i % 6], Offset(x - 4 * s * density, y - 2 * s * density), Size(8 * s * density, 4 * s * density))
                }
            }
        }
    }
}

/** Einstellungen: Feste an/aus, eigener Geburtstag (nur Tag und Monat). */
@Composable
fun FestivalSettings() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(FestivalStore.enabled(ctx)) }
    var seasons by remember { mutableStateOf(FestivalStore.seasons(ctx)) }
    var bday by remember { mutableStateOf(FestivalStore.birthday(ctx)) }
    var dayMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    fun monthName(m: Int) = java.time.Month.of(m).getDisplayName(JTextStyle.FULL_STANDALONE, Locale.GERMAN)
    fun save(b: Pair<Int, Int>?) { bday = b; FestivalStore.setBirthday(ctx, b) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Feste feiern", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(enabled, { enabled = it; FestivalStore.setEnabled(ctx, it) }, Modifier.semantics { contentDescription = "Feste feiern" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Jahreszeiten im Hintergrund", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(seasons, { seasons = it; FestivalStore.setSeasons(ctx, it) }, Modifier.semantics { contentDescription = "Jahreszeiten im Hintergrund" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mein Geburtstag", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(bday != null, { save(if (it) (1 to 1) else null) }, Modifier.semantics { contentDescription = "Mein Geburtstag" })
        }
        bday?.let { (m, d) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { dayMenu = true }) { Text("$d.") }
                    DropdownMenu(dayMenu, { dayMenu = false }) {
                        (1..31).forEach { x -> DropdownMenuItem(text = { Text("$x.") }, onClick = { dayMenu = false; save(m to x) }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { monthMenu = true }) { Text(monthName(m)) }
                    DropdownMenu(monthMenu, { monthMenu = false }) {
                        (1..12).forEach { x -> DropdownMenuItem(text = { Text(monthName(x)) }, onClick = { monthMenu = false; save(x to d) }) }
                    }
                }
            }
        }
    }
}

// ---------- Jahreszeit und Fest-Requisiten im Hintergrund ----------

/**
 * Requisiten (Tannenbaum, Ballons, Wimpelkette …), Jahreszeiten-Tönung und -Deko. Liegt über dem Szenenbild, unter dem Inhalt.
 * feetY = Plattformhöhe, garlandY = Höhe für Girlanden (knapp unter der Kopfzeile), beides in Pixeln der Ebene.
 */
@Composable
fun SceneThemeLayer(props: List<Prop>, tint: Long?, feetY: Float, garlandY: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduce) LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val measurer = rememberTextMeasurer()
    val flags = listOf(Color(0xFFFF4FA8), Color(0xFFFFD54F), Color(0xFF4FC3F7), Color(0xFFFF9800), Color(0xFF66BB6A), Color(0xFFB388FF))

    Canvas(modifier) {
        val w = size.width
        val dp = density
        tint?.let { drawRect(Color(it.toInt())) }
        fun chainPt(u: Float) = Offset(u * w, garlandY + 26 * dp * 4 * u * (1 - u))
        fun emoji(text: String, sizeDp: Float, center: Offset, bottom: Boolean = false) {
            val layout = measurer.measure(text, TextStyle(fontSize = (sizeDp / fontScale).sp))
            val topLeft = Offset(center.x - layout.size.width / 2f, if (bottom) center.y - layout.size.height else center.y - layout.size.height / 2f)
            drawText(layout, topLeft = topLeft)
        }
        for (p in props) when (p.kind) {
            PropKind.EMOJI -> {
                val bob = if (p.dy > 0) 5 * dp * sin(t * 1.3f + p.x * 9) else 0f
                emoji(p.text, p.size, Offset(p.x * w, feetY + 6 * dp - p.dy * dp + bob), bottom = true)
            }
            PropKind.BUNTING, PropKind.LIGHT_CHAIN, PropKind.LANTERNS, PropKind.HEART_GARLAND -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    chainPt(0f).let { moveTo(it.x, it.y) }; for (k in 1..40) chainPt(k / 40f).let { lineTo(it.x, it.y) }
                }
                drawPath(path, Color.White.copy(alpha = 0.75f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * dp))
                val n = when (p.kind) { PropKind.LANTERNS -> 6; PropKind.BUNTING -> 12; else -> 16 }
                for (i in 0 until n) {
                    val c = chainPt((i + 0.5f) / n)
                    when (p.kind) {
                        PropKind.BUNTING -> drawPath(androidx.compose.ui.graphics.Path().apply {
                            moveTo(c.x - 11 * dp, c.y); lineTo(c.x + 11 * dp, c.y); lineTo(c.x, c.y + 22 * dp); close()
                        }, flags[i % 6])
                        PropKind.LIGHT_CHAIN -> {
                            val on = 0.55f + 0.45f * sin(t * 2.4f + i * 1.7f)
                            val bc = listOf(Color(0xFFFFEB3B), Color(0xFFFF5252), Color(0xFF69F0AE), Color(0xFF40C4FF))[i % 4]
                            drawCircle(bc.copy(alpha = 0.25f * on), 9 * dp, Offset(c.x, c.y + 6 * dp))
                            drawOval(bc.copy(alpha = 0.5f + 0.5f * on), Offset(c.x - 3.5f * dp, c.y + 2.5f * dp), Size(7 * dp, 9 * dp))
                        }
                        PropKind.LANTERNS -> {
                            val sway = 3 * dp * sin(t * 1.5f + i)
                            drawCircle(Color(0xFFFF9800).copy(alpha = 0.22f), 22 * dp, Offset(c.x + sway, c.y + 24 * dp))
                            drawRoundRect(listOf(Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFFFF5252))[i % 3], Offset(c.x - 11 * dp + sway, c.y + 8 * dp),
                                Size(22 * dp, 28 * dp), androidx.compose.ui.geometry.CornerRadius(9 * dp))
                            drawOval(Color(0xFFFFF59D), Offset(c.x - 4 * dp + sway, c.y + 17 * dp), Size(8 * dp, 9 * dp))
                        }
                        else -> emoji(if (i % 2 == 0) "💗" else "💕", 16f, Offset(c.x, c.y + 10 * dp))
                    }
                }
            }
            PropKind.FIREWORKS -> for (b in 0 until 3) {
                val ph = ((t / 2.2f + b * 0.37f) % 1f)
                val cx = w * listOf(0.22f, 0.78f, 0.5f)[b]; val cy = garlandY + (40 + listOf(30f, 70f, 10f)[b]) * dp
                val r = (18 + 70 * ph) * dp; val a = max(0f, 1 - ph * 1.2f)
                val bc = listOf(Color(0xFFFFEB3B), Color(0xFFFF4FA8), Color(0xFF40C4FF))[b]
                for (k in 0 until 14) {
                    val ang = k / 14f * 2 * Math.PI.toFloat()
                    drawCircle(bc.copy(alpha = a), 2.5f * dp, Offset(cx + r * kotlin.math.cos(ang), cy + r * sin(ang)))
                }
            }
            PropKind.SNOW_GROUND -> for ((x, rw, rh) in listOf(Triple(0f, 0.34f, 34f), Triple(0.18f, 0.26f, 26f), Triple(0.82f, 0.26f, 26f), Triple(1f, 0.34f, 34f))) {
                drawOval(Color.White.copy(alpha = 0.85f), Offset((x - rw / 2) * w, feetY - rh * 0.35f * dp), Size(rw * w, rh * 2 * dp))
            }
            PropKind.SUN_GLOW -> {
                val c = Offset(w * 0.86f, garlandY + 10 * dp)
                drawCircle(Brush.radialGradient(listOf(Color(0x73FFD966), Color.Transparent), c, 170 * dp), 170 * dp, c)
            }
            PropKind.CANDLES -> {
                val lit = p.text.toIntOrNull() ?: 1; val cx = p.x * w; val base = feetY + 4 * dp
                drawOval(Color(0xFF217333), Offset(cx - 44 * dp, base - 14 * dp), Size(88 * dp, 22 * dp))
                for (k in 0 until 4) {
                    val x = cx + (-30 + k * 20) * dp; val h = (26 + if (k % 2 == 0) 0 else 6) * dp
                    drawRoundRect(Color(0xFFD92633), Offset(x - 5 * dp, base - 10 * dp - h), Size(10 * dp, h), androidx.compose.ui.geometry.CornerRadius(2 * dp))
                    if (k < lit) {
                        val fl = 1 + 0.15f * sin(t * 9 + k)
                        drawOval(Color(0xFFFF9800).copy(alpha = 0.25f), Offset(x - 7 * dp, base - 10 * dp - h - 22 * dp), Size(14 * dp, 22 * dp))
                        drawOval(Color(0xFFFFEB3B), Offset(x - 3 * dp, base - 10 * dp - h - 12 * dp * fl), Size(6 * dp, 12 * dp * fl))
                    }
                }
            }
        }
    }
}
