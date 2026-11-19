# FocusLock

A personal Android focus timer that blocks every other app until the timer ends.
Written in Kotlin with Jetpack Compose. minSdk 26, targetSdk 35. Built for a Samsung Galaxy S22 (One UI) that isn't rooted and has no device owner, installed by sideloading.

## Features

- Timer presets of 25 min, 1 h and 2 h, plus a custom length (1 min to 24 h).
- While a session runs, opening another app, pressing Home, opening Recents, opening Settings or pulling down the notification shade sends you straight back to the focus screen.
- Always allowed: the Phone app, the in-call screen and the emergency dialer. During a ringing or active cellular call nothing is blocked.
- You can also keep a list of allowed apps. It can only be edited when no session is running. Settings, the launcher and the package installer can't be added to it.
- Exit phrase, stored as a salted PBKDF2-SHA256 hash. Typing it on the focus screen gives a break: the 1st is 6 min, the 2nd 5 min, the 3rd 5 min. After that the phrase stops working until the session ends.
  - Paste is disabled in the phrase field. There is no copy/paste menu, the clipboard reads as empty, and if more than one character arrives at once the input is rejected. That last rule also catches the Samsung keyboard's clipboard panel.
  - A wrong phrase shows a short error. After 3 wrong tries there is a 10-second cooldown.
  - The focus screen shows "Breaks left: X/3".
- Breaks don't pause the session. A notification counts the break down and warns you 30 s before it ends. When the break ends, the focus screen comes back by itself. If the session ends during a break, the session simply ends.
- Changing the date or time doesn't help. Countdowns run on `SystemClock.elapsedRealtime()`, which is monotonic and unaffected by clock changes. A wall-clock copy is kept in step with it and is only used after a reboot.
- Rebooting doesn't help either. A `BOOT_COMPLETED` receiver brings the block back.

## How blocking works

| Layer | What it does |
|---|---|
| `BlockerAccessibilityService` | Watches `TYPE_WINDOW_STATE_CHANGED`. During FOCUS, if the foreground package isn't FocusLock, the phone/emergency app, your keyboard or an allowed app, it sends Back and relaunches the focus screen. The launcher (Home + Recents on One UI), `com.android.settings`, Samsung accessibility settings, Device care and the package installer are always blocked, so you can't turn the service off or force-stop the app mid-session. |
| Notification shade | Collapsed with `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` whenever the focus screen loses window focus. |
| `startLockTask()` | Screen pinning on the focus screen, as an extra layer. It's lifted when you tap Phone, Emergency or an allowed app, and during breaks. You can turn it off in Settings. |
| `FocusService` | A foreground service (type `specialUse`) with an ongoing countdown notification. It keeps the process alive, ends breaks and the session on time, and keeps the stored clock in step. |
| DataStore | Stores the end time, breaks used and break end time. Each deadline is saved as wall time + `elapsedRealtime` + `BOOT_COUNT`. |
| `BootReceiver` / `TimeChangeReceiver` | Resume the session after a reboot or app update, and keep the stored clock in step after a time change. |

Breaks: by default **Settings stay locked during breaks** (Settings → Breaks). Everything else works normally. Without this, a break could be used to switch the Accessibility service off. If you want breaks to be completely unrestricted, turn the option off.

## Build

You need JDK 17+ and the Android SDK (platform 35). Android Studio provides both.

```bash
cd focuslock-android
# Only needed outside Android Studio, if ANDROID_HOME isn't set:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # clock / break / hashing unit tests
```

## Install on the Galaxy S22

### Option A: USB with adb

1. On the phone, open **Settings → About phone → Software information** and tap **Build number** 7 times. Developer options are now on.
2. Open **Settings → Developer options** and turn on **USB debugging**.
3. Plug the phone into your computer. On the phone, accept the "Allow USB debugging?" prompt.
4. On the computer, using `platform-tools` from the Android SDK:
   ```bash
   adb devices                     # the phone should be listed as "device"
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   If you get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the APK was built on a different machine with a different debug key. Run `adb uninstall com.focuslock` first, then install again.

### Option B: copy the APK file

1. Copy `app-debug.apk` to the phone, by USB file transfer, Quick Share or cloud storage.
2. Open it with **My Files**. When asked, allow **"Install unknown apps"** for My Files (or for your browser), then tap **Install**.
3. If **Auto Blocker** is on (**Settings → Security and privacy → Auto Blocker**), it blocks sideloading. Turn it off for the install. You can turn it back on afterwards.

### First launch: setup checklist

Open FocusLock. **Start focus** is disabled until all four items have a green check.

0. **Allow restricted settings first (Android 13+ / One UI 5+).** A sideloaded app can't turn on Accessibility until you do this:
   - Go to **Settings → Apps → FocusLock** (or tap **Open App info** in the checklist).
   - Tap **⋮** (top right) and choose **Allow restricted settings**, then confirm with your PIN or fingerprint.
   - If **⋮** doesn't show the option, try to turn the Accessibility service on once. It will be refused. Then come back to App info and the option will be there.
1. **Accessibility service**: tap **Open**, then **Installed apps → FocusLock blocker → On**.
2. **Unrestricted battery**: tap **Allow** and confirm. To double-check, go to **App info → Battery** and make sure **Unrestricted** is selected. Also make sure FocusLock is *not* in **Device care → Battery → Background usage limits → Sleeping / Deep sleeping apps**.
3. **Notifications**: tap **Allow**.
4. **Display over other apps**: tap **Allow** and turn the switch on. FocusLock never draws over other apps. This permission only lets it bring the focus screen back from the background reliably.

Optional:
- **Settings → Security and privacy → More security settings → Pin app**: turn on, and turn on **"Ask for PIN before unpinning"**. Without this, screen pinning is easy to undo.
- In FocusLock's **Settings**, set your **exit phrase** and choose your **allowed apps**.
- Don't assign FocusLock to the **Accessibility shortcut** (holding both volume keys). That shortcut toggles the service off.

## Test plan

Before starting:
- All four checklist items are green.
- An exit phrase is set.
- One allowed app is set (for example, Clock).
- Start a **25 min** session. For the break-end and session-end tests, use **Custom → 0 h 8 min**.

When the pin prompt appears, tap **Pin** (or **Got it**).

| # | Try this | Expected |
|---|---|---|
| 1 | Press **Home** / swipe up from the bottom | You land back on the focus screen within about 1 s. With pinning on, Home simply does nothing. |
| 2 | Open **Recents** (swipe up and hold / Recents button) | Focus screen again. |
| 3 | Pull down the **notification shade / quick panel** (once and twice) | It snaps closed. If you manage to tap the Settings gear or a notification, you're sent back to the focus screen. |
| 4 | Open **Settings** any way you can (quick panel gear, Bixby, Edge panel, the "Open app info" route) | Blocked. You can't reach Accessibility or FocusLock's **Force stop**. |
| 5 | Open an app from the **Edge panel**, as a **pop-up view** or in **split screen** | The app is backed out and the focus screen returns. Note any app that stays visible. |
| 6 | Press **Back** on the focus screen | Nothing happens. |
| 7 | Tap **Phone**, dial and call someone. Also tap **Emergency call** (don't place the call) | Both open and work. Hang up and leave the dialer: you're back on the focus screen. |
| 8 | Have someone **call you** during the session | The incoming call shows and can be answered. Nothing is blocked during the call. |
| 9 | Tap your **allowed app** in the focus screen's row | It opens and stays open. Switching from it to any other app is blocked. |
| 10 | Long-press **paste** in the phrase field. Use the Samsung keyboard's **clipboard** button | No paste menu appears. A clipboard insert shows "Paste is disabled". |
| 11 | Type a **wrong phrase 3 times** | "Wrong phrase" twice, then "Too many wrong tries" and a 10 s countdown. The field is disabled during the countdown. |
| 12 | Type the **correct phrase** | You get a 6-min break, "Breaks left: 2/3", pinning lifts and a break countdown notification appears. Home, other apps and Recents all work. Settings stays locked if that option is on. |
| 13 | Let the break run out while you're in another app | A notification appears 30 s before the end. At 0 the focus screen comes back by itself and blocking resumes. |
| 14 | Take breaks 2 and 3 | They are 5 min each. After the third, the phrase field says no breaks are left. |
| 15 | **Change the time** during a break (Settings → General management → Date and time → turn off automatic → set +2 h). This requires turning off "Keep Settings locked during breaks" first | When the break ends the session countdown is unchanged, and the session does not end early. Turn automatic time back on afterwards. |
| 16 | **Reboot** mid-session (hold Side + Volume down → Restart) | After you unlock, the focus screen comes back within a few seconds and the remaining time is correct. If it doesn't, check that Accessibility is still on. |
| 17 | Session **ends during a break** (8-min session, take a break at about 3 min left) | The session just ends with a "Session complete" notification. Nothing is blocked any more. |
| 18 | Session **ends normally** | You see "Session complete", pinning is lifted, all apps work, the ongoing notification is gone, and Settings → Allowed apps / phrase are editable again. |
| 19 | Try to **edit settings during a session** | Opening FocusLock only shows the focus screen. Settings aren't reachable. |
| 20 | Leave the phone **locked for 10+ min** during a session, then unlock | The countdown is correct and you're on the focus screen. |

If something on the list *isn't* blocked, connect via adb and run
`adb logcat | grep -iE "FocusLauncher|FocusService|FocusActivity"`, then repeat the escape. Each One UI version names a few windows differently.

## Known limitations

These are inherent to a non-root, non-device-owner app.
- **Safe mode** (hold the power-off button → Safe mode) disables all third-party apps, including FocusLock.
- **adb** from a computer (`adb shell am force-stop com.focuslock`, `adb uninstall`) can always end it. Turn USB debugging off after installing if that's a temptation.
- If One UI kills the app anyway, or a crash turns Accessibility off, the focus screen shows a red warning and a "Blocker is off" notification appears.
- During a ringing or active cellular call, nothing is blocked. That's on purpose, so emergency calls are never blocked.
- Screen pinning asks for confirmation each time the focus screen re-pins, for example after you return from the dialer.

## Code map

```
app/src/main/java/com/focuslock/
  core/      SessionManager (state + rules), TrustedClock, BreakPolicy, PhraseHasher,
             SessionStore (DataStore), AllowPolicy, Permissions, AppCatalog
  service/   BlockerAccessibilityService, FocusService, FocusLauncher, Notifications
  receiver/  BootReceiver, TimeChangeReceiver
  ui/        MainActivity (+ MainScreens: checklist, duration, settings, allowed apps),
             FocusActivity (+ FocusScreen), theme
```
