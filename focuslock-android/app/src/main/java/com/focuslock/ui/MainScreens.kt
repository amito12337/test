package com.focuslock.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.core.AppCatalog
import com.focuslock.core.AppEntry
import com.focuslock.core.AppSettings
import com.focuslock.core.BreakPolicy
import com.focuslock.core.PermissionStatus
import com.focuslock.core.SessionManager
import com.focuslock.ui.theme.FocusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { HOME, SETTINGS, APPS }

@Composable
fun MainRoot(status: PermissionStatus, onSetup: (SetupAction) -> Unit, onStart: (Int) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snapshot by SessionManager.state.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.APPS) Screen.SETTINGS else Screen.HOME
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FocusColors.Background)
            .systemBarsPadding()
            .imePadding()
    ) {
        when (screen) {
            Screen.HOME -> HomeScreen(status, snapshot.settings, onSetup, onStart) { screen = Screen.SETTINGS }
            Screen.SETTINGS -> SettingsScreen(
                snapshot.settings,
                onBack = { screen = Screen.HOME },
                onEditApps = { screen = Screen.APPS },
            )
            Screen.APPS -> AllowedAppsScreen(snapshot.settings) { screen = Screen.SETTINGS }
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FocusColors.Text)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = FocusColors.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------- Home

private val PRESETS = listOf(25, 60, 120)

@Composable
private fun HomeScreen(
    status: PermissionStatus,
    settings: AppSettings,
    onSetup: (SetupAction) -> Unit,
    onStart: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(settings.lastDurationMin) }
    var showCustom by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Header("FocusLock") {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = FocusColors.Text)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SetupChecklist(status, settings, onSetup)

            SectionCard("Session length") {
                Text(
                    formatMinutes(minutes),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = FocusColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p ->
                        DurationChip(formatMinutes(p), selected = minutes == p) { minutes = p }
                    }
                    DurationChip("Custom", selected = minutes !in PRESETS) { showCustom = true }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(16.dp)) {
            if (!status.allGranted) {
                Text(
                    "Finish the setup checklist to start a session.",
                    color = FocusColors.Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = { confirm = true },
                enabled = status.allGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Start focus", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showCustom) {
        CustomDurationDialog(initial = minutes, onDismiss = { showCustom = false }) {
            minutes = it
            showCustom = false
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start ${formatMinutes(minutes)} focus?") },
            text = {
                Text(
                    buildString {
                        append("Every app except the Phone, emergency calls and your allowed apps will be blocked until the timer ends.")
                        if (settings.phraseHash != null) {
                            append("\n\nYour exit phrase gives up to ${BreakPolicy.MAX_BREAKS} breaks (6, 5 and 5 min). Breaks don't pause the timer.")
                        } else {
                            append("\n\nNo exit phrase is set, so there will be NO breaks.")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onStart(minutes) }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FocusColors.Accent,
            selectedLabelColor = FocusColors.Background,
        ),
    )
}

@Composable
private fun CustomDurationDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var hours by remember { mutableStateOf((initial / 60).toString()) }
    var mins by remember { mutableStateOf((initial % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val valid = total in BreakPolicy.MIN_SESSION_MIN..BreakPolicy.MAX_SESSION_MIN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom length") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = mins, onValueChange = { mins = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (valid) formatMinutes(total) else "Between 1 minute and 24 hours",
                    color = if (valid) FocusColors.Muted else FocusColors.Error, fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(total) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetupChecklist(status: PermissionStatus, settings: AppSettings, onSetup: (SetupAction) -> Unit) {
    SectionCard(if (status.allGranted) "Ready" else "Setup checklist") {
        if (!status.accessibility && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FocusColors.SurfaceHigh, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Android 13+: allow restricted settings first", color = FocusColors.Break, fontWeight = FontWeight.Medium)
                Text(
                    "Because this app was sideloaded, the Accessibility switch is greyed out until you open " +
                        "App info → ⋮ (top right) → \"Allow restricted settings\" and confirm. " +
                        "If the ⋮ option is missing, first try turning Accessibility on once (it will be refused), then come back.",
                    color = FocusColors.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                )
                OutlinedButton(onClick = { onSetup(SetupAction.APP_INFO) }) { Text("Open App info") }
            }
        }
        StatusRow(
            "1. Accessibility service",
            "Installed apps → FocusLock blocker → On",
            status.accessibility, "Open",
        ) { onSetup(SetupAction.ACCESSIBILITY) }
        StatusRow(
            "2. Unrestricted battery",
            "Stops One UI from killing the session",
            status.battery, "Allow",
        ) { onSetup(SetupAction.BATTERY) }
        StatusRow(
            "3. Notifications",
            "Countdown and break timer",
            status.notifications, "Allow",
        ) { onSetup(SetupAction.NOTIFICATIONS) }
        StatusRow(
            "4. Display over other apps",
            "Lets FocusLock pull you back instantly",
            status.overlay, "Allow",
        ) { onSetup(SetupAction.OVERLAY) }
        StatusRow(
            "Exit phrase (optional)",
            if (settings.phraseHash != null) "Set" else "Not set: no breaks possible. Set it in Settings.",
            settings.phraseHash != null, "", optional = true,
        ) {}
        if (settings.usePinning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Optional extra layer: enable \"Pin app\" in Security and privacy → More security settings.",
                    color = FocusColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSetup(SetupAction.PINNING) }) { Text("Open") }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, onEditApps: () -> Unit) {
    val scope = rememberCoroutineScope()
    var phrase by remember { mutableStateOf("") }
    var confirmPhrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Header("Settings", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Exit phrase") {
                Text(
                    if (settings.phraseHash != null) "A phrase is set (stored only as a salted hash)."
                    else "No phrase set. Without one there are no breaks.",
                    color = FocusColors.Muted, fontSize = 14.sp,
                )
                Text(
                    "Make it long and annoying to type, e.g. a full sentence. It is case-sensitive.",
                    color = FocusColors.Muted, fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = phrase, onValueChange = { phrase = it },
                    label = { Text("New phrase") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPhrase, onValueChange = { confirmPhrase = it },
                    label = { Text("Repeat phrase") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Text(it, color = FocusColors.Muted, fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val p = phrase.trim()
                            message = when {
                                p.length < 6 -> "Use at least 6 characters."
                                p != confirmPhrase.trim() -> "The two phrases don't match."
                                else -> null
                            }
                            if (message == null) {
                                saving = true
                                scope.launch {
                                    val ok = SessionManager.setPhrase(p)
                                    saving = false
                                    phrase = ""; confirmPhrase = ""
                                    message = if (ok) "Phrase saved." else "Can't change settings during a session."
                                }
                            }
                        },
                    ) { Text(if (saving) "Saving…" else "Save phrase") }
                    if (settings.phraseHash != null) {
                        OutlinedButton(onClick = {
                            scope.launch { SessionManager.clearPhrase(); message = "Phrase removed." }
                        }) { Text("Remove") }
                    }
                }
            }

            SectionCard("Allowed apps") {
                Text(
                    "Phone and emergency calls are always allowed. Add apps you need during focus (e.g. music, maps).",
                    color = FocusColors.Muted, fontSize = 14.sp,
                )
                Text("${settings.allowedApps.size} app(s) allowed", color = FocusColors.Text)
                OutlinedButton(onClick = onEditApps) { Text("Choose apps") }
            }

            SectionCard("Breaks") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep Settings locked during breaks", color = FocusColors.Text)
                        Text(
                            "Everything else works normally during a break, but Settings, Device care and the " +
                                "package installer stay blocked, so a break can't be used to switch the blocker off " +
                                "or force-stop FocusLock.",
                            color = FocusColors.Muted, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = settings.lockSettingsDuringBreaks,
                        onCheckedChange = { v -> scope.launch { SessionManager.setLockSettingsDuringBreaks(v) } },
                    )
                }
            }

            SectionCard("Screen pinning") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pin the focus screen", color = FocusColors.Text)
                        Text(
                            "Extra layer on top of the blocker. Android asks you to confirm \"Pin app\" each time " +
                                "the focus screen opens. Turn off if it gets in the way.",
                            color = FocusColors.Muted, fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = settings.usePinning,
                        onCheckedChange = { v -> scope.launch { SessionManager.setUsePinning(v) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- Allowed apps

@Composable
private fun AllowedAppsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AppCatalog.selectableApps(context) }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Allowed apps", onBack = onBack)
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search") }, singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Text(
            "Settings, launcher and installer apps can't be allowed (they would let you turn the blocker off).",
            color = FocusColors.Muted, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            Text("Loading…", color = FocusColors.Muted, modifier = Modifier.padding(16.dp))
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in settings.allowedApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (checked) settings.allowedApps - app.packageName
                                else settings.allowedApps + app.packageName
                                scope.launch { SessionManager.setAllowedApps(next) }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = FocusColors.Text)
                            Text(app.packageName, color = FocusColors.Muted, fontSize = 11.sp)
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Int = 40) {
    val icon = app.icon
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(Modifier.size(size.dp).background(FocusColors.SurfaceHigh, RoundedCornerShape(10.dp)))
    }
}

