package com.focuslock.core

object BreakPolicy {
    const val MAX_BREAKS = 3
    private val BREAK_MINUTES = listOf(6, 5, 5)
    val MAX_BREAK_MS: Long = BREAK_MINUTES.max() * 60_000L

    const val MAX_WRONG_TRIES = 3
    const val COOLDOWN_MS = 10_000L

    const val MIN_SESSION_MIN = 1
    const val MAX_SESSION_MIN = 24 * 60

    /** Length of the next break given how many have been used, or null if none are left. */
    fun breakDurationMs(breaksUsed: Int): Long? = BREAK_MINUTES.getOrNull(breaksUsed)?.let { it * 60_000L }

    fun breaksLeft(breaksUsed: Int): Int = (MAX_BREAKS - breaksUsed).coerceIn(0, MAX_BREAKS)
}
