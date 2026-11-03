// Bedtime.kt: Gute-Nacht-Routine, Geschichten (Vorlesen), Schlaflieder, Guten-Morgen-Karte, Einstellungen.
// Gegenstück zu Bedtime.swift.
//
// Inhalte: res/raw/bedtime.json (Quelle tools/bedtime.json, gleiche Datei wie iOS), Lieder res/raw/song_*.m4a.
// Vorlesen: android.speech.tts.TextToSpeech (deutsche Systemstimme, offline, falls installiert).
// Logik (Phasen, Bewertung der Nacht): engine/NightRoutine.kt.
//
// Speicher (Prefs "home"):
//   sleep.enabled, sleep.bed, sleep.wake (Minuten), sleep.reminder
//   night.goodnight.<Nacht-Tag> (Long ms), night.opens.<Nacht-Tag>, night.lastOpen, morning.seen.<Tag>

package com.example.osmon.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.HapticFeedbackConstants
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.osmon.MainActivity
import com.example.osmon.PetSync
import com.example.osmon.R
import com.example.osmon.engine.DayPhase
import com.example.osmon.engine.Mood
import com.example.osmon.engine.MorningReport
import com.example.osmon.engine.NightRating
import com.example.osmon.engine.NightRoutine
import com.example.osmon.engine.Overlay
import com.example.osmon.engine.SleepConfig
import com.example.osmon.figure.PetFigureCanvas
import com.example.osmon.figure.PetFigures
import com.example.osmon.figure.PetPose
import com.example.osmon.figure.rememberCurrentFigure
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject

// ---------- Inhalte ----------

data class Story(val id: String, val title: String, val figure: String, val minutes: Int, val text: String)
data class Song(val id: String, val title: String, val credit: String, @RawRes val res: Int, val lyrics: String)

class BedtimeContent(
    val stories: List<Story>, val songs: List<Song>, val goodnight: List<String>, val sleep: List<String>,
    val morning: Map<NightRating, List<String>>, val tips: List<String>
) {
    fun <T> pick(l: List<T>, day: Long, salt: Int = 0): T? = if (l.isEmpty()) null else l[NightRoutine.pickIndex(l.size, day, salt)]
    fun storyOfTheDay(day: Long) = pick(stories, day, 1)
    fun tipOfTheDay(day: Long) = pick(tips, day, 2)
    fun morningLine(r: NightRating, day: Long) = pick(morning[r].orEmpty(), day, 3) ?: "Guten Morgen!"

    companion object {
        @Volatile private var cached: BedtimeContent? = null

        fun get(ctx: Context): BedtimeContent = cached ?: synchronized(this) {
            cached ?: load(ctx).also { cached = it }
        }

        private fun load(ctx: Context): BedtimeContent {
            val o = JSONObject(ctx.resources.openRawResource(R.raw.bedtime).bufferedReader().use { it.readText() })
            fun strs(k: String, src: JSONObject = o) = src.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
            val st = o.getJSONArray("stories").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    Story(it.getString("id"), it.getString("title"), it.getString("figure"), it.getInt("minutes"), it.getString("text"))
                }
            }
            val so = o.getJSONArray("songs").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull {
                    val res = ctx.resources.getIdentifier(it.getString("audio"), "raw", ctx.packageName)
                    if (res == 0) null else Song(it.getString("id"), it.getString("title"), it.getString("credit"), res, it.getString("lyrics"))
                }
            }
            val m = o.getJSONObject("morning")
            return BedtimeContent(st, so, strs("goodnight"), strs("sleep"),
                mapOf(NightRating.GREAT to strs("great", m), NightRating.OK to strs("ok", m), NightRating.RESTLESS to strs("restless", m)),
                strs("tips"))
        }
    }
}

// ---------- Speicher ----------

data class MorningInfo(val day: Long, val greeting: String, val report: MorningReport, val tip: String?)

object SleepStore {
    private fun p(ctx: Context) = PetSync.prefs(ctx)

    fun config(ctx: Context) = SleepConfig(
        enabled = p(ctx).getBoolean("sleep.enabled", true),
        bedMin = p(ctx).getInt("sleep.bed", 22 * 60),
        wakeMin = p(ctx).getInt("sleep.wake", 7 * 60)
    )

    fun setConfig(ctx: Context, c: SleepConfig) {
        p(ctx).edit().putBoolean("sleep.enabled", c.enabled).putInt("sleep.bed", c.bedMin).putInt("sleep.wake", c.wakeMin).apply()
        BedtimeReminder.apply(ctx)
    }

    fun minuteOfDay(t: LocalTime = LocalTime.now()) = t.hour * 60 + t.minute
    fun phase(ctx: Context) = NightRoutine.phase(minuteOfDay(), config(ctx))
    fun nightDay(ctx: Context) = NightRoutine.nightDay(LocalDate.now().toEpochDay(), minuteOfDay(), config(ctx))

    fun markGoodnight(ctx: Context) {
        p(ctx).edit().putLong("night.goodnight.${nightDay(ctx)}", System.currentTimeMillis()).apply()
        AchievementStore.addGoodnight(ctx, nightDay(ctx))                 // Sammelalbum
    }
    fun saidGoodnight(ctx: Context, night: Long) = p(ctx).contains("night.goodnight.$night")

    /** Beim Öffnen der App aufrufen: zählt Öffnungen nach dem Gute-Nacht-Sagen bis zum Aufstehen (5 Min = ein Öffnen). */
    fun noteOpen(ctx: Context) {
        if (phase(ctx) != DayPhase.NIGHT) return
        val n = nightDay(ctx)
        if (!saidGoodnight(ctx, n)) return
        val now = System.currentTimeMillis()
        if (now - p(ctx).getLong("night.lastOpen", 0L) < 5 * 60_000L) return
        p(ctx).edit().putLong("night.lastOpen", now).putInt("night.opens.$n", p(ctx).getInt("night.opens.$n", 0) + 1).apply()
    }

    fun markMorningSeen(ctx: Context, day: Long) { p(ctx).edit().putBoolean("morning.seen.$day", true).apply() }

    /** Guten-Morgen-Karte für heute (null außerhalb der Morgenphase oder schon gesehen). */
    fun morning(ctx: Context, nightMinutes: Float?): MorningInfo? {
        if (phase(ctx) != DayPhase.MORNING) return null
        val day = LocalDate.now().toEpochDay()
        if (p(ctx).getBoolean("morning.seen.$day", false)) return null
        val r = NightRoutine.morningReport(nightMinutes, saidGoodnight(ctx, day), p(ctx).getInt("night.opens.$day", 0))
        val c = BedtimeContent.get(ctx)
        return MorningInfo(day, c.morningLine(r.rating, day), r, c.tipOfTheDay(day))
    }
}

// ---------- Erinnerung zur Schlafenszeit ----------

object BedtimeReminder {
    private const val CHANNEL = "osmon_bedtime"

    fun enabled(ctx: Context) = PetSync.prefs(ctx).getBoolean("sleep.reminder", false)
    fun setEnabled(ctx: Context, on: Boolean) { PetSync.prefs(ctx).edit().putBoolean("sleep.reminder", on).apply(); apply(ctx) }

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 4711, Intent(ctx, BedtimeReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Täglich zum Beginn der Abendphase (ungenau, akkuschonend). */
    fun apply(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intent(ctx))
        val c = SleepStore.config(ctx)
        if (!enabled(ctx) || !c.enabled) return
        val m = Math.floorMod(c.bedMin - NightRoutine.WIND_DOWN_MIN, 24 * 60)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, intent(ctx))
    }

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Schlafenszeit", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Erinnerung an die Gute-Nacht-Routine"
            })
        }
        val open = PendingIntent.getActivity(ctx, 4712, Intent(ctx, MainActivity::class.java).putExtra("open", "bedtime")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_osmon)
            .setContentTitle("Zeit fürs Bett 🌙")
            .setContentText("Dein Wesen wird müde. Sagt euch gute Nacht, auf Wunsch mit Geschichte.")
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4711, n) }      // ohne Erlaubnis: still
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> BedtimeReminder.apply(ctx)
            else -> if (SleepStore.config(ctx).enabled) BedtimeReminder.show(ctx)
        }
    }
}

// ---------- Vorlesen und Abspielen ----------

private class StoryReader(ctx: Context) {
    var speaking by mutableStateOf(false); private set
    private var ready = false
    private val tts = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { speaking = false }
            @Deprecated("") override fun onError(id: String?) { speaking = false }
        })
    }

    fun toggle(text: String) {
        if (speaking) { stop(); return }
        if (!ready) return
        tts.language = Locale.GERMAN
        tts.setSpeechRate(0.85f)
        tts.setPitch(1.05f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story")
        speaking = true
    }
    fun stop() { tts.stop(); speaking = false }
    fun release() { tts.stop(); tts.shutdown() }
}

private class SongPlayer(private val ctx: Context) {
    var playing by mutableStateOf<String?>(null); private set
    private var mp: MediaPlayer? = null

    fun toggle(s: Song) {
        if (playing == s.id) { stop(); return }
        stop()
        mp = MediaPlayer.create(ctx, s.res, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(), 0)?.apply {
            setVolume(0.8f, 0.8f)
            setOnCompletionListener { playing = null; release(); mp = null }   // einmal abspielen, dann Ruhe
            start()
        }
        if (mp != null) playing = s.id
    }
    fun stop() { mp?.run { stop(); release() }; mp = null; playing = null }
}

// ---------- Gute-Nacht-Ansicht ----------

@Composable
fun BedtimeScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val content = remember { BedtimeContent.get(ctx) }
    val figure = rememberCurrentFigure()
    val scene = rememberCurrentScene()
    val day = remember { LocalDate.now().toEpochDay() }
    val reader = remember { StoryReader(ctx) }
    val songs = remember { SongPlayer(ctx) }
    DisposableEffect(Unit) { onDispose { reader.release(); songs.stop() } }

    var asleep by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf<Story?>(null) }
    var showSongs by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(asleep) { if (asleep) { delay(10_000); dimmed = true } }
    val close = { reader.stop(); songs.stop(); onClose() }
    androidx.activity.compose.BackHandler(onBack = close)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0D29), Color(0xFF1A1A4D))))) {
        scene.res?.let {
            Image(painterResource(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.7f,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.7f); timesAssign(ColorMatrix(floatArrayOf(
                    0.6f, 0f, 0f, 0f, 0f, 0f, 0.6f, 0f, 0f, 0f, 0f, 0f, 0.65f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))) }))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x80000000), Color.Transparent, Color(0xB3000000)))))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = close, modifier = Modifier.semantics { contentDescription = "Schließen" }) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (asleep) "Schlaf gut!" else "Gute Nacht, ${figure.name}", color = Color.White,
                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                (if (asleep) content.pick(content.sleep, day, 4) else content.pick(content.goodnight, day, 5)).orEmpty(),
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
            )
            PetFigureCanvas(
                PetPose(if (asleep) Mood.HIBERNATION else Mood.TIRED, if (asleep) emptySet() else setOf(Overlay.YAWNING),
                    energy = 70f, rest = if (asleep) 100f else 40f,
                    stage = GrowthStore.stage(ctx).takeIf { it != com.example.osmon.engine.GrowthStage.EGG }),
                Modifier.size(250.dp).semantics { contentDescription = if (asleep) "${figure.name} schläft" else "${figure.name} ist müde" }
            )
            Spacer(Modifier.weight(1f))
            if (asleep) {
                Text("Leg das Handy jetzt weg. Das Display wird gleich dunkel.", color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            } else {
                content.storyOfTheDay(day)?.let { s ->
                    OutlinedButton(onClick = { story = s }, modifier = Modifier.fillMaxWidth()) { Text("📖  Geschichte: ${s.title}", color = Color.White) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("📚  Andere", color = Color.White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            content.stories.forEach { s -> DropdownMenuItem(text = { Text(s.title) }, onClick = { menu = false; story = s }) }
                        }
                    }
                    OutlinedButton(onClick = { showSongs = true }, modifier = Modifier.weight(1f)) { Text("🎵  Schlaflied", color = Color.White) }
                }
                Button(onClick = {
                    SleepStore.markGoodnight(ctx)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    asleep = true
                }, modifier = Modifier.fillMaxWidth()) { Text("🌙  Gute Nacht sagen") }
            }
        }
        AnimatedVisibility(dimmed, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0xE6000000)).clickable { dimmed = false }
                .semantics { contentDescription = "Display abgedunkelt. Tippen zum Aufhellen." })
        }
    }

    story?.let { s ->
        AlertDialog(
            onDismissRequest = { reader.stop(); story = null },
            title = { Text(s.title) },
            text = { Text(s.text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { reader.toggle(s.title + ".\n" + s.text) }) { Text(if (reader.speaking) "■ Stopp" else "🔊 Vorlesen") } },
            dismissButton = { TextButton(onClick = { reader.stop(); story = null }) { Text("Fertig") } }
        )
    }
    if (showSongs) {
        var open by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { songs.stop(); showSongs = false },
            title = { Text("Schlaflieder") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content.songs.forEach { s ->
                        Column(Modifier.fillMaxWidth().clickable { songs.toggle(s); open = s.id }) {
                            Text((if (songs.playing == s.id) "■  " else "▶  ") + s.title, style = MaterialTheme.typography.titleMedium)
                            Text(s.credit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (open == s.id) Text(s.lyrics, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songs.stop(); showSongs = false }) { Text("Fertig") } }
        )
    }
}

// ---------- Karten auf dem Home-Screen ----------

@Composable
fun BedtimePromptCard(phase: DayPhase, onStart: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF5C6BC0).copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 30.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (phase == DayPhase.NIGHT) "Schlafenszeit" else "Bald Schlafenszeit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Geschichte, Schlaflied und gute Nacht sagen.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStart) { Text("Los") }
        }
    }
}

@Composable
fun MorningCard(info: MorningInfo, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFA726).copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("☀️ Guten Morgen", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE08A1E))
            Text(info.greeting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(info.report.detail, style = MaterialTheme.typography.bodyMedium)
            info.tip?.let { Text("Tipp für heute: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row { Spacer(Modifier.weight(1f)); TextButton(onClick = onDismiss) { Text("Danke!") } }
        }
    }
}

// ---------- Einstellungen ----------

@Composable
fun SleepSettings() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(SleepStore.config(ctx)) }
    var reminder by remember { mutableStateOf(BedtimeReminder.enabled(ctx)) }
    fun save(c: SleepConfig) { cfg = c; SleepStore.setConfig(ctx, c) }
    fun pick(min: Int, onSet: (Int) -> Unit) =
        TimePickerDialog(ctx, { _, h, m -> onSet(h * 60 + m) }, min / 60, min % 60, true).show()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Gute-Nacht-Routine", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(cfg.enabled, { save(cfg.copy(enabled = it)) }, Modifier.semantics { contentDescription = "Gute-Nacht-Routine" })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Schlafenszeit", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pick(cfg.bedMin) { save(cfg.copy(bedMin = it)) } }, enabled = cfg.enabled) { Text(NightRoutine.clock(cfg.bedMin)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Aufstehzeit", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { pick(cfg.wakeMin) { save(cfg.copy(wakeMin = it)) } }, enabled = cfg.enabled) { Text(NightRoutine.clock(cfg.wakeMin)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Erinnerung zur Schlafenszeit", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(reminder, { reminder = it; BedtimeReminder.setEnabled(ctx, it) }, enabled = cfg.enabled,
                modifier = Modifier.semantics { contentDescription = "Erinnerung zur Schlafenszeit" })
        }
    }
}
