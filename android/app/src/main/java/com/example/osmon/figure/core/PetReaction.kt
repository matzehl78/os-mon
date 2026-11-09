// PetReaction.kt: Reaktionen beim Antippen des Wesens (Gegenstück zu PetReaction.swift)
//
// 1:1 aus dem Prototyp (REACTIONS, pickReaction, OSLimbs, Körperbewegung). Antippen ist reiner Spaß
// und ändert keine Werte; für Zuneigung bleibt „Streicheln“ zuständig.
//
// Auswahl:  Winterschlaf -> Aufschrecken, kränklich -> Schwach, sonst erster Tipp im Zeitfenster ->
// Gruß zur Tageszeit (Morgen 6–10, Mittag 11–14, Abend 18–22, Nacht 22–6), danach 30 % Gruß;
// müde/gähnend -> Verschlafen; sonst Kichern 45 % / Freudendreher 25 % / Staunen 16 % / Verlegen 14 %.
// Lob von den Eltern -> Verlegen (HomeScreen.kt).
// 5× Tippen in 3 s -> Schwindelig.

package com.example.osmon.figure

import com.example.osmon.engine.Mood
import com.example.osmon.engine.Overlay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class ReactionKind(val durMs: Int, val fx: List<String>, val say: List<String>) {
    GIGGLE(1100, listOf("💗", "💖", "✨"), listOf("Hihi, das kitzelt!", "Hehe, nochmal!", "Hihi!")),
    SPIN(1100, listOf("⭐", "✨", "🌟"), listOf("Juhuu!", "Wiiie!")),
    SURPRISE(900, listOf("❗"), listOf("Oh! Hallo du!", "Huch!")),
    DIZZY(1500, listOf("💫", "💫"), listOf("Mir ist ganz schwindelig …", "Nicht so viel tippen!")),
    SLEEPY(1400, listOf("💤"), listOf("Gähn … noch fünf Minuten.", "Ich bin so müde …")),
    WEAK(900, listOf("🤒"), listOf("Mir geht’s nicht so gut …", "Danke, dass du da bist.")),
    STARTLE(900, listOf("❓"), listOf("Hm? Ich schlaf doch …", "Zzz … was ist?")),
    MORNING(1600, listOf("☀️", "✨"), listOf("Guten Morgen! Aaah, gut geschlafen.", "Morgen! Bereit für heute?")),
    NOON(1000, listOf("🍎", "☕"), listOf("Mittagspause? Ich bin dabei!", "Zeit für eine kleine Pause?")),
    EVENING(1600, listOf("🌙", "✨"), listOf("Schöner Abend, oder?", "Na, wie war dein Tag?")),
    NIGHT(1400, listOf("🌙", "💤"), listOf("Psst … schon spät. Leg mich doch hin?", "Gähn … wollen wir schlafen gehen?")),
    SHY(1800, listOf("💕", "🌸"), listOf("Oh … du machst mich ganz verlegen.", "Hihi … danke!", "Ach, du …"));

    /** Fröhliches Gesicht (Augen ^ ^) */
    val happy get() = this == GIGGLE || this == SPIN || this == MORNING || this == NOON || this == EVENING
    /** Hüpfen: Schatten wird kurz kleiner */
    val hops get() = this == GIGGLE || this == SPIN || this == SURPRISE || this == NOON
}

/** Laufende Reaktion. startMs = SystemClock.uptimeMillis() beim Antippen. */
data class ActiveReaction(val kind: ReactionKind, val startMs: Long, val say: String) {
    fun progress(nowMs: Long): Float = (nowMs - startMs).toFloat() / kind.durMs
    fun isActive(nowMs: Long) = progress(nowMs) in 0f..0.9999f
    fun saying(nowMs: Long) = nowMs - startMs < 2600
}

/** Wählt die Reaktion; merkt sich die letzten Tipps und den letzten Tageszeit-Gruß. */
class ReactionPicker(private val rnd: Random = Random.Default) {
    private val taps = ArrayDeque<Long>()
    private var lastGreet = ""

    fun pick(mood: Mood, overlays: Set<Overlay>, hour: Float, epochDay: Long, nowMs: Long): ReactionKind {
        while (taps.isNotEmpty() && nowMs - taps.first() > 3000) taps.removeFirst()
        taps.addLast(nowMs)
        if (taps.size >= 5) { taps.clear(); return ReactionKind.DIZZY }
        if (mood == Mood.HIBERNATION) return ReactionKind.STARTLE
        if (mood == Mood.SICK) return ReactionKind.WEAK
        val per = period(hour)
        if (per != null) {
            val key = "$epochDay-$per"
            if (key != lastGreet || rnd.nextFloat() < 0.3f) { lastGreet = key; return per }
        }
        if (mood == Mood.TIRED || Overlay.YAWNING in overlays) return ReactionKind.SLEEPY
        val r = rnd.nextFloat()
        return if (r < 0.45f) ReactionKind.GIGGLE else if (r < 0.7f) ReactionKind.SPIN
            else if (r < 0.86f) ReactionKind.SURPRISE else ReactionKind.SHY
    }

    fun start(kind: ReactionKind, nowMs: Long) = ActiveReaction(kind, nowMs, kind.say[rnd.nextInt(kind.say.size)])

    companion object {
        fun period(h: Float): ReactionKind? = when {
            h >= 6 && h < 10 -> ReactionKind.MORNING
            h >= 11 && h < 14 -> ReactionKind.NOON
            h >= 18 && h < 22 -> ReactionKind.EVENING
            h >= 22 || h < 6 -> ReactionKind.NIGHT
            else -> null
        }
    }
}

/** l / r: Arm nach außen-oben in Grad (negativ = nach innen); fl / fr: Fuß anheben (Zeichen-Einheiten). */
data class Limbs(val l: Float, val r: Float, val fl: Float, val fr: Float)

/** Körperbewegung in Zeichen-Einheiten, Drehpunkt (110, 210) = Mitte unten. */
data class BodyMotion(val dx: Float = 0f, val dy: Float = 0f, val rot: Float = 0f, val sx: Float = 1f, val sy: Float = 1f)

object ReactionMotion {
    private const val K = 263f / 240f      // Prototyp-Pixel (240er Bühne) -> Zeichen-Einheiten

    fun limbs(k: ReactionKind, u: Float): Limbs {
        val env = sin(PI * u).toFloat()
        val quick = if (u < 0.2f) u / 0.2f else (1 - u) / 0.8f
        val hold = min(1f, min(u * 4, (1 - u) * 4))
        val alt = sin(u * PI * 4).toFloat()
        return when (k) {
            ReactionKind.GIGGLE -> { val f = env * (40 + 25 * sin(u * PI * 6).toFloat()); Limbs(f, f, 6 * max(0f, alt), 6 * max(0f, -alt)) }
            ReactionKind.SPIN -> Limbs(85 * env, 85 * env, 4 * env, 4 * env)
            ReactionKind.SURPRISE -> Limbs(140 * quick, 140 * quick, 3 * quick, 3 * quick)
            ReactionKind.DIZZY -> Limbs((20 + 25 * sin(u * PI * 5).toFloat()) * (1 - u), (20 + 25 * sin(u * PI * 5 + PI).toFloat()) * (1 - u),
                3 * max(0f, alt) * (1 - u), 3 * max(0f, -alt) * (1 - u))
            ReactionKind.SLEEPY, ReactionKind.NIGHT -> Limbs(10 * env, 135 * hold, 0f, 0f)
            ReactionKind.MORNING -> Limbs(165 * env, 165 * env, 3 * env, 3 * env)
            ReactionKind.NOON -> Limbs(30 * env + 15 * alt * env, 30 * env - 15 * alt * env, 5 * max(0f, alt), 5 * max(0f, -alt))
            ReactionKind.EVENING -> { val w = 140 * hold + 20 * sin(u * PI * 8).toFloat() * hold; Limbs(10 * env, w, 0f, 0f) }
            ReactionKind.WEAK -> Limbs(-8 * env, -8 * env, 0f, 0f)
            ReactionKind.STARTLE -> Limbs(45 * quick, 45 * quick, 0f, 0f)
            ReactionKind.SHY -> Limbs(-14 * hold, -14 * hold, 3 * max(0f, alt) * hold, 3 * max(0f, -alt) * hold)   // Arme an den Körper, Füße scharren
        }
    }

    fun body(k: ReactionKind, u: Float): BodyMotion {
        val s = { x: Double -> sin(x).toFloat() }
        val ease = if (u < 0.5f) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2
        return when (k) {
            ReactionKind.GIGGLE -> BodyMotion(dy = -abs(s(2 * PI * u)) * (1 - u / 2) * 26 * K, sx = 1 + 0.05f * s(4 * PI * u), sy = 1 - 0.05f * s(4 * PI * u))
            ReactionKind.SURPRISE -> BodyMotion(dy = -s(PI * u) * 34 * K)
            ReactionKind.SPIN -> BodyMotion(dy = -s(PI * u) * 22 * K, sx = cos(2 * PI * ease).toFloat())
            ReactionKind.DIZZY -> BodyMotion(rot = 12 * s(6 * PI * u) * (1 - u))
            ReactionKind.SLEEPY, ReactionKind.NIGHT -> BodyMotion(sx = 1 + 0.04f * s(PI * u), sy = 1 - 0.07f * s(PI * u))
            ReactionKind.MORNING -> BodyMotion(sx = 1 - 0.05f * s(PI * u), sy = 1 + 0.12f * s(PI * u))
            ReactionKind.EVENING -> BodyMotion(rot = 7 * s(2 * PI * u))
            ReactionKind.NOON -> BodyMotion(dy = -abs(s(2 * PI * u)) * 14 * K)
            ReactionKind.WEAK, ReactionKind.STARTLE -> BodyMotion(dx = 3 * K * s(16 * PI * u) * (1 - u))
            ReactionKind.SHY -> BodyMotion(rot = 6 * s(3 * PI * u) * min(1f, min(u * 4, (1 - u) * 4)))   // schaukelt verlegen hin und her
        }
    }

    /** Leerlauf (gut gelaunt): ab und zu ein Hüpfer mit Stauchen und Strecken (Muster B), strahlend dazwischen
     *  leichtes Wippen (Muster C). Gleiche Kurve wie im 3D-Renderer; t = Sekunden. Gegenstück in PetReaction.swift. */
    fun idle(t: Double, radiant: Boolean): BodyMotion {
        val period = if (radiant) 6.5 else 9.5
        val hp = ((t % period) / 0.95).toFloat()
        if (hp >= 1f) return BodyMotion(dy = if (radiant) -2 * abs(sin(t * 2.1).toFloat()) else 0f)
        val up = if (hp > 0.18f && hp < 0.78f) sin((hp - 0.18f) / 0.6f * PI).toFloat() else 0f
        val sq = if (hp <= 0.18f) sin(hp / 0.18f * PI).toFloat() * 0.11f
            else if (hp >= 0.78f) sin((hp - 0.78f) / 0.22f * PI).toFloat() * 0.09f else 0f
        return BodyMotion(dy = -up * 13, sx = 1 + sq - up * 0.04f, sy = 1 - sq + up * 0.06f)
    }
}
