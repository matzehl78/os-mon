// LogoScreen.kt: Logo-Film „Ulmatti Studios“ bei jedem App-Start (Kaltstart), vor Vorspann/Onboarding/Home.
// Gegenstück zu LogoSplashView.swift. Videos (weißer Grund, mittig auf Weiß):
//   res/raw/logo.mp4        ganze Fassung, 8,5 s, mit Ton – erster Start, vor dem Vorspann
//   res/raw/logo_short.mp4  kurze Fassung, ca. 3,3 s (3× schneller), ohne Ton – jeder weitere Start
// Antippen überspringt; am Ende geht es automatisch weiter.

package com.example.osmon.ui

import android.media.AudioAttributes
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.osmon.R

@Composable
fun LogoScreen(full: Boolean = true, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val done = remember { booleanArrayOf(false) }
    val finish = { if (!done[0]) { done[0] = true; onFinished() } }
    BackHandler(onBack = finish)
    val view = remember {
        VideoView(ctx).apply {
            setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${if (full) R.raw.logo else R.raw.logo_short}"))
            setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE)   // laufende Musik nicht unterbrechen
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnCompletionListener { finish() }
            setOnErrorListener { _, _, _ -> finish(); true }
            setOnPreparedListener { it.isLooping = false; if (!full) it.setVolume(0f, 0f); start() }
        }
    }
    DisposableEffect(view) { onDispose { view.stopPlayback() } }
    Box(
        Modifier.fillMaxSize().background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
                onClickLabel = "Überspringen", onClick = finish)
            .semantics { contentDescription = "Ulmatti Studios" },
        contentAlignment = Alignment.Center
    ) {
        // VideoView passt das Querformat-Video in die Breite ein; der Rest bleibt weiß
        AndroidView({ view }, Modifier.fillMaxWidth())
    }
}
