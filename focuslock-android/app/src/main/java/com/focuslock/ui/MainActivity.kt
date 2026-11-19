package com.focuslock.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.focuslock.core.PermissionStatus
import com.focuslock.core.Permissions
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import com.focuslock.service.FocusService
import com.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.launch

/** Setup checklist, duration picker and settings. Only usable while no session is running. */
class MainActivity : ComponentActivity() {

    private val status = mutableStateOf(PermissionStatus.NONE)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Permissions.openNotificationSettings(this)
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusTheme {
                MainRoot(
                    status = status.value,
                    onSetup = ::handleSetup,
                    onStart = ::startSession,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        lifecycleScope.launch {
            SessionManager.tick()
            if (SessionManager.phase() != Phase.IDLE) {
                startActivity(Intent(this@MainActivity, FocusActivity::class.java))
                finish()
            }
        }
    }

    private fun refresh() {
        status.value = Permissions.status(this)
    }

    private fun handleSetup(action: SetupAction) {
        when (action) {
            SetupAction.ACCESSIBILITY -> Permissions.openAccessibility(this)
            SetupAction.BATTERY -> Permissions.openBattery(this)
            SetupAction.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Permissions.openNotificationSettings(this)
                }
            SetupAction.OVERLAY -> Permissions.openOverlay(this)
            SetupAction.APP_INFO -> Permissions.openAppInfo(this)
            SetupAction.PINNING -> Permissions.openScreenPinning(this)
        }
    }

    private fun startSession(minutes: Int) {
        if (!Permissions.status(this).allGranted) return
        lifecycleScope.launch {
            SessionManager.setLastDurationMin(minutes)
            if (SessionManager.startSession(minutes * 60_000L)) {
                FocusService.start(this@MainActivity)
                startActivity(Intent(this@MainActivity, FocusActivity::class.java))
                finish()
            }
        }
    }
}

enum class SetupAction { ACCESSIBILITY, BATTERY, NOTIFICATIONS, OVERLAY, APP_INFO, PINNING }
