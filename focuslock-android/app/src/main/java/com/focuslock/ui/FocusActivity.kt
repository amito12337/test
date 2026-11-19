package com.focuslock.ui

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import com.focuslock.service.BlockerAccessibilityService
import com.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.launch

class FocusActivity : ComponentActivity() {

    private var lastPinRequestAt = 0L
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusTheme {
                FocusScreen(
                    onPhone = ::openDialer,
                    onEmergency = ::openEmergencyDialer,
                    onOpenApp = ::openAllowedApp,
                    onUsePhone = ::goHome,
                    onDone = ::finishToMain,
                    onPhaseChanged = { syncPinning() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        lifecycleScope.launch {
            SessionManager.tick()
            syncPinning()
        }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Notification shade / quick panel pulled down over the focus screen: push it back up.
        // (Collapsing the shade is a no-op for other system dialogs such as the power menu.)
        if (!hasFocus && resumed && SessionManager.phase() == Phase.FOCUS) {
            BlockerAccessibilityService.instance?.collapseShade()
        }
    }

    private val isPinned: Boolean
        get() = getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** Screen pinning on during FOCUS (extra layer), off otherwise. */
    private fun syncPinning() {
        val phase = SessionManager.phase()
        try {
            if (phase == Phase.FOCUS && SessionManager.settings.usePinning && resumed && !isPinned) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastPinRequestAt > 5_000) {
                    lastPinRequestAt = now
                    startLockTask()
                }
            } else if (phase != Phase.FOCUS && isPinned) {
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.w("FocusActivity", "Screen pinning change failed", e)
        }
    }

    private fun unpin() {
        try {
            if (isPinned) stopLockTask()
        } catch (_: Exception) {
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w("FocusActivity", "No activity for $intent", e)
        }
    }

    private fun openDialer() {
        unpin()
        launch(Intent(Intent.ACTION_DIAL))
    }

    private fun openEmergencyDialer() {
        unpin()
        // Emergency dialer (works even with no SIM / locked SIM). Falls back to the normal dialer.
        val emergency = Intent("com.android.phone.EmergencyDialer.DIAL")
        if (emergency.resolveActivity(packageManager) != null) launch(emergency) else launch(Intent(Intent.ACTION_DIAL))
    }

    private fun openAllowedApp(pkg: String) {
        if (pkg !in SessionManager.settings.allowedApps) return
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        unpin()
        launch(intent)
    }

    private fun goHome() {
        unpin()
        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun finishToMain() {
        unpin()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
