// SoundSynthTest.kt: Stimme und Geräusche – Länge, Lautstärke, Tonhöhe je Figur (gleiche Fälle wie SoundSynthTests.swift)

package com.example.osmon.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundSynthTest {
    @Test fun syllablesFollowWords() {
        assertEquals(2, SoundSynth.syllables("Hi"))
        assertEquals(4, SoundSynth.syllables("Hallo, wie geht’s dir?"))
        assertEquals(8, SoundSynth.syllables("eins zwei drei vier fünf sechs sieben acht neun zehn"))
        assertEquals(2, SoundSynth.syllables("🌟 !"))
    }

    @Test fun pitchPerFigureAndStage() {
        val a = SoundSynth.voicePitch("lumo", null)
        assertTrue(a in 280f..460f)
        assertEquals(a * 1.35f, SoundSynth.voicePitch("lumo", GrowthStage.BABY))
        val ids = listOf("lumo", "minto", "lila", "glut", "kasto", "tropfi", "wolki", "rauti", "sunni", "orbo", "schnecki")
        assertTrue(ids.map { SoundSynth.voicePitch(it, null) }.toSet().size >= 5)   // klingen verschieden
    }

    @Test fun soundsAreAudibleAndClipped() {
        for (s in Sfx.entries) {
            val b = SoundSynth.sfx(s, "lumo")
            assertTrue(b.isNotEmpty() && b.size < SoundSynth.RATE * 2, s.name)
            val peak = b.maxOf { abs(it) }
            assertTrue(peak in 0.1f..0.95f, "${s.name} $peak")
        }
        val v = SoundSynth.babble("Hallo! Wie geht es dir?", "kasto", GrowthStage.CHILD)
        assertTrue(v.size in SoundSynth.RATE / 2..SoundSynth.RATE * 2)
        assertTrue(v.maxOf { abs(it) } in 0.1f..0.95f)
        assertEquals(v.toList(), SoundSynth.babble("Hallo! Wie geht es dir?", "kasto", GrowthStage.CHILD).toList())   // gleich bei gleichem Satz
    }
}
