// SoundSynth.kt: Geräusche und Stimme des Wesens, zur Laufzeit erzeugt (reines Kotlin, keine Tondateien).
// Gegenstück: SoundSynth.swift (gleiche Formeln). Abspielen: SoundPlayer.kt / SoundPlayer.swift.
//
// Stimme: kein echtes Sprechen, sondern Brabbeln wie bei Tierfiguren im Spiel – Silben aus Grundton + Obertönen
// mit leichtem Vibrato. Die Tonhöhe hängt an der Figur (jede klingt anders) und an der Wachstumsstufe (Baby höher).
// Länge und Melodie folgen dem Satz: mehr Wörter = mehr Silben, Fragezeichen = Ton geht am Ende hoch.

package com.example.osmon.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class Sfx { TAP, GIGGLE, YAWN, CRACK, HATCH, STAGE_UP, STAGE_DOWN, ACHIEVEMENT, POP, OOPS, GAME_END, WARDROBE }

object SoundSynth {
    const val RATE = 22050

    /** Grundton der Stimme (Hz): je Figur eigen (280–460), Baby/Kind höher. */
    fun voicePitch(figureId: String, stage: GrowthStage?): Float {
        var h = 0
        for (ch in figureId) h = (h * 31 + ch.code) and 0x7FFFFFFF
        val base = 280f + (h % 19) * 10f
        val f = when (stage) { GrowthStage.EGG, GrowthStage.BABY -> 1.35f; GrowthStage.CHILD -> 1.2f; GrowthStage.TEEN -> 1.08f; else -> 1f }
        return base * f
    }

    /** Anzahl Silben für einen Satz: etwa eine pro Wort, 2–8. */
    fun syllables(text: String): Int {
        val words = text.split(' ', '\n').count { w -> w.any { it.isLetter() } }
        return words.coerceIn(2, 8)
    }

    /** Brabbel-Stimme für einen Satz. */
    fun babble(text: String, figureId: String, stage: GrowthStage?): FloatArray {
        val n = syllables(text)
        val p0 = voicePitch(figureId, stage)
        val question = text.trimEnd().endsWith("?")
        val excited = text.contains('!')
        var seed = 7
        for (ch in text) seed = (seed * 131 + ch.code) and 0x7FFFFFFF
        val rnd = GameRandom(seed.toLong())
        val syl = 0.11f
        val gap = 0.035f
        val out = FloatArray(((syl + gap) * n * RATE).toInt() + RATE / 20)
        for (k in 0 until n) {
            val last = k == n - 1
            var p = p0 * (0.9f + 0.25f * rnd.next())
            if (excited) p *= 1.08f
            if (last && question) p *= 1.25f
            val end = if (last && question) p * 1.18f else if (last) p * 0.88f else p * (0.95f + 0.1f * rnd.next())
            val len = syl * (if (last) 1.4f else 0.8f + 0.4f * rnd.next())
            val vowel = rnd.next()                        // Klangfarbe: mehr oder weniger Obertöne („a“ … „u“)
            tone(out, ((syl + gap) * k * RATE).toInt(), len, p, end, 0.5f, harmonics = 0.35f + 0.4f * vowel, vibrato = 0.03f)
        }
        return out
    }

    fun sfx(s: Sfx, figureId: String = "", stage: GrowthStage? = null): FloatArray {
        val p = if (figureId.isEmpty()) 360f else voicePitch(figureId, stage)
        return when (s) {
            Sfx.TAP -> buf(0.12f).also { tone(it, 0, 0.1f, p * 1.6f, p * 2.1f, 0.45f, harmonics = 0.2f) }
            Sfx.GIGGLE -> buf(0.5f).also { b -> for (k in 0 until 5) tone(b, (k * 0.09f * RATE).toInt(), 0.07f, p * (1.7f - k * 0.08f), p * (1.9f - k * 0.08f), 0.4f, harmonics = 0.4f) }
            Sfx.YAWN -> buf(1.0f).also { tone(it, 0, 0.95f, p * 1.15f, p * 0.6f, 0.4f, harmonics = 0.55f, vibrato = 0.02f, attack = 0.12f) }
            Sfx.CRACK -> buf(0.12f).also { noise(it, 0, 0.09f, 0.5f, 77) ; tone(it, 0, 0.05f, 900f, 500f, 0.2f) }
            Sfx.HATCH -> buf(1.1f).also { b -> noise(b, 0, 0.12f, 0.5f, 3); arp(b, (0.15f * RATE).toInt(), listOf(523f, 659f, 784f, 1047f), 0.13f, 0.4f) }
            Sfx.STAGE_UP -> buf(1.2f).also { arp(it, 0, listOf(392f, 523f, 659f, 784f, 1047f), 0.14f, 0.4f) }
            Sfx.STAGE_DOWN -> buf(0.9f).also { arp(it, 0, listOf(523f, 440f, 349f), 0.22f, 0.35f) }
            Sfx.ACHIEVEMENT -> buf(0.8f).also { b -> tone(b, 0, 0.35f, 880f, 880f, 0.35f, harmonics = 0.15f); tone(b, (0.16f * RATE).toInt(), 0.55f, 1319f, 1319f, 0.35f, harmonics = 0.15f) }
            Sfx.POP -> buf(0.1f).also { tone(it, 0, 0.07f, 700f, 1500f, 0.45f, harmonics = 0.1f) }
            Sfx.OOPS -> buf(0.3f).also { tone(it, 0, 0.26f, 300f, 170f, 0.4f, harmonics = 0.6f) }
            Sfx.GAME_END -> buf(1.0f).also { arp(it, 0, listOf(523f, 659f, 784f), 0.12f, 0.4f, hold = 0.5f) }
            Sfx.WARDROBE -> buf(0.45f).also { b -> tone(b, 0, 0.2f, 1047f, 1047f, 0.3f, harmonics = 0.1f); tone(b, (0.1f * RATE).toInt(), 0.3f, 1568f, 1568f, 0.28f, harmonics = 0.1f) }
        }
    }

    private fun buf(sec: Float) = FloatArray((sec * RATE).toInt())

    /** Ton mit Gleiten von f0 nach f1, weicher Hüllkurve, Obertönen und Vibrato. */
    private fun tone(out: FloatArray, start: Int, sec: Float, f0: Float, f1: Float, amp: Float,
                     harmonics: Float = 0.3f, vibrato: Float = 0f, attack: Float = 0.01f) {
        val n = (sec * RATE).toInt()
        var ph = 0.0
        for (i in 0 until n) {
            val j = start + i; if (j >= out.size) break
            val u = i.toFloat() / n
            val f = f0 + (f1 - f0) * u
            val vib = 1f + vibrato * sin(2 * PI * 6.0 * i / RATE).toFloat()
            ph += 2 * PI * f * vib / RATE
            val env = min(1f, (i.toFloat() / RATE) / attack) * (1f - u).pow(1.6f)
            val s = sin(ph) + harmonics * 0.5 * sin(2 * ph) + harmonics * 0.25 * sin(3 * ph)
            out[j] += (amp * env * s / (1 + 0.75 * harmonics)).toFloat()
        }
        clip(out)
    }

    private fun arp(out: FloatArray, start: Int, notes: List<Float>, step: Float, amp: Float, hold: Float = 0.3f) {
        notes.forEachIndexed { k, f ->
            val len = if (k == notes.size - 1) hold + step else step * 1.6f
            tone(out, start + (k * step * RATE).toInt(), len, f, f, amp, harmonics = 0.2f)
        }
    }

    private fun noise(out: FloatArray, start: Int, sec: Float, amp: Float, seed: Long) {
        val r = GameRandom(seed); val n = (sec * RATE).toInt()
        for (i in 0 until n) {
            val j = start + i; if (j >= out.size) break
            out[j] += amp * (r.next() * 2f - 1f) * exp(-6f * i / n)
        }
        clip(out)
    }

    private fun clip(out: FloatArray) { for (i in out.indices) if (abs(out[i]) > 0.95f) out[i] = if (out[i] > 0) 0.95f else -0.95f }

    /** 16-Bit-PCM (Little Endian) für AudioTrack bzw. WAV. */
    fun pcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
}
