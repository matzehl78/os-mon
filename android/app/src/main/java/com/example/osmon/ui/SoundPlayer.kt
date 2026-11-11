// SoundPlayer.kt: Spielt Geräusche und Stimme des Wesens (engine/SoundSynth.kt) über AudioTrack.
// Gegenstück: SoundPlayer.swift. Einstellung „Töne & Stimme“ (Standard an); folgt der Medien-Lautstärke,
// läuft als Spiel-Ton (USAGE_GAME) und hält nichts an, was gerade läuft. Erzeugt wird im Hintergrund.

package com.example.osmon.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.osmon.PetSync
import com.example.osmon.engine.GrowthStage
import com.example.osmon.engine.Sfx
import com.example.osmon.engine.SoundSynth
import com.example.osmon.figure.PetFigures
import java.util.concurrent.Executors

object SoundPlayer {
    const val KEY = "sound.on"
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = HashMap<String, ShortArray>()
    @Volatile private var lastVoiceAt = 0L

    fun enabled(ctx: Context) = PetSync.prefs(ctx).getBoolean(KEY, true)
    fun setEnabled(ctx: Context, on: Boolean) = PetSync.prefs(ctx).edit().putBoolean(KEY, on).apply()

    /** Geräusch abspielen (Figur bestimmt die Tonhöhe bei Antippen, Kichern, Gähnen). */
    fun play(ctx: Context, s: Sfx) {
        runCatching {
            if (!enabled(ctx)) return
            val fig = PetFigures.current(ctx).id
            val stage = runCatching { GrowthStore.stage(ctx) }.getOrNull()
            worker.execute {
                runCatching {                          // nie die App abstürzen lassen (Ton ist nur Beiwerk)
                    val key = "${s.name}|$fig|$stage"
                    val pcm = synchronized(cache) { cache.getOrPut(key) { SoundSynth.pcm16(SoundSynth.sfx(s, fig, stage)) } }
                    out(pcm)
                }
            }
        }
    }

    /** Brabbel-Stimme zur Sprechblase (höchstens alle 1,5 s, damit es nicht nervt). */
    fun say(ctx: Context, text: String, stage: GrowthStage? = null) {
        runCatching {
            if (!enabled(ctx) || text.isBlank()) return
            val now = System.currentTimeMillis()
            if (now - lastVoiceAt < 1500) return
            lastVoiceAt = now
            val fig = PetFigures.current(ctx).id
            worker.execute { runCatching { out(SoundSynth.pcm16(SoundSynth.babble(text, fig, stage))) } }
        }
    }

    private fun out(pcm: ShortArray) {
        runCatching {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundSynth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setNotificationMarkerPosition(pcm.size)
            t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) { track.release() }
                override fun onPeriodicNotification(track: AudioTrack) {}
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            t.play()
        }
    }
}
