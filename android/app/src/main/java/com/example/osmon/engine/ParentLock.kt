// ParentLock.kt: Eltern-PIN (rein, JVM). Gegenstück: ParentLock.swift (gleiche Regeln, gleiche Tests).
// PIN = 4 Ziffern. Gespeichert wird nur SHA-256(salt + ":" + pin) als Hex, nie die PIN selbst.
// Nach MAX_TRIES Fehlversuchen: Wartezeit 60 s, danach verdoppelt bis höchstens 15 Minuten.
// Nach dem Entsperren bleibt der Eltern-Bereich SESSION_MS offen.

package com.example.osmon.engine

import java.security.MessageDigest

object ParentLock {
    const val MAX_TRIES = 5
    const val SESSION_MS = 5 * 60_000L
    private const val BASE_WAIT_MS = 60_000L
    private const val MAX_WAIT_MS = 15 * 60_000L

    fun isValidPin(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun verify(pin: String, salt: String, storedHash: String) = isValidPin(pin) && hash(pin, salt) == storedHash

    /** Wartezeit nach `failures` Fehlversuchen in Folge (0 = sofort wieder versuchen). */
    fun waitMs(failures: Int): Long {
        if (failures < MAX_TRIES) return 0
        val n = (failures - MAX_TRIES).coerceAtMost(10)
        return minOf(MAX_WAIT_MS, BASE_WAIT_MS shl n)
    }

    /** Zu einfache PINs (1111, 1234, 4321 …) ablehnen. */
    fun isTooSimple(pin: String): Boolean {
        if (!isValidPin(pin)) return true
        val d = pin.map { it - '0' }
        val same = d.all { it == d[0] }
        val up = (1..3).all { d[it] == d[it - 1] + 1 }
        val down = (1..3).all { d[it] == d[it - 1] - 1 }
        return same || up || down
    }
}
