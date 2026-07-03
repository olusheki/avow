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
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
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

    private val doomscrollTracker = com.avow.app.util.DoomscrollTracker()
    private var allowedTimeTrackingJob: Job? = null
 
    override fun onCreate() {
        super.onCreate()
        vowDataStore = VowDataStore(this)
        val presenceFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(userPresentReceiver, presenceFilter)

        // Persist the strict-lockout fallback if the signed state was tampered with (the read flow
        // only corrects it in memory otherwise). The service is the boot-time enforcement anchor.
        serviceScope.launch { vowDataStore.repairTamperedStateIfNeeded() }

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
                // Seed the tracker from disk exactly once (service restart). Re-applying every
                // emission raced with the async per-tick saves and snapped the live counter back
                // to stale values — the root cause of the unreliable lockout.
                if (!doomscrollTrackerSeeded) {
                    doomscrollTracker.accumulatedMs = doomscrollAccumulatedMs
                    doomscrollTrackerSeeded = true
                }
                if (!doomscrollShieldEnabled) {
                    doomscrollTracker.accumulatedMs = 0L
                    doomscrollTracker.warningSent = false
                }
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
            
            // Avoid intercepting our own app
            if (pkgName == packageName) return

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
                // Settings stays blocked during an active-mode vow (prevents disabling the
                // accessibility service mid-vow) and during the deactivation cooling-off window.
                if (isCoolingOff || (isVowActive && isActiveVowMode)) {
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

            if (!isVowActive) return

            // 1. Check profile stubs (Samsung Secure Folder & Android 15 Private Space)
            if ((secureFolderEnabled && pkgName == "com.samsung.knox.securefolder") ||
                (privateSpaceEnabled && pkgName == "com.google.android.apps.privatespace")) {
                triggerBlackoutOverlay()
                return
            }

            // 2. Browser URL and Incognito checks (Chrome / Samsung Internet)
            if (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser") {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        // Check for Incognito/Secret mode window
                        if (isIncognitoModeActive(rootNode, pkgName)) {
                            triggerBlackoutOverlay()
                            return
                        }

                        val activeUrl = extractUrlOptimized(rootNode, pkgName)
                        currentBrowserUrl = activeUrl
                        if (activeUrl.isNotEmpty()) {
                            val isBanned = banDomainSet.any { domain ->
                                com.avow.app.util.DomainUtil.matchesHost(activeUrl, domain)
                            }
                            if (isBanned) {
                                triggerBlackoutOverlay()
                                return
                            }
                        }
                    } finally {
                        rootNode.recycle()
                    }
                }
            }

            // 2.5. Active Vow Mode continuous lockout check (blocks restricted apps and specific domains 24/7)
            if (isActiveVowMode && isVowActive) {
                if (isRestrictedAppPackage(pkgName) || isBrowserWithSpecificDomain(pkgName)) {
                    triggerBlackoutOverlay()
                    return
                }
            }

            // 3. Quiet Hours block check
            for (block in vowBlocks) {
                if (block.isEnabled && isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin)) {
                    if (isBlockRestrictedAppPackage(block, pkgName)) {
                        triggerBlackoutOverlay()
                        return
                    }
                    if (block.specificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
                        val activeUrl = currentBrowserUrl
                        if (activeUrl.isNotEmpty() && com.avow.app.util.DomainUtil.matches(activeUrl, block.specificDomain)) {
                            triggerBlackoutOverlay()
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
                    triggerBlackoutOverlay()
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
            withContext(Dispatchers.Main) { triggerBlackoutOverlay() }
        }
    }

    private fun isBrowserWithSpecificDomain(pkgName: String): Boolean {
        if (!isVowActive) return false
        return isTargetBrowserWithSpecificDomain(pkgName)
    }

    private fun isCurrentlyRestrictedForUsage(): Boolean {
        if (!isVowActive) return false
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
        if (!isVowActive) return false
        return block.targetApps.contains(pkgName)
    }

    private fun isRestrictedAppPackage(pkgName: String): Boolean {
        if (!isVowActive) return false
        return isTargetAppPackage(pkgName)
    }

    private fun isTargetAppPackage(pkgName: String): Boolean {
        return targetAppSet.contains(pkgName)
    }

    private fun isDoomscrollTargetApp(pkgName: String): Boolean {
        return doomscrollTargetApps.contains(pkgName)
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

    private fun showWarningNotification() {
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "doomscroll_warning_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Doomscroll Warning",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when the time limit in a target app is nearly reached"
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
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Doomscroll Warning")
            .setContentText("You've spent a while in here. Tap to bind a 15-minute vow.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(2002, builder.build())
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

    private fun isCurrentTimeBetween11PMAnd5AM(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
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

    private fun triggerBlackoutOverlay() {
        if (shouldDebounceOverlayLaunch()) return
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("TRIGGER_INTRUSION", true)
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
