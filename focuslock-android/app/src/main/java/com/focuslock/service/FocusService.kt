package com.focuslock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focuslock.core.Permissions
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that lives for the whole session (including breaks). It keeps the process
 * alive, shows the ongoing countdown, ends breaks / the session on time and re-anchors the clock.
 */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    private var lastPhase: Phase? = null
    private var lastBreaksUsed = -1
    private var lastRefreshAt = 0L
    private var ticks = 0
    private var warnedBreakSoon = false
    private var warnedA11y = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    if (!tickOnce()) break
                    delay(1_000)
                }
            }
        }
        return START_STICKY
    }

    private fun goForeground() {
        val n = Notifications.ongoing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, Notifications.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifications.ID_ONGOING, n)
        }
    }

    /** @return false when the session is over and the service should stop. */
    private suspend fun tickOnce(): Boolean {
        SessionManager.tick()
        val s = SessionManager.session
        if (s == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        val phase = SessionManager.phase(s)
        val now = SystemClock.elapsedRealtime()

        if (++ticks % 10 == 0) SessionManager.reanchor()

        if (phase != lastPhase || s.breaksUsed != lastBreaksUsed || now - lastRefreshAt > 30_000) {
            Notifications.refreshOngoing(this)
            lastPhase = phase
            lastBreaksUsed = s.breaksUsed
            lastRefreshAt = now
        }

        if (phase == Phase.BREAK) {
            val left = SessionManager.breakRemainingMs(s)
            if (left in 1..30_000 && !warnedBreakSoon) {
                Notifications.showBreakEndingSoon(this)
                warnedBreakSoon = true
            }
        } else if (warnedBreakSoon) {
            warnedBreakSoon = false
            Notifications.cancelBreakEndingSoon(this)
        }

        if (phase == Phase.FOCUS && !Permissions.accessibilityEnabled(this)) {
            if (!warnedA11y) {
                warnedA11y = true
                Notifications.showAccessibilityOff(this)
                FocusLauncher.launch(this)
            }
        } else if (warnedA11y) {
            warnedA11y = false
            Notifications.cancelAccessibilityOff(this)
        }
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException if battery is still optimized.
                Log.e("FocusService", "Could not start foreground service", e)
            }
        }
    }
}
