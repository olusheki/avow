<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/avow_logo_inverted.png">
    <img src="branding/avow_logo_gray.png" alt="aVow logo" width="96">
  </picture>

  <h1>aVow</h1>

  <p><strong>Lock yourself out of distracting apps — and actually mean it.</strong></p>

  <p>
    <a href="LICENSE"><img alt="License: GPL v3" src="https://img.shields.io/badge/License-GPLv3-blue.svg"></a>
    <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%2014%2B-3DDC84?logo=android&logoColor=white">
    <img alt="Built with" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
    <a href="https://github.com/olusheki/avow/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/olusheki/avow/actions/workflows/ci.yml/badge.svg"></a>
  </p>

</div>

aVow is an Android focus app that makes your self-control decisions stick. You set a **vow** — a timer from a few minutes to 99 days — and the apps and websites you chose to block stay blocked until it runs out. You can always make a vow *stricter* or add time, but you can't unlock early. Everything runs on your device: no account, no servers, no analytics.

## Screenshots

<!-- Add images to docs/screenshots/ and uncomment:
<p align="center">
  <img src="docs/screenshots/dashboard.png" width="220" alt="Dashboard">
  <img src="docs/screenshots/lockout.png"   width="220" alt="Lockout screen">
  <img src="docs/screenshots/insights.png"  width="220" alt="Focus insights">
</p>
-->

_Screenshots coming soon._

## Why aVow?

Most app blockers are one tap away from being switched off — right when they need to hold. aVow is built around a single idea: **once you commit, the decision is out of your hands until the timer ends.** It's for people who already know what distracts them and want a boundary that doesn't fold in a weak moment.

## Features

- ⏳ **Binding vows** — commit for minutes or months. Add time or tighten the rules whenever you like; never loosen or cancel until the timer runs out.
- 📵 **Block apps & websites** — pick what to shut out. Sites are blocked in *every* browser via an on-device DNS filter — no traffic leaves your phone.
- 🕓 **Schedules & usage limits** — "quiet hours" windows and per-app time budgets (say, 10 minutes an hour).
- 🌀 **Doomscroll shield** — spend past your allowance in a chosen app and it locks for a cooldown, with a stricter late-night window.
- 🙅 **No sneaking out** — attempts to disable aVow from Settings, use private/incognito browsing, or slip an app into a pop-out (picture-in-picture) or split-screen are caught too.
- 📊 **Focus insights** — a local history of your sessions with a simple 0–100 "zen score" and trends.
- 🔐 **Hardened enforcement** *(full edition)* — optional Device Owner locks make a vow genuinely hard to escape: block uninstalling aVow, factory reset, Safe Mode, and more.

## Two editions

Both build from one codebase and can be installed side by side.

| | **aVow** (lite) | **aVow Plus** (full) |
| --- | --- | --- |
| Distribution | Google Play | GitHub (sideload) |
| Enforcement | Accessibility service | Accessibility **+** Device Owner locks |
| Application ID | `com.avow.app` | `com.avow.app.plus` |

The lite edition is Google Play–policy compliant. The full edition adds the hardened Device Owner enforcement for people who want a vow that's nearly impossible to bypass.

## Build & install

You'll need Android Studio (or the Android SDK) and a device or emulator on **Android 14+**.

**Lite edition** — no special setup:

```bash
./gradlew assembleLiteDebug
adb install app/build/outputs/apk/lite/debug/app-lite-debug.apk
```

**Full edition** — adds Device Owner (requires a device with no accounts added yet):

```bash
./gradlew assembleFullDebug
adb install app/build/outputs/apk/full/debug/app-full-debug.apk
adb shell dpm set-device-owner com.avow.app.plus/com.avow.app.receiver.DeviceAdmin
```

> The `set-device-owner` step only works on a device with no accounts configured — remove them in **Settings → Accounts** first, then re-add them afterward.

Finally, turn on the aVow accessibility service in **Settings → Accessibility**.

## How it works

aVow enforces limits with an **AccessibilityService** that watches which app (or website) is in the foreground and steps in when it's one you've blocked. Vow state is stored locally and signed (HMAC via the Android Keystore) so it can't be quietly edited, and the countdown runs against the device's monotonic uptime so changing the clock doesn't buy you anything. An optional local **VpnService** blocks domains across every browser without sending traffic anywhere.

Worth a look if you want to dig in:

- [`BlockerService.kt`](app/src/main/java/com/avow/app/service/BlockerService.kt) — the foreground/website watcher and enforcement.
- [`VowDataStore.kt`](app/src/main/java/com/avow/app/data/VowDataStore.kt) — signed, tamper-evident vow state.
- [`DomainVpnService.kt`](app/src/main/java/com/avow/app/vpn/DomainVpnService.kt) — the on-device domain filter.
- [`DeviceAdmin.kt`](app/src/full/java/com/avow/app/receiver/DeviceAdmin.kt) — Device Owner locks *(full edition)*.

## Tech stack

Kotlin · Jetpack Compose · Coroutines · DataStore · WorkManager · SQLite · AccessibilityService · VpnService · Android Keystore

## Testing

```bash
./gradlew testLiteDebugUnitTest testFullDebugUnitTest
```

The suite focuses on the risky paths — lock/unlock, tamper detection, reboot recovery, and the blocking logic.

## Privacy

aVow has no servers, accounts, or analytics, and nothing you do leaves your device. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for exactly what it accesses and why.

## Contributing

Issues, ideas, and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Found a security issue? Please report it privately per [SECURITY.md](SECURITY.md).

## License

aVow is released under the [GNU General Public License v3.0](LICENSE).

## Author

Built by Daniel Olusheki · avowtheapp@gmail.com
