package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focuslock.core.AllowPolicy
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager

/**
 * The main blocking layer. On every TYPE_WINDOW_STATE_CHANGED during FOCUS, if the foreground
 * package isn't us / phone / keyboard / an allowed app, the focus screen is relaunched.
 * Launcher (Home + Recents on One UI), Settings and other escape hatches are always blocked.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val activityCache = HashMap<String, Boolean>()
    private var lastBringBackAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        SessionManager.init(applicationContext)
        when (SessionManager.phase()) {
            Phase.FOCUS -> {
                FocusService.start(this)
                FocusLauncher.launch(this)
            }
            Phase.BREAK -> FocusService.start(this)
            Phase.IDLE -> Unit
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        when (SessionManager.phase()) {
            Phase.FOCUS -> handleFocus(event, pkg)
            Phase.BREAK -> handleBreak(pkg)
            Phase.IDLE -> Unit
        }
    }

    /** During a break the phone is free, except (optionally) the apps that could disable FocusLock. */
    private fun handleBreak(pkg: String) {
        if (!SessionManager.settings.lockSettingsDuringBreaks) return
        if (!AllowPolicy.isSettingsEscape(pkg) || AllowPolicy.isInPhoneCall(this)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBringBackAt < 350) return
        lastBringBackAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, "Settings stay locked until the session ends", Toast.LENGTH_SHORT).show()
    }

    private fun handleFocus(event: AccessibilityEvent, pkg: String) {
        if (AllowPolicy.isAlwaysAllowed(this, pkg)) return
        val hardBlocked = AllowPolicy.isHardBlocked(this, pkg)
        if (!hardBlocked && pkg in SessionManager.settings.allowedApps) return
        if (AllowPolicy.isInPhoneCall(this)) return // never interfere with a ringing or active call

        if (pkg == SYSTEM_UI) {
            if (looksLikeShade(event)) collapseShade()
            return
        }
        // Ignore toasts, dialogs and overlays of other packages: only real activities (or the
        // launcher / settings, whatever window they report) count as "switching app".
        if (!hardBlocked && !isActivity(pkg, event.className?.toString())) return
        bringBack(pkg)
    }

    private fun bringBack(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBringBackAt < 350) return
        lastBringBackAt = now
        // Back out of the blocked app first, so it can't stay visible in a pop-up / split window.
        if (!AllowPolicy.isLauncher(this, pkg)) performGlobalAction(GLOBAL_ACTION_BACK)
        FocusLauncher.launch(this)
    }

    /** Pulls the notification shade / quick panel back up. Harmless if it isn't open. */
    fun collapseShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    private fun looksLikeShade(event: AccessibilityEvent): Boolean {
        val blob = buildString {
            event.text.forEach { append(it).append(' ') }
            append(event.contentDescription ?: "").append(' ')
            append(event.className ?: "")
        }.lowercase()
        return SHADE_HINTS.any { it in blob }
    }

    private fun isActivity(pkg: String, cls: String?): Boolean {
        if (cls.isNullOrEmpty()) return false
        return activityCache.getOrPut("$pkg/$cls") {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set

        private const val SYSTEM_UI = "com.android.systemui"
        private val SHADE_HINTS = listOf(
            "notification shade", "notificationshade", "notification panel", "notificationpanel",
            "quick settings", "quicksettings", "quick panel", "quickpanel", "shade",
        )
    }
}
