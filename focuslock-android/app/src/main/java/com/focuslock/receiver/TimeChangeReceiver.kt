package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.core.SessionManager
import com.focuslock.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Date/time changed. The countdown itself runs on elapsedRealtime, so nothing is gained by
 * changing the clock; we only re-anchor the stored wall-clock copy (used after a reboot).
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SessionManager.init(context)
                SessionManager.reanchor()
                if (SessionManager.session != null) Notifications.refreshOngoing(context)
            } finally {
                pending.finish()
            }
        }
    }
}
