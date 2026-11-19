package com.focuslock.core

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Tamper-resistant countdown math. See [Deadline]. */
object TrustedClock {
    const val UNKNOWN_BOOT = -1

    @Volatile
    private var cachedBoot: Int? = null

    /** Settings.Global.BOOT_COUNT increments on every boot; it can't change while our process lives. */
    fun bootCount(context: Context): Int {
        cachedBoot?.let { return it }
        val value = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (_: Settings.SettingNotFoundException) {
            UNKNOWN_BOOT
        }
        cachedBoot = value
        return value
    }

    fun deadlineIn(context: Context, durationMs: Long): Deadline = deadlineIn(
        durationMs, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context)
    )

    fun remainingMs(context: Context, d: Deadline, maxMs: Long): Long = remainingMs(
        d, maxMs, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount(context)
    )

    /** Same remaining time, re-expressed against the current wall clock and boot. */
    fun reanchor(context: Context, d: Deadline, maxMs: Long): Deadline =
        deadlineIn(context, remainingMs(context, d, maxMs))

    // ---- pure functions (unit-tested) ----

    fun deadlineIn(durationMs: Long, nowWall: Long, nowElapsed: Long, boot: Int): Deadline =
        Deadline(wallMs = nowWall + durationMs, elapsedMs = nowElapsed + durationMs, bootCount = boot)

    fun remainingMs(d: Deadline, maxMs: Long, nowWall: Long, nowElapsed: Long, boot: Int): Long {
        val sameBoot = boot != UNKNOWN_BOOT && boot == d.bootCount
        val raw = if (sameBoot) d.elapsedMs - nowElapsed else d.wallMs - nowWall
        // Never more than the original length (e.g. clock moved backwards across a reboot).
        return raw.coerceIn(0L, maxMs)
    }
}
