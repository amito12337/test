package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import com.focuslock.service.FocusLauncher
import com.focuslock.service.FocusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Resumes a session after a reboot (or an app update over adb). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SessionManager.init(context)
                SessionManager.reanchor()
                SessionManager.tick()
                when (SessionManager.phase()) {
                    Phase.FOCUS -> {
                        FocusService.start(context)
                        FocusLauncher.launch(context)
                    }
                    Phase.BREAK -> FocusService.start(context)
                    Phase.IDLE -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
