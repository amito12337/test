package com.focuslock.core

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.focuslock.service.BlockerAccessibilityService

data class PermissionStatus(
    val accessibility: Boolean,
    val battery: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
) {
    val allGranted get() = accessibility && battery && notifications && overlay

    companion object {
        val NONE = PermissionStatus(false, false, false, false)
    }
}

object Permissions {

    fun status(context: Context) = PermissionStatus(
        accessibility = accessibilityEnabled(context),
        battery = batteryUnrestricted(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        overlay = Settings.canDrawOverlays(context),
    )

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")

    fun openAccessibility(context: Context) {
        val component = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
        val details = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        } else null
        start(context, details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openBattery(context: Context) {
        @Suppress("BatteryLife")
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(context))
        start(context, request, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openNotificationSettings(context: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        start(context, i, appDetailsIntent(context))
    }

    fun openOverlay(context: Context) {
        start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(context)), appDetailsIntent(context))
    }

    fun openAppInfo(context: Context) = start(context, appDetailsIntent(context), null)

    fun openScreenPinning(context: Context) =
        start(context, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(context))

    private fun start(context: Context, primary: Intent?, fallback: Intent?) {
        for (intent in listOfNotNull(primary, fallback)) {
            try {
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
