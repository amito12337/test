package com.focuslock.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.focuslock.ui.FocusActivity

/** Brings the focus screen to the front from any context (service, receiver, activity). */
object FocusLauncher {
    private const val TAG = "FocusLauncher"

    fun intent(context: Context): Intent = Intent(context, FocusActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    )

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        balOptions(creator = true),
    )

    fun launch(context: Context) {
        // Prefer the accessibility service context: an app with a bound accessibility service
        // (or the "display over other apps" permission) is allowed to start activities from the background.
        val base: Context = BlockerAccessibilityService.instance ?: context.applicationContext
        try {
            // A PendingIntent sent by its own creator with explicit BAL opt-in is the most reliable path on 14+.
            pendingIntent(base).send(base, 0, null, null, null, null, balOptions(creator = false))
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent launch failed, falling back", e)
            try {
                base.startActivity(intent(base))
            } catch (e2: Exception) {
                Log.e(TAG, "Could not bring focus screen to front", e2)
            }
        }
    }

    private fun balOptions(creator: Boolean): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        @Suppress("DEPRECATION")
        if (creator) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        } else {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return options.toBundle()
    }
}
