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
        Decor.PUMPKINS -> listOf("🎃", "🦇", "🍬"); Decor.STARS -> listOf("⭐", "✨", "🌟"); else -> null
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
                emojis != null -> drawText(measurer, emojis[i % 3], Offset(x, y), TextStyle(fontSize = (14 + 10 * s).sp))
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
