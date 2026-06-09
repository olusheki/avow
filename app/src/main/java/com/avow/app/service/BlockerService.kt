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
import kotlinx.coroutines.sync.withLock
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
        
        private val SOCIAL_MEDIA_PACKAGES = setOf(
            "com.instagram.android",
            "com.tiktok.android",
            "com.twitter.android",
            "com.twitter.android.lite",
            "com.facebook.katana",
            "com.facebook.lite"
        )

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.google.android.settings"
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
    @Volatile private var quietHoursTargetAppSet = setOf("All Social Media")
    @Volatile private var quietHoursSpecificDomain = ""
    @Volatile private var usageLimitsUpdated = false
    @Volatile private var allowedValue = "5"
    @Volatile private var allowedUnit = "min"
    @Volatile private var selectedInterval = "hour"
    @Volatile private var targetAppSet = emptySet<String>()
    @Volatile private var specificDomain = ""
    @Volatile private var lastIntervalStartMs = 0L

    @Volatile private var isCollectiveLimit = false
    @Volatile private var packageUsageJsonStr = ""
    private val packageUsageCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val cacheMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile private var lastTrackedPackage = ""
    @Volatile private var lastFlushTime = 0L
    @Volatile private var ticksSinceLastFlush = 0

    @Volatile private var currentForegroundPackage = ""
    @Volatile private var vowBlocks = emptyList<VowBlock>()
    private lateinit var vowDataStore: VowDataStore

    @Volatile private var doomscrollLastClosedTime = 0L
    @Volatile private var doomscrollAccumulatedMs = 0L
    @Volatile private var temporaryLockoutEndTime = 0L

    private val doomscrollTracker = com.avow.app.util.DoomscrollTracker()
    private var allowedTimeTrackingJob: Job? = null
 
    override fun onCreate() {
        super.onCreate()
        vowDataStore = VowDataStore(this)
        
        // Collect DataStore flow asynchronously to maintain in-memory cache
        serviceScope.launch {
            var previousIsVowActive = false
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
                quietHoursTargetAppSet = prefs[VowDataStore.QUIET_HOURS_TARGET_APP_SET] ?: setOf("All Social Media")
                quietHoursSpecificDomain = prefs[VowDataStore.QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
                usageLimitsUpdated = prefs[VowDataStore.USAGE_LIMITS_UPDATED] ?: false
                allowedValue = prefs[VowDataStore.ALLOWED_VALUE] ?: "5"
                allowedUnit = prefs[VowDataStore.ALLOWED_UNIT] ?: "min"
                selectedInterval = prefs[VowDataStore.SELECTED_INTERVAL] ?: "hour"
                targetAppSet = prefs[VowDataStore.TARGET_APP_SET] ?: emptySet()
                specificDomain = prefs[VowDataStore.SPECIFIC_DOMAIN] ?: ""
                lastIntervalStartMs = prefs[VowDataStore.LAST_INTERVAL_START_MS] ?: 0L
                isCollectiveLimit = prefs[VowDataStore.IS_COLLECTIVE_LIMIT] ?: false
                doomscrollLastClosedTime = prefs[VowDataStore.DOOMSCROLL_LAST_CLOSED_TIME] ?: 0L
                doomscrollAccumulatedMs = prefs[VowDataStore.DOOMSCROLL_ACCUMULATED_MS] ?: 0L
                temporaryLockoutEndTime = prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
                doomscrollTracker.accumulatedMs = doomscrollAccumulatedMs
                val blocksJson = prefs[VowDataStore.VOW_BLOCKS_JSON] ?: ""
                vowBlocks = VowBlock.deserializeList(blocksJson)
                
                // Track Vow completion (transition from true to false)
                if (previousIsVowActive && !activeNow) {
                    val startTime = prefs[VowDataStore.VOW_START_TIME_MS] ?: 0L
                    val initialDurationSeconds = prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS] ?: 0L
                    val intrusions = prefs[VowDataStore.VOW_INTRUSIONS_COUNT] ?: 0
                    val allowedScreenTime = prefs[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS] ?: 0L
                    val endTime = System.currentTimeMillis()
                    
                    val durationSecs = if (initialDurationSeconds > 0) initialDurationSeconds else ((endTime - startTime) / 1000)
                    val zen = com.avow.app.util.VowValidator.calculateZenScore(
                        intrusions = intrusions,
                        allowedScreenTimeMs = allowedScreenTime,
                        durationSeconds = durationSecs
                    )
                    
                    val session = VowSession(
                        startTimeMillis = startTime,
                        endTimeMillis = endTime,
                        durationSeconds = durationSecs,
                        intrusionsBlocked = intrusions,
                        allowedScreenTimeMs = allowedScreenTime,
                        zenScore = zen
                    )
                    
                    launch(Dispatchers.IO) {
                        VowDatabase.getDatabase(this@BlockerService).vowSessionDao().insert(session)
                    }
                }
                
                previousIsVowActive = activeNow
                manageAllowedTimeTracking()

                val usageJson = prefs[VowDataStore.PACKAGE_USAGE_JSON] ?: ""
                if (usageJson != packageUsageJsonStr) {
                    packageUsageJsonStr = usageJson
                    cacheMutex.withLock {
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
            }
        }
    }

    private fun manageAllowedTimeTracking() {
        if (isVowActive) {
            if (allowedTimeTrackingJob == null || !allowedTimeTrackingJob!!.isActive) {
                allowedTimeTrackingJob = serviceScope.launch {
                    val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    while (isVowActive) {
                        delay(1000L)
                        val fg = currentForegroundPackage
                        if (fg.isNotEmpty() && isPermittedAppInForeground(fg)) {
                            if (pm == null || pm.isInteractive) {
                                vowDataStore.addAllowedScreenTimeMs(1000L)
                            }
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
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                try {
                    val activeUrl = extractUrlOptimized(rootNode, packageName)
                    if (activeUrl.isNotEmpty()) {
                        val isBanned = banDomainSet.any { domain ->
                            activeUrl.contains(domain, ignoreCase = true)
                        }
                        if (isBanned) return false
                        
                        if (specificDomain.isNotEmpty() && activeUrl.contains(specificDomain, ignoreCase = true)) {
                            return false
                        }
                        
                        for (block in vowBlocks) {
                            if (block.isEnabled && block.specificDomain.isNotEmpty() && 
                                isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin) && 
                                activeUrl.contains(block.specificDomain, ignoreCase = true)) {
                                return false
                            }
                        }
                    }
                } finally {
                    rootNode.recycle()
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
            runBlocking {
                cacheMutex.withLock {
                    if (com.avow.app.util.VowValidator.isNewUsageInterval(nowMs, lastIntervalStartMs, selectedInterval)) {
                        packageUsageCache.clear()
                        lastIntervalStartMs = nowMs
                        intervalReset = true
                    }
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
            if (System.currentTimeMillis() < temporaryLockoutEndTime) {
                if (isTargetAppPackage(pkgName) || isBrowserWithSpecificDomain(pkgName)) {
                    triggerLockoutOverlay()
                    return
                }
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                handleScrollEvent(pkgName)
            }

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val oldForeground = currentForegroundPackage
                currentForegroundPackage = pkgName
                handleForegroundPackageChange(oldForeground, pkgName)
            }

            // Settings app interception: redirect to HOME if vow is active or deactivation request cooling-off is active
            if (SETTINGS_PACKAGES.contains(pkgName)) {
                val isCoolingOff = deactivationRequestTime > 0L &&
                        (System.currentTimeMillis() - deactivationRequestTime) < 24L * 3600L * 1000L
                if (isVowActive || isCoolingOff) {
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    return
                }
            }

            if (!isVowActive && !isActiveVowMode) return

            // Re-evaluate tracking job on window changes or URL content changes inside browsers
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && 
                 (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser"))) {
                manageTrackingJob()
            }

            // 1. Check profile stubs (Samsung Secure Folder & Android 15 Private Space)
            if ((secureFolderEnabled && pkgName == "com.samsung.knox.securefolder") ||
                (privateSpaceEnabled && pkgName == "com.google.android.apps.privatespace")) {
                triggerBlackoutOverlay()
                return
            }

            // 2. Browser URL checks (Chrome / Samsung Internet)
            if (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser") {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        val activeUrl = extractUrlOptimized(rootNode, pkgName)
                        if (activeUrl.isNotEmpty()) {
                            val isBanned = banDomainSet.any { domain ->
                                activeUrl.contains(domain, ignoreCase = true)
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

            // 3. Quiet Hours block check
            for (block in vowBlocks) {
                if (block.isEnabled && isCurrentTimeInQuietHours(block.startHour, block.startMin, block.endHour, block.endMin)) {
                    if (isBlockRestrictedAppPackage(block, pkgName)) {
                        triggerBlackoutOverlay()
                        return
                    }
                    if (block.specificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            try {
                                val activeUrl = extractUrlOptimized(rootNode, pkgName)
                                if (activeUrl.isNotEmpty() && activeUrl.contains(block.specificDomain, ignoreCase = true)) {
                                    triggerBlackoutOverlay()
                                    return
                                }
                            } finally {
                                rootNode.recycle()
                            }
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
        if (isCurrentlyRestrictedForUsage()) {
            if (trackingJob == null || !trackingJob!!.isActive) {
                trackingJob = serviceScope.launch {
                    while (isActive) {
                        delay(1000L)
                        if (isCurrentlyRestrictedForUsage()) {
                            updateUsageStatistics()
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
        cacheMutex.withLock {
            val isNewInterval = com.avow.app.util.VowValidator.isNewUsageInterval(now, lastIntervalStartMs, selectedInterval)
            if (isNewInterval) {
                packageUsageCache.clear()
                lastIntervalStartMs = now
                val serialized = com.avow.app.data.PackageUsageSerializer.serialize(packageUsageCache)
                packageUsageJsonStr = serialized
                vowDataStore.savePackageUsage(serialized, now)
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

                var limitBreached = false
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
                    val serialized = com.avow.app.data.PackageUsageSerializer.serialize(packageUsageCache)
                    packageUsageJsonStr = serialized
                    vowDataStore.savePackageUsage(serialized, lastIntervalStartMs)
                    lastFlushTime = now
                    ticksSinceLastFlush = 0
                    lastTrackedPackage = activePkg
                }

                if (limitBreached) {
                    withContext(Dispatchers.Main) {
                        triggerBlackoutOverlay()
                    }
                }
            }
        }
    }

    private fun isBrowserWithSpecificDomain(pkgName: String): Boolean {
        if (!isVowActive && !isActiveVowMode) return false
        return isTargetBrowserWithSpecificDomain(pkgName)
    }

    private fun isCurrentlyRestrictedForUsage(): Boolean {
        if (!isVowActive && !isActiveVowMode) return false
        if (isRestrictedAppPackage(currentForegroundPackage)) return true
        if (isBrowserWithSpecificDomain(currentForegroundPackage)) return true
        return false
    }

    private fun isQuietHoursRestrictedAppPackage(pkgName: String): Boolean {
        if (!isVowActive && !isActiveVowMode) return false
        if (quietHoursTargetAppSet.contains("All Social Media")) {
            if (SOCIAL_MEDIA_PACKAGES.contains(pkgName)) return true
        }
        return quietHoursTargetAppSet.contains(pkgName)
    }

    private fun isBlockRestrictedAppPackage(block: VowBlock, pkgName: String): Boolean {
        if (!isVowActive && !isActiveVowMode) return false
        if (block.targetApps.contains("All Social Media")) {
            if (SOCIAL_MEDIA_PACKAGES.contains(pkgName)) return true
        }
        return block.targetApps.contains(pkgName)
    }

    private fun isRestrictedAppPackage(pkgName: String): Boolean {
        if (!isVowActive && !isActiveVowMode) return false
        return isTargetAppPackage(pkgName)
    }

    private fun isTargetAppPackage(pkgName: String): Boolean {
        if (targetAppSet.contains("All Social Media")) {
            if (SOCIAL_MEDIA_PACKAGES.contains(pkgName)) return true
        }
        return targetAppSet.contains(pkgName)
    }

    private fun isTargetBrowserWithSpecificDomain(pkgName: String): Boolean {
        if (specificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                try {
                    val activeUrl = extractUrlOptimized(rootNode, pkgName)
                    if (activeUrl.isNotEmpty() && activeUrl.contains(specificDomain, ignoreCase = true)) {
                        return true
                    }
                } finally {
                    rootNode.recycle()
                }
            }
        }
        return false
    }

    private fun handleScrollEvent(pkgName: String) {
        val isNightTime = isCurrentTimeBetween11PMAnd5AM()
        when (doomscrollTracker.handleScroll(System.currentTimeMillis(), isNightTime)) {
            com.avow.app.util.DoomscrollTracker.TrackingResult.TriggerLockout -> {
                serviceScope.launch {
                    vowDataStore.saveTemporaryLockoutEndTime(System.currentTimeMillis() + 60L * 60L * 1000L)
                    vowDataStore.saveDoomscrollAccumulatedMs(0L)
                }
                triggerLockoutOverlay()
            }
            com.avow.app.util.DoomscrollTracker.TrackingResult.TriggerWarning -> {
                showWarningNotification()
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
                description = "Notifies when continuous scrolling limit is reached"
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
            .setContentText("You have been scrolling for too long. Tap to bind a 15-minute vow.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(2002, builder.build())
    }

    private fun triggerLockoutOverlay() {
        serviceScope.launch {
            vowDataStore.incrementIntrusionsCount()
        }
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("IS_TEMPORARY_LOCKOUT", true)
        }
        startActivity(overlayIntent)
    }

    private fun handleForegroundPackageChange(oldPkg: String, newPkg: String) {
        val result = doomscrollTracker.handleForegroundChange(
            oldPkg = oldPkg,
            newPkg = newPkg,
            isTargetPkg = { isTargetAppPackage(it) },
            now = System.currentTimeMillis(),
            lastClosedTime = doomscrollLastClosedTime
        )
        if (result.saveClosedTime) {
            serviceScope.launch {
                vowDataStore.saveDoomscrollLastClosedTime(System.currentTimeMillis())
                vowDataStore.saveDoomscrollAccumulatedMs(result.newAccumulatedMs)
            }
        } else if (result.resetAccumulated) {
            serviceScope.launch {
                vowDataStore.saveDoomscrollAccumulatedMs(0L)
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

    private fun triggerBlackoutOverlay() {
        serviceScope.launch {
            vowDataStore.incrementIntrusionsCount()
        }
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("TRIGGER_INTRUSION", true)
        }
        startActivity(overlayIntent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
