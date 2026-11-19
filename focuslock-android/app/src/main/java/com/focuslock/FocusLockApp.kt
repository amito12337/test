package com.focuslock

import android.app.Application
import com.focuslock.core.AllowPolicy
import com.focuslock.core.SessionManager
import com.focuslock.service.FocusLauncher
import com.focuslock.service.Notifications

class FocusLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SessionManager.onBreakEnded = { ctx ->
            Notifications.refreshOngoing(ctx)
            // Don't pull you out of a phone call; the blocker catches the next app switch after it.
            if (!AllowPolicy.isInPhoneCall(ctx)) FocusLauncher.launch(ctx)
        }
        SessionManager.onSessionFinished = { ctx -> Notifications.showSessionComplete(ctx) }
        SessionManager.init(this)
    }
}
