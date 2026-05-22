### 🔍 SYSTEM EDGE CASES & COMPILING CONSIDERATIONS
When building a high-restriction app blocker for your personal phone, there are a few system edge cases you need to keep in mind so your app handles unexpected scenarios smoothly.

### 1. Managing Foldable Screen Transitions (Galaxy Z Fold 7 Continuity)
* **The Issue:** Opening your phone switches your screen layout instantly from the narrow outer cover screen to the massive, square inner main screen. If your app doesn't handle layout updates properly, your fullscreen graphite-grey overlay might fail to stretch, exposing a narrow vertical strip of a blocked app on the side of your display.
* **The Fix:** Your Jetpack Compose overlay layout must use explicit `Modifier.fillMaxSize()` bounds linked directly to the dynamic state layout framework. This ensures that when the phone folds or unfolds, the blacked-out window automatically re-measures and stretches to cover every single active pixel row across both screens instantly.

### 2. Outsmarting OS Battery Saver Optimizations
* **The Problem:** Samsung’s *One UI* software suite includes automated optimization tools that put background tracking services into deep sleep if they consume continuous processing threads. If the system puts your blocker to sleep, your focus lock could stop working.
* **The Fix:** When your app initializes, have your code trigger an explicit intent targeting the system battery optimization menu. This allows you to manually flag `aVow` as **"Unrestricted"** inside your phone's background settings, ensuring the Android kernel never puts your service to sleep.

### 3. Hardware Time-Tampering Protection
* **The Problem:** A common trick to bypass a long countdown lock is going into your phone's settings and manually advancing the clock forward by 90 days. If your app relies on the standard device clock time (`System.currentTimeMillis()`), manual time-shifting will trick the app into thinking the vow is over.
* **The Fix:** Your calculation module (`TimeCalc.kt`) must never look at wall-clock time. Instead, configure your app to query **`SystemClock.elapsedRealtime()`**. This tracks how many milliseconds have passed since the physical device was powered on, completely ignoring manual date and time adjustments. To track total overall time across reboots, have your app periodically save small encrypted timestamp files to storage, and verify them against an aggregate uptime calculation log.

### 4. Designing a Mandatory System Emergency Hatch
* **The Precaution:** When configuring an absolute lock with features like `DISALLOW_FACTORY_RESET` active, a code crash or loop bug can permanently break your phone access. While writing your code, configure a hidden, unlisted gesture routine inside `MainActivity.kt` (such as tapping a specific corner of the screen exactly 10 times in rhythmic succession).
* **The Rule:** Configure this hidden shortcut to completely strip the device admin rules and clear the active lock state. Keep this backdoor code active in your testing builds while debugging. Once you've thoroughly verified that your app runs stably and doesn't crash on your phone, you can comment out that secret backdoor code before running your final ADB push to create your true "binding vow."