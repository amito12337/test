package com.focuslock.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

object AppCatalog {
    private const val ICON_PX = 96

    /** Apps with a launcher icon that the user may put on the allowed list. */
    fun selectableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { AllowPolicy.canBeUserAllowed(context, it) }
            .mapNotNull { entry(pm, it) }
            .sortedBy { it.label.lowercase() }
    }

    fun entries(context: Context, packages: Collection<String>): List<AppEntry> =
        packages.mapNotNull { entry(context.packageManager, it) }.sortedBy { it.label.lowercase() }

    private fun entry(pm: PackageManager, pkg: String): AppEntry? = try {
        val info = pm.getApplicationInfo(pkg, 0)
        val icon = try {
            pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        } catch (_: Exception) {
            null
        }
        AppEntry(pkg, pm.getApplicationLabel(info).toString(), icon)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
