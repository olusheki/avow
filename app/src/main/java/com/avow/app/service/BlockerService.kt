package com.avow.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.avow.app.MainActivity
import com.avow.app.data.VowDataStore
import kotlinx.coroutines.*
import java.util.Calendar

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
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var trackingJob: Job? = null

    // Lightweight in-memory cache of restriction variables
    @Volatile private var isVowActive = false
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
    @Volatile private var accumulatedUsageMs = 0L
    @Volatile private var lastIntervalStartMs = 0L

    @Volatile private var currentForegroundPackage = ""
    private lateinit var vowDataStore: VowDataStore

    override fun onCreate() {
        super.onCreate()
        vowDataStore = VowDataStore(this)
        
        // Collect DataStore flow asynchronously to maintain in-memory cache
        serviceScope.launch {
            vowDataStore.preferencesFlow.collect { prefs ->
                isVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
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
                accumulatedUsageMs = prefs[VowDataStore.ACCUMULATED_USAGE_MS] ?: 0L
                lastIntervalStartMs = prefs[VowDataStore.LAST_INTERVAL_START_MS] ?: 0L
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (!isVowActive) return

            val pkgName = event.packageName?.toString() ?: return
            
            // Avoid intercepting our own app
            if (pkgName == packageName) return

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentForegroundPackage = pkgName
            }

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
                }
            }

            // 3. Quiet Hours block check
            if (quietHoursEnabled && isCurrentTimeInQuietHours(quietStartHour, quietStartMin, quietEndHour, quietEndMin)) {
                if (isQuietHoursRestrictedAppPackage(pkgName)) {
                    triggerBlackoutOverlay()
                    return
                }
                if (quietHoursSpecificDomain.isNotEmpty() && (pkgName == "com.android.chrome" || pkgName == "com.sec.android.app.sbrowser")) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        val activeUrl = extractUrlOptimized(rootNode, pkgName)
                        if (activeUrl.isNotEmpty() && activeUrl.contains(quietHoursSpecificDomain, ignoreCase = true)) {
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
                if (accumulatedUsageMs >= limitMs) {
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
        val isNewInterval = com.avow.app.util.VowValidator.isNewUsageInterval(now, lastIntervalStartMs, selectedInterval)

        val newAccumulated = if (isNewInterval) 1000L else accumulatedUsageMs + 1000L
        val newIntervalStart = if (isNewInterval) now else lastIntervalStartMs

        accumulatedUsageMs = newAccumulated
        lastIntervalStartMs = newIntervalStart

        // Save progress to DataStore for persistence
        vowDataStore.saveAccumulatedUsage(newAccumulated, newIntervalStart)

        // If limit is exceeded, trigger the overlay
        val limitVal = allowedValue.toDoubleOrNull() ?: 0.0
        val limitMs = if (allowedUnit.equals("hours", ignoreCase = true) || allowedUnit.equals("hour", ignoreCase = true)) {
            (limitVal * 3600.0 * 1000.0).toLong()
        } else {
            (limitVal * 60.0 * 1000.0).toLong()
        }

        if (newAccumulated >= limitMs) {
            withContext(Dispatchers.Main) {
                triggerBlackoutOverlay()
            }
        }
    }

    private fun isCurrentlyRestrictedForUsage(): Boolean {
        if (!isVowActive) return false
        if (isRestrictedAppPackage(currentForegroundPackage)) return true
        if (specificDomain.isNotEmpty() && (currentForegroundPackage == "com.android.chrome" || currentForegroundPackage == "com.sec.android.app.sbrowser")) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val activeUrl = extractUrlOptimized(rootNode, currentForegroundPackage)
                if (activeUrl.isNotEmpty() && activeUrl.contains(specificDomain, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isQuietHoursRestrictedAppPackage(pkgName: String): Boolean {
        if (!isVowActive) return false
        if (quietHoursTargetAppSet.contains("All Social Media")) {
            if (SOCIAL_MEDIA_PACKAGES.contains(pkgName)) return true
        }
        return quietHoursTargetAppSet.contains(pkgName)
    }

    private fun isRestrictedAppPackage(pkgName: String): Boolean {
        if (!isVowActive) return false
        if (targetAppSet.contains("All Social Media")) {
            if (SOCIAL_MEDIA_PACKAGES.contains(pkgName)) return true
        }
        return targetAppSet.contains(pkgName)
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
                val text = urlBarNodes[0].text?.toString() ?: ""
                urlBarNodes.forEach { it.recycle() }
                if (text.isNotEmpty()) return text
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
                    val text = urlBarNodes[0].text?.toString() ?: ""
                    urlBarNodes.forEach { it.recycle() }
                    if (text.isNotEmpty()) return text
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
            val result = extractUrlRecursive(child, maxDepth, currentDepth + 1)
            child.recycle()
            if (result.isNotEmpty()) return result
        }
        return ""
    }

    private fun triggerBlackoutOverlay() {
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
