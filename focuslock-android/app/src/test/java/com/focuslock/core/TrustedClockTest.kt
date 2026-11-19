package com.focuslock.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedClockTest {
    private val hour = 3_600_000L

    @Test
    fun sameBoot_ignoresWallClockChanges() {
        val d = TrustedClock.deadlineIn(hour, nowWall = 1_000_000L, nowElapsed = 50_000L, boot = 7)
        // 10 minutes really pass, but the user moves the clock 5 hours forward.
        val r = TrustedClock.remainingMs(d, hour, nowWall = 1_000_000L + 5 * hour, nowElapsed = 50_000L + 600_000L, boot = 7)
        assertEquals(hour - 600_000L, r)
    }

    @Test
    fun afterReboot_usesWallClock() {
        val d = TrustedClock.deadlineIn(hour, nowWall = 1_000_000L, nowElapsed = 50_000L, boot = 7)
        // Rebooted (boot 8): elapsed restarted near 0, 20 real minutes passed by the wall clock.
        val r = TrustedClock.remainingMs(d, hour, nowWall = 1_000_000L + 1_200_000L, nowElapsed = 30_000L, boot = 8)
        assertEquals(hour - 1_200_000L, r)
    }

    @Test
    fun afterReboot_clockMovedBack_isCappedAtSessionLength() {
        val d = TrustedClock.deadlineIn(hour, nowWall = 10 * hour, nowElapsed = 50_000L, boot = 7)
        val r = TrustedClock.remainingMs(d, hour, nowWall = 0L, nowElapsed = 10L, boot = 8)
        assertEquals(hour, r)
    }

    @Test
    fun expired_isZeroNotNegative() {
        val d = TrustedClock.deadlineIn(1_000L, nowWall = 0L, nowElapsed = 0L, boot = 1)
        assertEquals(0L, TrustedClock.remainingMs(d, 1_000L, nowWall = 0L, nowElapsed = 5_000L, boot = 1))
    }
}
