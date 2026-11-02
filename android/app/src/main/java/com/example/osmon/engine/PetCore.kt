// PetCore.kt: plattformunabhängige Pet Engine (reines Kotlin, keine Android-Imports)
// Ablage im KMP-Projekt: commonMain. Alle Funktionen sind rein und deterministisch.
//
// Sie bildet die Formeln aus dem PRD (Kapitel 2) vollständig ab und ersetzt die vereinfachte
// Energie-Logik aus OSMonWidget.kt: dort target()/approach() durch PetCore.energy()/PetCore.step() ersetzen.
//
// Fehlende Eingaben (null = Sensor nicht verfügbar oder Erlaubnis fehlt) blenden den Wert aus,
// die Gewichte der übrigen werden renormiert (degradierter Betrieb, siehe PRD 1.4).

package com.example.osmon.engine

import kotlin.math.exp
import kotlin.math.pow

/** Eigene Gruppen-Enum, damit die Engine ohne Plattform-Code auskommt. Mapping: AppGroup.valueOf(g.name). */
enum class AppGroup { SOCIAL, ENTERTAINMENT, GAMES, PRODUCTIVITY, OTHER }

data class Config(
    val screenGoalMin: Float = 180f,      // Z, nach Woche 1 adaptiv: 0,8 * Median, begrenzt auf 90..240
    val stepGoal: Int = 7000,
    val pickupRef: Float = 50f
)

data class Inputs(
    val batteryPct: Float? = null,
    val charging: Boolean = false,
    val lowPower: Boolean = false,
    val freeStorageRatio: Float? = null,  // frei / gesamt, 0..1
    val screenMinutes: Float? = null,     // heute
    val nightMinutes: Float? = null,
    val pickups: Int? = null,             // nur Android; null auf iOS
    val steps: Int? = null,
    val pats: Int = 0,                    // Streicheln heute
    val focusSessions: Int = 0            // abgeschlossene Fokus-Sessions heute
)

/** E Energie, R Ruhe, F Fitness, V Vitalität, A Zuneigung (je 0..100, null = ausgeblendet). */
data class PetStats(
    val e: Float? = null,
    val r: Float? = null,
    val f: Float? = null,
    val v: Float? = null,
    val a: Float? = null
)

enum class Mood(val min: Float) {
    HIBERNATION(0f), SICK(20f), TIRED(40f), CONTENT(65f), RADIANT(85f)
}

enum class Overlay { YAWNING, CHUBBY, STRESSED, SLUGGISH }

enum class Form { CHATTERLING, BINGEON, PIXELO, ZENFUCHS, WANDERO }

enum class Branch { RADIANT, STANDARD, WILD }

data class Evolution(val form: Form, val hybrid: Form?, val branch: Branch)

data class Snapshot(
    val stats: PetStats,
    val wellbeing: Float,
    val mood: Mood,
    val overlays: Set<Overlay>
)

object PetCore {
    private const val HYSTERESIS = 5f
    private const val MAX_PATS = 4        // Streicheln pro Tag gedeckelt (UI erlaubt 1x pro 3 h)

    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    // ---------- Zielwerte aus Sensordaten ----------

    fun energy(i: Inputs): Float? = i.batteryPct?.let {
        (it * (if (i.lowPower) 0.85f else 1f) + (if (i.charging) 10f else 0f)).coerceIn(0f, 100f)
    }

    fun fitness(i: Inputs): Float? = i.freeStorageRatio?.let {
        100f * clamp01((it - 0.05f) / 0.25f).pow(0.8f)
    }

    /** Stress S. Mit Pickups (Android) 0,65/0,25/0,10, ohne (iOS) 0,72/0,28. */
    fun stress(i: Inputs, c: Config): Float? {
        val t = i.screenMinutes ?: return null
        val n = i.nightMinutes ?: 0f
        val x = if (i.pickups != null) {
            0.65f * t / (2f * c.screenGoalMin) + 0.25f * n / 45f + 0.10f * i.pickups / (2f * c.pickupRef)
        } else {
            0.72f * t / (2f * c.screenGoalMin) + 0.28f * n / 45f
        }
        return (100f * x).coerceIn(0f, 100f)
    }

    fun rest(i: Inputs, c: Config): Float? = stress(i, c)?.let { 100f - it }

    fun vitality(i: Inputs, c: Config): Float? = i.steps?.let {
        100f * clamp01(it.toFloat() / c.stepGoal).pow(0.7f)
    }

    /** Zuneigung A = 0,7*R + 0,3*Interaktion; ohne R nur die Interaktionskomponente. */
    fun affection(rest: Float?, i: Inputs): Float {
        val interaction = minOf(100f, 25f * minOf(i.pats, MAX_PATS) + 50f * i.focusSessions)
        return if (rest != null) 0.7f * rest + 0.3f * interaction else interaction
    }

    fun targets(i: Inputs, c: Config): PetStats {
        val r = rest(i, c)
        return PetStats(e = energy(i), r = r, f = fitness(i), v = vitality(i, c), a = affection(r, i))
    }

    // ---------- Trägheit ----------

    /**
     * Nähert die Werte den Zielen an (exponentiell, zeitbasiert statt "pro Update").
     * E: Zeitkonstante 0,5 h, R: 1,5 h, A: 6 h. F und V folgen dem Ziel direkt
     * (Speicher und Schrittzähler ändern sich ohnehin träge bzw. monoton).
     */
    fun step(prev: PetStats?, target: PetStats, dtHours: Float): PetStats {
        val dt = dtHours.coerceAtLeast(0f)
        fun ease(p: Float?, t: Float?, tau: Float): Float? = when {
            t == null -> null
            p == null -> t
            else -> p + (t - p) * (1f - exp(-dt / tau))
        }
        return PetStats(
            e = ease(prev?.e, target.e, 0.5f),
            r = ease(prev?.r, target.r, 1.5f),
            f = target.f,
            v = target.v,
            a = ease(prev?.a, target.a, 6f)
        )
    }

    // ---------- Gesamtwohlbefinden ----------

    /**
     * H_roh = 0,30*E + 0,25*R + 0,20*F + 0,15*V + 0,10*A (renormiert über vorhandene Werte)
     * H = H_roh * (0,6 + 0,4 * min(E,R,F,V) / 100)   ("schwächstes Glied")
     */
    fun wellbeing(s: PetStats): Float {
        val parts = listOfNotNull(
            s.e?.let { 0.30f to it }, s.r?.let { 0.25f to it }, s.f?.let { 0.20f to it },
            s.v?.let { 0.15f to it }, s.a?.let { 0.10f to it }
        )
        if (parts.isEmpty()) return 50f
        val weightSum = parts.sumOf { it.first.toDouble() }.toFloat()
        val raw = parts.sumOf { (it.first * it.second).toDouble() }.toFloat() / weightSum
        val weakest = listOfNotNull(s.e, s.r, s.f, s.v).minOrNull() ?: 100f
        return (raw * (0.6f + 0.4f * weakest / 100f)).coerceIn(0f, 100f)
    }

    // ---------- Zustände ----------

    /** Hysterese ±5 Punkte gegen Flackern an den Grenzen. */
    fun mood(h: Float, prev: Mood?): Mood {
        val all = Mood.values()
        var s = prev ?: all.last { h >= it.min }
        while (s.ordinal < all.lastIndex && h >= all[s.ordinal + 1].min + HYSTERESIS) s = all[s.ordinal + 1]
        while (s.ordinal > 0 && h < s.min - HYSTERESIS) s = all[s.ordinal - 1]
        return s
    }

    fun overlays(s: PetStats): Set<Overlay> = buildSet {
        if ((s.e ?: 100f) < 15f) add(Overlay.YAWNING)
        if ((s.f ?: 100f) < 20f) add(Overlay.CHUBBY)
        if ((s.r ?: 100f) < 30f) add(Overlay.STRESSED)      // R < 30 entspricht S > 70
        if ((s.v ?: 100f) < 20f) add(Overlay.SLUGGISH)
    }

    /** Ein kompletter Update-Zyklus: Eingaben -> neuer Zustand. */
    fun tick(prev: Snapshot?, i: Inputs, c: Config, dtHours: Float): Snapshot {
        val stats = step(prev?.stats, targets(i, c), dtHours)
        val h = wellbeing(stats)
        return Snapshot(stats, h, mood(h, prev?.mood), overlays(stats))
    }

    // ---------- Evolution ----------

    private val thresholds = mapOf(
        AppGroup.SOCIAL to (Form.CHATTERLING to 0.35f),
        AppGroup.ENTERTAINMENT to (Form.BINGEON to 0.35f),
        AppGroup.GAMES to (Form.PIXELO to 0.30f),
        AppGroup.PRODUCTIVITY to (Form.ZENFUCHS to 0.30f)
    )

    /**
     * shares: gewichtete Kategorienanteile der letzten 14 Tage (Summe ca. 1), z. B. aus blendShares().
     * outdoorShare: Anteil Outdoor/Navigation/Gesundheit; dafür braucht es eine eigene Gruppe im
     * Mapping (aktuell geht OTHER nicht in diese Zahl ein, daher Default 0).
     * Wandero greift sonst im Balance-Fall: keine Kategorie > 30 % und Tagesdurchschnitt <= Ziel.
     */
    fun evolve(
        shares: Map<AppGroup, Float>,
        avgDailyMinutes: Float,
        avgWellbeing14: Float,
        c: Config,
        outdoorShare: Float = 0f
    ): Evolution {
        val candidates = buildList {
            thresholds.forEach { (g, formAndMin) ->
                val share = shares[g] ?: 0f
                if (share >= formAndMin.second) add(formAndMin.first to share)
            }
            if (outdoorShare >= 0.25f) add(Form.WANDERO to outdoorShare)
        }.sortedByDescending { it.second }

        val topShare = shares.filterKeys { it != AppGroup.OTHER }.values.maxOrNull() ?: 0f
        val balanced = topShare <= 0.30f && avgDailyMinutes <= c.screenGoalMin

        val form = when {
            candidates.isNotEmpty() -> candidates[0].first
            balanced -> Form.WANDERO
            else -> thresholds.maxByOrNull { shares[it.key] ?: 0f }!!.value.first   // nächstliegende Form
        }
        // Hybrid-Merkmal: Zweitplatzierter weniger als 5 Prozentpunkte dahinter
        val hybrid = candidates.getOrNull(1)
            ?.takeIf { candidates[0].second - it.second < 0.05f }?.first

        val branch = when {
            avgWellbeing14 >= 65f -> Branch.RADIANT
            avgWellbeing14 < 40f -> Branch.WILD
            else -> Branch.STANDARD
        }
        return Evolution(form, hybrid, branch)
    }
}
