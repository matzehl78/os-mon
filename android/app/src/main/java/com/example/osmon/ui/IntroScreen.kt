// IntroScreen.kt: Vorspann-Film beim ersten Start (vor dem Onboarding), erneut abrufbar in den Einstellungen.
// Gegenstück zu IntroVideoView.swift. Video: res/raw/intro.mp4 (720×1280, ca. 41 s, Text im Bild, Musik).
// VideoView ohne Zusatzbibliothek; "Überspringen" oben rechts, am Ende geht es automatisch weiter.

package com.example.osmon.ui

import android.media.AudioAttributes
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.osmon.R

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val done = remember { booleanArrayOf(false) }
    val finish = { if (!done[0]) { done[0] = true; onFinished() } }
    BackHandler(onBack = finish)
    val view = remember {
        VideoView(ctx).apply {
            setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.intro}"))
            setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE)   // laufende Musik nicht unterbrechen
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            setOnCompletionListener { finish() }
            setOnErrorListener { _, _, _ -> finish(); true }
            setOnPreparedListener { it.isLooping = false; start() }
        }
    }
    DisposableEffect(view) { onDispose { view.stopPlayback() } }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .semantics {
                contentDescription = "Einführungsfilm: Dein Handy ist ein Haustier. Akku wird zu Energie, Bildschirmzeit zu Ruhe, " +
                    "freier Speicher zu Fitness, Schritte zu Vitalität. Antippen, Streicheln und Pausen machen es glücklich, " +
                    "gute Gewohnheiten schalten neue Hintergründe frei. Alles bleibt auf deinem Handy."
            }
    ) {
        AndroidView({ view }, Modifier.align(Alignment.Center).fillMaxSize())
        TextButton(
            onClick = finish,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                .background(Color(0x66000000), RoundedCornerShape(50))
        ) { Text("Überspringen", color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}
