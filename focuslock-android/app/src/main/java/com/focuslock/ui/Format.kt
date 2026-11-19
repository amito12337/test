package com.focuslock.ui

/** 1:02:03 or 04:05. Rounds up so the display never shows 00:00 while time is left. */
fun formatClock(ms: Long): String {
    val total = ((ms.coerceAtLeast(0L) + 999L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
