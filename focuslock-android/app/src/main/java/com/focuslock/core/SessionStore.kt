package com.focuslock.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focuslock")

/** Maps [Snapshot] to/from DataStore preferences. */
object SessionStore {
    private val ACTIVE = booleanPreferencesKey("session_active")
    private val TOTAL_MS = longPreferencesKey("session_total_ms")
    private val BREAKS_USED = intPreferencesKey("session_breaks_used")
    private const val END = "session_end"
    private const val BREAK_END = "break_end"

    private val PHRASE_HASH = stringPreferencesKey("phrase_hash")
    private val ALLOWED_APPS = stringSetPreferencesKey("allowed_apps")
    private val USE_PINNING = booleanPreferencesKey("use_pinning")
    private val LAST_DURATION_MIN = intPreferencesKey("last_duration_min")
    private val LOCK_SETTINGS_IN_BREAKS = booleanPreferencesKey("lock_settings_in_breaks")

    fun read(p: Preferences): Snapshot {
        val d = AppSettings.DEFAULT
        val settings = AppSettings(
            phraseHash = p[PHRASE_HASH],
            allowedApps = p[ALLOWED_APPS] ?: d.allowedApps,
            usePinning = p[USE_PINNING] ?: d.usePinning,
            lastDurationMin = p[LAST_DURATION_MIN] ?: d.lastDurationMin,
            lockSettingsDuringBreaks = p[LOCK_SETTINGS_IN_BREAKS] ?: d.lockSettingsDuringBreaks,
        )
        val end = readDeadline(p, END)
        val session = if (p[ACTIVE] == true && end != null) {
            Session(
                totalMs = p[TOTAL_MS] ?: 0L,
                end = end,
                breaksUsed = p[BREAKS_USED] ?: 0,
                breakEnd = readDeadline(p, BREAK_END),
            )
        } else null
        return Snapshot(session, settings)
    }

    fun writeSession(p: MutablePreferences, s: Session?) {
        if (s == null) {
            p.remove(ACTIVE); p.remove(TOTAL_MS); p.remove(BREAKS_USED)
            writeDeadline(p, END, null)
            writeDeadline(p, BREAK_END, null)
            return
        }
        p[ACTIVE] = true
        p[TOTAL_MS] = s.totalMs
        p[BREAKS_USED] = s.breaksUsed
        writeDeadline(p, END, s.end)
        writeDeadline(p, BREAK_END, s.breakEnd)
    }

    fun writeSettings(p: MutablePreferences, s: AppSettings) {
        if (s.phraseHash == null) p.remove(PHRASE_HASH) else p[PHRASE_HASH] = s.phraseHash
        p[ALLOWED_APPS] = s.allowedApps
        p[USE_PINNING] = s.usePinning
        p[LAST_DURATION_MIN] = s.lastDurationMin
        p[LOCK_SETTINGS_IN_BREAKS] = s.lockSettingsDuringBreaks
    }

    private fun wallKey(prefix: String) = longPreferencesKey("${prefix}_wall")
    private fun elapsedKey(prefix: String) = longPreferencesKey("${prefix}_elapsed")
    private fun bootKey(prefix: String) = intPreferencesKey("${prefix}_boot")

    private fun readDeadline(p: Preferences, prefix: String): Deadline? {
        val wall = p[wallKey(prefix)] ?: return null
        val elapsed = p[elapsedKey(prefix)] ?: return null
        return Deadline(wall, elapsed, p[bootKey(prefix)] ?: TrustedClock.UNKNOWN_BOOT)
    }

    private fun writeDeadline(p: MutablePreferences, prefix: String, d: Deadline?) {
        if (d == null) {
            p.remove(wallKey(prefix)); p.remove(elapsedKey(prefix)); p.remove(bootKey(prefix))
        } else {
            p[wallKey(prefix)] = d.wallMs
            p[elapsedKey(prefix)] = d.elapsedMs
            p[bootKey(prefix)] = d.bootCount
        }
    }
}
