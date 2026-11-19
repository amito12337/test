package com.focuslock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakPolicyTest {
    @Test
    fun breakLengths_are_6_5_5_thenNone() {
        assertEquals(6 * 60_000L, BreakPolicy.breakDurationMs(0))
        assertEquals(5 * 60_000L, BreakPolicy.breakDurationMs(1))
        assertEquals(5 * 60_000L, BreakPolicy.breakDurationMs(2))
        assertNull(BreakPolicy.breakDurationMs(3))
    }

    @Test
    fun breaksLeft() {
        assertEquals(3, BreakPolicy.breaksLeft(0))
        assertEquals(0, BreakPolicy.breaksLeft(3))
        assertEquals(0, BreakPolicy.breaksLeft(5))
    }
}
