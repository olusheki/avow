# aVow // UNYIELDING DIGITAL BINDING VOW FOR ABSOLUTE FOCUS

aVow is an offline-first Android application designed to enforce inescapable digital distraction boundaries. It turns your device into a dedicated, single-purpose workspace by locking down accessibility controls, settings, and distracting apps using Android enterprise device owner administration policies.

Once the vow countdown is active, the system prevents uninstallation, clearing storage, safe mode reboots, Knox secure folder use, and setting bypasses. The lockdown remains active until the countdown timer expires.

---

## Repository and Project Details

* GitHub Repository: [olusheki/avow](https://github.com/olusheki/avow)
* Author/Concept: Daniel Olusheki
* Tooling: Scaffolded and optimized using Google Antigravity 2.0 in conjunction with Android Studio.

---

## System Architecture

* Minimum SDK: API 34 (Android 14)
* Target SDK: API 36 (Android 16)
* Compile SDK: API 36 (Android 16.1)
* Aesthetic Theme: Minimal Monospace "Lab-Stark" (Monotonal graphite-grey and white)
* Network access: None (Fully local and offline)

---

## Core Features

### 1. Inescapable Countdown Lock
* Ticking Loop: Runs continuously in the background. It measures intervals using monotonic system uptime differential (SystemClock.elapsedRealtime()) to prevent drift or timezone manipulation.
* Active vs. Passive Vows: Users can toggle between Passive Vow (only blocks configured apps/schedules) and Active Vow (continuous lockout during all hours) when the device is unlocked. Once locked, the setting is frozen.
* Duration Limits: Supports countdowns from 1 hour to 90 days. Users can append more time but cannot reduce it.

### 2. Enterprise Device Administration Policies
Leverages Device Owner privileges to enforce:
* Block Uninstallation: Prevents uninstalling the application.
* Block Data Wipe: Disables factory resets and application storage clears.
* Disable Safe Boot: Blocks escaping restrictions by rebooting into Safe Mode.
* Block Google Play Store: Prevents installing new distracting apps.
* Knox & Private Space Suspension: Suspends Knox Secure Folders and Android 15 Private Spaces when restrictions are active.
* Deactivate USB Debugging: Restricts ADB commands from force-stopping or disabling the app service.

### 3. Accessibility Blocker Service
* Settings Hijack & Redirect: Intercepts user attempts to open system settings and accessibility controls, redirecting them to the home screen.
* URL Extraction: Scans address bars for Chrome and Samsung Internet in O(1) time to intercept banned domains.
* Zero Lag Interception: Caches configuration in memory to run checks instantly, preventing Application Not Responding (ANR) flags.

### 4. Mins per Hour Usage Limits
* Collective vs. Independent Limits: Allows configuring collective limits (combined duration for all restricted apps) or independent limits (tracked separately per app).
* Write-Behind Cache: Keeps a thread-safe ConcurrentHashMap memory cache updating at 1Hz, committing to disk every 30 seconds or during app state transitions to save database I/O.
* Eager Reset checks: Resolves hour-boundary transitions under a cacheMutex lock to reset usage timers at boundary intervals.

### 5. Multiple Blocks Scheduler
* Custom Schedules: Supports up to 4 parallel custom scheduled blocks defaulting to "Quiet Hours".
* Subset Time Containment Validation: Enforces that new schedule changes satisfy BlockedMinutes(Old) subset check of BlockedMinutes(New) during active vows, preventing users from shortening schedules.

### 6. Doomscroll gesture Warning & Neutral Lockout
* Scroll Monitoring: Detects high-frequency scroll inputs inside target apps. Exceeding 15 minutes (or 5 minutes between 11 PM and 5 AM) triggers a warning notification.
* Warning Preloading: Tapping the notification preloads a 15-minute lockout vow.
* Disappointed face lockout: Ignoring the warning triggers a 1-hour neutral lockout screen displaying a disappointed face (😐) outline with zero touch targets.

### 7. Focus History and Productivity Analytics
* SQLite Room Facade: Logs focus sessions locally to a native SQLiteOpenHelper database wrapper, returning Flow objects for UI states.
* Zen Score Formula: Calculates focus productivity (0-100) per session using the formula:
  Zen Score = max(0, 100 - (Intrusions * 10) - ((Allowed Screen Time (Min) / Focus Duration (Hr)) * 5))
  Intrusions represent intercepted app launches, and Allowed Screen Time measures interactive screen-on time spent in permitted apps.
* UI Trend Comparison: Displays cumulative analytics and a Canvas line graph illustrating Zen Score trends across the last seven focus sessions.

---

## Technical Architecture Index

1. [VowDataStore.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/data/VowDataStore.kt): Encapsulates preference storage, state signatures, and Keystore-backed HMAC verification keys.
2. [BlockerService.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/service/BlockerService.kt): Extends AccessibilityService, checking foreground packages, URL extractions, settings overrides, and tracking allowed screen usage time.
3. [BootReceiver.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/receiver/BootReceiver.kt): Listens to boot broadcasts to relaunch MainActivity on reboot if a vow is active.
4. [DeviceAdmin.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/receiver/DeviceAdmin.kt): Extends DeviceAdminReceiver, utilizing DevicePolicyManager to enforce system-level locks.
5. [MainActivity.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/MainActivity.kt): App router processing intrusion intent signals and launching overlays.
6. [MainScreen.kt](file:///Users/danielolusheki/AndroidStudioProjects/aVow/app/src/main/java/com/avow/app/ui/MainScreen.kt): Stark Compose dashboard rendering controls, digital clock countdown, and history workspaces.

---

## Deployment & Setup

### Step 1: Build Debug APK
```bash
git clone https://github.com/olusheki/avow.git
cd avow
./gradlew assembleDebug
```

### Step 2: Install and Grant Device Owner Privileges
1. Connect device with USB Debugging enabled.
2. Install APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
3. Remove all user accounts from the device (Settings > Accounts).
4. Assign device owner via ADB shell:
   ```bash
   adb shell dpm set-device-owner com.avow.app/com.avow.app.receiver.DeviceAdmin
   ```
5. Re-add user accounts.

### Step 3: Turn on Accessibility
Enable the aVow Accessibility Service in Settings > Accessibility > Installed Apps.

---

## Verification Suite
Run JUnit and Mockk local unit tests:
```bash
./gradlew test
```
