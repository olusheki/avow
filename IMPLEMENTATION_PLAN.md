# aVow — Implementation Plan (Review Fixes + New Tweaks)

> Consolidated backlog from the 2026-07-02 review session plus the owner's own usage tweaks.
> Every item is detailed enough to hand to an AI implementer cold. Grouped into **7 workstreams**,
> each intended as its own branch. Ordering matters where noted — **W1 (foundation refactor) should
> land before the parts of W3/W5 that depend on live DataStore state.**
>
> **Legend:** `[R]` = surfaced in code review · `[U]` = owner tweak · Size = S/M/L/XL effort.
> **Already fixed on `main` in the 2026-07-02 session** (do NOT redo): doomscroll tracker re-seed
> race, in-memory lockout enforcement, `enterTemporaryLockout` end-time reload, screen-off
> accumulation, overlay debounce, settings intercept during active vow, `clearVowConfig` no longer
> wiping shield/blocks, cooling-off toast string. Those are the baseline this plan builds on.

---

## Branch / workstream overview

| # | Branch | Theme | Depends on | Size |
|---|---|---|---|---|
| **W1** | `refactor/viewmodel-datastore-flow` | Make the ViewModel observe DataStore; fix IO-on-lock, write amplification, cache/tamper correctness | — | L |
| **W2** | `refactor/cleanup-and-data-model` | Remove "All Social Media", remove scroll tracking, delete dead code, move startup work off main thread | W1 (light) | M |
| **W3** | `fix/enforcement-correctness` | Domain host-matching, incognito targeting, narrow settings intercept, stricter-while-locked editing, time/reboot edge cases | W1 | L |
| **W4** | `feat/play-compliance-lite` | Gradle flavor split, overlay + UsageStats detection, VpnService domains, manifest split, device-owner row handling | W1, W2 | XL |
| **W5** | `feat/lockout-mascot-dashboard-ux` | Lockout reason/encouragement/timer, smiley on lockout, ticker/ripple removal, limit-mode button, unified back, insights "see more", clock animation, mascot copy | W1 | L |
| **W6** | `feat/onboarding-and-recommended` | Extra onboarding slides + one-tap "recommended settings" | W2 (needs final app-selection model), W4 (permission copy) | M |
| **W7** | `feat/reminder-notifications` | Casual "set a vow" reminder when the app has been idle for days | W1 | M |

**Suggested merge order:** W1 → W2 → W3 → W5 → W6 → W7, with **W4 developed in parallel off `main`
after W1/W2 merge** (it's the biggest and can integrate last). W4's flavor scaffolding is mechanical
and can start immediately if you want to unblock it early (per the existing
`DEPLOYMENT_AND_FEATURE_PLAN.md` §7).

---

## W1 · `refactor/viewmodel-datastore-flow`  *(foundation — do first)* · ✅ DONE (2026-07-03)
> Branch `refactor/viewmodel-datastore-flow`, 5 commits. All 5 tasks landed; suite 77→80 green
> (added `TamperRepairTest`). Live-update path is scoped to service-owned fields only (lockout end
> time, doomscroll accumulation, tamper/boot escalation) so user config stays one-way UI→DataStore.

### W1-T1 · MainViewModel must observe the DataStore, not read it once  `[R]` · L
- **Problem:** `MainViewModel.loadState()` calls `preferencesFlow.first()` exactly once at init. Any
  state the `BlockerService` writes afterward (temporary lockouts, tamper fallback, doomscroll
  accumulation, usage flushes) is invisible to the UI until the process restarts. Several existing
  bugs are symptoms of this single design flaw.
- **Why:** It's the root enabler for W1-T*, W3 (stricter-while-locked), W5 (live lockout timer), and
  removes the need for the one-shot `enterTemporaryLockout()` reload hack added in the last session.
- **Files:** `ui/MainViewModel.kt`.
- **Approach:** Convert `loadState()` into a long-lived `viewModelScope.launch { preferencesFlow.collect { … } }`.
  Split the current body into (a) a pure `mapPrefsToState(prefs)` that updates the non-ephemeral
  fields on every emission, and (b) the one-time countdown/DeviceAdmin reconciliation that must run
  only on first load or on genuine vow-active transitions (guard with a `hasLoadedOnce` flag). Keep
  `frozen*` values seeded once at vow start (they already are, in `addBindingTime`) so continuous
  collection doesn't clobber them. Preserve the existing screen-state precedence
  (INTRUSION/TEMPORARY_LOCKOUT overrides) when merging.
- **Risk:** High-touch. The `frozen*` snapshot fields and the "don't downgrade an overlay screen"
  logic are subtle — write ViewModel tests first (there's an existing `MainViewModelTest.kt`).
- **Verify:** With a vow active, have the service write a lockout; the UI should react without
  restart. Existing 77 tests stay green.

### W1-T2 · Don't hold `cacheMutex` across DataStore writes  `[R]` · M
- **Problem:** `updateUsageStatistics()` holds `cacheMutex.withLock` while calling
  `vowDataStore.savePackageUsage(...)` (a suspending disk write), and `onAccessibilityEvent` does a
  `runBlocking { cacheMutex.withLock { … } }` on the accessibility thread. A disk write can stall
  event handling → jank/ANR.
- **Files:** `service/BlockerService.kt`.
- **Approach:** Inside the lock, snapshot the serialized string and release; perform the DataStore
  write outside the lock. Replace the `runBlocking` interval-reset check with a non-blocking path
  (compute reset need from volatile fields; enqueue the clear on `serviceScope`).
- **Risk:** Ensure the interval-reset race is still correct (two events arriving together). A single
  `@Volatile lastIntervalStartMs` guarded by compare-and-set is enough.

### W1-T3 · Batch `addAllowedScreenTimeMs` instead of one transaction per second  `[R]` · S
- **Problem:** During a vow, allowed-screen-time is persisted with a full DataStore edit every
  second for the entire vow (days). Needless write amplification and battery.
- **Files:** `service/BlockerService.kt` (`manageAllowedTimeTracking`), `data/VowDataStore.kt`.
- **Approach:** Accumulate in a volatile counter; flush every ~30s and on vow end / service destroy,
  mirroring the existing `ticksSinceLastFlush` pattern for package usage.

### W1-T4 · Stop stale usage emissions resurrecting cleared counters  `[R]` · S
- **Problem:** In the collector, the merge rule `if (usage > current || current == 0L)` can re-import
  a stale higher value after an interval reset cleared the cache.
- **Files:** `service/BlockerService.kt` (preferences collector).
- **Approach:** Once W1-T1 makes the collector authoritative, only import persisted usage on first
  load; while running, the in-memory cache is the source of truth (same principle already applied to
  the doomscroll tracker). Add a guard flag `usageCacheSeeded`.

### W1-T5 · Persist the tamper-fallback rewrite  `[R]` · S
- **Problem:** `preferencesFlow`'s tamper branch builds a corrected `mutablePrefs` and emits it, but
  never writes it back; it only "sticks" when some later save happens.
- **Files:** `data/VowDataStore.kt`.
- **Approach:** On detecting an invalid signature, enqueue an explicit `dataStore.edit { }` that
  writes the strict-lockout config + recomputed signature, in addition to emitting it. Guard against
  a write loop (only rewrite if the stored signature actually differs).

---

## W2 · `refactor/cleanup-and-data-model` · ✅ DONE (2026-07-03)
> Branch `refactor/cleanup-and-data-model`, 3 commits (T1 landed earlier on `main`). Suite 80 green,
> debug build clean. Note: `VowDbHelper` and `computeStateSignature` were kept (both still in use).

### W2-T1 · Remove the "All Social Media" magic string  `[U]` · M · ✅ DONE (2026-07-03)
> Removed: `SOCIAL_MEDIA_PACKAGES` set, the dead `isQuietHoursRestrictedAppPackage`, the
> `quietHoursTargetAppSet` service field, all `contains("All Social Media")` branches, the dropdown
> item + chip special-casing, and the tamper-signature defaults (now `emptySet()` in `VowValidator`,
> `VowDataStore.computeSignatureFromPrefs`/`isSignatureValid`). Suite green at 77/77.

- **Goal:** Users pick specific apps; there is no catch-all bucket.
- **Why:** The bucket has 3 inconsistent definitions (`BlockerService.SOCIAL_MEDIA_PACKAGES` = 8 pkgs
  incl. the nonexistent `com.tiktok.android`; `SocialUsageStats.SOCIAL_MEDIA_KEYWORDS` = broader).
  Removing it deletes a whole class of "blocked the wrong thing / congratulated me on an unblocked
  app" bugs (this resolves review item M9).
- **Files (19 references):** `util/VowValidator.kt`, `ui/MainScreen.kt`, `data/VowDataStore.kt`,
  `service/BlockerService.kt`.
- **Approach:**
  1. Delete the `SOCIAL_MEDIA_PACKAGES` set and every `contains("All Social Media")` branch in
     `BlockerService` (`isTargetAppPackage`, `isDoomscrollTargetApp`, `isBlockRestrictedAppPackage`,
     `isQuietHoursRestrictedAppPackage`).
  2. Remove the "All Social Media" dropdown item + chip-label special-casing in `MainScreen`
     (doomscroll picker, usage-limits picker; scheduled-blocks already filter it out).
  3. **Change defaults that reference it, together with the tamper signature**, or existing installs
     trip the 7-day lockout: `quietHoursTargetAppSet` default becomes `emptySet()` in
     `VowValidator.computeHMACSignature`, `VowDataStore.computeSignatureFromPrefs`,
     `isSignatureValid` (the `!= setOf("All Social Media")` first-launch check), and `MainViewModel`.
  4. Keep `SocialUsageStats.SOCIAL_MEDIA_KEYWORDS` — it's only used for the onboarding "scan" and the
     mascot reflection, which are heuristic aggregates, not enforcement. (Optionally rename to make
     that scope explicit.)
- **Risk:** Signature defaults must all change in lockstep. `versionCode` is still 1 (unshipped), so
  no real migration is needed, but add a `StateSignatureTest` case for the new empty default.

### W2-T2 · Remove scroll-activity tracking  `[U]` `[R]` · S
- **Problem:** `accessibility_service_config.xml` requests `typeViewScrolled`, but doomscroll is a
  pure time-in-app metric — the scroll events are never read. Dead capability that widens the
  accessibility surface (bad for the Play review in W4).
- **Files:** `res/xml/accessibility_service_config.xml` (drop `typeViewScrolled`), and audit
  `onAccessibilityEvent` for any scroll-type handling to delete (there is none today, confirm).
- **Note:** Also correct the doomscroll warning copy "You have been scrolling for too long" →
  time-based wording (handled in W5-T8's copy pass).

### W2-T3 · Delete dead code  `[R]` · S
- **NOT dead (keep):** `VowDbHelper.kt` — the history store is hand-rolled SQLite; `VowDatabase` and
  `VowSessionDao` depend on it (the README's "Room" claim is wrong). `VowValidator.computeStateSignature`
  — used by `DataStoreIntegrityTest` and `StateSignatureTest`.
- **Remove:** `VowValidator.SALT` (unused const); `previousIsVowActive` in the `BlockerService`
  collector (assigned, never read); `panelThreeSubtitle` param + `"RESTRICTION_2"` arg (param never
  used in the body); `DashboardPanelButton.subtitle` param + its 3 empty call args + the dead
  `if (subtitle.isNotEmpty())` block.

### W2-T4 · Move startup work off the main thread  `[R]` · S
- **Problem:** `MainViewModel.loadInstalledApps()` runs a synchronous `PackageManager.queryIntentActivities`
  + `loadLabel` on the main thread during init.
- **Approach:** Move into a `viewModelScope.launch(Dispatchers.IO)`; publish into state when ready.
  The apps list is already nullable-safe in the UI (shows fallback list).

---

## W3 · `fix/enforcement-correctness`  *(depends on W1)* · ✅ DONE (2026-07-03)
> Branch `fix/enforcement-correctness`, 6 commits, suite 87 green. T1–T7 landed. Scope note on T4:
> the device-owner restriction toggles' "enable-while-locked (+ live re-assert)" is deferred to W4,
> where those rows are restructured behind the capability interface; W3-T4 fixed the live-enforced
> doomscroll paths and closed the Secure-Folder disable-while-locked hole.

### W3-T1 · Host-based domain matching  `[U]` `[R:M3]` · M
- **Problem:** `DomainUtil.matches` and the `banDomainSet` check use `url.contains(domain)`.
  `instagram.com` matches `notinstagram.company.com` and any URL that merely mentions the domain in a
  path/query (e.g. a news article) → spurious full-screen blackouts.
- **Files:** `util/DomainUtil.kt`, `service/BlockerService.kt` (both banned-domain checks).
- **Approach:** Extract the host from the URL text (strip scheme/path; handle bare
  `host/path` from address bars). Match with label-boundary logic:
  `host == domain || host.endsWith("." + domain)`. Add unit tests (subdomain match, false-friend
  reject, path-mention reject). Keep it tolerant of partial address-bar text (Chrome sometimes shows
  the host only).

### W3-T2 · Targeted incognito detection  `[U]` `[R:M4]` · M
- **Problem:** `isIncognitoModeActive` recursively scans the **entire** node tree for the literal
  "Incognito"/"Secret mode" on every browser content-change → googling the word blackouts the screen,
  and it's the most expensive per-event path.
- **Files:** `service/BlockerService.kt`.
- **Approach:** Check Chrome's incognito indicator by view id / a bounded toolbar subtree rather than
  full-tree text; cap recursion depth. Prefer the Device-Owner `IncognitoModeAvailability` policy in
  the full build (already set in `DeviceAdmin`) and treat the node scan as a lite-only fallback.

### W3-T3 · Narrow the Settings intercept to the dangerous screens  `[U]` `[R:M1]` · L
- **Goal:** During a vow, block only the specific Settings destinations that let the user disable
  aVow — its **app-info / force-stop / clear-data page** and the **Accessibility settings** page —
  not the entire Settings app (currently `performGlobalAction(HOME)` on any `SETTINGS_PACKAGES`
  match). A multi-day vow shouldn't block Wi-Fi or battery settings.
- **Files:** `service/BlockerService.kt`.
- **Approach:** On a Settings window event, inspect the active window content to decide whether it's a
  sensitive screen: match aVow's package label / "aVow" app-info heading, and the Accessibility
  services list. This is heuristic and OEM-dependent — implement per-OEM matchers where feasible and
  fall back to the current broad block only for the accessibility-settings activity
  (`android.settings.ACCESSIBILITY_SETTINGS`) which is detectable. Keep the existing
  behavior gated behind active-vow (already fixed) + cooling-off.
- **Risk:** Fragile across Samsung/Xiaomi/etc. Document the fallback behavior; add a comment that this
  is best-effort friction, consistent with the digital-wellbeing framing in the deployment plan.
- **Note:** Update `BlockerServiceSettingsInterceptTest` expectations to the narrower contract.

### W3-T4 · Make "stricter while locked" actually editable everywhere  `[U]` · L
- **Problem:** The intended model is "you can tighten but never loosen a live vow," but most config
  paths just hard-block edits while locked. Concretely: `onDoomscrollTimeUpdate`,
  `onDoomscrollAllTimeToggle`, and several toggles show "cannot be modified while locked"
  unconditionally, while domains/usage-limits/scheduled-blocks *do* allow stricter edits. It's
  inconsistent, so it reads as "I can't edit anything."
- **Files:** `ui/MainScreen.kt` (all the `onXToggle` / `onXUpdate` lambdas in the CONFIGURATION
  branch), `util/VowValidator.kt` (add comparators where missing).
- **Approach:** Define, per setting, what "stricter" means and allow it while locked; block only
  loosening, with a specific message naming the setting:
  - Doomscroll allowance: already stricter-only (shorter). Extend the same pattern to
    **cooldown** (longer-only, already present) and **time window** (a *wider* window is stricter →
    allow widening, block narrowing; compute via the same minute-set containment used for scheduled
    blocks).
  - Doomscroll target apps: adding is stricter (allowed while locked), removing is looser (blocked) —
    already the intent; make the toggle/time edits obey it instead of a blanket block.
  - Restriction toggles (secure folder, etc.): enabling is stricter (allow), disabling is looser
    (block) — today all are blanket-blocked while locked.
  - Domains: adding stricter (allow), removing looser (block) — already correct; keep.
- **Depends on:** W1 (so edits made while locked persist and reflect back live).
- **Risk:** Each path needs a clear stricter/looser predicate; add tests mirroring the existing
  containment tests.

### W3-T5 · Time-change handling: stop punishing timezone/auto-time  `[R:M7]` · S
- **Problem:** `TimeChangedReceiver` applies a 1-hour lockout on `TIMEZONE_CHANGED` (flights, auto
  timezone) and on any `TIME_SET` (NTP nudges).
- **Files:** `receiver/TimeChangedReceiver.kt`.
- **Approach:** Ignore `TIMEZONE_CHANGED` entirely (the vow clock is `elapsedRealtime`-based, immune
  to wall-clock). For `TIME_SET`, only punish when auto-time is off
  (`Settings.Global.AUTO_TIME == 0`) and the jump is material.

### W3-T6 · Reboot must not inflate doomscroll cooldowns  `[R:M8]` · S
- **Problem:** `temporaryLockoutEndTime` is an `elapsedRealtime` timestamp; after reboot elapsed
  restarts near zero, so a cooldown saved at high uptime over-enforces.
- **Files:** `data/VowDataStore.kt`, `receiver/BootReceiver.kt`, `service/BlockerService.kt`.
- **Approach:** Store a wall-clock companion end time and take the min of the two when enforcing, or
  clear `TEMPORARY_LOCKOUT_END_TIME` in `BootReceiver`. (The main vow countdown already handles
  reboot correctly via `calculateRemainingSeconds`; only the doomscroll cooldown is affected.)

### W3-T7 · Exclude the vow DataStore from backup  `[R:M10]` · S
- **Problem:** `allowBackup=true` + the HMAC key lives in the (non-exportable) Keystore. Restore to a
  new device restores prefs but not the key → signature mismatch → 7-day strict lockout on first run.
- **Files:** `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.
- **Approach:** Exclude the `vow_settings` preferences DataStore file from both backup and D2D
  transfer.

---

## W3.5 · `fix/enforcement-followups`  *(device-testing fallout from W3)*
Found on-device after W3 merged. Branch `fix/enforcement-followups`, suite 89 green.
- **Active mode did nothing without a running vow** — all enforcement was gated on `isVowActive`,
  so choosing Active mode (which is meant to enforce the configured rules *without* a vow) had no
  effect. Fixed by gating on `isEnforcementActive = isVowActive || isActiveVowMode`; each rule still
  respects its own schedule (a 10pm–7am block only fires 10pm–7am — Active mode just removes the vow
  requirement). Removed the old "continuous 24/7 lockout" section, which was a misreading of the
  semantics. See [[vow-mode-semantics]].
- **Settings intercept** now applies during any active enforcement (passive vow too — you shouldn't
  be able to disable the service or wipe data to escape a vow), and the app-label scan depth was
  raised 12 → 40 (Samsung App info / Storage nests the label deep, so those screens weren't caught).
- **Vow mode reset to Passive when a vow ended** — `IS_ACTIVE_VOW_MODE` is now preserved as the
  user's persistent preference.
- Still open (low priority): manual-clock-change "punishment" sets a cooldown silently (only felt if
  a target app is opened) — cosmetic; the timer itself correctly ignores wall-clock changes.

## W4 · `feat/play-compliance-lite`  *(largest; develop in parallel, integrate last)*

> Verified against current policy (2026): accessibility review tightened Jan 2026;
> `isAccessibilityTool` restricted from Oct 2026; Android 17 Advanced Protection auto-revokes
> accessibility for non-tool apps. The lite build must not depend on accessibility for its **core**
> blocking. See the review write-up for sources.

### W4-T1 · Gradle product-flavor split  `[from deployment plan §7]` · M · ✅ DONE (2026-07-03)
> Branch `feat/flavor-split`. Both `lite`/`full` compile and pass 89/89 tests each. `EnforcementCapabilities`
> interface in `src/main`; `Full…`/`Lite…` impls + `CapabilitiesFactory` per flavor; `MainViewModel`
> now uses injected `capabilities` (no `DevicePolicyManager`/`DeviceAdmin` refs in `src/main`).
> `DeviceAdmin.kt` + its receiver/metadata moved to `src/full`; verified the lite merged manifest
> has zero device-admin surface. lite = "aVow" (`com.avow.app`), full = "aVow Plus" (`com.avow.app.plus`).
- **Branding (decided 2026-07-03):** lite = **"aVow"**, `applicationId com.avow.app` (the Play
  listing, the "normal" app). full = **"aVow Plus"**, `applicationId com.avow.app.plus` +
  `versionNameSuffix "-plus"` (GitHub power build; distinct id so both can be installed side by side).
  Override `app_name` per flavor via `src/full/res/values/strings.xml`.
- Add `lite` / `full` flavors + `src/lite` / `src/full` source sets. Introduce
  `EnforcementCapabilities` interface in `src/main` with `full`/`lite` impls + a `CapabilitiesFactory`
  (full spec already in `DEPLOYMENT_AND_FEATURE_PLAN.md` §7.2–7.5). Move the `DeviceAdminReceiver`
  declaration into `src/full/AndroidManifest.xml` so the **lite APK carries no device-admin surface
  at all** (reviewer optics). Confirm `assembleLiteDebug` and `assembleFullDebug` both compile.
- **Size note:** mechanical; can start before W1/W2 merge.

### W4-T2 · Compliant foreground detection: UsageStats + foreground service  `[U:9]` · XL
- **Goal:** Detect the foreground app **without** accessibility in lite.
- **Approach:** A foreground `Service` (typed `specialUse` or `dataSync` per current FGS rules) polls
  `UsageStatsManager.queryEvents` (~1s) for `MOVE_TO_FOREGROUND`. Reuse the existing target-set /
  usage-limit / scheduled-block / doomscroll logic, refactored out of `BlockerService` into a shared
  `EnforcementEngine` both the accessibility service (full) and the poller (lite) call.
- **Risk:** Polling latency (~1s) and Doze. Keep the FGS + battery-unrestricted guidance.

### W4-T3 · Overlay blocking UI (`TYPE_APPLICATION_OVERLAY`)  `[U:9]` · L
- **Problem:** Today the "blackout" is a launched full-screen `Activity`. Launching an activity over
  another app is increasingly restricted and looks worse to reviewers than an overlay.
- **Approach:** Add a `SYSTEM_ALERT_WINDOW` overlay window rendering the block screen (reason +
  encouragement + timer from W5-T1). Keep the activity path as the full-build fallback if desired.
- **Ties to:** onboarding permission request (W4-T5) and W5-T1 (the overlay content).

### W4-T4 · VpnService domain blocking (lite)  `[U:9]` · L
- **Why:** UsageStats can't read URLs; accessibility URL-reading is exactly what Play scrutinizes.
  A **local, no-server** VpnService that filters DNS/drops banned domains is the compliant path for
  `banDomainSet` in lite (deployment plan §6). No traffic leaves the device.
- **Approach:** New `VpnService` gated on the VPN consent dialog; wire `banDomainSet` (host-matched
  per W3-T1). Full build can keep both VPN and accessibility URL checks.
- **Risk:** Largest single new subsystem. Could be a **sub-branch** `feat/vpn-domain-blocking` off W4.

### W4-T5 · Onboarding permission swap  `[U:8,9]` · M
- **Goal:** Lite onboarding requests **Usage Access + Overlay (+ optional VPN consent)** as the core
  permissions; **Accessibility becomes an optional "stronger enforcement" upgrade**, not a hard gate.
- **Files:** `ui/OnboardingScreen.kt` (`SlidePermissions`, the `continueBlocked` gate), plus a proper
  **prominent-disclosure consent screen** for any accessibility use (required by policy).
- **Approach:** Per-flavor permission list. In lite, `continueBlocked` keys off overlay+usage, not
  accessibility. Add the disclosure copy naming exactly what each permission reads and why.

### W4-T6 · Device-owner rows: omit in lite, ungray the genuinely no-DO ones  `[U:20]` `[R]` · M
- **Reality check:** Of the eight `ENFORCEMENT RESTRICTIONS`, Knox Secure Folder, Private Space,
  uninstall-lock, data-wipe, safe-boot, Play-Store-block, and USB-debugging **all require Device
  Owner** — none can be honestly "ungrayed" in lite; they must be **omitted** via
  `caps.supportsX` (deployment plan §7.6), not shown greyed (greyed rows a user can never enable read
  as broken).
- **Dynamic Reinstall Guard — decided (2026-07-03):** **dropped entirely from lite** (omit the row
  like the other device-owner features). In full it stays as a full-only row.
- **Files:** `ui/MainScreen.kt` (`restrictionsList`, the `isItemEnabled` logic), capabilities
  interface.

### W4-T7 · Play submission collateral  `[U:9]` · M
- `QUERY_ALL_PACKAGES` → replace with a `<queries><intent>` launcher declaration if possible (you
  only enumerate launchable apps), else file the declaration.
- Data Safety form (declare on-device-only; keep lite network-free aside from the local VPN),
  hosted privacy-policy URL, foreground-service-type disclosure, expanded accessibility
  `description` string. Category = Productivity, not Parental Control.

---

## W5 · `feat/lockout-mascot-dashboard-ux`  *(depends on W1 for the live timer)*

### W5-T1 · Rich lockout screen: reason + encouragement + vow timer  `[U:4]` · L
- **Goal:** When blocked, tell the user **what fired**, a **relevant** encouragement, and the
  **vow countdown when a vow is active**. E.g. `[ USAGE LIMITS ]` · "That's enough for now — back to
  it." · `02:14:07`.
- **Files:** `service/BlockerService.kt` (all `triggerBlackoutOverlay()` call sites),
  `MainActivity.kt`, `ui/MainViewModel.kt`, `ui/MainScreen.kt` (`IntrusionInterceptOverlay`),
  `ui/MascotMessages.kt`.
- **Approach:**
  1. Add a `BLOCK_REASON` enum (`USAGE_LIMIT`, `SCHEDULED_BLOCK`, `ACTIVE_VOW`, `BANNED_DOMAIN`,
     `INCOGNITO`, `DOOMSCROLL`) passed as an intent extra from each block site.
  2. Thread it through `handleIntent` → state → overlay.
  3. `IntrusionInterceptOverlay` renders: reason label (system voice), a `MascotMessages`
     encouragement keyed by reason (new map, coach voice), and — if `isVowActive` — the live
     `days/hours/minutes/seconds` countdown (available once W1 makes it live). For an **active vow**
     there may be no timer; show the "ACTIVE" status instead of a countdown.
- **Note:** The doomscroll `TemporaryLockoutOverlay` already got a countdown in the last session;
  fold its copy into the same reason/encouragement system for consistency.

### W5-T2 · Smiley (not straight face) on the doomscroll lockout  `[U:3,U:14]` · XS
- **Files:** `ui/MainScreen.kt` → `TemporaryLockoutOverlay` currently uses `StraightFaceOutline`;
  swap to `SmileyFaceOutline`. (One line; U3 and U14 are the same request.)

### W5-T3 · Remove the live ticker from the doomscroll **config**  `[U:2]` · XS
- **Files:** `ui/MainScreen.kt` → delete the "LIVE TICKER: Ns" / lockout block in
  `ConfigurationWorkspace` (the `doomscrollAccumulatedMs` display). Keep the cooldown/allowance
  sliders. Drop the now-unused `doomscrollAccumulatedMs` param if nothing else reads it.

### W5-T4 · Remove the tap ripple on the header mascot  `[U:15]` · XS
- **Files:** `ui/MainScreen.kt` → the `SmileyFaceOutline(... .clickable { … })` in `VaultDashboard`.
  Use `clickable(interactionSource = remember { MutableInteractionSource() }, indication = null)`.

### W5-T5 · Limit-mode toggle should look like a button  `[U:13]` · S
- **Files:** `ui/MainScreen.kt` → `UsageLimitsConfigDialog`, the
  `LIMIT MODE: [ COLLECTIVE / INDEPENDENT ]` tappable `Text`. Replace with a two-segment control
  (reuse the `ModeSegment`/`SingleChoiceSegmentedButtonRow` style already in the app) + one line
  explaining each mode.

### W5-T6 · Unify the two back buttons  `[U:11]` · XS
- **Files:** `ui/MainScreen.kt` → `ConfigurationWorkspace` uses a `< BACK` text link; change it to the
  `{ BACK }` `SharpBorderButton` used by `FocusHistoryWorkspace`, matching placement.

### W5-T7 · Focus insights: latest 5 sessions + "see more"  `[U:12]` · S
- **Files:** `ui/MainScreen.kt` → `FocusHistoryWorkspace` session-log list. Show `sessions.take(5)`;
  add a `[ SEE MORE ]` row that expands to the full (scrollable) list. Stats/graph unchanged.

### W5-T8 · Shorter mascot copy; drop the "// aVow" header  `[U:18]` · S
- **Files:** `ui/MascotMessages.kt`, `ui/MainScreen.kt` (`MascotSpeechBubble`).
- **Approach:** Remove the leading greeting fragment from `generateMessage` (drop the
  `greetings` pool or the first clause) so lines are just context + tagline. Remove the `// aVow`
  `Text` above the bubble body. Also fix the time-based doomscroll warning copy here (W2-T2).

### W5-T9 · Animate the vault clock  `[U:16]` · M
- **Files:** `ui/MainScreen.kt` → `ClockDigitColumn`. Animate the seconds digit on tick (e.g.
  `AnimatedContent` / slide-roll per the design plan's "boot-up / digit roll"). Keep the static
  `00:00:00:00` when idle (U10 — do **not** replace the idle clock with an invitation state; the
  owner wants it kept). Watch recomposition cost since it ticks every second.

---

## W6 · `feat/onboarding-and-recommended`  *(depends on W2 app-selection model; W4 for permission copy)*

### W6-T1 · Feature-education onboarding slides  `[U:8]` · M
- **Goal:** Teach how to actually use the app: scheduled blocks, usage limits, doomscroll detection,
  focus insights, passive vs active — before the ready screen.
- **Files:** `ui/OnboardingScreen.kt` (bump `ONBOARDING_SLIDE_COUNT`, add slides + dots).
- **Approach:** Add concise, illustrated slides (reuse the mascot + system-voice copy). Keep them
  skippable and the typewriter tap-to-complete (see W6-T3). Sequence them after the apps/domains pick
  so the recommended-settings step (W6-T2) can reference the user's choices.

### W6-T2 · One-tap "Use recommended settings"  `[U:8]` · M
- **Spec (exact):** Applies **passive vow** mode, **one enabled scheduled block 22:00→07:00** on the
  apps + domains selected during onboarding, **usage limits enabled at 10 min per hour, INDEPENDENT**
  on those same apps. (Doomscroll left off unless the user opts in.)
- **Files:** `ui/OnboardingScreen.kt` (new CTA on the relevant slide), `ui/MainViewModel.kt` (an
  `applyRecommendedSettings(selectedApps, domains)` that sets state + persists).
- **Resolved (2026-07-03):** the final onboarding page hosts the same `WheelNumberPicker` clock
  scroller used in the binding-vow dialog, so the user sets a duration and **inflicts the first vow
  right there** — this closes the "passive mode enforces nothing" gap. `applyRecommendedSettings`
  arms the config; the last slide's picker + CTA calls `addBindingTime(seconds)` to start the vow
  before entering the vault. Reuse `WheelNumberPicker` + `BindingVowConfigDialog`'s totalSeconds math.

### W6-T3 · Skippable typewriter text  `[R]` · S
- **Problem:** `TypewriterText` (and the mascot bubble) can't be skipped; ~5–8s per paragraph on every
  slide entry, including going back.
- **Files:** `ui/OnboardingScreen.kt`, `ui/MainScreen.kt`.
- **Approach:** First tap completes the reveal; second advances.

---

## W7 · `feat/reminder-notifications`  *(depends on W1)*

### W7-T1 · Casual "set a vow" reminder when idle  `[U:17]` · M
- **Goal:** If the user hasn't opened the app for several days **and** hasn't had a vow (and wasn't
  in a multi-day vow) for several days, send a gentle reminder notification to set one.
- **New dependency:** WorkManager (`androidx.work:work-runtime-ktx`) — not currently in the project.
- **Files:** new `worker/ReminderWorker.kt`, `data/VowDataStore.kt` (persist `lastAppOpenMs` and
  `lastVowEndedMs`; update `lastAppOpenMs` in `MainActivity.onStart`), `AndroidManifest.xml`.
- **Approach:** A periodic WorkManager job (daily) checks the two timestamps; if both exceed the
  threshold (e.g. 3 days) and no vow is active, post one coach-voice notification via the existing
  notification channel. Debounce so it fires at most once every N days. Respect
  `POST_NOTIFICATIONS`.
- **Risk:** Don't nag — cap frequency and suppress entirely while a vow is active or a cooldown is
  running.

---

## Cross-cutting notes

- **Owner overrides of the review:** keep the `00:00:00:00` idle clock (W5-T9), and animate it. These
  supersede the earlier "replace the dead clock" suggestion.
- **Duplicates merged:** U3 == U14 (smiley on doomscroll lockout, W5-T2). U7 == review M3+M4
  (W3-T1/T2).
- **Tests:** every enforcement change (W3) and the ViewModel refactor (W1) should extend the existing
  JVM suites (`StateSignatureTest`, `ContainmentAndSignatureTest`, `BlockerServiceSettingsInterceptTest`,
  `MainViewModelTest`, `DoomscrollTrackerTest`). Target: suite stays green at each merge.

## Open decisions
1. ~~Recommended-settings + passive mode~~ — **RESOLVED:** last onboarding slide has the clock
   scroller and inflicts the first vow there (W6-T2).
2. ~~Dynamic Reinstall Guard~~ — **RESOLVED:** dropped from lite; full-only (W4-T6).
3. ~~Lite branding~~ — **RESOLVED:** lite = "aVow" (`com.avow.app`), full = "aVow Plus"
   (`com.avow.app.plus`) (W4-T1).
4. ~~VpnService scope~~ — **RESOLVED:** ship the local `VpnService` domain blocking **in the first
   lite release** alongside app-blocking (W4-T4).

## Full ("SDK") vs lite: keep device-owner code for full, strip it from lite
The device-owner features (Knox Secure Folder, Private Space, uninstall lock, data wipe, safe boot,
Play-Store block, USB debugging) stay **only in the `full` flavor** and are **compiled out of `lite`**
— not a separate repo or branch. One `main` branch, two build variants:

```
app/src/main/   ← shared: all UI, enforcement engine, onboarding, mascot (~95%)
app/src/full/   ← DeviceAdminReceiver + FullEnforcementCapabilities (device-owner powers)
app/src/lite/   ← LiteEnforcementCapabilities (every capability reports false / no-ops)
```

Shared code never names `DevicePolicyManager` directly; it calls `CapabilitiesFactory.create(context)`
and gets the right impl at compile time. The `DeviceAdminReceiver` manifest declaration lives in
`app/src/full/AndroidManifest.xml`, so the lite APK ships with zero device-admin surface. Builds:
`./gradlew assembleFullRelease` (GitHub) and `assembleLiteRelease` (Play). This is W4-T1; the full
interface/impl/factory skeleton is already written out in `DEPLOYMENT_AND_FEATURE_PLAN.md` §7.2–7.5.
