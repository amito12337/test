package com.focuslock.core

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

sealed interface BreakResult {
    data class Granted(val minutes: Int) : BreakResult
    data class Wrong(val triesBeforeCooldown: Int) : BreakResult
    data class Cooldown(val secondsLeft: Int) : BreakResult
    data object NoneLeft : BreakResult
    data object NoPhrase : BreakResult
    data object Empty : BreakResult
    data object NotAvailable : BreakResult
}

enum class TickEvent { NONE, BREAK_ENDED, SESSION_FINISHED }

/**
 * Single source of truth for the session and settings. In-memory [state] mirrors DataStore
 * so the accessibility service can decide synchronously on every window change.
 */
@android.annotation.SuppressLint("StaticFieldLeak") // holds the Application context only
object SessionManager {
    private lateinit var app: Context
    private val mutex = Mutex()
    private val _state = MutableStateFlow(Snapshot.EMPTY)
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    // Wrong-phrase tracking lives in memory: the app can't be killed mid-session anyway.
    @Volatile private var wrongTries = 0
    @Volatile private var cooldownUntilElapsed = 0L

    /** Hooks set by the app layer (keeps core free of UI/service classes). */
    var onBreakEnded: (Context) -> Unit = {}
    var onSessionFinished: (Context) -> Unit = {}

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        runBlocking {
            _state.value = SessionStore.read(app.focusDataStore.data.first())
            // After a reboot elapsedRealtime restarted: convert deadlines to this boot right away.
            reanchor()
        }
    }

    val session: Session? get() = _state.value.session
    val settings: AppSettings get() = _state.value.settings

    fun sessionRemainingMs(s: Session? = session): Long =
        s?.let { TrustedClock.remainingMs(app, it.end, it.totalMs) } ?: 0L

    fun breakRemainingMs(s: Session? = session): Long {
        val b = s?.breakEnd ?: return 0L
        return TrustedClock.remainingMs(app, b, BreakPolicy.MAX_BREAK_MS)
    }

    /** Computed from the deadlines on every call, so blocking never depends on a tick having run. */
    fun phase(s: Session? = session): Phase {
        if (s == null || sessionRemainingMs(s) <= 0L) return Phase.IDLE
        if (s.breakEnd != null && breakRemainingMs(s) > 0L) return Phase.BREAK
        return Phase.FOCUS
    }

    fun cooldownRemainingMs(): Long =
        (cooldownUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    // ---- session lifecycle ----

    suspend fun startSession(durationMs: Long): Boolean = mutex.withLock {
        if (phase() != Phase.IDLE) return@withLock false
        val min = BreakPolicy.MIN_SESSION_MIN * 60_000L
        val max = BreakPolicy.MAX_SESSION_MIN * 60_000L
        val d = durationMs.coerceIn(min, max)
        wrongTries = 0
        cooldownUntilElapsed = 0L
        persistSession(Session(totalMs = d, end = TrustedClock.deadlineIn(app, d), breaksUsed = 0, breakEnd = null))
        true
    }

    /** Expires breaks/sessions and fires side effects. Safe to call from anywhere, often. */
    suspend fun tick(): TickEvent {
        val event = mutex.withLock {
            val s = session ?: return@withLock TickEvent.NONE
            when {
                sessionRemainingMs(s) <= 0L -> {
                    persistSession(null)
                    TickEvent.SESSION_FINISHED
                }
                s.breakEnd != null && breakRemainingMs(s) <= 0L -> {
                    persistSession(s.copy(breakEnd = null))
                    TickEvent.BREAK_ENDED
                }
                else -> TickEvent.NONE
            }
        }
        when (event) {
            TickEvent.BREAK_ENDED -> onBreakEnded(app)
            TickEvent.SESSION_FINISHED -> {
                wrongTries = 0
                cooldownUntilElapsed = 0L
                onSessionFinished(app)
            }
            TickEvent.NONE -> Unit
        }
        return event
    }

    /**
     * Keeps the stored wall-clock deadlines consistent with the trusted elapsed clock. Called
     * when the time is changed, periodically, and at process start (which covers reboots).
     */
    suspend fun reanchor() = mutex.withLock {
        val s = session ?: return@withLock
        val newEnd = TrustedClock.reanchor(app, s.end, s.totalMs)
        val newBreak = s.breakEnd?.let { TrustedClock.reanchor(app, it, BreakPolicy.MAX_BREAK_MS) }
        val oldBreak = s.breakEnd
        val changed = moved(s.end, newEnd) || (oldBreak != null && newBreak != null && moved(oldBreak, newBreak))
        if (changed) persistSession(s.copy(end = newEnd, breakEnd = newBreak))
    }

    private fun moved(a: Deadline, b: Deadline) =
        a.bootCount != b.bootCount || abs(a.wallMs - b.wallMs) > 1_500L

    suspend fun requestBreak(input: String): BreakResult {
        val cd = cooldownRemainingMs()
        if (cd > 0L) return BreakResult.Cooldown(((cd + 999) / 1000).toInt())
        val s = session
        if (s == null || phase(s) != Phase.FOCUS) return BreakResult.NotAvailable
        if (s.breaksUsed >= BreakPolicy.MAX_BREAKS) return BreakResult.NoneLeft
        val hash = settings.phraseHash ?: return BreakResult.NoPhrase
        if (input.isBlank()) return BreakResult.Empty

        val ok = withContext(Dispatchers.Default) { PhraseHasher.verify(input, hash) }
        if (!ok) {
            wrongTries++
            if (wrongTries >= BreakPolicy.MAX_WRONG_TRIES) {
                wrongTries = 0
                cooldownUntilElapsed = SystemClock.elapsedRealtime() + BreakPolicy.COOLDOWN_MS
                return BreakResult.Cooldown((BreakPolicy.COOLDOWN_MS / 1000).toInt())
            }
            return BreakResult.Wrong(BreakPolicy.MAX_WRONG_TRIES - wrongTries)
        }
        wrongTries = 0
        return mutex.withLock {
            val cur = session
            if (cur == null || phase(cur) != Phase.FOCUS) return@withLock BreakResult.NotAvailable
            val len = BreakPolicy.breakDurationMs(cur.breaksUsed) ?: return@withLock BreakResult.NoneLeft
            persistSession(cur.copy(breaksUsed = cur.breaksUsed + 1, breakEnd = TrustedClock.deadlineIn(app, len)))
            BreakResult.Granted((len / 60_000L).toInt())
        }
    }

    // ---- settings (only editable when no session is running) ----

    private suspend fun clearExpiredSession() {
        if (session != null && phase() == Phase.IDLE) tick()
    }

    suspend fun setPhrase(phrase: String): Boolean {
        clearExpiredSession()
        if (session != null) return false
        val hash = withContext(Dispatchers.Default) { PhraseHasher.hash(phrase) }
        return updateSettings { it.copy(phraseHash = hash) }
    }

    suspend fun clearPhrase(): Boolean = updateSettings { it.copy(phraseHash = null) }

    suspend fun setAllowedApps(apps: Set<String>): Boolean = updateSettings { it.copy(allowedApps = apps) }

    suspend fun setUsePinning(value: Boolean): Boolean = updateSettings { it.copy(usePinning = value) }

    suspend fun setLockSettingsDuringBreaks(value: Boolean): Boolean =
        updateSettings { it.copy(lockSettingsDuringBreaks = value) }

    suspend fun setLastDurationMin(min: Int): Boolean = updateSettings { it.copy(lastDurationMin = min) }

    private suspend fun updateSettings(transform: (AppSettings) -> AppSettings): Boolean {
        clearExpiredSession()
        return mutex.withLock {
            if (session != null) return@withLock false
            val next = transform(settings)
            app.focusDataStore.edit { SessionStore.writeSettings(it, next) }
            _state.update { it.copy(settings = next) }
            true
        }
    }

    private suspend fun persistSession(s: Session?) {
        app.focusDataStore.edit { SessionStore.writeSession(it, s) }
        _state.update { it.copy(session = s) }
    }
}
