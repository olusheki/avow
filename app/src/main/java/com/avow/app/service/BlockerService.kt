package com.avow.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.avow.app.MainActivity
import com.avow.app.data.VowDataStore
import com.avow.app.model.VowBlock
import kotlinx.coroutines.*
import kotlin.concurrent.withLock
import java.util.Calendar
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt
import com.avow.app.data.history.VowDatabase
import com.avow.app.data.history.VowSession

class BlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "BlockerService"
        
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.google.android.settings",
            "com.oneplus.security",
            "com.oneplus.settings",
            "com.coloros.safecenter",
            "com.coloros.settings",
            "com.huawei.systemmanager",
            "com.huawei.settings",
            "com.vivo.settings"
        )
    }

    private val serviceJob = SupervisorJob()
    // Fire-and-forget DataStore writes are launched into this scope; a thrown IOException (disk full,
    // corrupt prefs file) would otherwise propagate uncaught and kill the whole app process — taking
    // enforcement down with it. Log and carry on instead.
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Uncaught exception in a BlockerService coroutine, recovered.", e)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + serviceExceptionHandler)
    private var trackingJob: Job? = null

    // Lightweight in-memory cache of restriction variables
    @Volatile private var isVowActive = false
    @Volatile private var isActiveVowMode = false
    @Volatile private var deactivationRequestTime = 0L
    @Volatile private var banDomainSet = emptySet<String>()
    @Volatile private var secureFolderEnabled = false
    @Volatile private var privateSpaceEnabled = false
    @Volatile private var quietHoursEnabled = false
    @Volatile private var quietStartHour = 22
    @Volatile private var quietStartMin = 0
    @Volatile private var quietEndHour = 7
    @Volatile private var quietEndMin = 0
    @Volatile private var quietHoursSpecificDomain = ""
    @Volatile private var usageLimitsUpdated = false
    @Volatile private var allowedValue = "5"
    @Volatile private var allowedUnit = "min"
    @Volatile private var selectedInterval = "hour"
    @Volatile private var targetAppSet = emptySet<String>()
    @Volatile private var specificDomain = ""
    @Volatile private var lastIntervalStartMs = 0L

    @Volatile private var doomscrollShieldEnabled = false
    @Volatile private var doomscrollAllTime = false
    @Volatile private var doomscrollStartHour = 23
    @Volatile private var doomscrollStartMin = 0
    @Volatile private var doomscrollEndHour = 5
    @Volatile private var doomscrollEndMin = 0
    @Volatile private var doomscrollTargetApps = emptySet<String>()
    @Volatile private var doomscrollCooldownMinutes = 60
    @Volatile private var doomscrollAllowanceMinutes = 15

    @Volatile private var isCollectiveLimit = false
    @Volatile private var packageUsageJsonStr = ""
    private val packageUsageCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    // Plain lock (not a coroutine Mutex): critical sections are tiny in-memory ops, and this lets
    // the accessibility thread guard the cache without runBlocking. Never suspend while holding it.
    private val cacheLock = java.util.concurrent.locks.ReentrantLock()
    @Volatile private var usageCacheSeeded = false

    @Volatile private var lastTrackedPackage = ""
    @Volatile private var lastFlushTime = 0L
    @Volatile private var ticksSinceLastFlush = 0

    @Volatile private var currentForegroundPackage = ""
    @Volatile private var currentBrowserUrl = ""
    @Volatile private var vowBlocks = emptyList<VowBlock>()
    // The launcher/dialer/self set that must never be blocked (see BlockGuard). Resolved in onCreate.
    @Volatile private var neverBlockablePackages = emptySet<String>()
    private lateinit var vowDataStore: VowDataStore

    private val userPresentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    if (isVowActive) {
                        serviceScope.launch { vowDataStore.incrementPickupsCount() }
                    }
                    // Screen came back inside a doomscroll target: apply the leaky-bucket drain
                    // for the time the screen was off (recorded below on ACTION_SCREEN_OFF).
                    if (doomscrollShieldEnabled && isDoomscrollTargetApp(currentForegroundPackage)) {
                        handleForegroundPackageChange("", currentForegroundPackage)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen off while inside a doomscroll target counts as leaving it.
                    if (doomscrollShieldEnabled && isDoomscrollTargetApp(currentForegroundPackage)) {
                        val now = System.currentTimeMillis()
                        doomscrollLastClosedTime = now
                        val snapshot = doomscrollTracker.accumulatedMs
                        serviceScope.launch {
                            vowDataStore.saveDoomscrollLastClosedTime(now)
                            if (snapshot > 0L) vowDataStore.saveDoomscrollAccumulatedMs(snapshot)
                        }
                    }
                }
            }
        }
    }

    @Volatile private var doomscrollLastClosedTime = 0L
    @Volatile private var doomscrollAccumulatedMs = 0L
    @Volatile private var temporaryLockoutEndTime = 0L
    @Volatile private var doomscrollTrackerSeeded = false
    @Volatile private var lastOverlayLaunchMs = 0L
    private var doomscrollTicksSinceFlush = 0
    // Our own app label, used to scope the Settings intercept to screens about aVow. Defaults to
    // "aVow" so unit tests (which don't run onCreate) don't depend on resources.
    @Volatile private var selfLabel = "aVow"

    private val doomscrollTracker = com.avow.app.util.DoomscrollTracker()
    private var allowedTimeTrackingJob: Job? = null

    /**
     * Whether the configured rules (scheduled blocks, usage limits, banned domains) should enforce.
     * True when a timed vow is running (Passive or Active) OR the user chose Active mode — in which
     * case the rules enforce on their own schedule with no vow required. This is NOT 24/7 blocking:
     * each rule still respects its own window/limit; Active mode only removes the vow requirement.
     */
    private val isEnforcementActive: Boolean get() = isVowActive || isActiveVowMode
 
    /**
     * Seeds the live doomscroll counter from the persisted value on the FIRST emission only. Split
     * out of the onCreate collector so it's unit-testable. Re-applying on later emissions raced the
     * async per-tick saves and snapped the live counter back to stale values (the unreliable-lockout
     * root cause). Disabling the shield resets the counter.
     */
    @Synchronized
    internal fun applyDoomscrollSeed(diskAccumulatedMs: Long, shieldEnabled: Boolean) {
        if (!doomscrollTrackerSeeded) {
            doomscrollTracker.accumulatedMs = diskAccumulatedMs
            doomscrollTrackerSeeded = true
        }
        if (!shieldEnabled) {
            doomscrollTracker.accumulatedMs = 0L
            doomscrollTracker.warningSent = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        vowDataStore = VowDataStore(this)
        selfLabel = try { getString(com.avow.app.R.string.app_name) } catch (e: Throwable) { "aVow" }
        neverBlockablePackages = try {
            com.avow.app.util.BlockGuard.neverBlockablePackages(this)
        } catch (e: Throwable) { setOf(packageName) }
        val presenceFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(userPresentReceiver, presenceFilter)

        // Persist the softened tamper/key-loss fallback if the signed state didn't verify (the read
        // flow only corrects it in memory otherwise). The service is the boot-time enforcement anchor.
        // When it actually applies (once per detection), tell the user why so a blameless key
        // invalidation isn't a silent, unexplained lockout.
        serviceScope.launch {
            if (vowDataStore.repairTamperedStateIfNeeded()) {
                showTamperLockoutNotification()
            }
        }

        // Collect DataStore flow asynchronously to maintain in-memory cache
        serviceScope.launch {
            vowDataStore.preferencesFlow.collect { prefs ->
                val activeNow = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
                
                isVowActive = activeNow
                isActiveVowMode = prefs[VowDataStore.IS_ACTIVE_VOW_MODE] ?: false
                deactivationRequestTime = prefs[VowDataStore.DEACTIVATION_REQUEST_TIME] ?: 0L
                banDomainSet = prefs[VowDataStore.BAN_DOMAIN_SET] ?: emptySet()
                secureFolderEnabled = prefs[VowDataStore.SECURE_FOLDER_ENABLED] ?: false
                privateSpaceEnabled = prefs[VowDataStore.PRIVATE_SPACE_ENABLED] ?: false
                quietHoursEnabled = prefs[VowDataStore.QUIET_HOURS_ENABLED] ?: false
                quietStartHour = prefs[VowDataStore.QUIET_START_HOUR] ?: 22
                quietStartMin = prefs[VowDataStore.QUIET_START_MIN] ?: 0
                quietEndHour = prefs[VowDataStore.QUIET_END_HOUR] ?: 7
                quietEndMin = prefs[VowDataStore.QUIET_END_MIN] ?: 0
                quietHoursSpecificDomain = prefs[VowDataStore.QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
                usageLimitsUpdated = prefs[VowDataStore.USAGE_LIMITS_UPDATED] ?: false
                allowedValue = prefs[VowDataStore.ALLOWED_VALUE] ?: "5"
                allowedUnit = prefs[VowDataStore.ALLOWED_UNIT] ?: "min"
                selectedInterval = prefs[VowDataStore.SELECTED_INTERVAL] ?: "hour"
                targetAppSet = prefs[VowDataStore.TARGET_APP_SET] ?: emptySet()
                specificDomain = prefs[VowDataStore.SPECIFIC_DOMAIN] ?: ""
                lastIntervalStartMs = prefs[VowDataStore.LAST_INTERVAL_START_MS] ?: 0L
                isCollectiveLimit = prefs[VowDataStore.IS_COLLECTIVE_LIMIT] ?: false
                val savedLastClosed = prefs[VowDataStore.DOOMSCROLL_LAST_CLOSED_TIME] ?: 0L
                if (doomscrollLastClosedTime == 0L && savedLastClosed > 0L) {
                    doomscrollLastClosedTime = savedLastClosed
                }
                doomscrollAccumulatedMs = prefs[VowDataStore.DOOMSCROLL_ACCUMULATED_MS] ?: 0L
                // The service's in-memory value is authoritative while running: persisted saves are
                // async and unordered, so a disk value must never shrink an already-enforced lockout.
                temporaryLockoutEndTime = maxOf(temporaryLockoutEndTime, prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L)
                doomscrollShieldEnabled = prefs[VowDataStore.DOOMSCROLL_SHIELD_ENABLED] ?: false
                doomscrollAllTime = prefs[VowDataStore.DOOMSCROLL_ALL_TIME] ?: false
                doomscrollStartHour = prefs[VowDataStore.DOOMSCROLL_START_HOUR] ?: 23
                doomscrollStartMin = prefs[VowDataStore.DOOMSCROLL_START_MIN] ?: 0
                doomscrollEndHour = prefs[VowDataStore.DOOMSCROLL_END_HOUR] ?: 5
                doomscrollEndMin = prefs[VowDataStore.DOOMSCROLL_END_MIN] ?: 0
                doomscrollTargetApps = prefs[VowDataStore.DOOMSCROLL_TARGET_APP_SET] ?: emptySet()
                doomscrollCooldownMinutes = prefs[VowDataStore.DOOMSCROLL_COOLDOWN_MINUTES] ?: 60
                doomscrollAllowanceMinutes = prefs[VowDataStore.DOOMSCROLL_ALLOWANCE_MINUTES] ?: 15
                applyDoomscrollSeed(doomscrollAccumulatedMs, doomscrollShieldEnabled)
                val blocksJson = prefs[VowDataStore.VOW_BLOCKS_JSON] ?: ""
                vowBlocks = VowBlock.deserializeList(blocksJson)

                manageAllowedTimeTracking()

                // Import persisted usage only when seeding (service start / between vows). While a
                // vow runs, the in-memory cache is authoritative — re-importing our own async
                // writes could resurrect a stale higher value after an interval reset.
                val usageJson = prefs[VowDataStore.PACKAGE_USAGE_JSON] ?: ""
                if (usageJson != packageUsageJsonStr && (!usageCacheSeeded || !activeNow)) {
                    packageUsageJsonStr = usageJson
                    cacheLock.withLock {
                        val parsedMap = com.avow.app.data.PackageUsageSerializer.deserialize(usageJson)
                        if (parsedMap.isEmpty()) {
                            packageUsageCache.clear()
                        } else {
                            for (pkg in packageUsageCache.keys) {
                                if (!parsedMap.containsKey(pkg)) {
                                    packageUsageCache.remove(pkg)
                                }
                            }
                            for ((pkg, usage) in parsedMap) {
                                val current = packageUsageCache[pkg] ?: 0L
                                if (usage > current || current == 0L) {
                                    packageUsageCache[pkg] = usage
                                }
                            }
                        }
                    }
                }
                usageCacheSeeded = activeNow
            }
        }
    }

    private fun manageAllowedTimeTracking() {
        if (isVowActive) {
            if (allowedTimeTrackingJob == null || !allowedTimeTrackingJob!!.isActive) {
                allowedTimeTrackingJob = serviceScope.launch {
                    val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    // Accumulate in memory and flush every ~30s instead of a DataStore transaction
                    // per second (a vow can run for days). Flush on exit too so nothing is lost.
                    var pendingMs = 0L
                    try {
                        while (isVowActive) {
                            delay(1000L)
                            val fg = currentForegroundPackage
                            if (fg.isNotEmpty() && isPermittedAppInForeground(fg)) {
                                if (pm == null || pm.isInteractive) {
                                    pendingMs += 1000L
                                }
                            }
                            if (pendingMs >= 30000L) {
                                vowDataStore.addAllowedScreenTimeMs(pendingMs)
                                pendingMs = 0L
                            }
                        }
                    } finally {
                        if (pendingMs > 0L) {
                            withContext(NonCancellable) { vowDataStore.addAllowedScreenTimeMs(pendingMs) }
                        }
                    }
                }
            }
        } else {
            allowedTimeTrackingJob?.cancel()
            allowedTimeTrackingJob = null
        }
    }

    private fun isPermittedAppInForeground(packageName: String): Boolean {
        if (isTargetAppPackage(packageName)) return false
        
        if (packageName == "com.android.chrome" || packageName == "com.sec.android.app.sbrowser") {
            val activeUrl = currentBrowserUrl
            if (activeUrl.isNotEmpty()) {
                val isBanned = banDomainSet.any { domain ->
                    com.avow.app.util.DomainUtil.matchesHost(activeUrl, domain)
                }
                if (isBanned) return false
                
                if (com.avow.app.util.DomainUtil.matches(activeUrl, specificDomain)) {
                    return false
                }

                for (block in vowBlocks) {
                    if (block.isEnabled &&
                        isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin) &&
                        com.avow.app.util.DomainUtil.matches(activeUrl, block.specificDomain)) {
                        return false
                    }
                }
            }
        }
        
        if (packageName == this.packageName) return false
        val lower = packageName.lowercase()
        if (lower.contains("launcher") || lower.contains("systemui") || packageName == "android") return false

        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val pkgName = event.packageName?.toString() ?: return

            // Our own UI (the vault, or the intercept/lockout overlay) came to the foreground.
            // Record it as the foreground package so the usage/doomscroll trackers see the target
            // app is no longer in front and stop firing the overlay. Otherwise the trackers keep
            // believing the target app is foreground and re-launch the overlay ~1s after every
            // double-tap dismissal, trapping the user on the block screen. Never enforce against us.
            if (pkgName == packageName) {
                if ((event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
                    currentForegroundPackage != packageName
                ) {
                    val oldForeground = currentForegroundPackage
                    currentForegroundPackage = packageName
                    handleForegroundPackageChange(oldForeground, packageName)
                    manageTrackingJob()
                }
                return
            }

            // Eagerly check and reset cache if a new usage interval begins
            val nowMs = System.currentTimeMillis()
            var intervalReset = false
            cacheLock.withLock {
                if (com.avow.app.util.VowValidator.isNewUsageInterval(nowMs, lastIntervalStartMs, selectedInterval)) {
                    packageUsageCache.clear()
                    lastIntervalStartMs = nowMs
                    intervalReset = true
                }
            }
            if (intervalReset) {
                val serialized = com.avow.app.data.PackageUsageSerializer.serialize(packageUsageCache)
                packageUsageJsonStr = serialized
                serviceScope.launch {
                    vowDataStore.savePackageUsage(serialized, nowMs)
                }
            }

            // Intercept any attempt to launch target apps during temporary lockout
            if (SystemClock.elapsedRealtime() < temporaryLockoutEndTime) {
                if (isDoomscrollTargetApp(pkgName)) {
                    triggerLockoutOverlay()
                    return
                }
            }

            var pkgChanged = false
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                if (pkgName.isNotEmpty() && pkgName != currentForegroundPackage) {
                    val oldForeground = currentForegroundPackage
                    currentForegroundPackage = pkgName
                    handleForegroundPackageChange(oldForeground, pkgName)
                    pkgChanged = true
                }
            }

            if (SETTINGS_PACKAGES.contains(pkgName)) {
                val isCoolingOff = deactivationRequestTime > 0L &&
                        (System.currentTimeMillis() - deactivationRequestTime) < 24L * 3600L * 1000L
                // Only bounce the Settings screens that are *about aVow* — its app-info/force-stop/
                // clear-data page and the accessibility toggle — not the whole Settings app, so a
                // multi-day vow doesn't block Wi-Fi, battery, etc. Applies during ANY running vow
                // (passive too — the user shouldn't be able to disable the service or wipe data to
                // escape a vow) and during the deactivation cooling-off window.
                if ((isCoolingOff || isEnforcementActive) && isSettingsScreenAboutSelf()) {
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    return
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                pkgChanged ||
                (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                 (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser"))) {
                manageTrackingJob()
            }

            // Picture-in-Picture evasion: dragging a target into a PiP window keeps it playing while
            // a different app becomes "foreground", dodging every currentForegroundPackage check
            // below. Scan the on-screen windows for a restricted target in PiP and block it. Runs
            // before the enforcement-active return so a doomscroll-only shield (no vow) still catches
            // it. Gated to window-state changes to bound the (cheap) windows scan.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (restrictedPackageInPip() != null) {
                    triggerBlackoutOverlay("PICTURE-IN-PICTURE")
                    return
                }
            }

            // Enforce when a vow is running OR the user chose Active mode (rules apply without a vow).
            if (!isEnforcementActive) return

            // 1. Check profile stubs (Samsung Secure Folder & Android 15 Private Space)
            if ((secureFolderEnabled && pkgName == "com.samsung.knox.securefolder") ||
                (privateSpaceEnabled && pkgName == "com.google.android.apps.privatespace")) {
                triggerBlackoutOverlay("SECURE PROFILE")
                return
            }

            // 2. Browser URL and Incognito checks (Chrome / Samsung Internet)
            if (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser") {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        // Check for Incognito/Secret mode window
                        if (isIncognitoModeActive(rootNode, pkgName)) {
                            triggerBlackoutOverlay("PRIVATE BROWSING")
                            return
                        }

                        val activeUrl = extractUrlOptimized(rootNode, pkgName)
                        currentBrowserUrl = activeUrl
                        if (activeUrl.isNotEmpty()) {
                            val isBanned = banDomainSet.any { domain ->
                                com.avow.app.util.DomainUtil.matchesHost(activeUrl, domain)
                            }
                            if (isBanned) {
                                triggerBlackoutOverlay("BANNED SITE")
                                return
                            }
                        }
                    } finally {
                        rootNode.recycle()
                    }
                }
            }

            // 3. Quiet Hours block check
            for (block in vowBlocks) {
                if (block.isEnabled && isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin)) {
                    if (isBlockRestrictedAppPackage(block, pkgName)) {
                        triggerBlackoutOverlay("SCHEDULED BLOCK")
                        return
                    }
                    if (block.specificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
                        val activeUrl = currentBrowserUrl
                        if (activeUrl.isNotEmpty() && com.avow.app.util.DomainUtil.matches(activeUrl, block.specificDomain)) {
                            triggerBlackoutOverlay("SCHEDULED BLOCK")
                            return
                        }
                    }
                }
            }

            // 4. Usage limit block check (if limits updated/active and already exceeded)
            if (usageLimitsUpdated && isCurrentlyRestrictedForUsage()) {
                val limitVal = allowedValue.toDoubleOrNull() ?: 0.0
                val limitMs = if (allowedUnit.equals("hours", ignoreCase = true) || allowedUnit.equals("hour", ignoreCase = true)) {
                    (limitVal * 3600.0 * 1000.0).toLong()
                } else {
                    (limitVal * 60.0 * 1000.0).toLong()
                }
                
                val isExceeded = if (isCollectiveLimit) {
                    packageUsageCache.values.sum() >= limitMs
                } else {
                    (packageUsageCache[currentForegroundPackage] ?: 0L) >= limitMs
                }
                if (isExceeded) {
                    triggerBlackoutOverlay("USAGE LIMIT")
                    return
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Uncaught exception in accessibility event receiver, safely recovered.", e)
        }
    }

    private fun manageTrackingJob() {
        val usageActive = isCurrentlyRestrictedForUsage()
        val doomscrollActive = isCurrentlyRestrictedForDoomscroll()
        
        if (usageActive || doomscrollActive) {
            if (trackingJob == null || !trackingJob!!.isActive) {
                trackingJob = serviceScope.launch {
                    while (isActive) {
                        delay(1000L)
                        val currentUsageActive = isCurrentlyRestrictedForUsage()
                        val currentDoomscrollActive = isCurrentlyRestrictedForDoomscroll()
                        
                        if (currentUsageActive || currentDoomscrollActive) {
                            if (currentUsageActive) updateUsageStatistics()
                            if (currentDoomscrollActive) updateDoomscrollStatistics()
                        } else {
                            break
                        }
                    }
                }
            }
        } else {
            trackingJob?.cancel()
            trackingJob = null
        }
    }

    private suspend fun updateUsageStatistics() {
        val now = System.currentTimeMillis()
        // Do only in-memory work under the lock; capture what needs persisting and act after
        // releasing so a disk write never stalls the accessibility thread or another tick.
        var flushSerialized: String? = null
        var flushIntervalStart = 0L
        var limitBreached = false
        cacheLock.withLock {
            val isNewInterval = com.avow.app.util.VowValidator.isNewUsageInterval(now, lastIntervalStartMs, selectedInterval)
            if (isNewInterval) {
                packageUsageCache.clear()
                lastIntervalStartMs = now
                flushSerialized = com.avow.app.data.PackageUsageSerializer.serialize(packageUsageCache)
                packageUsageJsonStr = flushSerialized!!
                flushIntervalStart = now
                lastFlushTime = now
                ticksSinceLastFlush = 0
            } else {
                val activePkg = currentForegroundPackage
                if (activePkg.isNotEmpty()) {
                    val currentUsage = packageUsageCache[activePkg] ?: 0L
                    packageUsageCache[activePkg] = currentUsage + 1000L
                }
                ticksSinceLastFlush++

                val limitVal = allowedValue.toDoubleOrNull() ?: 0.0
                val limitMs = if (allowedUnit.equals("hours", ignoreCase = true) || allowedUnit.equals("hour", ignoreCase = true)) {
                    (limitVal * 3600.0 * 1000.0).toLong()
                } else {
                    (limitVal * 60.0 * 1000.0).toLong()
                }

                if (isCollectiveLimit) {
                    if (packageUsageCache.values.sum() >= limitMs) {
                        limitBreached = true
                    }
                } else {
                    for ((pkg, usage) in packageUsageCache) {
                        if (isRestrictedAppPackage(pkg) || isBrowserWithSpecificDomain(pkg)) {
                            if (usage >= limitMs) {
                                limitBreached = true
                                break
                            }
                        }
                    }
                }

                val appSwitched = activePkg != lastTrackedPackage
                val timeToFlush = ticksSinceLastFlush >= 30 || (now - lastFlushTime) >= 30000L

                if (limitBreached || appSwitched || timeToFlush) {
                    flushSerialized = com.avow.app.data.PackageUsageSerializer.serialize(packageUsageCache)
                    packageUsageJsonStr = flushSerialized!!
                    flushIntervalStart = lastIntervalStartMs
                    lastFlushTime = now
                    ticksSinceLastFlush = 0
                    lastTrackedPackage = activePkg
                }
            }
        }

        flushSerialized?.let { vowDataStore.savePackageUsage(it, flushIntervalStart) }
        if (limitBreached) {
            withContext(Dispatchers.Main) { triggerBlackoutOverlay("USAGE LIMIT") }
        }
    }

    private fun isBrowserWithSpecificDomain(pkgName: String): Boolean {
        if (!isEnforcementActive) return false
        return isTargetBrowserWithSpecificDomain(pkgName)
    }

    private fun isCurrentlyRestrictedForUsage(): Boolean {
        if (!isEnforcementActive) return false
        if (isRestrictedAppPackage(currentForegroundPackage)) return true
        if (isBrowserWithSpecificDomain(currentForegroundPackage)) return true
        return false
    }

    private fun isCurrentlyRestrictedForDoomscroll(): Boolean {
        if (!doomscrollShieldEnabled) return false
        val pkg = currentForegroundPackage
        if (pkg.isEmpty()) return false
        return isDoomscrollTargetApp(pkg)
    }

    private fun isBlockRestrictedAppPackage(block: VowBlock, pkgName: String): Boolean {
        if (!isEnforcementActive) return false
        if (pkgName in neverBlockablePackages) return false
        return block.targetApps.contains(pkgName)
    }

    private fun isRestrictedAppPackage(pkgName: String): Boolean {
        if (!isEnforcementActive) return false
        return isTargetAppPackage(pkgName)
    }

    // The three target-set checks below all funnel through the never-blockable guard: blocking the
    // launcher or dialer could brick the phone into an aVow-only device for the length of a vow.
    private fun isTargetAppPackage(pkgName: String): Boolean {
        if (pkgName in neverBlockablePackages) return false
        return targetAppSet.contains(pkgName)
    }

    private fun isDoomscrollTargetApp(pkgName: String): Boolean {
        if (pkgName in neverBlockablePackages) return false
        return doomscrollTargetApps.contains(pkgName)
    }

    /**
     * Returns a restricted target found in a Picture-in-Picture window whose restriction is currently
     * in force, or null. This closes the drag-to-PiP bypass: a target in PiP keeps playing while a
     * different app is foreground, so the foreground-based checks never see it.
     *
     * Only the binary window-based restrictions are covered here — an enabled scheduled block inside
     * its window, and the doomscroll shield inside its window — because those don't need per-second
     * usage metering (which PiP can't provide). We can detect and interrupt, but note there is no
     * public API to force another app out of PiP, so this re-asserts the block rather than dismissing
     * the pop-out. Requires flagRetrieveInteractiveWindows (accessibility_service_config.xml).
     *
     * NOTE: not fully device-verified — getWindows()/PiP reporting varies by OEM. Fail-soft (any
     * error yields null, i.e. no false block).
     */
    private fun restrictedPackageInPip(): String? {
        if (!isEnforcementActive && !doomscrollShieldEnabled) return null
        val wins = try { windows } catch (e: Throwable) { return null } ?: return null
        for (w in wins) {
            val inPip = try { w.isInPictureInPictureMode } catch (e: Throwable) { false }
            if (!inPip) continue
            val root = try { w.root } catch (e: Throwable) { null } ?: continue
            val pkg = try {
                root.packageName?.toString()
            } catch (e: Throwable) { null } finally {
                try { root.recycle() } catch (_: Throwable) {}
            }
            if (pkg.isNullOrEmpty()) continue

            // Scheduled block covering this app right now (needs a running vow / Active mode).
            if (isEnforcementActive) {
                for (block in vowBlocks) {
                    if (block.isEnabled &&
                        isBlockRestrictedAppPackage(block, pkg) &&
                        isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin)) {
                        return pkg
                    }
                }
            }
            // Doomscroll shield inside its active window (independent of any vow).
            if (doomscrollShieldEnabled && isDoomscrollTargetApp(pkg)) {
                val windowActive = doomscrollAllTime || isCurrentTimeInQuietHours(
                    doomscrollStartHour, doomscrollStartMin, doomscrollEndHour, doomscrollEndMin
                )
                if (windowActive) return pkg
            }
        }
        return null
    }

    private fun isTargetBrowserWithSpecificDomain(pkgName: String): Boolean {
        if (specificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
            val activeUrl = currentBrowserUrl
            if (activeUrl.isNotEmpty() && com.avow.app.util.DomainUtil.matches(activeUrl, specificDomain)) {
                return true
            }
        }
        return false
    }

    private suspend fun updateDoomscrollStatistics() {
        // Don't accumulate scroll time while the screen is off (phone in pocket with the
        // target app still foreground would otherwise walk straight into a lockout).
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && !pm.isInteractive) return

        val isRestrictionActive = doomscrollAllTime || isCurrentTimeInQuietHours(
            doomscrollStartHour,
            doomscrollStartMin,
            doomscrollEndHour,
            doomscrollEndMin
        )
        val allowanceMs = doomscrollAllowanceMinutes * 60L * 1000L

        val result = doomscrollTracker.tick(isRestrictionActive, allowanceMs)

        // Flush the running total at most every 10 ticks; warning/lockout flush immediately.
        doomscrollTicksSinceFlush++
        if (doomscrollTicksSinceFlush >= 10) {
            doomscrollTicksSinceFlush = 0
            val snapshot = doomscrollTracker.accumulatedMs
            serviceScope.launch { vowDataStore.saveDoomscrollAccumulatedMs(snapshot) }
        }

        when (result) {
            com.avow.app.util.DoomscrollTracker.TrackingResult.TriggerLockout -> {
                val cooldownMs = doomscrollCooldownMinutes * 60L * 1000L
                val endTime = SystemClock.elapsedRealtime() + cooldownMs
                // Enforce in-memory first: the app-launch intercept reads this field, and the
                // DataStore write may land seconds later.
                temporaryLockoutEndTime = endTime
                serviceScope.launch {
                    vowDataStore.saveTemporaryLockoutEndTime(endTime)
                    vowDataStore.saveDoomscrollAccumulatedMs(0L)
                }
                withContext(Dispatchers.Main) {
                    triggerLockoutOverlay()
                }
            }
            com.avow.app.util.DoomscrollTracker.TrackingResult.TriggerWarning -> {
                serviceScope.launch { vowDataStore.saveDoomscrollAccumulatedMs(doomscrollTracker.accumulatedMs) }
                withContext(Dispatchers.Main) {
                    showWarningNotification()
                }
            }
            else -> {}
        }
    }

    /**
     * High-importance, heads-up notification shown once when the softened tamper/key-loss lockout is
     * applied (D-3). Worded for the innocent case (an OS update can invalidate the security key) so a
     * blameless user understands the 24h vow and that it lifts on its own.
     */
    private fun showTamperLockoutNotification() {
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "tamper_lockout_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(com.avow.app.R.string.tamper_channel_name),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(com.avow.app.R.string.tamper_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 1003, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val text = getString(com.avow.app.R.string.tamper_text)
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.avow.app.R.string.tamper_title))
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        com.avow.app.util.NotificationStyle.applyBranding(builder, this)

        notificationManager.notify(2003, builder.build())
    }

    private fun showWarningNotification() {
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "doomscroll_warning_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(com.avow.app.R.string.doomscroll_warning_channel_name),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(com.avow.app.R.string.doomscroll_warning_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("PRELOAD_15M_VOW", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            1001,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.avow.app.R.string.doomscroll_warning_title))
            .setContentText(getString(com.avow.app.R.string.doomscroll_warning_text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        com.avow.app.util.NotificationStyle.applyBranding(builder, this)

        notificationManager.notify(2002, builder.build())
    }

    /**
     * True when the current Settings screen is about aVow itself — its app-info / force-stop /
     * clear-data page, the accessibility services list/detail, or the "allow full control" dialog.
     * Detected by our app label appearing in the Settings window; general Settings screens
     * (Wi-Fi, battery, display) never mention it and stay reachable.
     */
    private fun isSettingsScreenAboutSelf(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            // Deep scan: on some OEMs (e.g. Samsung) the app label on the App info / Storage page
            // is nested well below a shallow cap, which is why those screens weren't being caught.
            nodeTreeContainsText(root, selfLabel, maxDepth = 40, currentDepth = 0)
        } finally {
            root.recycle()
        }
    }

    private fun nodeTreeContainsText(
        node: AccessibilityNodeInfo,
        target: String,
        maxDepth: Int,
        currentDepth: Int
    ): Boolean {
        if (currentDepth > maxDepth || target.isEmpty()) return false
        val t = node.text?.toString() ?: ""
        val d = node.contentDescription?.toString() ?: ""
        if (t.contains(target, ignoreCase = true) || d.contains(target, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (nodeTreeContainsText(child, target, maxDepth, currentDepth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    /** Accessibility events arrive in bursts; avoid stacking startActivity calls. */
    private fun shouldDebounceOverlayLaunch(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastOverlayLaunchMs != 0L && now - lastOverlayLaunchMs < 1500L) return true
        lastOverlayLaunchMs = now
        return false
    }

    private fun triggerLockoutOverlay() {
        if (shouldDebounceOverlayLaunch()) return
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("IS_TEMPORARY_LOCKOUT", true)
        }
        startActivity(overlayIntent)
    }

    private fun handleForegroundPackageChange(oldPkg: String, newPkg: String) {
        val oldIsTarget = doomscrollShieldEnabled && isDoomscrollTargetApp(oldPkg)
        val newIsTarget = doomscrollShieldEnabled && isDoomscrollTargetApp(newPkg)
        
        val result = doomscrollTracker.handleForegroundChange(
            oldIsTarget = oldIsTarget,
            newIsTarget = newIsTarget,
            now = System.currentTimeMillis(),
            lastClosedTime = doomscrollLastClosedTime
        )
        if (result.saveClosedTime) {
            val closedTime = System.currentTimeMillis()
            doomscrollLastClosedTime = closedTime
            serviceScope.launch {
                vowDataStore.saveDoomscrollLastClosedTime(closedTime)
                // We don't save accumulatedMs if it's currently 0 to prevent overwriting max value
                if (result.newAccumulatedMs > 0L) {
                    vowDataStore.saveDoomscrollAccumulatedMs(result.newAccumulatedMs)
                }
            }
        } else if (result.resetAccumulated) {
            // Only reset accumulated if temporary lockout is not active
            if (SystemClock.elapsedRealtime() >= temporaryLockoutEndTime) {
                serviceScope.launch {
                    vowDataStore.saveDoomscrollAccumulatedMs(0L)
                }
            }
        }
    }

    private fun isCurrentTimeInQuietHours(startHour: Int, startMin: Int, endHour: Int, endMin: Int): Boolean {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMin = now.get(Calendar.MINUTE)
        val currentTimeInMins = currentHour * 60 + currentMin
        val startInMins = startHour * 60 + startMin
        val endInMins = endHour * 60 + endMin
        
        return if (startInMins <= endInMins) {
            currentTimeInMins in startInMins..endInMins
        } else {
            // Crosses midnight, e.g. 22:00 to 07:00
            currentTimeInMins >= startInMins || currentTimeInMins <= endInMins
        }
    }

    /**
     * Optimized extraction targeting specific URL address views directly by resource ID.
     * Prevents lag and visual leaks.
     */
    private fun extractUrlOptimized(rootNode: AccessibilityNodeInfo, packageName: String): String {
        // Target Chrome directly by ID
        if (packageName == "com.android.chrome") {
            val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            if (!urlBarNodes.isNullOrEmpty()) {
                try {
                    val text = urlBarNodes[0].text?.toString() ?: ""
                    if (text.isNotEmpty()) return text
                } finally {
                    urlBarNodes.forEach { it.recycle() }
                }
            }
        }
        
        // Target Samsung Internet directly by ID
        if (packageName == "com.sec.android.app.sbrowser") {
            val sbrowserIds = listOf(
                "com.sec.android.app.sbrowser:id/url_bar",
                "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            )
            for (id in sbrowserIds) {
                val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!urlBarNodes.isNullOrEmpty()) {
                    try {
                        val text = urlBarNodes[0].text?.toString() ?: ""
                        if (text.isNotEmpty()) return text
                    } finally {
                        urlBarNodes.forEach { it.recycle() }
                    }
                }
            }
        }

        // Fast recursive fallback if target ID is not found, but limit search depth
        return extractUrlRecursive(rootNode, maxDepth = 15, currentDepth = 0)
    }

    private fun extractUrlRecursive(node: AccessibilityNodeInfo, maxDepth: Int, currentDepth: Int): String {
        if (currentDepth > maxDepth) return ""
        
        if (node.className == "android.widget.EditText" || 
            node.viewIdResourceName?.endsWith("url_bar") == true ||
            node.viewIdResourceName?.endsWith("location_bar_edit_text") == true) {
            val textContext = node.text?.toString()
            if (!textContext.isNullOrBlank()) return textContext
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = extractUrlRecursive(child, maxDepth, currentDepth + 1)
                if (result.isNotEmpty()) return result
            } finally {
                child.recycle()
            }
        }
        return ""
    }

    private fun isIncognitoModeActive(node: AccessibilityNodeInfo, packageName: String): Boolean {
        return hasIncognitoIndicator(node, packageName, maxDepth = 8, currentDepth = 0)
    }

    /**
     * Detects incognito/secret mode by the toolbar badge's accessibility *content description* only
     * — never arbitrary page text. Matching visible text meant googling "incognito" or opening an
     * article about it blacked out the screen. The badge is UI chrome and lives shallow, so we also
     * cap the recursion depth.
     */
    private fun hasIncognitoIndicator(
        node: AccessibilityNodeInfo,
        packageName: String,
        maxDepth: Int,
        currentDepth: Int
    ): Boolean {
        if (currentDepth > maxDepth) return false
        val desc = node.contentDescription?.toString() ?: ""
        if (packageName == "com.android.chrome") {
            if (desc.contains("Incognito", ignoreCase = true)) return true
        } else if (packageName == "com.sec.android.app.sbrowser") {
            if (desc.contains("Secret mode", ignoreCase = true) || desc.contains("SecretMode", ignoreCase = true)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasIncognitoIndicator(child, packageName, maxDepth, currentDepth + 1)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun triggerBlackoutOverlay(reason: String = "RESTRICTED") {
        if (shouldDebounceOverlayLaunch()) return
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("TRIGGER_INTRUSION", true)
            putExtra("BLOCK_REASON", reason)
        }
        startActivity(overlayIntent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister userPresentReceiver", e)
        }
        serviceJob.cancel()
    }
}
