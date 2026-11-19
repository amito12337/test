package com.focuslock.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager

/** Which packages are always allowed, never allowed, or user-configurable during a session. */
object AllowPolicy {

    /** Phone, in-call UI and emergency packages. These are NEVER blocked. */
    private val PHONE_PACKAGES = setOf(
        "com.android.phone",                 // emergency dialer, SIM / call services
        "com.android.server.telecom",
        "com.android.dialer",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.samsung.android.dialer",        // Samsung Phone app
        "com.samsung.android.incallui",      // Samsung in-call screen
        "com.samsung.android.app.telephonyui",
        "com.android.emergency",             // emergency information
        "com.samsung.android.emergency",
        "com.google.android.apps.safetyhub",
    )

    /** Apps that can turn the blocker off (accessibility, force stop, uninstall, permissions). */
    private val SETTINGS_ESCAPES = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.samsung.accessibility",         // One UI accessibility settings
        "com.samsung.android.lool",          // Device care (force stop / battery)
        "com.samsung.android.sm",
        "com.samsung.android.sm.devicesecurity",
        "com.samsung.android.app.settings.bixby",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /** Escape hatches. Always blocked during focus and can't be added to the allowed list. */
    private val HARD_BLOCKED = SETTINGS_ESCAPES + setOf(
        "com.sec.android.app.launcher",      // One UI Home + Recents
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.samsung.android.app.galaxyfinder",
    )

    private class Cache(val at: Long, val phone: Set<String>, val launchers: Set<String>, val ime: String?)

    @Volatile private var cache: Cache? = null
    private const val CACHE_TTL_MS = 15_000L

    private fun cache(context: Context): Cache {
        val now = SystemClock.elapsedRealtime()
        cache?.let { if (now - it.at < CACHE_TTL_MS) return it }
        val fresh = Cache(now, computePhone(context), computeLaunchers(context), computeIme(context))
        cache = fresh
        return fresh
    }

    private fun computePhone(context: Context): Set<String> {
        val result = PHONE_PACKAGES.toMutableSet()
        val telecom = context.getSystemService(TelecomManager::class.java)
        try {
            telecom?.defaultDialerPackage?.let { result += it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) telecom?.systemDialerPackage?.let { result += it }
        } catch (_: Exception) {
        }
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
            .forEach { result += it.activityInfo.packageName }
        return result
    }

    private fun computeLaunchers(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .toSet()
    }

    private fun computeIme(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    fun phonePackages(context: Context): Set<String> = cache(context).phone

    /** Our app, phone/emergency, and the current keyboard (otherwise typing the phrase would be blocked). */
    fun isAlwaysAllowed(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return true
        val c = cache(context)
        return pkg in c.phone || pkg == c.ime
    }

    fun isHardBlocked(context: Context, pkg: String): Boolean =
        pkg in HARD_BLOCKED || pkg in cache(context).launchers

    fun isSettingsEscape(pkg: String): Boolean = pkg in SETTINGS_ESCAPES

    fun isLauncher(context: Context, pkg: String): Boolean =
        pkg in cache(context).launchers || pkg == "com.sec.android.app.launcher"

    /** Ringing or in a cellular call: never interfere. */
    fun isInPhoneCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING)
    }

    fun canBeUserAllowed(context: Context, pkg: String): Boolean =
        pkg != context.packageName && !isHardBlocked(context, pkg) && pkg !in phonePackages(context)
}
