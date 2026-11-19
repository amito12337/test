package com.focuslock.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focuslock.R
import com.focuslock.core.BreakPolicy
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import com.focuslock.ui.formatClock

object Notifications {
    private const val CH_SESSION = "session"
    private const val CH_ALERTS = "alerts"

    const val ID_ONGOING = 1
    private const val ID_COMPLETE = 2
    private const val ID_A11Y_OFF = 3
    private const val ID_BREAK_SOON = 4

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing countdown while a session or break is running"
                    setShowBadge(false)
                },
                NotificationChannel(CH_ALERTS, "Session alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Break ending, session complete, blocker problems"
                },
            )
        )
    }

    fun ongoing(context: Context): Notification {
        val s = SessionManager.session
        val b = NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(FocusLauncher.pendingIntent(context))
        val now = System.currentTimeMillis()
        when (SessionManager.phase(s)) {
            Phase.BREAK -> {
                val breakLeft = SessionManager.breakRemainingMs(s)
                b.setContentTitle("Break · ends in ${formatClock(breakLeft)}")
                    .setContentText("Session keeps running: ${formatClock(SessionManager.sessionRemainingMs(s))} left")
                    .setWhen(now + breakLeft)
            }
            Phase.FOCUS -> {
                val left = BreakPolicy.breaksLeft(s?.breaksUsed ?: 0)
                b.setContentTitle("Focusing")
                    .setContentText("Breaks left: $left/${BreakPolicy.MAX_BREAKS}")
                    .setWhen(now + SessionManager.sessionRemainingMs(s))
            }
            Phase.IDLE -> b.setContentTitle("Finishing session…").setShowWhen(false)
        }
        if (SessionManager.phase(s) != Phase.IDLE) {
            // The system renders a live countdown; we only refresh on changes.
            b.setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        return b.build()
    }

    fun refreshOngoing(context: Context) = post(context, ID_ONGOING, ongoing(context))

    fun showSessionComplete(context: Context) {
        cancel(context, ID_BREAK_SOON)
        cancel(context, ID_A11Y_OFF)
        post(
            context, ID_COMPLETE,
            alert(context).setContentTitle("Session complete").setContentText("Nice work. Everything is unlocked.")
                .setAutoCancel(true).build()
        )
    }

    fun showBreakEndingSoon(context: Context) = post(
        context, ID_BREAK_SOON,
        alert(context).setContentTitle("Break ends in 30 seconds").setContentText("FocusLock will bring you back.")
            .setTimeoutAfter(35_000).setAutoCancel(true).build()
    )

    fun cancelBreakEndingSoon(context: Context) = cancel(context, ID_BREAK_SOON)

    fun showAccessibilityOff(context: Context) = post(
        context, ID_A11Y_OFF,
        alert(context).setContentTitle("Blocker is off")
            .setContentText("Accessibility for FocusLock was turned off. Blocking only works while it is on.")
            .setOngoing(false).build()
    )

    fun cancelAccessibilityOff(context: Context) = cancel(context, ID_A11Y_OFF)

    private fun alert(context: Context) = NotificationCompat.Builder(context, CH_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_focus)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(FocusLauncher.pendingIntent(context))

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) nm.notify(id, n)
    }

    private fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
