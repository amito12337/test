package com.focuslock.core

/**
 * A point in time stored two ways so the phone's clock can't be used to cheat:
 *  - [elapsedMs]: SystemClock.elapsedRealtime() target. Monotonic, includes deep sleep,
 *    unaffected by changing the date/time. Only valid within the same boot ([bootCount]).
 *  - [wallMs]: System.currentTimeMillis() target. Only used after a reboot, and kept
 *    re-anchored to the trusted elapsed clock while the phone is running.
 */
data class Deadline(val wallMs: Long, val elapsedMs: Long, val bootCount: Int)

data class Session(
    val totalMs: Long,
    val end: Deadline,
    val breaksUsed: Int,
    val breakEnd: Deadline?,
)

data class AppSettings(
    val phraseHash: String?,
    val allowedApps: Set<String>,
    val usePinning: Boolean,
    val lastDurationMin: Int,
    /** Settings / Device care / installer stay blocked during breaks so the blocker can't be disabled. */
    val lockSettingsDuringBreaks: Boolean,
) {
    companion object {
        val DEFAULT = AppSettings(
            phraseHash = null, allowedApps = emptySet(), usePinning = true, lastDurationMin = 25,
            lockSettingsDuringBreaks = true,
        )
    }
}

data class Snapshot(val session: Session?, val settings: AppSettings) {
    companion object {
        val EMPTY = Snapshot(null, AppSettings.DEFAULT)
    }
}

enum class Phase { IDLE, FOCUS, BREAK }
