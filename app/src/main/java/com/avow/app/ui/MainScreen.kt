package com.avow.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.avow.app.receiver.DeviceAdmin
import com.avow.app.ui.theme.*
import kotlinx.coroutines.delay
import android.os.SystemClock
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.util.Log
import com.avow.app.service.BlockerService
import com.avow.app.data.VowDataStore
import com.avow.app.model.VowBlock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Screen states representing the different visual layout configurations.
 */
enum class ScreenState {
    UNLOCKED_VAULT,       // Dashboard with "UNLOCKED" state
    LOCKED_VAULT,         // Dashboard with "LOCKED" countdown active
    CONFIGURATION,        // Scrollable packages/domain setup workspace
    INTRUSION_INTERCEPT,  // Flat graphite gray background with centered smiley
    TEMPORARY_LOCKOUT,    // Inescapable straight face lockout overlay
    FOCUS_HISTORY         // Focus session logging and analytics
}

// Slightly darker gray color for activated panels
val DarkerSurfaceColor = Color(0xFF5A5A5A)

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    triggerIntrusion: Boolean = false,
    onIntrusionHandled: () -> Unit = {},
    preload15mVow: Boolean = false,
    onPreloadHandled: () -> Unit = {},
    isTemporaryLockout: Boolean = false,
    onTemporaryLockoutHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val vowDataStore = remember { VowDataStore(context) }
    var currentState by remember { mutableStateOf(ScreenState.UNLOCKED_VAULT) }
    var previousState by remember { mutableStateOf(ScreenState.UNLOCKED_VAULT) }
    
    // Vow Lock Active State (Source of truth for timer countdown)
    var isVowActive by remember { mutableStateOf(false) }
    var isActiveVowMode by remember { mutableStateOf(false) }
    var deactivationRequestTime by remember { mutableStateOf(0L) }
    
    // Ticker to trigger recompositions for the cooling-off button countdown
    var tickTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(deactivationRequestTime) {
        if (deactivationRequestTime > 0L) {
            while (true) {
                delay(1000L)
                tickTrigger++
            }
        }
    }
    
    // In-app Toast Banner State
    var inAppToastMessage by remember { mutableStateOf<String?>(null) }
    val showToast: (String) -> Unit = { inAppToastMessage = it }
    
    // Auto-dismiss toast
    LaunchedEffect(inAppToastMessage) {
        if (inAppToastMessage != null) {
            delay(3000L)
            inAppToastMessage = null
        }
    }
    
    // Query downloaded applications using PackageManager
    val installedApps = remember(context) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val list = resolveInfos.map {
            val packageName = it.activityInfo.packageName
            val label = it.loadLabel(pm).toString()
            packageName to label
        }.distinctBy { it.first }.sortedBy { it.second }
        
        if (list.isEmpty()) {
            listOf(
                "com.instagram.android" to "Instagram",
                "com.sec.android.app.sbrowser" to "Samsung Internet",
                "com.android.chrome" to "Chrome",
                "com.facebook.katana" to "Facebook",
                "com.twitter.android" to "X / Twitter",
                "com.tiktok.android" to "TikTok",
                "com.youtube.android" to "YouTube"
            )
        } else {
            list
        }
    }

    // Usage Limit Config States
    var showUsageLimitsDialog by remember { mutableStateOf(false) }
    var usageLimitsUpdated by remember { mutableStateOf(false) }
    
    var allowedValue by remember { mutableStateOf("5") }
    var allowedUnit by remember { mutableStateOf("min") }
    var selectedInterval by remember { mutableStateOf("hour") }
    var targetAppSet by remember { mutableStateOf(emptySet<String>()) }
    var specificDomain by remember { mutableStateOf("") }
    var isCollectiveLimit by remember { mutableStateOf(false) }
    
    // Frozen states for stricter-only validation when locked
    var frozenAllowedValue by remember { mutableStateOf("5") }
    var frozenAllowedUnit by remember { mutableStateOf("min") }
    var frozenInterval by remember { mutableStateOf("hour") }
    
    // Quiet Hours Dialog State
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    var quietHoursEnabled by remember { mutableStateOf(false) }
    var quietStartHour by remember { mutableStateOf(22) }
    var quietStartMin by remember { mutableStateOf(0) }
    var quietEndHour by remember { mutableStateOf(7) }
    var quietEndMin by remember { mutableStateOf(0) }
    var quietHoursTargetAppSet by remember { mutableStateOf(emptySet<String>()) }
    var quietHoursSpecificDomain by remember { mutableStateOf("") }
    
    // Frozen Quiet Hours
    var frozenQuietHoursEnabled by remember { mutableStateOf(false) }
    var frozenQuietStartHour by remember { mutableStateOf(22) }
    var frozenQuietStartMin by remember { mutableStateOf(0) }
    var frozenQuietEndHour by remember { mutableStateOf(7) }
    var frozenQuietEndMin by remember { mutableStateOf(0) }
    var frozenQuietHoursTargetAppSet by remember { mutableStateOf(emptySet<String>()) }
    var frozenQuietHoursSpecificDomain by remember { mutableStateOf("") }
    
    // Custom Scheduled Blocks
    var vowBlocks by remember { mutableStateOf(listOf<VowBlock>()) }
    var frozenVowBlocks by remember { mutableStateOf(listOf<VowBlock>()) }
    
    // Binding Vow Dialog State
    var showBindingVowDialog by remember { mutableStateOf(false) }
    var initialDaysForDialog by remember { mutableStateOf("00") }
    var initialHoursForDialog by remember { mutableStateOf("00") }
    var initialMinutesForDialog by remember { mutableStateOf("00") }
    var initialSecondsForDialog by remember { mutableStateOf("00") }

    LaunchedEffect(preload15mVow) {
        if (preload15mVow) {
            initialDaysForDialog = "00"
            initialHoursForDialog = "00"
            initialMinutesForDialog = "15"
            initialSecondsForDialog = "00"
            showBindingVowDialog = true
            onPreloadHandled()
        }
    }

    LaunchedEffect(isTemporaryLockout) {
        if (isTemporaryLockout) {
            previousState = currentState
            currentState = ScreenState.TEMPORARY_LOCKOUT
            onTemporaryLockoutHandled()
        }
    }

    // Auto-exit temporary lockout when time has elapsed
    LaunchedEffect(currentState) {
        if (currentState == ScreenState.TEMPORARY_LOCKOUT) {
            while (true) {
                delay(1000L)
                val prefs = vowDataStore.preferencesFlow.first()
                val endTime = prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
                if (System.currentTimeMillis() >= endTime) {
                    currentState = if (isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT
                    break
                }
            }
        }
    }
    
    // Target Profiles / Apps (Samsung Secure Folder, Android 15 Private Space)
    var secureFolderEnabled by remember { mutableStateOf(false) } // Default unselected
    var privateSpaceEnabled by remember { mutableStateOf(false) }  // Default unselected

    // Enforcement Restrictions States (Settings Workspace)
    var lockUninstall by remember { mutableStateOf(false) } // Default unselected
    var disallowDataWipe by remember { mutableStateOf(false) } // Default unselected
    var disableSafeBoot by remember { mutableStateOf(false) } // Default unselected
    var blockPlayStore by remember { mutableStateOf(false) } // Default unselected
    var dynamicReinstall by remember { mutableStateOf(false) } // Default unselected
    var deactivateUsbDebugging by remember { mutableStateOf(false) } // Default unselected

    var banDomainSet by remember { mutableStateOf(setOf("instagram.com")) }

    // Countdown Clock State
    var days by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }

    var vowStartTimeMs by remember { mutableStateOf(0L) }
    var vowInitialDurationSeconds by remember { mutableStateOf(0L) }

    val scope = rememberCoroutineScope()
    var isLoaded by remember { mutableStateOf(false) }

    // Handle incoming intrusion trigger
    LaunchedEffect(triggerIntrusion) {
        if (triggerIntrusion) {
            previousState = currentState
            currentState = ScreenState.INTRUSION_INTERCEPT
            onIntrusionHandled()
        }
    }

    // Load DataStore configurations and verify countdown timer with reboot detection on startup
    LaunchedEffect(Unit) {
        try {
            val prefs = vowDataStore.preferencesFlow.first()
            isVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
            isActiveVowMode = prefs[VowDataStore.IS_ACTIVE_VOW_MODE] ?: false
            deactivationRequestTime = prefs[VowDataStore.DEACTIVATION_REQUEST_TIME] ?: 0L
            banDomainSet = prefs[VowDataStore.BAN_DOMAIN_SET] ?: setOf("instagram.com")
            secureFolderEnabled = prefs[VowDataStore.SECURE_FOLDER_ENABLED] ?: false
            privateSpaceEnabled = prefs[VowDataStore.PRIVATE_SPACE_ENABLED] ?: false
            lockUninstall = prefs[VowDataStore.LOCK_UNINSTALL] ?: false
            disallowDataWipe = prefs[VowDataStore.DISALLOW_DATA_WIPE] ?: false
            disableSafeBoot = prefs[VowDataStore.DISABLE_SAFE_BOOT] ?: false
            blockPlayStore = prefs[VowDataStore.BLOCK_PLAY_STORE] ?: false
            dynamicReinstall = prefs[VowDataStore.DYNAMIC_REINSTALL] ?: false
            deactivateUsbDebugging = prefs[VowDataStore.DEACTIVATE_USB_DEBUGGING] ?: false
            quietHoursEnabled = prefs[VowDataStore.QUIET_HOURS_ENABLED] ?: false
            quietStartHour = prefs[VowDataStore.QUIET_START_HOUR] ?: 22
            quietStartMin = prefs[VowDataStore.QUIET_START_MIN] ?: 0
            quietEndHour = prefs[VowDataStore.QUIET_END_HOUR] ?: 7
            quietEndMin = prefs[VowDataStore.QUIET_END_MIN] ?: 0
            quietHoursTargetAppSet = prefs[VowDataStore.QUIET_HOURS_TARGET_APP_SET] ?: emptySet()
            quietHoursSpecificDomain = prefs[VowDataStore.QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
            vowStartTimeMs = prefs[VowDataStore.VOW_START_TIME_MS] ?: 0L
            vowInitialDurationSeconds = prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS] ?: 0L
            
            val blocksJson = prefs[VowDataStore.VOW_BLOCKS_JSON] ?: ""
            vowBlocks = VowBlock.deserializeList(blocksJson)
            if (vowBlocks.isEmpty()) {
                vowBlocks = listOf(
                    VowBlock(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "Quiet Hours",
                        isEnabled = quietHoursEnabled,
                        startHour = quietStartHour,
                        startMin = quietStartMin,
                        endHour = quietEndHour,
                        endMin = quietEndMin,
                        targetApps = quietHoursTargetAppSet,
                        specificDomain = quietHoursSpecificDomain
                    )
                )
            }
            frozenVowBlocks = vowBlocks
            
            usageLimitsUpdated = prefs[VowDataStore.USAGE_LIMITS_UPDATED] ?: false
            allowedValue = prefs[VowDataStore.ALLOWED_VALUE] ?: "5"
            allowedUnit = prefs[VowDataStore.ALLOWED_UNIT] ?: "min"
            selectedInterval = prefs[VowDataStore.SELECTED_INTERVAL] ?: "hour"
            targetAppSet = prefs[VowDataStore.TARGET_APP_SET] ?: emptySet()
            specificDomain = prefs[VowDataStore.SPECIFIC_DOMAIN] ?: ""
            isCollectiveLimit = prefs[VowDataStore.IS_COLLECTIVE_LIMIT] ?: false

            // Sync frozen states on load
            frozenAllowedValue = allowedValue
            frozenAllowedUnit = allowedUnit
            frozenInterval = selectedInterval
            frozenQuietHoursEnabled = quietHoursEnabled
            frozenQuietStartHour = quietStartHour
            frozenQuietStartMin = quietStartMin
            frozenQuietEndHour = quietEndHour
            frozenQuietEndMin = quietEndMin
            frozenQuietHoursTargetAppSet = quietHoursTargetAppSet
            frozenQuietHoursSpecificDomain = quietHoursSpecificDomain
            
            // Resume timer if active
            val savedRemaining = prefs[VowDataStore.REMAINING_VOW_SECONDS] ?: 0L
            if (isVowActive && savedRemaining > 0) {
                val lastUptime = prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
                val currentUptime = SystemClock.elapsedRealtime()
                
                val finalRemaining = com.avow.app.util.VowValidator.calculateRemainingSeconds(
                    currentUptimeMillis = currentUptime,
                    lastUptimeMillis = lastUptime,
                    savedRemainingSeconds = savedRemaining
                )
                
                if (finalRemaining > 0) {
                    days = (finalRemaining / 86400).toInt()
                    hours = ((finalRemaining % 86400) / 3600).toInt()
                    minutes = ((finalRemaining % 3600) / 60).toInt()
                    seconds = (finalRemaining % 60).toInt()
                    currentState = ScreenState.LOCKED_VAULT
                } else {
                    // Expired while closed
                    isVowActive = false
                    isActiveVowMode = false
                    currentState = ScreenState.UNLOCKED_VAULT
                    DeviceAdmin.assertBindingVow(
                        context = context,
                        activate = false,
                        secureFolderEnabled = secureFolderEnabled,
                        privateSpaceEnabled = privateSpaceEnabled,
                        lockUninstall = lockUninstall,
                        disallowDataWipe = disallowDataWipe,
                        disableSafeBoot = disableSafeBoot,
                        blockPlayStore = blockPlayStore,
                        deactivateUsbDebugging = deactivateUsbDebugging
                    )
                    vowDataStore.clearVowConfig()
                    isCollectiveLimit = false
                }
            }
            isLoaded = true
        } catch (e: Exception) {
            Log.e("MainScreen", "Failed to load DataStore settings", e)
            isLoaded = true
        }
    }

    // Automatically persist settings to DataStore when changes are made
    LaunchedEffect(
        isLoaded,
        secureFolderEnabled, privateSpaceEnabled, lockUninstall, disallowDataWipe,
        disableSafeBoot, blockPlayStore, dynamicReinstall, deactivateUsbDebugging,
        banDomainSet, quietHoursEnabled, quietStartHour, quietStartMin, quietEndHour, quietEndMin,
        quietHoursTargetAppSet, quietHoursSpecificDomain,
        usageLimitsUpdated, allowedValue, allowedUnit, selectedInterval, targetAppSet, specificDomain,
        isActiveVowMode, deactivationRequestTime, isCollectiveLimit, vowBlocks
    ) {
        if (isLoaded) {
            try {
                vowDataStore.saveVowConfig(
                    isVowActive = isVowActive,
                    isActiveVowMode = isActiveVowMode,
                    remainingVowSeconds = com.avow.app.util.VowValidator.clampRemainingSeconds(days * 86400L + hours * 3600L + minutes * 60L + seconds),
                    lastSystemUptimeMillis = SystemClock.elapsedRealtime(),
                    banDomainSet = banDomainSet,
                    secureFolderEnabled = secureFolderEnabled,
                    privateSpaceEnabled = privateSpaceEnabled,
                    lockUninstall = lockUninstall,
                    disallowDataWipe = disallowDataWipe,
                    disableSafeBoot = disableSafeBoot,
                    blockPlayStore = blockPlayStore,
                    dynamicReinstall = dynamicReinstall,
                    deactivateUsbDebugging = deactivateUsbDebugging,
                    quietHoursEnabled = quietHoursEnabled,
                    quietStartHour = quietStartHour,
                    quietStartMin = quietStartMin,
                    quietEndHour = quietEndHour,
                    quietEndMin = quietEndMin,
                    quietHoursTargetAppSet = quietHoursTargetAppSet,
                    quietHoursSpecificDomain = quietHoursSpecificDomain,
                    usageLimitsUpdated = usageLimitsUpdated,
                    allowedValue = allowedValue,
                    allowedUnit = allowedUnit,
                    selectedInterval = selectedInterval,
                    targetAppSet = targetAppSet,
                    specificDomain = specificDomain,
                    deactivationRequestTime = deactivationRequestTime,
                    isCollectiveLimit = isCollectiveLimit,
                    vowBlocksJson = VowBlock.serializeList(vowBlocks),
                    vowStartTimeMs = vowStartTimeMs,
                    vowInitialDurationSeconds = vowInitialDurationSeconds
                )
            } catch (e: Exception) {
                Log.e("MainScreen", "Failed to auto-save settings", e)
            }
        }
    }

    // Helper to calculate quiet hours duration in minutes
    fun getQuietHoursDurationMinutes(startH: Int, startM: Int, endH: Int, endM: Int): Int {
        return com.avow.app.util.VowValidator.getQuietHoursDurationMinutes(startH, startM, endH, endM)
    }

    // Helper to calculate usage limits rate in equivalent minutes per hour
    fun getUsageLimitRate(valueStr: String, unit: String, interval: String): Float {
        return com.avow.app.util.VowValidator.getUsageLimitRate(valueStr, unit, interval)
    }

    // Run active countdown simulation when LOCKED (ticks background-safely whenever isVowActive is true)
    LaunchedEffect(isVowActive) {
        if (isVowActive) {
            var lastCheckUptime = SystemClock.elapsedRealtime()
            while (isVowActive) {
                delay(1000L)
                val currentUptime = SystemClock.elapsedRealtime()
                val elapsedMs = currentUptime - lastCheckUptime
                if (elapsedMs >= 1000L) {
                    val elapsedSeconds = elapsedMs / 1000L
                    lastCheckUptime += elapsedSeconds * 1000L
                    
                    var totalRemaining = days * 86400L + hours * 3600L + minutes * 60L + seconds
                    totalRemaining -= elapsedSeconds
                    
                    if (totalRemaining <= 0L) {
                        // Vow expired: clear restrictions natively
                        DeviceAdmin.assertBindingVow(
                            context = context,
                            activate = false,
                            secureFolderEnabled = secureFolderEnabled,
                            privateSpaceEnabled = privateSpaceEnabled,
                            lockUninstall = lockUninstall,
                            disallowDataWipe = disallowDataWipe,
                            disableSafeBoot = disableSafeBoot,
                            blockPlayStore = blockPlayStore,
                            deactivateUsbDebugging = deactivateUsbDebugging
                        )
                        isVowActive = false
                        currentState = ScreenState.UNLOCKED_VAULT
                        vowDataStore.clearVowConfig()
                        isCollectiveLimit = false
                        showToast("aVow: Temporal lock has expired. Restrictions cleared.")
                        break
                    } else {
                        days = (totalRemaining / 86400).toInt()
                        hours = ((totalRemaining % 86400) / 3600).toInt()
                        minutes = ((totalRemaining % 3600) / 60).toInt()
                        seconds = (totalRemaining % 60).toInt()
                        
                        vowDataStore.saveCountdownState(totalRemaining, currentUptime)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightGraphiteBg)
    ) {
        when (currentState) {
            ScreenState.UNLOCKED_VAULT, ScreenState.LOCKED_VAULT -> {
                VaultDashboard(
                    statusText = if (isVowActive) "LOCKED" else "UNLOCKED",
                    statusColor = if (isVowActive) LockedRed else MonospaceText,
                    days = days,
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds,
                    panelThreeTitle = if (isVowActive) "ADD BINDING TIME" else "INFLICT BINDING VOW",
                    panelThreeSubtitle = "RESTRICTION_2",
                    onQuietHoursClick = { showQuietHoursDialog = true },
                    onSetUsageLimitsClick = { showUsageLimitsDialog = true },
                    onPanelThreeClick = { showBindingVowDialog = true },
                    onSettingsClick = { currentState = ScreenState.CONFIGURATION },
                    onDeactivateClick = {
                        val currentTime = System.currentTimeMillis()
                        if (deactivationRequestTime == 0L) {
                            deactivationRequestTime = currentTime
                            scope.launch {
                                vowDataStore.saveDeactivationRequestTime(currentTime)
                            }
                            showToast("Deactivation requested. 24-hour cooling-off period initiated.")
                        } else {
                            val elapsed = currentTime - deactivationRequestTime
                            val remainingMs = 24L * 3600L * 1000L - elapsed
                            if (remainingMs > 0) {
                                val remainingHours = remainingMs / (3600L * 1000L)
                                val remainingMins = (remainingMs % (3600L * 1000L)) / (60L * 1000L)
                                val remainingSecs = (remainingMs % (60L * 1000L)) / 1000L
                                showToast("Cooling-off active. Try again in ${remainingHours}h ${remainingMins}m ${remainingSecs}s.")
                            } else {
                                val err = DeviceAdmin.deactivateDeviceOwner(context)
                                if (err != null) {
                                    showToast(err)
                                } else {
                                    showToast("aVow: System Authority Deactivated. Natively Uninstallable.")
                                    deactivationRequestTime = 0L
                                    scope.launch {
                                        vowDataStore.saveDeactivationRequestTime(0L)
                                    }
                                }
                            }
                        }
                    },
                    onViewFocusInsightsClick = {
                        currentState = ScreenState.FOCUS_HISTORY
                    },
                    quietHoursActivated = vowBlocks.any { it.isEnabled },
                    usageLimitsActivated = usageLimitsUpdated,
                    bindingVowActivated = isVowActive,
                    deactivationRequestTime = deactivationRequestTime,
                    tickTrigger = tickTrigger
                )
            }
            ScreenState.CONFIGURATION -> {
                ConfigurationWorkspace(
                    secureFolderEnabled = secureFolderEnabled,
                    onSecureFolderToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else secureFolderEnabled = !secureFolderEnabled
                    },
                    privateSpaceEnabled = privateSpaceEnabled,
                    onPrivateSpaceToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else privateSpaceEnabled = !privateSpaceEnabled
                    },
                    lockUninstall = lockUninstall,
                    onLockUninstallToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else lockUninstall = !lockUninstall
                    },
                    disallowDataWipe = disallowDataWipe,
                    onDisallowDataWipeToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else disallowDataWipe = !disallowDataWipe
                    },
                    disableSafeBoot = disableSafeBoot,
                    onDisableSafeBootToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else disableSafeBoot = !disableSafeBoot
                    },
                    blockPlayStore = blockPlayStore,
                    onBlockPlayStoreToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else blockPlayStore = !blockPlayStore
                    },
                    dynamicReinstall = dynamicReinstall,
                    onDynamicReinstallToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else dynamicReinstall = !dynamicReinstall
                    },
                    deactivateUsbDebugging = deactivateUsbDebugging,
                    onDeactivateUsbDebuggingToggle = {
                        if (isVowActive) showToast("Error: Restrictions cannot be modified while locked.")
                        else deactivateUsbDebugging = !deactivateUsbDebugging
                    },
                    banDomainSet = banDomainSet,
                    onDomainAdd = { domain ->
                        if (!banDomainSet.contains(domain)) {
                            banDomainSet = banDomainSet + domain
                        }
                    },
                    onDomainRemove = { domain ->
                        if (isVowActive) {
                            showToast("Error: Cannot remove domains while locked.")
                        } else {
                            banDomainSet = banDomainSet - domain
                        }
                    },
                    onBack = {
                        currentState = if (isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT
                    },
                    isLocked = isVowActive
                )
            }
            ScreenState.INTRUSION_INTERCEPT -> {
                IntrusionInterceptOverlay(
                    onExit = {
                        currentState = previousState
                    }
                )
            }
            ScreenState.TEMPORARY_LOCKOUT -> {
                TemporaryLockoutOverlay()
            }
            ScreenState.FOCUS_HISTORY -> {
                FocusHistoryWorkspace(
                    onBack = {
                        currentState = if (isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT
                    },
                    db = com.avow.app.data.history.VowDatabase.getDatabase(context)
                )
            }
        }

        // Scheduled Blocks Settings Dialog
        if (showQuietHoursDialog) {
            QuietHoursConfigDialog(
                vowBlocks = vowBlocks,
                installedApps = installedApps,
                isLocked = isVowActive,
                onDismiss = { showQuietHoursDialog = false },
                showToast = showToast,
                onUpdate = { newBlocks ->
                    if (isVowActive) {
                        val isContainmentValid = com.avow.app.util.VowValidator.validateContainment(frozenVowBlocks, newBlocks)
                        if (!isContainmentValid) {
                            showToast("Error: Scheduled blocks cannot be shortened, shifted or deleted when locked.")
                        } else {
                            vowBlocks = newBlocks
                            frozenVowBlocks = newBlocks
                            showQuietHoursDialog = false
                            showToast("Scheduled blocks updated (stricter).")
                        }
                    } else {
                        vowBlocks = newBlocks
                        showQuietHoursDialog = false
                        showToast("Scheduled blocks updated.")
                    }
                }
            )
        }

        // Usage Limits Settings Dialog
        if (showUsageLimitsDialog) {
            UsageLimitsConfigDialog(
                enabled = usageLimitsUpdated,
                onEnabledChange = { enabled ->
                    if (isVowActive) {
                        if (usageLimitsUpdated && !enabled) {
                            showToast("Error: Usage limits cannot be disabled while locked.")
                        } else {
                            usageLimitsUpdated = enabled
                        }
                    } else {
                        usageLimitsUpdated = enabled
                    }
                },
                allowedValue = allowedValue,
                onAllowedValueChange = { allowedValue = it },
                allowedUnit = allowedUnit,
                onAllowedUnitChange = { allowedUnit = it },
                selectedInterval = selectedInterval,
                onIntervalSelect = { selectedInterval = it },
                targetAppSet = targetAppSet,
                onTargetAppAdd = { pkg ->
                    if (!targetAppSet.contains(pkg)) {
                        targetAppSet = targetAppSet + pkg
                    }
                },
                onTargetAppRemove = { pkg ->
                    if (isVowActive) {
                        showToast("Error: Target applications cannot be removed while locked.")
                    } else {
                        targetAppSet = targetAppSet - pkg
                    }
                },
                specificDomain = specificDomain,
                onSpecificDomainChange = { specificDomain = it },
                installedApps = installedApps,
                isLocked = isVowActive,
                onDismiss = { showUsageLimitsDialog = false },
                onUpdate = {
                    val minutesVal = allowedValue.toFloatOrNull()
                    if (minutesVal == null || minutesVal <= 0f) {
                        showToast("Error: Invalid allowed value.")
                    } else {
                        if (isVowActive) {
                            val newRate = getUsageLimitRate(allowedValue, allowedUnit, selectedInterval)
                            val oldRate = getUsageLimitRate(frozenAllowedValue, frozenAllowedUnit, frozenInterval)
                            
                            if (newRate > oldRate) {
                                showToast("Error: Usage limits can only be made stricter (fewer minutes).")
                            } else {
                                frozenAllowedValue = allowedValue
                                frozenAllowedUnit = allowedUnit
                                frozenInterval = selectedInterval
                                showUsageLimitsDialog = false
                                showToast("Usage limits updated (stricter).")
                            }
                        } else {
                            showUsageLimitsDialog = false
                            showToast("Usage limits updated.")
                        }
                    }
                },
                isCollectiveLimit = isCollectiveLimit,
                onCollectiveLimitChange = { isCollectiveLimit = it }
            )
        }

        // Binding Vow Settings Dialog (DD:HH:MM:SS)
        if (showBindingVowDialog) {
            BindingVowConfigDialog(
                isLockedState = isVowActive,
                initialDays = initialDaysForDialog,
                initialHours = initialHoursForDialog,
                initialMinutes = initialMinutesForDialog,
                initialSeconds = initialSecondsForDialog,
                onDismiss = {
                    showBindingVowDialog = false
                    initialDaysForDialog = "00"
                    initialHoursForDialog = "00"
                    initialMinutesForDialog = "00"
                    initialSecondsForDialog = "00"
                },
                onConfirm = label@ { additionalSeconds ->
                    if (isVowActive) {
                        val currentTotalSeconds = days * 86400L + hours * 3600L + minutes * 60L + seconds
                        val newTotalSeconds = currentTotalSeconds + additionalSeconds
                        days = (newTotalSeconds / 86400).toInt()
                        hours = ((newTotalSeconds % 86400) / 3600).toInt()
                        minutes = ((newTotalSeconds % 3600) / 60).toInt()
                        seconds = (newTotalSeconds % 60).toInt()
                        
                        scope.launch {
                            vowDataStore.saveCountdownState(newTotalSeconds, SystemClock.elapsedRealtime())
                        }
                        showToast("Added time to the active Vow.")
                    } else {
                        // Check if BlockerService Accessibility Service is active
                        if (!isAccessibilityServiceEnabled(context, BlockerService::class.java)) {
                            showToast("Error: aVow Accessibility Service is not active. Enable it in Settings first.")
                            showBindingVowDialog = false
                            initialDaysForDialog = "00"
                            initialHoursForDialog = "00"
                            initialMinutesForDialog = "00"
                            initialSecondsForDialog = "00"
                            return@label
                        }

                        // Inflicting Vow: Call DeviceAdmin policy restrictions
                        val err = DeviceAdmin.assertBindingVow(
                            context = context,
                            activate = true,
                            secureFolderEnabled = secureFolderEnabled,
                            privateSpaceEnabled = privateSpaceEnabled,
                            lockUninstall = lockUninstall,
                            disallowDataWipe = disallowDataWipe,
                            disableSafeBoot = disableSafeBoot,
                            blockPlayStore = blockPlayStore,
                            deactivateUsbDebugging = deactivateUsbDebugging
                        )

                        if (err != null) {
                            showToast(err)
                        }
                        
                        days = (additionalSeconds / 86400).toInt()
                        hours = ((additionalSeconds % 86400) / 3600).toInt()
                        minutes = ((additionalSeconds % 3600) / 60).toInt()
                        seconds = (additionalSeconds % 60).toInt()
                        
                        frozenAllowedValue = allowedValue
                        frozenAllowedUnit = allowedUnit
                        frozenInterval = selectedInterval
                        frozenQuietHoursEnabled = quietHoursEnabled
                        frozenQuietStartHour = quietStartHour
                        frozenQuietStartMin = quietStartMin
                        frozenQuietEndHour = quietEndHour
                        frozenQuietEndMin = quietEndMin
                        frozenQuietHoursTargetAppSet = quietHoursTargetAppSet
                        frozenQuietHoursSpecificDomain = quietHoursSpecificDomain
                        frozenVowBlocks = vowBlocks
                        
                        isVowActive = true
                        currentState = ScreenState.LOCKED_VAULT
                        
                        isActiveVowMode = true
                        val startMs = System.currentTimeMillis()
                        vowStartTimeMs = startMs
                        vowInitialDurationSeconds = additionalSeconds
                        scope.launch {
                            vowDataStore.saveVowConfig(
                                isVowActive = true,
                                isActiveVowMode = true,
                                remainingVowSeconds = additionalSeconds,
                                lastSystemUptimeMillis = SystemClock.elapsedRealtime(),
                                banDomainSet = banDomainSet,
                                secureFolderEnabled = secureFolderEnabled,
                                privateSpaceEnabled = privateSpaceEnabled,
                                lockUninstall = lockUninstall,
                                disallowDataWipe = disallowDataWipe,
                                disableSafeBoot = disableSafeBoot,
                                blockPlayStore = blockPlayStore,
                                dynamicReinstall = dynamicReinstall,
                                deactivateUsbDebugging = deactivateUsbDebugging,
                                quietHoursEnabled = quietHoursEnabled,
                                quietStartHour = quietStartHour,
                                quietStartMin = quietStartMin,
                                quietEndHour = quietEndHour,
                                quietEndMin = quietEndMin,
                                quietHoursTargetAppSet = quietHoursTargetAppSet,
                                quietHoursSpecificDomain = quietHoursSpecificDomain,
                                usageLimitsUpdated = usageLimitsUpdated,
                                allowedValue = allowedValue,
                                allowedUnit = allowedUnit,
                                selectedInterval = selectedInterval,
                                targetAppSet = targetAppSet,
                                specificDomain = specificDomain,
                                deactivationRequestTime = deactivationRequestTime,
                                isCollectiveLimit = isCollectiveLimit,
                                vowBlocksJson = VowBlock.serializeList(vowBlocks),
                                vowStartTimeMs = startMs,
                                vowInitialDurationSeconds = additionalSeconds
                            )
                        }

                        if (err == null) {
                            showToast("Binding Vow Inflicted - System Authority Active")
                        }
                    }
                    showBindingVowDialog = false
                    initialDaysForDialog = "00"
                    initialHoursForDialog = "00"
                    initialMinutesForDialog = "00"
                    initialSecondsForDialog = "00"
                }
            )
        }

        // Toast Banner Overlay
        AnimatedVisibility(
            visible = inAppToastMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            inAppToastMessage?.let { msg ->
                InAppToastBanner(
                    message = msg,
                    onDismiss = { inAppToastMessage = null }
                )
            }
        }
    }
}

/**
 * Custom Canvas outline straight face mark drawn with a constant-width #8A8A8A accent line.
 */
@Composable
fun StraightFaceOutline(
    modifier: Modifier = Modifier,
    color: Color = OutlineAccent
) {
    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val scale = minDim / 200f
        val strokeWidth = 4f * scale

        // 1. Outer Face boundary circle (cx=100, cy=100, r=80)
        drawCircle(
            color = color,
            radius = 80f * scale,
            center = Offset(100f * scale, 100f * scale),
            style = Stroke(width = strokeWidth)
        )

        // 2. Left Eye circle (cx=75, cy=82, r=10)
        drawCircle(
            color = color,
            radius = 10f * scale,
            center = Offset(75f * scale, 82f * scale),
            style = Stroke(width = strokeWidth)
        )
        
        // 3. Right Eye circle (cx=125, cy=82, r=10)
        drawCircle(
            color = color,
            radius = 10f * scale,
            center = Offset(125f * scale, 82f * scale),
            style = Stroke(width = strokeWidth)
        )

        // 4. Straight mouth horizontal line
        drawLine(
            color = color,
            start = Offset(70f * scale, 140f * scale),
            end = Offset(130f * scale, 140f * scale),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Temporary Lockout Overlay (😐 Face on LightGraphiteBg background).
 */
@Composable
fun TemporaryLockoutOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightGraphiteBg),
        contentAlignment = Alignment.Center
    ) {
        StraightFaceOutline(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .aspectRatio(1f)
        )
    }
}

/**
 * Custom Canvas outline smiley face mark drawn with a constant-width #8A8A8A accent line.
 * Reconstructed to scale mathematically from 200x200 SVG specifications.
 */
@Composable
fun SmileyFaceOutline(
    modifier: Modifier = Modifier,
    color: Color = OutlineAccent
) {
    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        val scale = minDim / 200f
        val strokeWidth = 4f * scale

        // 1. Outer Face boundary circle (cx=100, cy=100, r=80)
        drawCircle(
            color = color,
            radius = 80f * scale,
            center = Offset(100f * scale, 100f * scale),
            style = Stroke(width = strokeWidth)
        )

        // 2. Left Eye circle (cx=75, cy=82, r=10)
        drawCircle(
            color = color,
            radius = 10f * scale,
            center = Offset(75f * scale, 82f * scale),
            style = Stroke(width = strokeWidth)
        )
        
        // 3. Right Eye circle (cx=125, cy=82, r=10)
        drawCircle(
            color = color,
            radius = 10f * scale,
            center = Offset(125f * scale, 82f * scale),
            style = Stroke(width = strokeWidth)
        )

        // 4. Smile Arc cubic bezier path (M60 130C60 130 75 150 100 150C125 150 140 130 140 130)
        val path = Path().apply {
            moveTo(60f * scale, 130f * scale)
            cubicTo(
                60f * scale, 130f * scale,
                75f * scale, 150f * scale,
                100f * scale, 150f * scale
            )
            cubicTo(
                125f * scale, 150f * scale,
                140f * scale, 130f * scale,
                140f * scale, 130f * scale
            )
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Dashboard Layout displaying the countdown timer, status, and option panels.
 */
@Composable
fun VaultDashboard(
    statusText: String,
    statusColor: Color,
    days: Int,
    hours: Int,
    minutes: Int,
    seconds: Int,
    panelThreeTitle: String,
    panelThreeSubtitle: String,
    onQuietHoursClick: () -> Unit,
    onSetUsageLimitsClick: () -> Unit,
    onPanelThreeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    onViewFocusInsightsClick: () -> Unit,
    quietHoursActivated: Boolean,
    usageLimitsActivated: Boolean,
    bindingVowActivated: Boolean,
    deactivationRequestTime: Long = 0L,
    tickTrigger: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top status bar layout (Stark header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmileyFaceOutline(
                modifier = Modifier
                    .size(26.dp)
            )
        }
        
        // Horizontal grid line divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineAccent)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // 2. Status Label (LOCKED / UNLOCKED)
        Text(
            text = statusText,
            color = statusColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Countdown Clock Grid (DD : HH : MM : SS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formatNum = { num: Int -> String.format("%02d", num) }
            
            ClockDigitColumn(digit = formatNum(days), label = "DD")
            ClockSeparator()
            ClockDigitColumn(digit = formatNum(hours), label = "HH")
            ClockSeparator()
            ClockDigitColumn(digit = formatNum(minutes), label = "MM")
            ClockSeparator()
            ClockDigitColumn(digit = formatNum(seconds), label = "SS")
        }

        Spacer(modifier = Modifier.weight(1f))

        // 4. Sharp Dash-Bordered Option Panels
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardPanelButton(
                title = "QUIET HOURS",
                subtitle = panelThreeSubtitle.replace("2", "0"), // RESTRICTION_0
                isActivated = quietHoursActivated,
                onClick = onQuietHoursClick
            )
            DashboardPanelButton(
                title = "SET USAGE LIMITS",
                subtitle = panelThreeSubtitle.replace("2", "1"), // RESTRICTION_1
                isActivated = usageLimitsActivated,
                onClick = onSetUsageLimitsClick
            )
            DashboardPanelButton(
                title = panelThreeTitle,
                subtitle = panelThreeSubtitle, // RESTRICTION_2
                isActivated = bindingVowActivated,
                onClick = onPanelThreeClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            SharpBorderButton(
                text = "[ VIEW FOCUS INSIGHTS ]",
                onClick = onViewFocusInsightsClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!bindingVowActivated) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Read tickTrigger to force recomposition
                val trigger = tickTrigger
                val buttonText = if (deactivationRequestTime > 0L) {
                    val elapsed = System.currentTimeMillis() - deactivationRequestTime
                    val remainingMs = 24L * 3600L * 1000L - elapsed
                    if (remainingMs > 0) {
                        val hoursLeft = remainingMs / (3600L * 1000L)
                        val minsLeft = (remainingMs % (3600L * 1000L)) / (60L * 1000L)
                        val secsLeft = (remainingMs % (60L * 1000L)) / 1000L
                        "{ COOLING OFF: ${String.format("%02d:%02d:%02d", hoursLeft, minsLeft, secsLeft)} }"
                    } else {
                        "{ DEACTIVATE DEVICE OWNER }"
                    }
                } else {
                    "{ DEACTIVATE DEVICE OWNER }"
                }
                SharpBorderButton(
                    text = buttonText,
                    onClick = onDeactivateClick,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1.5f))

        // 5. Bottom Centered Navigation Action Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SharpBorderButton(
                text = "{ SETTINGS }",
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

@Composable
fun ClockDigitColumn(digit: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        Text(
            text = digit,
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 38.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ClockSeparator() {
    Text(
        text = ":",
        color = MonospaceText,
        fontFamily = FontFamily.Monospace,
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .padding(bottom = 16.dp)
    )
}

/**
 * Modifier drawing a dashed sharp border for dashboard options.
 */
fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    on: Dp = 6.dp,
    off: Dp = 6.dp
) = drawBehind {
    val strokeWidthPx = width.toPx()
    val onPx = on.toPx()
    val offPx = off.toPx()
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(onPx, offPx), 0f)
    
    drawRoundRect(
        color = color,
        style = Stroke(width = strokeWidthPx, pathEffect = pathEffect)
    )
}

/**
 * Modifier drawing a solid sharp border (no corner rounding).
 */
fun Modifier.sharpBorder(
    width: Dp,
    color: Color
) = drawBehind {
    val strokeWidthPx = width.toPx()
    drawRect(
        color = color,
        style = Stroke(width = strokeWidthPx)
    )
}

@Composable
fun DashboardPanelButton(
    title: String,
    subtitle: String,
    isActivated: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .then(
                if (isActivated) {
                    Modifier
                        .background(DarkerSurfaceColor)
                        .sharpBorder(1.dp, OutlineAccent)
                } else {
                    Modifier.dashedBorder(1.dp, OutlineAccent)
                }
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun SharpBorderButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .sharpBorder(1.dp, OutlineAccent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

data class RestrictionItem(
    val title: String,
    val detail: String,
    val isChecked: Boolean,
    val onToggle: () -> Unit
)

/**
 * Configuration Workspace View (State A) - Renders static list of ENFORCEMENT_RESTRICTIONS.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigurationWorkspace(
    secureFolderEnabled: Boolean,
    onSecureFolderToggle: () -> Unit,
    privateSpaceEnabled: Boolean,
    onPrivateSpaceToggle: () -> Unit,
    lockUninstall: Boolean,
    onLockUninstallToggle: () -> Unit,
    disallowDataWipe: Boolean,
    onDisallowDataWipeToggle: () -> Unit,
    disableSafeBoot: Boolean,
    onDisableSafeBootToggle: () -> Unit,
    blockPlayStore: Boolean,
    onBlockPlayStoreToggle: () -> Unit,
    dynamicReinstall: Boolean,
    onDynamicReinstallToggle: () -> Unit,
    deactivateUsbDebugging: Boolean,
    onDeactivateUsbDebuggingToggle: () -> Unit,
    banDomainSet: Set<String>,
    onDomainAdd: (String) -> Unit,
    onDomainRemove: (String) -> Unit,
    onBack: () -> Unit,
    isLocked: Boolean
) {
    val restrictionsList = listOf(
        RestrictionItem("SAMSUNG KNOX SECURE FOLDER", "com.samsung.knox.securefolder", secureFolderEnabled, onSecureFolderToggle),
        RestrictionItem("ANDROID 15 PRIVATE SPACE", "com.google.android.apps.privatespace", privateSpaceEnabled, onPrivateSpaceToggle),
        RestrictionItem("LOCK UNINSTALLATION", "setUninstallBlocked", lockUninstall, onLockUninstallToggle),
        RestrictionItem("DISALLOW DATA WIPE", "DISALLOW_APPS_CONTROL", disallowDataWipe, onDisallowDataWipeToggle),
        RestrictionItem("DISABLE SAFE BOOT", "DISALLOW_SAFE_BOOT", disableSafeBoot, onDisableSafeBootToggle),
        RestrictionItem("BLOCK PLAY STORE", "com.android.vending", blockPlayStore, onBlockPlayStoreToggle),
        RestrictionItem("DYNAMIC REINSTALL GUARD", "ACTION_PACKAGE_ADDED", dynamicReinstall, onDynamicReinstallToggle),
        RestrictionItem("DEACTIVATE USB DEBUGGING", "DISALLOW_DEBUGGING_FEATURES", deactivateUsbDebugging, onDeactivateUsbDebuggingToggle)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Workspace Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isLocked) "[ aVow // STATUS: LOCKED ]" else "[ aVow // STATUS: OPEN ]",
                color = if (isLocked) LockedRed else MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Text(
                text = "< BACK",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineAccent)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Custom Domain Input Field
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "BAN_DOMAIN_SET >",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            var domainInput by remember { mutableStateOf("") }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MonospaceTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        placeholder = "e.g. youtube.com",
                        enabled = true
                    )
                }
                
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .border(1.dp, OutlineAccent)
                        .background(DarkerSurfaceColor)
                        .clickable {
                            if (domainInput.isNotBlank()) {
                                onDomainAdd(domainInput.trim())
                                domainInput = ""
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "{ ADD }",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (banDomainSet.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    banDomainSet.forEach { domain ->
                        InputChip(
                            text = domain,
                            onRemove = { onDomainRemove(domain) },
                            enabled = !isLocked
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Scrollable list of ENFORCEMENT_RESTRICTIONS & Target profiles
        Text(
            text = "ENFORCEMENT_RESTRICTIONS",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .sharpBorder(1.dp, OutlineAccent)
                .background(MutedSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(restrictionsList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { item.onToggle() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "[x]" else "[ ]",
                        color = if (item.isChecked) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = item.title,
                            color = MonospaceText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "(${item.detail})",
                            color = SubtextGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Stark terminal input field implementation.
 */
@Composable
fun MonospaceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = if (enabled) MonospaceText else SubtextGrey
        ),
        cursorBrush = SolidColor(MonospaceText),
        decorationBox = { innerTextField ->
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(MutedSurface)
                    .sharpBorder(1.dp, OutlineAccent)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = SubtextGrey
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun InputChip(
    text: String,
    onRemove: (() -> Unit)?,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .border(1.dp, OutlineAccent, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(MutedSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            color = MonospaceText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.dp, if (enabled) LockedRed else SubtextGrey, shape = androidx.compose.foundation.shape.CircleShape)
                    .then(
                        if (enabled) {
                            Modifier.clickable { onRemove() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "x",
                    color = if (enabled) LockedRed else SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-1).dp)
                )
            }
        }
    }
}

/**
 * Intrusion Intercept Block (State C).
 */
@Composable
fun IntrusionInterceptOverlay(
    onExit: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightGraphiteBg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onExit()
                    },
                    onTap = {
                        tapCount++
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        SmileyFaceOutline(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .aspectRatio(1f)
        )
    }
}

/**
 * Dialog layout configured to match the "CONFIG: USAGE LIMITS" specifications.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsageLimitsConfigDialog(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    allowedValue: String,
    onAllowedValueChange: (String) -> Unit,
    allowedUnit: String,
    onAllowedUnitChange: (String) -> Unit,
    selectedInterval: String,
    onIntervalSelect: (String) -> Unit,
    targetAppSet: Set<String>,
    onTargetAppAdd: (String) -> Unit,
    onTargetAppRemove: (String) -> Unit,
    specificDomain: String,
    onSpecificDomainChange: (String) -> Unit,
    installedApps: List<Pair<String, String>>,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    isCollectiveLimit: Boolean,
    onCollectiveLimitChange: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightGraphiteBg)
                .border(1.dp, OutlineAccent)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIG: USAGE LIMITS",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OutlineAccent)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Enabled Toggle (Stark Bracket style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLocked) { onEnabledChange(!enabled) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (enabled) "[x] ENABLED" else "[ ] ENABLED",
                        color = if (enabled) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Limit Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLocked) { onCollectiveLimitChange(!isCollectiveLimit) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIMIT_MODE: [ ${if (isCollectiveLimit) "COLLECTIVE" else "INDEPENDENT"} ]",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Limit Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Allow",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )

                    // Allowed Value Textbox
                    BasicTextField(
                        value = allowedValue,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                onAllowedValueChange(newValue)
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MonospaceText,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(MonospaceText),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(34.dp)
                                    .border(1.dp, OutlineAccent)
                                    .background(MutedSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                innerTextField()
                            }
                        }
                    )

                    // Unit dropdown (min vs hours)
                    var unitDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .border(1.dp, OutlineAccent)
                                .background(MutedSurface)
                                .clickable(enabled = !isLocked) { unitDropdownExpanded = true }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = allowedUnit,
                                color = MonospaceText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "v",
                                color = SubtextGrey,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        
                        DropdownMenu(
                            expanded = unitDropdownExpanded,
                            onDismissRequest = { unitDropdownExpanded = false },
                            modifier = Modifier
                                .background(MutedSurface)
                                .border(1.dp, OutlineAccent)
                        ) {
                            DropdownMenuItem(
                                text = { Text("min", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    onAllowedUnitChange("min")
                                    unitDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("hours", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    onAllowedUnitChange("hours")
                                    unitDropdownExpanded = false
                                }
                            )
                        }
                    }

                    Text(
                        text = "per",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )

                    // Interval Selector Box
                    var intervalDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .border(1.dp, OutlineAccent)
                                .background(MutedSurface)
                                .clickable(enabled = !isLocked) { intervalDropdownExpanded = true }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = selectedInterval,
                                color = MonospaceText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "v",
                                color = SubtextGrey,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        
                        DropdownMenu(
                            expanded = intervalDropdownExpanded,
                            onDismissRequest = { intervalDropdownExpanded = false },
                            modifier = Modifier
                                .background(MutedSurface)
                                .border(1.dp, OutlineAccent)
                        ) {
                            DropdownMenuItem(
                                text = { Text("hour", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    onIntervalSelect("hour")
                                    intervalDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("day", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    onIntervalSelect("day")
                                    intervalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Target Application Label
                Text(
                    text = "TARGET APPLICATIONS",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp))

                // Target App Dropdown Select Container
                var appDropdownExpanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, OutlineAccent)
                            .background(MutedSurface)
                            .clickable { appDropdownExpanded = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "+ SELECT APPLICATION",
                            color = MonospaceText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "v",
                            color = SubtextGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }

                    DropdownMenu(
                        expanded = appDropdownExpanded,
                        onDismissRequest = { appDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 250.dp)
                            .background(MutedSurface)
                            .border(1.dp, OutlineAccent)
                    ) {
                        installedApps.forEach { (pkg, label) ->
                            DropdownMenuItem(
                                text = { Text("$label ($pkg)", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    onTargetAppAdd(pkg)
                                    appDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (targetAppSet.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        targetAppSet.forEach { pkg ->
                            val label = if (pkg == "All Social Media") {
                                "All Social Media"
                            } else {
                                installedApps.find { it.first == pkg }?.second ?: pkg
                            }
                            InputChip(
                                text = label,
                                onRemove = { onTargetAppRemove(pkg) },
                                enabled = !isLocked
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Specific Domain Input Label
                Text(
                    text = "SPECIFIC DOMAIN",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Specific Domain Textbox
                MonospaceTextField(
                    value = specificDomain,
                    onValueChange = onSpecificDomainChange,
                    placeholder = "e.g. youtube.com",
                    enabled = !isLocked
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Update Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(MonospaceText)
                        .clickable { onUpdate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "UPDATE",
                        color = LightGraphiteBg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Dialog layout configured to match the "CONFIG: QUIET HOURS" specifications.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuietHoursConfigDialog(
    vowBlocks: List<VowBlock>,
    installedApps: List<Pair<String, String>>,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    showToast: (String) -> Unit,
    onUpdate: (List<VowBlock>) -> Unit
) {
    var currentBlocks by remember { mutableStateOf(vowBlocks) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(LightGraphiteBg)
                .border(1.dp, OutlineAccent)
                .padding(16.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIG: SCHEDULED BLOCKS",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OutlineAccent)
                )

                Spacer(modifier = Modifier.height(16.dp))

                currentBlocks.forEachIndexed { index, block ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OutlineAccent)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "BLOCK SLOT #${index + 1}",
                            color = MonospaceText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Name text field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NAME: ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = block.name,
                                onValueChange = { newName ->
                                    currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(name = newName) else it }
                                },
                                enabled = !isLocked,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(MonospaceText),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, OutlineAccent)
                                    .background(MutedSurface)
                                    .padding(6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enabled Checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newEnabled = !block.isEnabled
                                    if (isLocked && block.isEnabled && !newEnabled) {
                                        showToast("Error: Scheduled blocks cannot be disabled when locked.")
                                    } else {
                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(isEnabled = newEnabled) else it }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (block.isEnabled) "[x] ENABLED" else "[ ] ENABLED",
                                color = if (block.isEnabled) MonospaceText else SubtextGrey,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Start Time (HH:MM)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("START: ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = String.format("%02d", block.startHour),
                                onValueChange = { newValue ->
                                    val parsed = newValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..23) {
                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(startHour = parsed) else it }
                                    }
                                },
                                enabled = !isLocked,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(MonospaceText),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(30.dp)
                                            .border(1.dp, OutlineAccent)
                                            .background(MutedSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                            Text(":", color = MonospaceText, fontFamily = FontFamily.Monospace)
                            BasicTextField(
                                value = String.format("%02d", block.startMin),
                                onValueChange = { newValue ->
                                    val parsed = newValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..59) {
                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(startMin = parsed) else it }
                                    }
                                },
                                enabled = !isLocked,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(MonospaceText),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(30.dp)
                                            .border(1.dp, OutlineAccent)
                                            .background(MutedSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // End Time (HH:MM)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("END:   ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = String.format("%02d", block.endHour),
                                onValueChange = { newValue ->
                                    val parsed = newValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..23) {
                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(endHour = parsed) else it }
                                    }
                                },
                                enabled = !isLocked,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(MonospaceText),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(30.dp)
                                            .border(1.dp, OutlineAccent)
                                            .background(MutedSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                            Text(":", color = MonospaceText, fontFamily = FontFamily.Monospace)
                            BasicTextField(
                                value = String.format("%02d", block.endMin),
                                onValueChange = { newValue ->
                                    val parsed = newValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..59) {
                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(endMin = parsed) else it }
                                    }
                                },
                                enabled = !isLocked,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(MonospaceText),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(30.dp)
                                            .border(1.dp, OutlineAccent)
                                            .background(MutedSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Applications
                        Text(
                            text = "TARGET APPLICATIONS",
                            color = SubtextGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .border(1.dp, OutlineAccent)
                                .background(MutedSurface)
                                .padding(4.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(installedApps.filter { it.first != "All Social Media" }) { (pkg, label) ->
                                    val isChecked = block.targetApps.contains(pkg)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isLocked) {
                                                    if (isChecked) {
                                                        showToast("Error: Target applications cannot be removed while locked.")
                                                    } else {
                                                        val newApps = block.targetApps + pkg
                                                        currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(targetApps = newApps) else it }
                                                    }
                                                } else {
                                                    val newApps = if (isChecked) block.targetApps - pkg else block.targetApps + pkg
                                                    currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(targetApps = newApps) else it }
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isChecked) "[x] " else "[ ] ",
                                            color = MonospaceText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "$label ($pkg)",
                                            color = MonospaceText,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Specific Domain
                        Text(
                            text = "SPECIFIC DOMAIN",
                            color = SubtextGrey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MonospaceTextField(
                            value = block.specificDomain,
                            onValueChange = { newDomain ->
                                currentBlocks = currentBlocks.map { if (it.id == block.id) it.copy(specificDomain = newDomain) else it }
                            },
                            placeholder = "e.g. youtube.com",
                            enabled = !isLocked
                        )

                        // Remove block button
                        if (currentBlocks.size > 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "[ REMOVE BLOCK SLOT ]",
                                color = LockedRed,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        if (isLocked) {
                                            showToast("Error: Scheduled blocks cannot be removed while locked.")
                                        } else {
                                            currentBlocks = currentBlocks.filter { it.id != block.id }
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Add slot button
                if (currentBlocks.size < 4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, OutlineAccent)
                            .background(MutedSurface)
                            .clickable {
                                if (isLocked) {
                                    showToast("Error: Cannot add new block slots while locked.")
                                } else {
                                    currentBlocks = currentBlocks + VowBlock(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = "Quiet Hours",
                                        isEnabled = false,
                                        startHour = 22,
                                        startMin = 0,
                                        endHour = 7,
                                        endMin = 0,
                                        targetApps = emptySet(),
                                        specificDomain = ""
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ ADD NEW BLOCK SLOT",
                            color = MonospaceText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Update Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(MonospaceText)
                        .clickable { onUpdate(currentBlocks) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "UPDATE",
                        color = LightGraphiteBg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Dialog layout configured to match the "CONFIG: BINDING VOW" specifications with DD:HH:MM:SS format input fields.
 */
@Composable
fun BindingVowConfigDialog(
    isLockedState: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    initialDays: String = "00",
    initialHours: String = "00",
    initialMinutes: String = "00",
    initialSeconds: String = "00"
) {
    var vowDays by remember { mutableStateOf(initialDays) }
    var vowHours by remember { mutableStateOf(initialHours) }
    var vowMinutes by remember { mutableStateOf(initialMinutes) }
    var vowSeconds by remember { mutableStateOf(initialSeconds) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightGraphiteBg)
                .border(1.dp, OutlineAccent)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIG: BINDING VOW",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "X",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OutlineAccent)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // DD:HH:MM:SS Label
                Text(
                    text = "DURATION (DD:HH:MM:SS) - MAX 99:99:99:99",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VowTimeInput(value = vowDays, onValueChange = { vowDays = it }, label = "DD")
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp))
                    VowTimeInput(value = vowHours, onValueChange = { vowHours = it }, label = "HH")
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp))
                    VowTimeInput(value = vowMinutes, onValueChange = { vowMinutes = it }, label = "MM")
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 14.dp))
                    VowTimeInput(value = vowSeconds, onValueChange = { vowSeconds = it }, label = "SS")
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Calculations
                val d = vowDays.toLongOrNull() ?: 0L
                val h = vowHours.toLongOrNull() ?: 0L
                val m = vowMinutes.toLongOrNull() ?: 0L
                val s = vowSeconds.toLongOrNull() ?: 0L
                val totalSeconds = d * 86400L + h * 3600L + m * 60L + s
                val isValid = totalSeconds > 0L

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(if (isValid) MonospaceText else OutlineAccent)
                        .clickable(enabled = isValid) {
                            onConfirm(totalSeconds)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLockedState) "ADD BINDING TIME" else "INFLICT BINDING VOW",
                        color = if (isValid) LightGraphiteBg else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Text box helper to accept exactly two digits for duration fields.
 */
@Composable
fun VowTimeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(54.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= 2) {
                    onValueChange(filtered)
                }
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = MonospaceText,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(MonospaceText),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .border(1.dp, OutlineAccent)
                        .background(MutedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isEmpty()) {
                        Text("00", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                    }
                    innerTextField()
                }
            }
        )
        Text(
            text = label,
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Custom In-App Toast Banner Component.
 */
@Composable
fun InAppToastBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .background(MutedSurface)
            .sharpBorder(1.dp, OutlineAccent)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "[X]",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(start = 8.dp)
            )
        }
    }
}

/**
 * Utility to check if a specific AccessibilityService is enabled in the system.
 */
fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val expectedComponentName = ComponentName(context, service)
    val enabledServicesSetting = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun FocusHistoryWorkspace(
    onBack: () -> Unit,
    db: com.avow.app.data.history.VowDatabase,
    modifier: Modifier = Modifier
) {
    val sessions by db.vowSessionDao().getAllSessions().collectAsState(initial = emptyList())
    
    val totalSessions = sessions.size
    val totalDurationSec = sessions.sumOf { it.durationSeconds }
    val totalIntrusions = sessions.sumOf { it.intrusionsBlocked }
    val avgZenScore = if (sessions.isNotEmpty()) sessions.map { it.zenScore }.average() else 0.0
    val last7Sessions = sessions.take(7).reversed()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightGraphiteBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // Stark Header with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FOCUS INSIGHTS",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            SharpBorderButton(
                text = "{ BACK }",
                onClick = onBack,
                modifier = Modifier.width(80.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OutlineAccent)
        )
        
        // Scrollable content
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 20.dp)
        ) {
            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatBox(label = "TOTAL SESSIONS", value = totalSessions.toString(), modifier = Modifier.weight(1f))
                StatBox(label = "TOTAL FOCUS TIME", value = formatDurationForHistory(totalDurationSec), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatBox(label = "BLOCKED INTRUSIONS", value = totalIntrusions.toString(), modifier = Modifier.weight(1f))
                StatBox(label = "AVG ZEN SCORE", value = "${avgZenScore.roundToInt()}%", modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Zen Score Trend title
            Text(
                text = "ZEN SCORE TREND (LAST 7 SESSIONS)",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Canvas Graph
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .sharpBorder(1.dp, OutlineAccent)
                        .background(LightGraphiteBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO SESSIONS RECORDED",
                        color = SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            } else {
                val scores = last7Sessions.map { it.zenScore }
                val pointCount = scores.size
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(LightGraphiteBg)
                        .sharpBorder(1.dp, OutlineAccent)
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    val gridLines = listOf(0f, 0.5f, 1f)
                    for (ratio in gridLines) {
                        val y = ratio * height
                        drawLine(
                            color = OutlineAccent.copy(alpha = 0.3f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }
                    
                    val points = mutableListOf<Offset>()
                    val xStep = if (pointCount > 1) width / (pointCount - 1) else width
                    
                    for (i in scores.indices) {
                        val score = scores[i]
                        val y = height - (score / 100f) * height
                        val x = if (pointCount > 1) i * xStep else width / 2f
                        points.add(Offset(x, y))
                    }
                    
                    val path = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                    }
                    
                    drawPath(
                        path = path,
                        color = MonospaceText,
                        style = Stroke(width = 3f)
                    )
                    
                    for (point in points) {
                        drawCircle(
                            color = MonospaceText,
                            radius = 5f,
                            center = point
                        )
                        drawCircle(
                            color = LightGraphiteBg,
                            radius = 2f,
                            center = point
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Session logs title
            Text(
                text = "SESSION HISTORY LOGS",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Scrollable list of logs
            if (sessions.isEmpty()) {
                Text(
                    text = "No history log items found.",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (session in sessions) {
                        LogItem(session = session)
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .sharpBorder(1.dp, OutlineAccent)
            .background(LightGraphiteBg)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = label,
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun LogItem(session: com.avow.app.data.history.VowSession) {
    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(session.startTimeMillis))
        
    val focusStr = formatDurationForHistory(session.durationSeconds)
    val allowedStr = formatDurationForHistory(session.allowedScreenTimeMs / 1000)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .sharpBorder(1.dp, OutlineAccent)
            .background(LightGraphiteBg)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    color = MonospaceText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ZEN SCORE: ${session.zenScore}%",
                    color = if (session.zenScore >= 80) MonospaceText else if (session.zenScore >= 50) SubtextGrey else LockedRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DURATION: $focusStr",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "BLOCKED: ${session.intrusionsBlocked}",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "ALLOWED: $allowedStr",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

fun formatDurationForHistory(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) "${hrs}h ${mins}m" else if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
