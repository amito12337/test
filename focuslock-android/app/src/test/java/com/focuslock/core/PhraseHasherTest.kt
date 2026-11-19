package com.focuslock.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseHasherTest {
    private val iterations = 1_000 // fast for tests

    @Test
    fun verifiesCorrectPhrase_andRejectsWrongOne() {
        val stored = PhraseHasher.hash("I really want to stop", iterations)
        assertTrue(PhraseHasher.verify("I really want to stop", stored))
        assertTrue(PhraseHasher.verify("  I really want to stop  ", stored)) // trimmed
        assertFalse(PhraseHasher.verify("i really want to stop", stored))    // case-sensitive
        assertFalse(PhraseHasher.verify("", stored))
    }

    @Test
    fun doesNotStorePlaintext_andIsSalted() {
        val a = PhraseHasher.hash("secret phrase", iterations)
        val b = PhraseHasher.hash("secret phrase", iterations)
        assertFalse(a.contains("secret"))
        assertNotEquals(a, b)
    }

    @Test
    fun rejectsMalformedStoredValue() {
        assertFalse(PhraseHasher.verify("x", "garbage"))
        assertFalse(PhraseHasher.verify("x", "v1:abc:def:ghi"))
    }
}
