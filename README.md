# aVow // UNYIELDING DIGITAL BINDING VOW FOR ABSOLUTE FOCUS

aVow is an offline-first Android application designed to enforce inescapable digital distraction boundaries. It turns your device into a dedicated, single-purpose workspace by locking down accessibility controls, settings, and distracting apps.

Once a binding vow's countdown is active, restrictions cannot be loosened — you can add time or tighten rules, but never shorten or disable them until the timer expires.

---

## Two editions

aVow ships as two Gradle product flavors from one codebase:

| Edition | App name | Application ID | Distribution | Enforcement |
| --- | --- | --- | --- | --- |
| **lite** | aVow | `com.avow.app` | Google Play | Accessibility service only (Play-policy compliant) |
| **full** | aVow Plus | `com.avow.app.plus` | GitHub (sideload) | Accessibility **plus** enterprise Device Owner locks |

Both can be installed side by side. The lite build omits the device-admin surface entirely so it satisfies Google Play's restricted-permission policies; the full build adds the hardened Device Owner enforcement below.

---

## Repository and Project Details

* GitHub Repository: [olusheki/avow](https://github.com/olusheki/avow)
* Author/Concept: Daniel Olusheki
* Contact: avowtheapp@gmail.com
* Tooling: Scaffolded and optimized using Google Antigravity 2.0 in conjunction with Android Studio.

---

## System Architecture

* Minimum SDK: API 34 (Android 14)
* Target SDK: API 36 (Android 16)
* Compile SDK: API 36 (Android 16.1)
* Aesthetic Theme: Minimal Monospace "Lab-Stark" (monotonal graphite-grey and white)
* Network access: No servers, no analytics, no accounts — nothing leaves the device. Domain filtering runs entirely on-device (see below).

---

## Core Features

### 1. Inescapable Countdown Lock
* Ticking Loop: Runs continuously in the background, measuring intervals via monotonic system uptime (`SystemClock.elapsedRealtime()`) to prevent drift or timezone manipulation.
* Active vs. Passive Vows: Passive vows enforce only their configured apps/schedules during a timed vow; Active mode enforces the same rules continuously without requiring a running vow (rules still respect their own schedules). The mode is frozen once a vow is locked.
* Duration Limits: Countdowns from 1 hour to 90 days. Users can append more time but cannot reduce it.

### 2. Enterprise Device Administration Policies *(full edition only)*
Leverages Device Owner privileges to enforce:
* Block Uninstallation: Prevents uninstalling the application.
* Block Data Wipe: Disables factory resets and application storage clears.
* Disable Safe Boot: Blocks escaping restrictions by rebooting into Safe Mode.
* Block Google Play Store: Prevents installing new distracting apps.
* Knox & Private Space Suspension: Suspends Knox Secure Folders and Android 15 Private Spaces when restrictions are active.
* Deactivate USB Debugging: Restricts ADB commands from force-stopping or disabling the app service.

### 3. Accessibility Blocker Service
* Settings Hijack & Redirect: Intercepts attempts to open system settings and accessibility controls, redirecting to the home screen.
* URL Extraction: Scans address bars for Chrome and Samsung Internet in O(1) time to intercept banned domains.
* Zero Lag Interception: Caches configuration in memory to run checks instantly, avoiding Application Not Responding (ANR) flags.
* Rich Lockout Screen: Blocked launches surface a full-screen lockout naming the reason (scheduled block, usage limit, banned site, secure profile, and so on).

### 4. On-Device Domain Filter (browser-agnostic)
* Local DNS Sinkhole: An optional `VpnService` runs a fully local, no-server DNS filter that blocks banned domains across *every* app and browser — not just the ones the accessibility scanner recognizes.
* No traffic leaves the device: it requires the `INTERNET` permission only to open a local socket; it routes nothing to any remote server and self-heals (auto-disables) on error.
* Locked while vowed: cannot be switched off during an active vow.

### 5. Mins-per-Hour Usage Limits
* Collective vs. Independent Limits: Configure a collective limit (combined duration across all restricted apps) or independent limits (tracked separately per app).
* Write-Behind Cache: A thread-safe `ConcurrentHashMap` updates at 1 Hz and commits to disk every 30 seconds or on app-state transitions to save database I/O.
* Eager Reset Checks: Resolves hour-boundary transitions under a `cacheMutex` lock to reset usage timers at interval boundaries.

### 6. Multiple Blocks Scheduler
* Custom Schedules: Up to 4 parallel custom scheduled blocks, defaulting to "Quiet Hours".
* Subset Time Containment Validation: Enforces `BlockedMinutes(Old) ⊆ BlockedMinutes(New)` during active vows, preventing users from shortening schedules.

### 7. Doomscroll Shield
* Scroll Monitoring: Detects high-frequency scroll input inside target apps. Exceeding the configured allowance (with a stricter late-night window) triggers a branded warning notification.
* Warning Preloading: Tapping the notification preloads a 15-minute lockout vow.
* Neutral Lockout: Ignoring the warning triggers a temporary lockout screen with zero touch targets until the cooldown elapses.

### 8. Idle Vow Reminder
* A daily WorkManager check nudges you to set a new vow if you've drifted away — onboarding complete, no active vow, and the app unopened for 3+ days — and rate-limits itself to avoid nagging.

### 9. Focus History and Productivity Analytics
* SQLite Room Facade: Logs focus sessions locally, returning `Flow` objects for UI state.
* Zen Score Formula (0–100 per session):
  `Zen Score = max(0, 100 - (Intrusions * 10) - ((Allowed Screen Time (min) / Focus Duration (hr)) * 5))`
  Intrusions are intercepted app launches; Allowed Screen Time is interactive screen-on time in permitted apps.
* UI Trend Comparison: Cumulative analytics and a Canvas line graph of Zen Score across the last seven sessions.

---

## Technical Architecture Index

1. [VowDataStore.kt](app/src/main/java/com/avow/app/data/VowDataStore.kt): Preference storage, tamper-evident state signatures, and Keystore-backed HMAC verification.
2. [BlockerService.kt](app/src/main/java/com/avow/app/service/BlockerService.kt): AccessibilityService checking foreground packages, URL extraction, settings overrides, usage tracking, and doomscroll monitoring.
3. [DomainVpnService.kt](app/src/main/java/com/avow/app/vpn/DomainVpnService.kt): Local, no-server DNS-filter VpnService for browser-agnostic domain blocking.
4. [ReminderWorker.kt](app/src/main/java/com/avow/app/worker/ReminderWorker.kt): Daily WorkManager job for the idle "set a vow" nudge.
5. [BootReceiver.kt](app/src/main/java/com/avow/app/receiver/BootReceiver.kt): Relaunches MainActivity on reboot if a vow is active.
6. [DeviceAdmin.kt](app/src/full/java/com/avow/app/receiver/DeviceAdmin.kt): *(full edition)* DeviceAdminReceiver enforcing system-level locks via DevicePolicyManager.
7. [MainActivity.kt](app/src/main/java/com/avow/app/MainActivity.kt): App router processing intrusion intents and launching overlays.
8. [MainScreen.kt](app/src/main/java/com/avow/app/ui/MainScreen.kt): Stark Compose dashboard — controls, digital-clock countdown, and history workspaces.

---

## Deployment & Setup

### Clone
```bash
git clone https://github.com/olusheki/avow.git
cd avow
```

### Option A — lite edition (Google Play / accessibility only)
No ADB or Device Owner step is required.
```bash
./gradlew assembleLiteDebug
adb install app/build/outputs/apk/lite/debug/app-lite-debug.apk
```
Then enable the aVow Accessibility Service in **Settings → Accessibility → Installed Apps**.

### Option B — full edition (GitHub power build with Device Owner)
```bash
./gradlew assembleFullDebug
adb install app/build/outputs/apk/full/debug/app-full-debug.apk
```
Grant Device Owner (requires a device with **no** existing accounts):
1. Connect the device with USB Debugging enabled.
2. Remove all user accounts (Settings → Accounts).
3. Assign device owner:
   ```bash
   adb shell dpm set-device-owner com.avow.app.plus/com.avow.app.receiver.DeviceAdmin
   ```
4. Re-add user accounts.
5. Enable the aVow Accessibility Service in **Settings → Accessibility → Installed Apps**.

---

## Verification Suite
Run JUnit and MockK local unit tests:
```bash
./gradlew testLiteDebugUnitTest
```
