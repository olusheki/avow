# Contributing to aVow

Thanks for your interest! aVow is a solo project, but issues, ideas, and pull requests are welcome.

## Build & run

You'll need Android Studio (or the Android SDK) and a device or emulator on **Android 14+** (API 34).

```bash
git clone https://github.com/olusheki/avow.git
cd avow
./gradlew assembleLiteDebug
```

The app ships as two flavors: `lite` (Google Play, accessibility only) and `full` (adds Device Owner
enforcement). Most work happens in `lite` — only touch `full` for Device Owner behavior.

## Run the tests

```bash
./gradlew testLiteDebugUnitTest testFullDebugUnitTest
```

Please add or update tests for any behavior change — especially anything touching lock/unlock, vow
state, or enforcement. Those are the paths that can lock a user out if they break, so they're the ones
that matter most.

## Project layout

- `app/src/main` — shared code (UI, services, data). The bulk of the app.
- `app/src/lite` / `app/src/full` — flavor-specific enforcement.
- `app/src/test` — JVM unit tests.

## Pull requests

- Keep each PR focused on one concern.
- Commit messages use a lowercase conventional prefix and a short summary, e.g.
  `fix: never block the launcher or dialer`.
- Make sure **both** flavors build and the full test suite passes before opening the PR.

## Reporting bugs & security issues

- **Bugs and ideas:** open a GitHub issue with steps to reproduce, your device, and Android version.
- **Security vulnerabilities:** do *not* open a public issue — follow [SECURITY.md](SECURITY.md).
