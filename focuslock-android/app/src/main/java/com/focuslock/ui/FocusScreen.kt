package com.focuslock.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.core.AppCatalog
import com.focuslock.core.AppEntry
import com.focuslock.core.BreakPolicy
import com.focuslock.core.BreakResult
import com.focuslock.core.Phase
import com.focuslock.core.SessionManager
import com.focuslock.ui.theme.FocusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FocusScreen(
    onPhone: () -> Unit,
    onEmergency: () -> Unit,
    onOpenApp: (String) -> Unit,
    onUsePhone: () -> Unit,
    onDone: () -> Unit,
    onPhaseChanged: (Phase) -> Unit,
) {
    val snapshot by SessionManager.state.collectAsStateWithLifecycle()
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(250)
            value = SystemClock.elapsedRealtime()
        }
    }
    // Also expire breaks/sessions from here, in case the service was delayed.
    LaunchedEffect(Unit) {
        while (true) {
            SessionManager.tick()
            delay(1_000)
        }
    }

    val session = snapshot.session
    val phase = remember(session, now) { SessionManager.phase(session) }
    LaunchedEffect(phase) { onPhaseChanged(phase) }
    BackHandler(enabled = phase != Phase.IDLE) { /* swallowed during a session */ }

    Column(
        Modifier
            .fillMaxSize()
            .background(FocusColors.Background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        if (session == null || phase == Phase.IDLE) {
            SessionComplete(onDone)
        } else {
            ActiveSession(session, phase, now, snapshot.settings, onPhone, onEmergency, onOpenApp, onUsePhone)
        }
    }
}

@Composable
private fun ActiveSession(
    session: com.focuslock.core.Session,
    phase: Phase,
    now: Long,
    settings: com.focuslock.core.AppSettings,
    onPhone: () -> Unit,
    onEmergency: () -> Unit,
    onOpenApp: (String) -> Unit,
    onUsePhone: () -> Unit,
) {
    val accent = if (phase == Phase.BREAK) FocusColors.Break else FocusColors.Accent
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (phase == Phase.BREAK) "BREAK" else "FOCUS",
            color = accent, letterSpacing = 6.sp, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(20.dp))

        val remaining = remember(session, now) { SessionManager.sessionRemainingMs(session) }
        val progress = if (session.totalMs > 0) remaining.toFloat() / session.totalMs else 0f
        Countdown(formatClock(remaining), progress, accent, label = "remaining")

        Spacer(Modifier.height(20.dp))
        val left = BreakPolicy.breaksLeft(session.breaksUsed)
        Text("Breaks left: $left/${BreakPolicy.MAX_BREAKS}", color = FocusColors.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))

        when (phase) {
            Phase.BREAK -> BreakPanel(remember(session, now) { SessionManager.breakRemainingMs(session) }, onUsePhone)
            Phase.FOCUS -> PhraseEntry(
                hasPhrase = settings.phraseHash != null,
                breaksLeft = left,
                now = now,
            )
            Phase.IDLE -> Unit
        }

        Spacer(Modifier.height(28.dp))
        val context = LocalContext.current
        val a11yOn = remember(now / 5_000) { com.focuslock.core.Permissions.accessibilityEnabled(context) }
        if (phase == Phase.FOCUS && !a11yOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = FocusColors.Error)
                Spacer(Modifier.width(8.dp))
                Text("Accessibility blocker is off. Blocking is weakened.", color = FocusColors.Error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
        EscapeRow(settings.allowedApps, onPhone, onEmergency, onOpenApp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Countdown(text: String, progress: Float, accent: Color, label: String) {
    Box(Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(FocusColors.Outline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                accent, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = TextStyle(
                    fontSize = if (text.length > 5) 60.sp else 76.sp,
                    fontWeight = FontWeight.Light,
                    color = FocusColors.Text,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(label, color = FocusColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BreakPanel(breakLeftMs: Long, onUsePhone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FocusColors.Surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Break ends in", color = FocusColors.Muted, fontSize = 13.sp)
        Text(
            formatClock(breakLeftMs),
            style = TextStyle(fontSize = 40.sp, color = FocusColors.Break, fontFeatureSettings = "tnum"),
        )
        Text(
            "Your phone works normally. The session keeps counting down, and FocusLock comes back automatically when the break ends.",
            color = FocusColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Button(
            onClick = onUsePhone,
            colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Break, contentColor = Color(0xFF1C1405)),
        ) { Text("Use my phone") }
    }
}

@Composable
private fun PhraseEntry(hasPhrase: Boolean, breaksLeft: Int, now: Long) {
    if (!hasPhrase) {
        Hint("No exit phrase was set, so breaks aren't available this session.")
        return
    }
    if (breaksLeft == 0) {
        Hint("No breaks left. The phrase is disabled until this session ends.")
        return
    }

    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val cooldownMs = remember(now) { SessionManager.cooldownRemainingMs() }
    val coolingDown = cooldownMs > 0

    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }

    fun submit() {
        if (busy || coolingDown) return
        val attempt = text
        busy = true
        scope.launch {
            val r = SessionManager.requestBreak(attempt)
            busy = false
            text = ""
            message = when (r) {
                is BreakResult.Granted -> null
                is BreakResult.Wrong -> "Wrong phrase"
                is BreakResult.Cooldown -> "Too many wrong tries. Wait ${r.secondsLeft}s."
                BreakResult.NoneLeft -> "No breaks left."
                BreakResult.NoPhrase -> "No exit phrase set."
                BreakResult.Empty -> "Type your exit phrase."
                BreakResult.NotAvailable -> null
            }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Paste is disabled: no context menu, an empty clipboard, and multi-character inserts are rejected.
        CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar, LocalClipboardManager provides NoClipboard) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.length - text.length > 1) {
                        message = "Paste is disabled. Type the phrase."
                    } else {
                        text = new
                    }
                },
                enabled = !coolingDown && !busy,
                singleLine = true,
                label = { Text("Exit phrase") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        val status = when {
            coolingDown -> "Try again in ${(cooldownMs + 999) / 1000}s"
            else -> message
        }
        Text(
            status ?: " ",
            color = FocusColors.Error, fontSize = 13.sp,
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.height(6.dp))
        val next = BreakPolicy.breakDurationMs(BreakPolicy.MAX_BREAKS - breaksLeft)?.div(60_000) ?: 0
        OutlinedButton(onClick = ::submit, enabled = !coolingDown && !busy && text.isNotEmpty()) {
            Text(if (busy) "Checking…" else "Take a $next-min break")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = FocusColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun EscapeRow(
    allowed: Set<String>,
    onPhone: () -> Unit,
    onEmergency: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(allowed) {
        apps = withContext(Dispatchers.IO) { AppCatalog.entries(context, allowed) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPhone) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Phone")
        }
        OutlinedButton(
            onClick = onEmergency,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusColors.Error),
        ) { Text("Emergency call") }
    }
    if (apps.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable { onOpenApp(app.packageName) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, size = 44)
                    Text(app.label, color = FocusColors.Muted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SessionComplete(onDone: () -> Unit) {
    Spacer(Modifier.height(120.dp))
    Text("Session complete", fontSize = 32.sp, fontWeight = FontWeight.Light, color = FocusColors.Text)
    Spacer(Modifier.height(12.dp))
    Text("Everything is unlocked.", color = FocusColors.Muted)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onDone) { Text("Done") }
}
