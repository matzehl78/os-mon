// OSMonWidget.kt: Jetpack Glance Widget + WorkManager (Android 10+, minSdk 29)
//
// Rechnet über denselben Kern wie der Home-Screen (PetSync.tick -> PetCore.tick) und liest/schreibt
// denselben Snapshot. Die frühere eigene Energie-Formel (PetState/PetPrefs/PetEngine, Prefs "pet") ist entfallen.
//
// Gradle (Modul):
//   implementation("androidx.core:core-ktx:1.13.1")          // PathParser (PetFigure.kt)
//   implementation("androidx.glance:glance-appwidget:1.1.1")
//   implementation("androidx.work:work-runtime-ktx:2.9.1")
//
// Manifest (innerhalb <application>):
//   <receiver android:name=".PetWidgetReceiver" android:exported="true">
//     <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE"/></intent-filter>
//     <meta-data android:name="android.appwidget.provider"
//                android:resource="@xml/pet_widget_info"/>
//   </receiver>
//
// res/xml/pet_widget_info.xml:
//   <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
//       android:minWidth="110dp" android:minHeight="110dp"
//       android:updatePeriodMillis="0"
//       android:resizeMode="horizontal|vertical"
//       android:widgetCategory="home_screen"/>
//   (updatePeriodMillis=0: Aktualisierung läuft über Worker, Home-Screen und Widget-Aktionen.)

package com.example.osmon

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.update
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.osmon.engine.Snapshot
import com.example.osmon.figure.PetFigureRenderer
import com.example.osmon.figure.PetFigures
import com.example.osmon.figure.PetPose
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Widget (Glance) ----------

class PetWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        // Gerade erst gerechnet (Worker/Home/Aktion haben vorher getickt)? Dann nur zeigen, sonst ticken.
        val snap = withContext(Dispatchers.IO) {
            PetSync.lastSnapshot(context)?.takeIf { now - it.second in 0..60_000L }?.first
                ?: PetSync.tick(context, now).snapshot
        }
        PetSync.markWidgetRendered(context, snap)
        val readyAt = PetSync.patReadyAt(context, now)
        val readyText = readyAt?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
        // Gewählte Figur als Bild (Glance kann nicht selbst zeichnen; gleicher Renderer wie im Home-Screen)
        val pet = PetFigureRenderer.bitmap(PetFigures.current(context), PetPose.of(snap), sizePx = 300)
        provideContent { PetContent(snap, readyText, pet) }
    }
}

@Composable
private fun PetContent(snap: Snapshot, patReadyText: String?, pet: Bitmap) {
    val white = ColorProvider(Color.White)
    val h = snap.wellbeing.roundToInt()
    Column(
        modifier = GlanceModifier.fillMaxSize().background(moodColor(snap.mood)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(pet),
            contentDescription = "Dein Wesen: ${moodTitle(snap.mood)}",
            modifier = GlanceModifier.size(64.dp)
        )
        Text(
            moodTitle(snap.mood),
            style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        )
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = snap.wellbeing / 100f,
            modifier = GlanceModifier.fillMaxWidth(),
            color = white,
            backgroundColor = ColorProvider(Color(0x55FFFFFF))
        )
        Text("Wohlbefinden $h", style = TextStyle(color = white, fontSize = 11.sp))
        Spacer(GlanceModifier.height(6.dp))
        if (patReadyText == null) {
            Button(text = "Streicheln", onClick = actionRunCallback<PatAction>())
        } else {
            Text("Streicheln ab $patReadyText", style = TextStyle(color = white, fontSize = 11.sp))
        }
    }
}

/** Streicheln direkt im Widget: zählt zur Zuneigung (A), höchstens 1× pro 3 Stunden. */
class PatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            if (PetSync.pat(context)) PetSync.tick(context)
        }
        PetWidget().update(context, glanceId)            // Button-Zustand ändert sich immer
    }
}

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PetScheduler.start(context)
    }

    // Kein Stop in onDisabled: Seit dem Onboarding läuft der Worker auch ohne Widget
    // (er liefert das Tageslog für die Evolution).
}

// ---------- Hintergrund (WorkManager) ----------

class PetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        refreshSteps(ctx)                                             // Health Connect; ohne Erlaubnis still
        val t = PetSync.tick(ctx, maxScreenAgeMs = 0L)                // frische Bildschirmzeit
        PetSync.updateWidgetIfNeeded(ctx, t.snapshot)                 // nur bei spürbarer Änderung
        PushDelivery.run(ctx, t)                                      // Push-Regeln prüfen, höchstens 1 Meldung
        return Result.success()
    }
}

object PetScheduler {
    private const val NAME = "osmon_pet_sync"

    /** Idempotent (KEEP): kann bei jedem App-Start aufgerufen werden. */
    fun start(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        // Minimum-Intervall von WorkManager: 15 Minuten, Doze-konform.
        wm.enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PetWorker>(15, TimeUnit.MINUTES).build()
        )
        wm.enqueueUniqueWork(                                       // sofortiger Sync, nicht doppelt
            "$NAME.now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PetWorker>().build()
        )
    }

    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
