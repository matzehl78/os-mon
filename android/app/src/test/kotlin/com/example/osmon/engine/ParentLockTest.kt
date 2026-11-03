// ParentLockTest.kt: PIN prüfen, Hash, Wartezeiten (gleiche Fälle wie ParentLockTests.swift)

package com.example.osmon.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParentLockTest {
    @Test fun validPins() {
        assertTrue(ParentLock.isValidPin("0429")); assertFalse(ParentLock.isValidPin("123")); assertFalse(ParentLock.isValidPin("12a4"))
        assertTrue(ParentLock.isTooSimple("1111")); assertTrue(ParentLock.isTooSimple("1234")); assertTrue(ParentLock.isTooSimple("9876"))
        assertFalse(ParentLock.isTooSimple("0429"))
    }

    @Test fun hashAndVerify() {
        // Referenz: printf 'abc:0429' | sha256sum
        assertEquals("5af02ec23a7d86f2efb251d1b65558b1a507b519b3b17e156c83a3a68882bcf6".length, ParentLock.hash("0429", "abc").length)
        val h = ParentLock.hash("0429", "abc")
        assertTrue(ParentLock.verify("0429", "abc", h)); assertFalse(ParentLock.verify("0428", "abc", h)); assertFalse(ParentLock.verify("0429", "abd", h))
        assertEquals(HASH_0429_ABC, h)
    }

    @Test fun waits() {
        assertEquals(0L, ParentLock.waitMs(4)); assertEquals(60_000L, ParentLock.waitMs(5))
        assertEquals(120_000L, ParentLock.waitMs(6)); assertEquals(900_000L, ParentLock.waitMs(20))
    }

    companion object { const val HASH_0429_ABC = "5af02ec23a7d86f2efb251d1b65558b1a507b519b3b17e156c83a3a68882bcf6" }
}
