// PushPolicy.kt: Regel-Engine für die 5 Haustier-Benachrichtigungen (reines Kotlin, KMP-tauglich)
//
// Die Engine ENTSCHEIDET nur (welche Push, wenn überhaupt). Zustellung und Persistenz liegen
// bei der Plattform: lokale Notification (Android NotificationManager / iOS UNUserNotificationCenter),
// PushState als JSON im State Store ablegen. Alle Funktionen sind rein und testbar.
//
// Regeln (PRD 3.2):
//  - maximal 3 Pushes pro Tag (sinkt, wenn der Nutzer Pushes ignoriert)
//  - Ruhezeit 22:00-08:00, Ausnahme NIGHT_USE (der Nutzer ist dann ohnehin aktiv am Handy)
//  - mindestens 2 h Abstand zwischen zwei Pushes
//  - Cooldown je Typ, Priorität nach Reihenfolge in PushType
//
// Hinweis zum PRD: Dort stand "Ruhezeit 22-8 Uhr" und gleichzeitig ein Push um 23:47.
// Das war ein Widerspruch. Gelöst wie oben: NIGHT_USE darf in der Ruhezeit feuern,
// aber nur bei aktiver Nutzung (>= 10 Min seit 23:30) und höchstens einmal pro Nacht.

package com.example.osmon.engine

import kotlin.math.roundToInt

/** Reihenfolge = Priorität (BATTERY_LOW zuerst). */
enum class PushType { BATTERY_LOW, NIGHT_USE, STORAGE_FULL, SCREEN_GOAL, STEPS_PRAISE }

data class PushSignals(
    val batteryPct: Float? = null,
    val charging: Boolean = false,
    val freeStorageRatio: Float? = null,
    val screenMinutes: Float? = null,
    val screenGoalMin: Float = 180f,
    val nightActiveMinutes: Float? = null,   // aktive Bildschirmzeit seit 23:30
    val steps: Int? = null,
    val stepGoal: Int = 7000
)

/** Lokale Uhrzeit liefert die Plattform (Engine bleibt zeitzonenfrei). dayKey z. B. "2026-11-02". */
data class PushClock(val nowMs: Long, val minuteOfDay: Int, val dayKey: String)

data class PushState(
    val dayKey: String = "",
    val sentToday: Int = 0,
    val lastSentMs: Map<PushType, Long> = emptyMap(),
    val lastAnyMs: Long? = null,
    val ignoredStreak: Int = 0               // aufeinanderfolgend ignorierte Pushes
)

data class PushMessage(
    val type: PushType,
    val title: String,
    val body: String,
    val actions: List<String> = listOf("Fokus 15 Min", "Später")
)

object PushPolicy {
    const val BASE_CAP = 3
    const val MIN_GAP_MS = 2 * 3_600_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    private val cooldownMs = mapOf(
        PushType.BATTERY_LOW to 6 * HOUR_MS,
        PushType.NIGHT_USE to 20 * HOUR_MS,          // einmal pro Nacht
        PushType.STORAGE_FULL to 7 * DAY_MS,         // Speicher voll nervt sonst schnell
        PushType.SCREEN_GOAL to 20 * HOUR_MS,        // einmal pro Tag
        PushType.STEPS_PRAISE to 20 * HOUR_MS
    )

    /** 3 Pushes/Tag; je 3 ignorierte in Folge sinkt das Limit um 1 (Minimum 1). */
    fun dailyCap(s: PushState): Int = BASE_CAP - minOf(2, s.ignoredStreak / 3)

    fun isQuiet(minuteOfDay: Int): Boolean = minuteOfDay >= 22 * 60 || minuteOfDay < 8 * 60

    private fun rollover(s: PushState, dayKey: String) =
        if (s.dayKey == dayKey) s else s.copy(dayKey = dayKey, sentToday = 0)

    private fun eligible(type: PushType, sig: PushSignals, clock: PushClock): Boolean = when (type) {
        PushType.BATTERY_LOW -> (sig.batteryPct ?: 100f) <= 15f && !sig.charging
        PushType.NIGHT_USE ->
            (clock.minuteOfDay >= 23 * 60 + 30 || clock.minuteOfDay < 3 * 60) &&
                (sig.nightActiveMinutes ?: 0f) >= 10f
        PushType.STORAGE_FULL -> (sig.freeStorageRatio ?: 1f) < 0.08f
        PushType.SCREEN_GOAL -> (sig.screenMinutes ?: 0f) >= sig.screenGoalMin
        // 80 % des Ziels erreicht (ganzzahlig gerechnet, kein Float-Vergleich)
        PushType.STEPS_PRAISE -> sig.steps?.let { it * 5 >= sig.stepGoal * 4 } ?: false
    }

    /** Liefert die Push, die jetzt gesendet werden soll, oder null. */
    fun evaluate(state: PushState, sig: PushSignals, clock: PushClock): PushMessage? {
        val s = rollover(state, clock.dayKey)
        if (s.sentToday >= dailyCap(s)) return null
        s.lastAnyMs?.let { if (clock.nowMs - it < MIN_GAP_MS) return null }

        val quiet = isQuiet(clock.minuteOfDay)
        for (type in PushType.values()) {
            if (quiet && type != PushType.NIGHT_USE) continue
            val last = s.lastSentMs[type]
            if (last != null && clock.nowMs - last < cooldownMs.getValue(type)) continue
            if (eligible(type, sig, clock)) return message(type, sig, clock)
        }
        return null
    }

    // ---------- Zustandsübergänge ----------

    fun onSent(state: PushState, type: PushType, clock: PushClock): PushState {
        val s = rollover(state, clock.dayKey)
        return s.copy(
            sentToday = s.sentToday + 1,
            lastSentMs = s.lastSentMs + (type to clock.nowMs),
            lastAnyMs = clock.nowMs
        )
    }

    /** Push wurde weggewischt oder verfiel unbeachtet. */
    fun onIgnored(state: PushState): PushState = state.copy(ignoredStreak = state.ignoredStreak + 1)

    /** Nutzer hat getippt oder eine Aktion gewählt. */
    fun onEngaged(state: PushState): PushState = state.copy(ignoredStreak = 0)

    // ---------- Texte ----------

    private fun pad2(n: Int) = n.toString().padStart(2, '0')

    private fun thousands(n: Int): String =
        n.toString().reversed().chunked(3).joinToString(".").reversed()

    private fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m Min."
            m == 0 -> "$h Std."
            else -> "$h Std. $m Min."
        }
    }

    fun message(type: PushType, sig: PushSignals, clock: PushClock): PushMessage = when (type) {
        PushType.BATTERY_LOW -> PushMessage(
            type, "Gähn … 🪫",
            "Ich hab nur noch ${(sig.batteryPct ?: 0f).roundToInt()} % Energie. " +
                "Gibst du mir Strom, bevor ich einschlafe?"
        )
        PushType.STORAGE_FULL -> PushMessage(
            type, "Die Haut spannt! 🍔",
            "Nur noch ${((sig.freeStorageRatio ?: 0f) * 100f).roundToInt()} % Platz, " +
                "ich passe kaum noch in mich rein. Misten wir 1 GB alte Videos aus?"
        )
        PushType.SCREEN_GOAL -> PushMessage(
            type, "Meine Augen werden eckig 👀",
            "${duration((sig.screenMinutes ?: 0f).roundToInt())} Bildschirm heute. " +
                "Eine 10-Minuten-Pause für uns beide?"
        )
        PushType.NIGHT_USE -> PushMessage(
            type, "Psst … 🌙",
            "Es ist ${pad2(clock.minuteOfDay / 60)}:${pad2(clock.minuteOfDay % 60)} " +
                "und ich kann nicht schlafen. Legst du uns schlafen?"
        )
        PushType.STEPS_PRAISE -> {
            val steps = sig.steps ?: 0
            val body = if (steps >= sig.stepGoal)
                "${thousands(steps)} Schritte, Ziel geschafft! Ich bin ganz stolz auf uns."
            else
                "${thousands(steps)} Schritte, wow. Noch eine Runde frische Luft, dann sind wir strahlend?"
            PushMessage(type, "Ich hab Muckis! 💪", body)
        }
    }
}

// ---------- Einbindung ----------
//
// Android: PushDelivery.kt (Aufruf aus PetWorker nach PetSync.tick, Reaktionen über PushActionReceiver).
// iOS:     PushPolicy.swift (Port) + PushDelivery.swift / PushResponder.swift.
// Parität: golden_push.py -> golden_push.json, geprüft in PushGoldenTest.kt und PushGoldenTests.swift.
