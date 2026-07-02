package com.avow.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.RectangleShape
import com.avow.app.receiver.DeviceAdmin
import com.avow.app.ui.theme.*
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.util.Log
import com.avow.app.service.BlockerService
import com.avow.app.data.VowDataStore
import com.avow.app.data.history.VowSession
import com.avow.app.model.VowBlock
import kotlin.random.Random
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Slightly darker gray color for activated panels
val DarkerSurfaceColor = Color(0xFF5A5A5A)

fun formatTimeAmPm(hour: Int, minute: Int): String {
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    val amPm = if (hour < 12) "AM" else "PM"
    return String.format("%02d:%02d %s", displayHour, minute, amPm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    triggerIntrusion: Boolean = false,
    onIntrusionHandled: () -> Unit = {},
    preload15mVow: Boolean = false,
    onPreloadHandled: () -> Unit = {},
    isTemporaryLockout: Boolean = false,
    onTemporaryLockoutHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Dialog states (kept in Composable as they are pure UI states)
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    var showUsageLimitsDialog by remember { mutableStateOf(false) }
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
            viewModel.setScreenState(ScreenState.TEMPORARY_LOCKOUT)
            onTemporaryLockoutHandled()
        }
    }

    LaunchedEffect(triggerIntrusion) {
        if (triggerIntrusion) {
            viewModel.setScreenState(ScreenState.INTRUSION_INTERCEPT)
            onIntrusionHandled()
        }
    }

    val isMainScreen = uiState.currentState == ScreenState.UNLOCKED_VAULT || uiState.currentState == ScreenState.LOCKED_VAULT
    BackHandler(enabled = !isMainScreen) {
        when (uiState.currentState) {
            ScreenState.CONFIGURATION -> {
                viewModel.setScreenState(if (uiState.isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT)
            }
            ScreenState.FOCUS_HISTORY -> {
                viewModel.setScreenState(if (uiState.isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT)
            }
            ScreenState.INTRUSION_INTERCEPT -> {
                viewModel.setScreenState(uiState.previousState)
            }
            else -> {
                // Do nothing for other states (e.g. TEMPORARY_LOCKOUT) to avoid back-bypass
            }
        }
    }

    LaunchedEffect(uiState.inAppToastMessage) {
        if (uiState.inAppToastMessage != null) {
            delay(4000L)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightGraphiteBg)
    ) {
        when (uiState.currentState) {
            ScreenState.ONBOARDING -> {
                OnboardingFlow(viewModel = viewModel)
            }
            ScreenState.UNLOCKED_VAULT, ScreenState.LOCKED_VAULT -> {
                VaultDashboard(
                    statusText = if (uiState.isVowActive) "LOCKED" else "UNLOCKED",
                    statusColor = if (uiState.isVowActive) LockedRed else MonospaceText,
                    days = uiState.days,
                    hours = uiState.hours,
                    minutes = uiState.minutes,
                    seconds = uiState.seconds,
                    panelThreeTitle = if (uiState.isVowActive) "ADD BINDING TIME" else "INFLICT BINDING VOW",
                    panelThreeSubtitle = "RESTRICTION_2",
                    onQuietHoursClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showQuietHoursDialog = true 
                    },
                    onSetUsageLimitsClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showUsageLimitsDialog = true 
                    },
                    onPanelThreeClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showBindingVowDialog = true 
                    },
                    onSettingsClick = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.setScreenState(ScreenState.CONFIGURATION) 
                    },
                    onDeactivateClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.requestDeactivation()
                    },
                    onViewFocusInsightsClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.setScreenState(ScreenState.FOCUS_HISTORY)
                    },
                    quietHoursActivated = uiState.vowBlocks.any { it.isEnabled },
                    usageLimitsActivated = uiState.usageLimitsUpdated,
                    bindingVowActivated = uiState.isVowActive,
                    deactivationRequestTime = uiState.deactivationRequestTime,
                    tickTrigger = uiState.seconds, // use seconds as tick trigger for UI updates
                    isActiveVowMode = uiState.isActiveVowMode,
                    onVowModeChange = { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.updateState { copy(isActiveVowMode = it) }
                        viewModel.saveConfigToDataStore()
                    }
                )
            }
            ScreenState.CONFIGURATION -> {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        viewModel.showToast("Notification permission denied. Doomscroll warning will not appear.")
                    }
                }
                ConfigurationWorkspace(
                    secureFolderEnabled = uiState.secureFolderEnabled,
                    onSecureFolderToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        viewModel.updateState { copy(secureFolderEnabled = !secureFolderEnabled) }
                        viewModel.saveConfigToDataStore()
                    },
                    privateSpaceEnabled = uiState.privateSpaceEnabled,
                    onPrivateSpaceToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(privateSpaceEnabled = !privateSpaceEnabled) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    lockUninstall = uiState.lockUninstall,
                    onLockUninstallToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(lockUninstall = !lockUninstall) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    disallowDataWipe = uiState.disallowDataWipe,
                    onDisallowDataWipeToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(disallowDataWipe = !disallowDataWipe) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    disableSafeBoot = uiState.disableSafeBoot,
                    onDisableSafeBootToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(disableSafeBoot = !disableSafeBoot) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    blockPlayStore = uiState.blockPlayStore,
                    onBlockPlayStoreToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(blockPlayStore = !blockPlayStore) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    dynamicReinstall = uiState.dynamicReinstall,
                    onDynamicReinstallToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(dynamicReinstall = !dynamicReinstall) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    deactivateUsbDebugging = uiState.deactivateUsbDebugging,
                    onDeactivateUsbDebuggingToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) viewModel.showToast("Error: Restrictions cannot be modified while locked.")
                        else {
                            viewModel.updateState { copy(deactivateUsbDebugging = !deactivateUsbDebugging) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    banDomainSet = uiState.banDomainSet,
                    onDomainAdd = { domain ->
                        if (!uiState.banDomainSet.contains(domain)) {
                            viewModel.updateState { copy(banDomainSet = banDomainSet + domain) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    onDomainRemove = { domain ->
                        if (uiState.isVowActive) {
                            viewModel.showToast("Error: Cannot remove domains while locked.")
                        } else {
                            viewModel.updateState { copy(banDomainSet = banDomainSet - domain) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    onBack = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.setScreenState(if (uiState.isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT)
                    },
                    isLocked = uiState.isVowActive,
                    isDeviceOwner = uiState.isDeviceOwner,
                    doomscrollShieldEnabled = uiState.doomscrollShieldEnabled,
                    onDoomscrollShieldToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        viewModel.updateState { copy(doomscrollShieldEnabled = !doomscrollShieldEnabled) }
                        viewModel.saveConfigToDataStore()
                        if (!uiState.doomscrollShieldEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    doomscrollAllTime = uiState.doomscrollAllTime,
                    onDoomscrollAllTimeToggle = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (uiState.isVowActive) {
                            viewModel.showToast("Error: Doomscroll Shield settings cannot be modified while locked.")
                        } else {
                            viewModel.updateState { copy(doomscrollAllTime = !doomscrollAllTime) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    doomscrollStartHour = uiState.doomscrollStartHour,
                    doomscrollStartMin = uiState.doomscrollStartMin,
                    doomscrollEndHour = uiState.doomscrollEndHour,
                    doomscrollEndMin = uiState.doomscrollEndMin,
                    onDoomscrollTimeUpdate = { sh, sm, eh, em ->
                        if (uiState.isVowActive) {
                            viewModel.showToast("Error: Doomscroll Shield settings cannot be modified while locked.")
                        } else {
                            viewModel.updateState {
                                copy(
                                    doomscrollStartHour = sh,
                                    doomscrollStartMin = sm,
                                    doomscrollEndHour = eh,
                                    doomscrollEndMin = em
                                )
                            }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    doomscrollTargetApps = uiState.doomscrollTargetAppSet,
                    onDoomscrollTargetAppsUpdate = { apps ->
                        if (uiState.isVowActive) {
                            val removedApps = uiState.doomscrollTargetAppSet - apps
                            if (removedApps.isNotEmpty()) {
                                viewModel.showToast("Error: Target applications cannot be removed while locked.")
                            } else {
                                viewModel.updateState { copy(doomscrollTargetAppSet = apps, frozenDoomscrollTargetAppSet = apps) }
                                viewModel.saveConfigToDataStore()
                            }
                        } else {
                            viewModel.updateState { copy(doomscrollTargetAppSet = apps) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    doomscrollCooldownMinutes = uiState.doomscrollCooldownMinutes,
                    onDoomscrollCooldownMinutesUpdate = { cooldown ->
                        if (uiState.isVowActive) {
                            if (cooldown < uiState.frozenDoomscrollCooldownMinutes) {
                                viewModel.showToast("Error: Cooldown minutes can only be made longer (stricter) while locked.")
                            } else {
                                viewModel.updateState {
                                    copy(
                                        doomscrollCooldownMinutes = cooldown,
                                        frozenDoomscrollCooldownMinutes = cooldown
                                    )
                                }
                                viewModel.saveConfigToDataStore()
                            }
                        } else {
                            viewModel.updateState { copy(doomscrollCooldownMinutes = cooldown) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    doomscrollAllowanceMinutes = uiState.doomscrollAllowanceMinutes,
                    doomscrollAccumulatedMs = uiState.doomscrollAccumulatedMs,
                    onDoomscrollAllowanceMinutesUpdate = { allowance ->
                        if (uiState.isVowActive) {
                            if (allowance > uiState.frozenDoomscrollAllowanceMinutes) {
                                viewModel.showToast("Error: Allowance minutes can only be made shorter (stricter) while locked.")
                            } else {
                                viewModel.updateState {
                                    copy(
                                        doomscrollAllowanceMinutes = allowance,
                                        frozenDoomscrollAllowanceMinutes = allowance
                                    )
                                }
                                viewModel.saveConfigToDataStore()
                            }
                        } else {
                            viewModel.updateState { copy(doomscrollAllowanceMinutes = allowance) }
                            viewModel.saveConfigToDataStore()
                        }
                    },
                    installedApps = uiState.installedApps,
                    deactivationRequestTime = uiState.deactivationRequestTime,
                    onDeactivateClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.requestDeactivation()
                    },
                    tickTrigger = uiState.seconds
                )
            }
            ScreenState.INTRUSION_INTERCEPT -> {
                IntrusionInterceptOverlay(
                    onExit = {
                        viewModel.setScreenState(uiState.previousState)
                    }
                )
            }
            ScreenState.TEMPORARY_LOCKOUT -> {
                TemporaryLockoutOverlay()
            }
            ScreenState.FOCUS_HISTORY -> {
                FocusHistoryWorkspace(
                    onBack = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.setScreenState(if (uiState.isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT)
                    },
                    db = com.avow.app.data.history.VowDatabase.getDatabase(context)
                )
            }
        }

        if (showQuietHoursDialog) {
            QuietHoursConfigDialog(
                vowBlocks = uiState.vowBlocks,
                installedApps = uiState.installedApps,
                isLocked = uiState.isVowActive,
                onDismiss = { showQuietHoursDialog = false },
                showToast = { viewModel.showToast(it) },
                onUpdate = { newBlocks ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (uiState.isVowActive) {
                        val isContainmentValid = com.avow.app.util.VowValidator.validateContainment(uiState.frozenVowBlocks, newBlocks)
                        if (!isContainmentValid) {
                            viewModel.showToast("Error: Scheduled blocks cannot be shortened, shifted or deleted when locked.")
                        } else {
                            viewModel.updateState { copy(vowBlocks = newBlocks, frozenVowBlocks = newBlocks) }
                            viewModel.saveConfigToDataStore()
                            showQuietHoursDialog = false
                            viewModel.showToast("Scheduled blocks updated (stricter).")
                        }
                    } else {
                        viewModel.updateState { copy(vowBlocks = newBlocks) }
                        viewModel.saveConfigToDataStore()
                        showQuietHoursDialog = false
                        viewModel.showToast("Scheduled blocks updated.")
                    }
                }
            )
        }

        if (showUsageLimitsDialog) {
            UsageLimitsConfigDialog(
                enabled = uiState.usageLimitsUpdated,
                allowedValue = uiState.allowedValue,
                allowedUnit = uiState.allowedUnit,
                selectedInterval = uiState.selectedInterval,
                targetAppSet = uiState.targetAppSet,
                specificDomain = uiState.specificDomain,
                installedApps = uiState.installedApps,
                isLocked = uiState.isVowActive,
                onDismiss = { showUsageLimitsDialog = false },
                onUpdate = { newEnabled, newAllowedValue, newAllowedUnit, newSelectedInterval, newTargetAppSet, newSpecificDomain, newIsCollectiveLimit ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    val minutesVal = newAllowedValue.toFloatOrNull()
                    if (minutesVal == null || minutesVal <= 0f) {
                        viewModel.showToast("Error: Invalid allowed value.")
                        return@UsageLimitsConfigDialog
                    }
                    
                    if (uiState.isVowActive) {
                        if (uiState.usageLimitsUpdated && !newEnabled) {
                            viewModel.showToast("Error: Usage limits cannot be disabled while locked.")
                            return@UsageLimitsConfigDialog
                        }
                        val removedApps = uiState.targetAppSet - newTargetAppSet
                        if (removedApps.isNotEmpty()) {
                            viewModel.showToast("Error: Target applications cannot be removed while locked.")
                            return@UsageLimitsConfigDialog
                        }
                        val newRate = com.avow.app.util.VowValidator.getUsageLimitRate(newAllowedValue, newAllowedUnit, newSelectedInterval)
                        val oldRate = com.avow.app.util.VowValidator.getUsageLimitRate(uiState.frozenAllowedValue, uiState.frozenAllowedUnit, uiState.frozenInterval)
                        if (newRate > oldRate) {
                            viewModel.showToast("Error: Usage limits can only be made stricter (fewer minutes).")
                            return@UsageLimitsConfigDialog
                        }
                        
                        viewModel.updateState { 
                            copy(
                                usageLimitsUpdated = newEnabled, allowedValue = newAllowedValue, allowedUnit = newAllowedUnit,
                                selectedInterval = newSelectedInterval, targetAppSet = newTargetAppSet, specificDomain = newSpecificDomain,
                                isCollectiveLimit = newIsCollectiveLimit, frozenAllowedValue = newAllowedValue,
                                frozenAllowedUnit = newAllowedUnit, frozenInterval = newSelectedInterval
                            )
                        }
                        viewModel.saveConfigToDataStore()
                        showUsageLimitsDialog = false
                        viewModel.showToast("Usage limits updated (stricter).")
                    } else {
                        viewModel.updateState { 
                            copy(
                                usageLimitsUpdated = newEnabled, allowedValue = newAllowedValue, allowedUnit = newAllowedUnit,
                                selectedInterval = newSelectedInterval, targetAppSet = newTargetAppSet, specificDomain = newSpecificDomain,
                                isCollectiveLimit = newIsCollectiveLimit
                            )
                        }
                        viewModel.saveConfigToDataStore()
                        showUsageLimitsDialog = false
                        viewModel.showToast("Usage limits updated.")
                    }
                },
                isCollectiveLimit = uiState.isCollectiveLimit
            )
        }

        if (showBindingVowDialog) {
            BindingVowConfigDialog(
                isLockedState = uiState.isVowActive,
                initialDays = initialDaysForDialog,
                initialHours = initialHoursForDialog,
                initialMinutes = initialMinutesForDialog,
                initialSeconds = initialSecondsForDialog,
                onDismiss = {
                    showBindingVowDialog = false
                    initialDaysForDialog = "00"; initialHoursForDialog = "00"; initialMinutesForDialog = "00"; initialSecondsForDialog = "00"
                },
                onConfirm = { additionalSeconds ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    val err = viewModel.addBindingTime(additionalSeconds)
                    if (err != null) {
                        viewModel.showToast(err)
                    }
                    showBindingVowDialog = false
                    initialDaysForDialog = "00"; initialHoursForDialog = "00"; initialMinutesForDialog = "00"; initialSecondsForDialog = "00"
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.inAppToastMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            uiState.inAppToastMessage?.let { msg ->
                InAppToastBanner(
                    message = msg,
                    onDismiss = { viewModel.clearToast() }
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
    color: Color = OutlineAccent,
    mouthOpen: Boolean = false
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

        // 5. Open-mouth grin (":D") — a straight top lip closing the smile arc so the mouth
        //    reads as open while the mascot is "speaking".
        if (mouthOpen) {
            drawLine(
                color = color,
                start = Offset(60f * scale, 130f * scale),
                end = Offset(140f * scale, 130f * scale),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// Typewriter pacing (ms). Punctuation gets a longer pause to mimic speech cadence.
private const val TYPE_CHAR_DELAY_MS = 34L
private const val TYPE_SENTENCE_PAUSE_MS = 260L
private const val TYPE_CLAUSE_PAUSE_MS = 150L

/**
 * Zen-score trend for the mascot: the most recent focus session's zen score minus the average of
 * all prior sessions, in points. Returns null when fewer than two sessions have been recorded.
 * [sessions] is expected newest-first (as returned by the DAO).
 */
fun computeMascotZenDelta(sessions: List<VowSession>): Int? {
    if (sessions.size < 2) return null
    val latest = sessions.first().zenScore
    val priorAvg = sessions.drop(1).map { it.zenScore }.average()
    return (latest - priorAvg).roundToInt()
}

/**
 * Converts a marked mascot message (see [MascotMessages]) into an [AnnotatedString], applying
 * sage for [[s]] spans and red for [[r]] spans and stripping the markers.
 */
fun parseMascotMessage(raw: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith(MascotMessages.SAGE_OPEN, i) -> {
                val end = raw.indexOf(MascotMessages.SAGE_CLOSE, i)
                val contentStart = i + MascotMessages.SAGE_OPEN.length
                if (end >= 0) {
                    withStyle(SpanStyle(color = SageGreen)) { append(raw.substring(contentStart, end)) }
                    i = end + MascotMessages.SAGE_CLOSE.length
                } else {
                    append(raw.substring(contentStart)); i = raw.length
                }
            }
            raw.startsWith(MascotMessages.RED_OPEN, i) -> {
                val end = raw.indexOf(MascotMessages.RED_CLOSE, i)
                val contentStart = i + MascotMessages.RED_OPEN.length
                if (end >= 0) {
                    withStyle(SpanStyle(color = LockedRed)) { append(raw.substring(contentStart, end)) }
                    i = end + MascotMessages.RED_CLOSE.length
                } else {
                    append(raw.substring(contentStart)); i = raw.length
                }
            }
            else -> {
                append(raw[i]); i++
            }
        }
    }
}

/**
 * Floating speech-bubble tooltip that points to the mascot logo. Types [rawMessage] out one
 * character at a time (pausing at punctuation) and reports back via [onSpeakingChange] so the
 * mascot can animate its mouth. Re-typing is keyed on [trigger].
 */
@Composable
fun MascotSpeechBubble(
    rawMessage: String,
    trigger: Int,
    onSpeakingChange: (Boolean) -> Unit,
    onTypingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(rawMessage) { parseMascotMessage(rawMessage) }
    var visibleChars by remember(trigger) { mutableStateOf(0) }

    LaunchedEffect(trigger) {
        visibleChars = 0
        onSpeakingChange(true)
        val text = annotated.text
        for (idx in 1..text.length) {
            visibleChars = idx
            val delayMs = when (text[idx - 1]) {
                '.', '!', '?', '—' -> TYPE_SENTENCE_PAUSE_MS
                ',', ';', ':' -> TYPE_CLAUSE_PAUSE_MS
                else -> TYPE_CHAR_DELAY_MS
            }
            delay(delayMs)
        }
        onSpeakingChange(false)
        onTypingComplete()
    }

    Box(
        modifier = modifier
            .drawBehind {
                // Tail: a triangle on the left edge pointing toward the mascot.
                val tailSize = 7.dp.toPx()
                val tailHalf = 6.dp.toPx()
                val cy = 16.dp.toPx()
                val border = 1.dp.toPx()
                val fill = Path().apply {
                    moveTo(0f, cy - tailHalf)
                    lineTo(-tailSize, cy)
                    lineTo(0f, cy + tailHalf)
                    close()
                }
                drawPath(fill, MutedSurface)
                drawLine(OutlineAccent, Offset(0f, cy - tailHalf), Offset(-tailSize, cy), border)
                drawLine(OutlineAccent, Offset(-tailSize, cy), Offset(0f, cy + tailHalf), border)
            }
            .background(MutedSurface)
            .sharpBorder(1.dp, OutlineAccent)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column {
            Text(
                text = "// aVow",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            Text(
                text = annotated.subSequence(0, visibleChars.coerceIn(0, annotated.length)),
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
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
    tickTrigger: Int = 0,
    isActiveVowMode: Boolean = false,
    onVowModeChange: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top status bar layout (Stark header) with mascot + speech bubble
        var bubbleVisible by remember { mutableStateOf(false) }
        var bubbleMessage by remember { mutableStateOf("") }
        var bubbleTrigger by remember { mutableStateOf(0) }
        var speaking by remember { mutableStateOf(false) }
        var mouthOpen by remember { mutableStateOf(false) }

        // Zen-score trend from the recorded focus sessions (same SQLite source as Focus Insights).
        val dashContext = LocalContext.current
        val sessions by remember(dashContext) {
            com.avow.app.data.history.VowDatabase.getDatabase(dashContext)
                .vowSessionDao().getAllSessions()
        }.collectAsState(initial = emptyList())
        val zenDelta = remember(sessions) { computeMascotZenDelta(sessions) }

        fun openBubble() {
            bubbleMessage = MascotMessages.generateLine(
                isLocked = bindingVowActivated,
                days = days,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                zenDelta = zenDelta,
                random = Random.Default
            )
            bubbleVisible = true
            bubbleTrigger++
        }

        // Greet the user when the dashboard is first shown, and re-greet with the correct copy
        // whenever the lock state resolves/changes (the vow state loads asynchronously, so the
        // first composition can briefly report UNLOCKED before the datastore value arrives).
        LaunchedEffect(bindingVowActivated) { openBubble() }

        // Flap the mouth while the bubble is typing.
        LaunchedEffect(speaking) {
            if (speaking) {
                while (true) {
                    mouthOpen = !mouthOpen
                    delay(150L)
                }
            } else {
                mouthOpen = false
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmileyFaceOutline(
                modifier = Modifier
                    .size(39.dp)
                    .clickable {
                        if (bubbleVisible) bubbleVisible = false else openBubble()
                    },
                mouthOpen = mouthOpen
            )
            Spacer(modifier = Modifier.width(14.dp))
            AnimatedVisibility(
                visible = bubbleVisible,
                enter = fadeIn() + scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    transformOrigin = TransformOrigin(0f, 0.25f)
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                MascotSpeechBubble(
                    rawMessage = bubbleMessage,
                    trigger = bubbleTrigger,
                    onSpeakingChange = { speaking = it },
                    onTypingComplete = {}
                )
            }
        }

        // Auto-dismiss ~4s after the message finishes typing.
        LaunchedEffect(bubbleTrigger, speaking) {
            if (bubbleVisible && !speaking && bubbleMessage.isNotEmpty()) {
                delay(4000L)
                bubbleVisible = false
            }
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

        Spacer(modifier = Modifier.height(20.dp))

        // Vow Mode Segmented Control
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(40.dp)
        ) {
            SegmentedButton(
                selected = !isActiveVowMode,
                onClick = { onVowModeChange(false) },
                shape = RectangleShape,
                enabled = !bindingVowActivated,
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MonospaceText,
                    activeContentColor = Color(0xFF1C1C1C),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MonospaceText,
                    activeBorderColor = OutlineAccent,
                    inactiveBorderColor = OutlineAccent,
                    disabledActiveContainerColor = MonospaceText,
                    disabledActiveContentColor = Color(0xFF1C1C1C),
                    disabledInactiveContainerColor = Color.Transparent,
                    disabledInactiveContentColor = SubtextGrey,
                    disabledActiveBorderColor = OutlineAccent,
                    disabledInactiveBorderColor = OutlineAccent
                )
            ) {
                Text(
                    text = "PASSIVE VOW",
                    color = if (bindingVowActivated) {
                        if (!isActiveVowMode) Color(0xFF1C1C1C) else SubtextGrey
                    } else {
                        if (!isActiveVowMode) Color(0xFF1C1C1C) else MonospaceText
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SegmentedButton(
                selected = isActiveVowMode,
                onClick = { onVowModeChange(true) },
                shape = RectangleShape,
                enabled = !bindingVowActivated,
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MonospaceText,
                    activeContentColor = Color(0xFF1C1C1C),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = MonospaceText,
                    activeBorderColor = OutlineAccent,
                    inactiveBorderColor = OutlineAccent,
                    disabledActiveContainerColor = MonospaceText,
                    disabledActiveContentColor = Color(0xFF1C1C1C),
                    disabledInactiveContainerColor = Color.Transparent,
                    disabledInactiveContentColor = SubtextGrey,
                    disabledActiveBorderColor = OutlineAccent,
                    disabledInactiveBorderColor = OutlineAccent
                )
            ) {
                Text(
                    text = "ACTIVE VOW",
                    color = if (bindingVowActivated) {
                        if (isActiveVowMode) Color(0xFF1C1C1C) else SubtextGrey
                    } else {
                        if (isActiveVowMode) Color(0xFF1C1C1C) else MonospaceText
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
                title = "SCHEDULED BLOCKS",
                subtitle = "",
                isActivated = quietHoursActivated,
                onClick = onQuietHoursClick
            )
            DashboardPanelButton(
                title = "USAGE LIMITS",
                subtitle = "",
                isActivated = usageLimitsActivated,
                onClick = onSetUsageLimitsClick
            )
            DashboardPanelButton(
                title = panelThreeTitle,
                subtitle = "",
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
            if (subtitle.isNotEmpty()) {
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
    val onToggle: () -> Unit,
    val requiresDeviceOwner: Boolean = false
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
    isLocked: Boolean,
    isDeviceOwner: Boolean,
    doomscrollShieldEnabled: Boolean,
    onDoomscrollShieldToggle: () -> Unit,
    doomscrollAllTime: Boolean,
    onDoomscrollAllTimeToggle: () -> Unit,
    doomscrollStartHour: Int,
    doomscrollStartMin: Int,
    doomscrollEndHour: Int,
    doomscrollEndMin: Int,
    onDoomscrollTimeUpdate: (Int, Int, Int, Int) -> Unit,
    doomscrollTargetApps: Set<String>,
    onDoomscrollTargetAppsUpdate: (Set<String>) -> Unit,
    doomscrollCooldownMinutes: Int,
    onDoomscrollCooldownMinutesUpdate: (Int) -> Unit,
    doomscrollAllowanceMinutes: Int,
    doomscrollAccumulatedMs: Long = 0L,
    temporaryLockoutEndTime: Long = 0L,
    onDoomscrollAllowanceMinutesUpdate: (Int) -> Unit,
    installedApps: List<Pair<String, String>>,
    deactivationRequestTime: Long = 0L,
    onDeactivateClick: () -> Unit = {},
    tickTrigger: Int = 0
) {
    val restrictionsList = listOf(
        RestrictionItem("SAMSUNG KNOX SECURE FOLDER", "com.samsung.knox.securefolder", secureFolderEnabled, onSecureFolderToggle, requiresDeviceOwner = true),
        RestrictionItem("ANDROID 15 PRIVATE SPACE", "com.google.android.apps.privatespace", privateSpaceEnabled, onPrivateSpaceToggle, requiresDeviceOwner = true),
        RestrictionItem("LOCK UNINSTALLATION", "setUninstallBlocked", lockUninstall, onLockUninstallToggle, requiresDeviceOwner = true),
        RestrictionItem("DISALLOW DATA WIPE", "DISALLOW_APPS_CONTROL", disallowDataWipe, onDisallowDataWipeToggle, requiresDeviceOwner = true),
        RestrictionItem("DISABLE SAFE BOOT", "DISALLOW_SAFE_BOOT", disableSafeBoot, onDisableSafeBootToggle, requiresDeviceOwner = true),
        RestrictionItem("BLOCK PLAY STORE", "com.android.vending", blockPlayStore, onBlockPlayStoreToggle, requiresDeviceOwner = true),
        RestrictionItem("DYNAMIC REINSTALL GUARD", "ACTION_PACKAGE_ADDED", dynamicReinstall, onDynamicReinstallToggle, requiresDeviceOwner = false),
        RestrictionItem("DEACTIVATE USB DEBUGGING", "DISALLOW_DEBUGGING_FEATURES", deactivateUsbDebugging, onDeactivateUsbDebuggingToggle, requiresDeviceOwner = true)
    )

    val scrollState = rememberScrollState()
    var doomscrollTimePickerTarget by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
    ) {
        // 1. Workspace Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
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
                text = "BAN DOMAIN SET >",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            DomainSetEditor(
                domains = banDomainSet.toList(),
                onAdd = onDomainAdd,
                onRemove = onDomainRemove,
                canAdd = true,
                canRemove = !isLocked
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. List of ENFORCEMENT RESTRICTIONS & Target profiles
        Text(
            text = "ENFORCEMENT RESTRICTIONS",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .sharpBorder(1.dp, OutlineAccent)
                .background(MutedSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            restrictionsList.forEach { item ->
                val isItemEnabled = if (item.requiresDeviceOwner) isDeviceOwner else true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isItemEnabled) { item.onToggle() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "[x]" else "[ ]",
                        color = if (!isItemEnabled) SubtextGrey.copy(alpha = 0.5f) else if (item.isChecked) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = item.title,
                            color = if (!isItemEnabled) SubtextGrey.copy(alpha = 0.5f) else MonospaceText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        // --- DOOMSCROLL SHIELD SECTION ---
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "DOOMSCROLL BLOCK CONFIG",
            color = SubtextGrey,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .sharpBorder(1.dp, OutlineAccent)
                .background(MutedSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enable Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDoomscrollShieldToggle() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ENABLE",
                    color = if (doomscrollShieldEnabled) MonospaceText else SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = doomscrollShieldEnabled,
                    onCheckedChange = { onDoomscrollShieldToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LightGraphiteBg,
                        checkedTrackColor = MonospaceText,
                        checkedBorderColor = OutlineAccent,
                        uncheckedThumbColor = OutlineAccent,
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = OutlineAccent
                    )
                )
            }

            if (doomscrollShieldEnabled) {
                // All Time Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDoomscrollAllTimeToggle() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RESTRICT ALL DAY",
                        color = if (doomscrollAllTime) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = doomscrollAllTime,
                        onCheckedChange = { onDoomscrollAllTimeToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LightGraphiteBg,
                            checkedTrackColor = MonospaceText,
                            checkedBorderColor = OutlineAccent,
                            uncheckedThumbColor = OutlineAccent,
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = OutlineAccent
                        )
                    )
                }

                if (!doomscrollAllTime) {
                    // Time Interval Selection (Start / End times)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start Time Display Box
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("START TIME: ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(30.dp)
                                    .border(1.dp, OutlineAccent)
                                    .background(MutedSurface)
                                    .clickable(enabled = !isLocked) {
                                        doomscrollTimePickerTarget = "START"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatTimeAmPm(doomscrollStartHour, doomscrollStartMin),
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // End Time Display Box
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("END TIME:   ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(30.dp)
                                    .border(1.dp, OutlineAccent)
                                    .background(MutedSurface)
                                    .clickable(enabled = !isLocked) {
                                        doomscrollTimePickerTarget = "END"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatTimeAmPm(doomscrollEndHour, doomscrollEndMin),
                                    color = if (!isLocked) MonospaceText else SubtextGrey,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Cooldown Minutes Slider (5-minute stops, max 60 minutes)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "LOCKOUT COOLDOWN: $doomscrollCooldownMinutes Min",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = doomscrollCooldownMinutes.toFloat().coerceIn(5f, 60f),
                        onValueChange = { newValue ->
                            val rounded = (newValue.roundToInt() / 5) * 5
                            onDoomscrollCooldownMinutesUpdate(rounded.coerceIn(5, 60))
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = MonospaceText,
                            activeTrackColor = MonospaceText,
                            inactiveTrackColor = OutlineAccent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Allowance Minutes Slider (5-minute stops, max 60 minutes)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SCROLL ALLOWANCE: $doomscrollAllowanceMinutes Min",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = doomscrollAllowanceMinutes.toFloat().coerceIn(5f, 60f),
                        onValueChange = { newValue ->
                            val rounded = (newValue.roundToInt() / 5) * 5
                            onDoomscrollAllowanceMinutesUpdate(rounded.coerceIn(5, 60))
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = MonospaceText,
                            activeTrackColor = MonospaceText,
                            inactiveTrackColor = OutlineAccent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    val currentUptime = SystemClock.elapsedRealtime()
                    if (temporaryLockoutEndTime > currentUptime) {
                        val remainingSec = (temporaryLockoutEndTime - currentUptime) / 1000
                        Text(
                            text = "LOCKOUT: ${remainingSec / 60}m ${remainingSec % 60}s",
                            color = Color(0xFFE57373),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else if (doomscrollAccumulatedMs > 0L) {
                        Text(
                            text = "LIVE TICKER: ${doomscrollAccumulatedMs / 1000}s",
                            color = OutlineAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Text(
                            text = "LIVE TICKER: 0s",
                            color = OutlineAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // App Picker / target apps list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TARGET APPS",
                        color = SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    var appDropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .border(1.dp, OutlineAccent)
                                .background(DarkerSurfaceColor)
                                .clickable { appDropdownExpanded = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "+ ADD TARGET APP",
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
                            DropdownMenuItem(
                                text = { Text("All Social Media", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    if (!doomscrollTargetApps.contains("All Social Media")) {
                                        onDoomscrollTargetAppsUpdate(doomscrollTargetApps + "All Social Media")
                                    }
                                    appDropdownExpanded = false
                                }
                            )
                            installedApps.forEach { (pkg, label) ->
                                DropdownMenuItem(
                                    text = { Text("$label ($pkg)", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                    onClick = {
                                        if (!doomscrollTargetApps.contains(pkg)) {
                                            onDoomscrollTargetAppsUpdate(doomscrollTargetApps + pkg)
                                        }
                                        appDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Display targeted apps as chips
                    if (doomscrollTargetApps.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            doomscrollTargetApps.forEach { pkg ->
                                val label = if (pkg == "All Social Media") "All Social Media" else {
                                    installedApps.find { it.first == pkg }?.second ?: pkg
                                }
                                InputChip(
                                    text = label,
                                    onRemove = { onDoomscrollTargetAppsUpdate(doomscrollTargetApps - pkg) },
                                    enabled = true
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isLocked) {
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

        doomscrollTimePickerTarget?.let { target ->
            val initialHour = if (target == "START") doomscrollStartHour else doomscrollEndHour
            val initialMinute = if (target == "START") doomscrollStartMin else doomscrollEndMin
            DialTimePickerDialog(
                initialHour = initialHour,
                initialMinute = initialMinute,
                onDismiss = { doomscrollTimePickerTarget = null },
                onConfirm = { hour, minute ->
                    if (target == "START") {
                        onDoomscrollTimeUpdate(hour, minute, doomscrollEndHour, doomscrollEndMin)
                    } else {
                        onDoomscrollTimeUpdate(doomscrollStartHour, doomscrollStartMin, hour, minute)
                    }
                    doomscrollTimePickerTarget = null
                }
            )
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
 * Reusable multi-domain editor: text field + { ADD } button + removable chips.
 * Shared by the global BAN DOMAIN SET, Usage Limits, and Scheduled Blocks so all three
 * present the same interface. Each scope keeps its own independent list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomainSetEditor(
    domains: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "e.g. youtube.com",
    canAdd: Boolean = true,
    canRemove: Boolean = true
) {
    var domainInput by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MonospaceTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = placeholder,
                    enabled = canAdd
                )
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .sharpBorder(1.dp, OutlineAccent)
                    .background(if (canAdd) DarkerSurfaceColor else MutedSurface)
                    .clickable(enabled = canAdd) {
                        val d = domainInput.trim().lowercase()
                        if (d.isNotEmpty()) {
                            onAdd(d)
                            domainInput = ""
                        }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "{ ADD }",
                    color = if (canAdd) MonospaceText else SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (domains.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                domains.forEach { domain ->
                    InputChip(
                        text = domain,
                        onRemove = if (canRemove) ({ onRemove(domain) }) else null,
                        enabled = canRemove
                    )
                }
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
    allowedValue: String,
    allowedUnit: String,
    selectedInterval: String,
    targetAppSet: Set<String>,
    specificDomain: String,
    installedApps: List<Pair<String, String>>,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (
        newEnabled: Boolean,
        newAllowedValue: String,
        newAllowedUnit: String,
        newSelectedInterval: String,
        newTargetAppSet: Set<String>,
        newSpecificDomain: String,
        newIsCollectiveLimit: Boolean
    ) -> Unit,
    isCollectiveLimit: Boolean
) {
    var localEnabled by remember { mutableStateOf(enabled) }
    var localSelectedInterval by remember { mutableStateOf(selectedInterval) }
    var localAllowedUnit by remember {
        mutableStateOf(
            if (selectedInterval == "hour") "min" else allowedUnit
        )
    }
    val initialMax = if (localAllowedUnit == "hours") 24 else 60
    var localAllowedValue by remember {
        mutableStateOf(
            (allowedValue.toIntOrNull() ?: 1).coerceIn(1, initialMax).toString()
        )
    }
    var localTargetAppSet by remember { mutableStateOf(targetAppSet) }
    var localSpecificDomain by remember { mutableStateOf(specificDomain) }
    var localIsCollectiveLimit by remember { mutableStateOf(isCollectiveLimit) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightGraphiteBg)
                .border(1.dp, OutlineAccent)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Header
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

                // Enabled Toggle Row with M3 Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLocked) { localEnabled = !localEnabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ENABLED",
                        color = if (localEnabled) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { if (!isLocked) localEnabled = it },
                        enabled = !isLocked,
                        thumbContent = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LightGraphiteBg,
                            checkedTrackColor = MonospaceText,
                            checkedBorderColor = OutlineAccent,
                            uncheckedThumbColor = OutlineAccent,
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = OutlineAccent,
                            disabledCheckedThumbColor = OutlineAccent,
                            disabledCheckedTrackColor = MutedSurface,
                            disabledUncheckedThumbColor = MutedSurface,
                            disabledUncheckedTrackColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Limit Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLocked) { localIsCollectiveLimit = !localIsCollectiveLimit }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIMIT MODE: [ ${if (localIsCollectiveLimit) "COLLECTIVE" else "INDEPENDENT"} ]",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Limit Controls Label & Selectors
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Allow: ${localAllowedValue}",
                        color = MonospaceText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
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
                                text = localAllowedUnit,
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
                                    localAllowedUnit = "min"
                                    unitDropdownExpanded = false
                                }
                            )
                            if (localSelectedInterval == "day") {
                                DropdownMenuItem(
                                    text = { Text("hours", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                    onClick = {
                                        localAllowedUnit = "hours"
                                        val currentVal = localAllowedValue.toIntOrNull() ?: 1
                                        if (currentVal > 24) {
                                            localAllowedValue = "24"
                                        }
                                        unitDropdownExpanded = false
                                    }
                                )
                            }
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
                                text = localSelectedInterval,
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
                                    localSelectedInterval = "hour"
                                    localAllowedUnit = "min"
                                    val currentVal = localAllowedValue.toIntOrNull() ?: 1
                                    if (currentVal > 60) {
                                        localAllowedValue = "60"
                                    }
                                    intervalDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("day", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                                onClick = {
                                    localSelectedInterval = "day"
                                    intervalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Slider (Range 1 to max allowed based on unit)
                val sliderMax = if (localAllowedUnit == "hours") 24f else 60f
                val sliderValue = (localAllowedValue.toFloatOrNull() ?: 1f).coerceIn(1f, sliderMax)

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        localAllowedValue = newValue.roundToInt().coerceIn(1, sliderMax.toInt()).toString()
                    },
                    valueRange = 1f..sliderMax,
                    steps = (sliderMax - 1).toInt(),
                    enabled = !isLocked,
                    colors = SliderDefaults.colors(
                        thumbColor = MonospaceText,
                        activeTrackColor = MonospaceText,
                        inactiveTrackColor = OutlineAccent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

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
                            .clickable(enabled = !isLocked) { appDropdownExpanded = true }
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
                                    if (!localTargetAppSet.contains(pkg)) {
                                        localTargetAppSet = localTargetAppSet + pkg
                                    }
                                    appDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (localTargetAppSet.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        localTargetAppSet.forEach { pkg ->
                            val label = if (pkg == "All Social Media") {
                                "All Social Media"
                            } else {
                                installedApps.find { it.first == pkg }?.second ?: pkg
                            }
                            InputChip(
                                text = label,
                                onRemove = { localTargetAppSet = localTargetAppSet - pkg },
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

                // Specific Domains (multi-domain chip list)
                DomainSetEditor(
                    domains = com.avow.app.util.DomainUtil.parse(localSpecificDomain),
                    onAdd = { domain ->
                        localSpecificDomain = com.avow.app.util.DomainUtil.format(
                            com.avow.app.util.DomainUtil.parse(localSpecificDomain) + domain
                        )
                    },
                    onRemove = { domain ->
                        localSpecificDomain = com.avow.app.util.DomainUtil.format(
                            com.avow.app.util.DomainUtil.parse(localSpecificDomain) - domain
                        )
                    },
                    canAdd = !isLocked,
                    canRemove = !isLocked
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightGraphiteBg)
                    .drawBehind { 
                        drawLine(OutlineAccent, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onUpdate(
                            localEnabled,
                            localAllowedValue,
                            localAllowedUnit,
                            localSelectedInterval,
                            localTargetAppSet,
                            localSpecificDomain,
                            localIsCollectiveLimit
                        )
                    },
                    containerColor = MonospaceText,
                    contentColor = LightGraphiteBg,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp
                    ),
                    modifier = Modifier.sharpBorder(1.dp, OutlineAccent)
                ) {
                    Text(
                        text = "UPDATE",
                        color = Color(0xFF1C1C1C),
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
 * Custom wrapper for Material 3 TimePicker Dialog.
 */
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RectangleShape,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(containerColor)
                .border(1.dp, OutlineAccent)
                .padding(24.dp),
            color = containerColor
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SELECT TIME (DIAL VIEW)",
                    color = MonospaceText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                content()
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * Dial time picker dialog helper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text(
                text = "OK",
                color = MonospaceText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onConfirm(state.hour, state.minute) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "CANCEL",
                color = SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        containerColor = LightGraphiteBg
    ) {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = MutedSurface,
                clockDialSelectedContentColor = LightGraphiteBg,
                clockDialUnselectedContentColor = MonospaceText,
                selectorColor = MonospaceText,
                periodSelectorBorderColor = OutlineAccent,
                periodSelectorSelectedContainerColor = Color(0xFF2E2E2E), // Darker when selected
                periodSelectorUnselectedContainerColor = MutedSurface, // Lighter when unselected
                periodSelectorSelectedContentColor = MonospaceText, // White text on dark selected container
                periodSelectorUnselectedContentColor = Color(0xFF1C1C1C), // Dark text on light unselected container
                timeSelectorSelectedContainerColor = MutedSurface,
                timeSelectorUnselectedContainerColor = MutedSurface,
                timeSelectorSelectedContentColor = MonospaceText,
                timeSelectorUnselectedContentColor = MonospaceText
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockSlotEditor(
    index: Int,
    block: VowBlock,
    installedApps: List<Pair<String, String>>,
    isLocked: Boolean,
    showToast: (String) -> Unit,
    onBlockChange: (VowBlock) -> Unit,
    onTimePickerTrigger: (String) -> Unit,
    onRemoveSlot: (() -> Unit)?
) {
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
                    onBlockChange(block.copy(name = newName))
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

        // Enabled Checkbox Row with M3 Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val newEnabled = !block.isEnabled
                    if (isLocked && block.isEnabled && !newEnabled) {
                        showToast("Error: Scheduled blocks cannot be disabled when locked.")
                    } else {
                        onBlockChange(block.copy(isEnabled = newEnabled))
                    }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ENABLED",
                color = if (block.isEnabled) MonospaceText else SubtextGrey,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = block.isEnabled,
                onCheckedChange = { newEnabled ->
                    if (isLocked && block.isEnabled && !newEnabled) {
                        showToast("Error: Scheduled blocks cannot be disabled when locked.")
                    } else {
                        onBlockChange(block.copy(isEnabled = newEnabled))
                    }
                },
                thumbContent = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LightGraphiteBg,
                    checkedTrackColor = MonospaceText,
                    checkedBorderColor = OutlineAccent,
                    uncheckedThumbColor = OutlineAccent,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = OutlineAccent,
                    disabledCheckedThumbColor = OutlineAccent,
                    disabledCheckedTrackColor = MutedSurface,
                    disabledUncheckedThumbColor = MutedSurface,
                    disabledUncheckedTrackColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Start Time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("START: ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(30.dp)
                    .border(1.dp, OutlineAccent)
                    .background(MutedSurface)
                    .clickable(enabled = !isLocked) {
                        onTimePickerTrigger("START")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatTimeAmPm(block.startHour, block.startMin),
                    color = if (!isLocked) MonospaceText else SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // End Time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("END:   ", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(30.dp)
                    .border(1.dp, OutlineAccent)
                    .background(MutedSurface)
                    .clickable(enabled = !isLocked) {
                        onTimePickerTrigger("END")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatTimeAmPm(block.endHour, block.endMin),
                    color = if (!isLocked) MonospaceText else SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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

        var appDropdownExpanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, OutlineAccent)
                    .background(MutedSurface)
                    .clickable(enabled = !isLocked) { appDropdownExpanded = true }
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
                installedApps.filter { it.first != "All Social Media" }.forEach { (pkg, label) ->
                    DropdownMenuItem(
                        text = { Text("$label ($pkg)", fontFamily = FontFamily.Monospace, color = MonospaceText) },
                        onClick = {
                            if (!block.targetApps.contains(pkg)) {
                                onBlockChange(block.copy(targetApps = block.targetApps + pkg))
                            }
                            appDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (block.targetApps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                block.targetApps.forEach { pkg ->
                    val label = if (pkg == "All Social Media") {
                        "All Social Media"
                    } else {
                        installedApps.find { it.first == pkg }?.second ?: pkg
                    }
                    InputChip(
                        text = label,
                        onRemove = {
                            if (isLocked) {
                                showToast("Error: Target applications cannot be removed while locked.")
                            } else {
                                onBlockChange(block.copy(targetApps = block.targetApps - pkg))
                            }
                        },
                        enabled = !isLocked
                    )
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
        DomainSetEditor(
            domains = com.avow.app.util.DomainUtil.parse(block.specificDomain),
            onAdd = { domain ->
                onBlockChange(
                    block.copy(
                        specificDomain = com.avow.app.util.DomainUtil.format(
                            com.avow.app.util.DomainUtil.parse(block.specificDomain) + domain
                        )
                    )
                )
            },
            onRemove = { domain ->
                onBlockChange(
                    block.copy(
                        specificDomain = com.avow.app.util.DomainUtil.format(
                            com.avow.app.util.DomainUtil.parse(block.specificDomain) - domain
                        )
                    )
                )
            },
            canAdd = !isLocked,
            canRemove = !isLocked
        )

        // Remove block button
        if (onRemoveSlot != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "[ REMOVE BLOCK SLOT ]",
                color = LockedRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onRemoveSlot() }
                    .padding(vertical = 4.dp)
            )
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
    var timePickerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(LightGraphiteBg)
                .border(1.dp, OutlineAccent)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
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
                    BlockSlotEditor(
                        index = index,
                        block = block,
                        installedApps = installedApps,
                        isLocked = isLocked,
                        showToast = showToast,
                        onBlockChange = { updatedBlock ->
                            currentBlocks = currentBlocks.map { if (it.id == block.id) updatedBlock else it }
                        },
                        onTimePickerTrigger = { type ->
                            timePickerTarget = Pair(block.id, type)
                        },
                        onRemoveSlot = if (currentBlocks.size > 1) {
                            {
                                if (isLocked) {
                                    showToast("Error: Scheduled blocks cannot be removed while locked.")
                                } else {
                                    currentBlocks = currentBlocks.filter { it.id != block.id }
                                }
                            }
                        } else null
                    )
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

            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightGraphiteBg)
                    .drawBehind { 
                        drawLine(OutlineAccent, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onUpdate(currentBlocks) },
                    containerColor = MonospaceText,
                    contentColor = LightGraphiteBg,
                    shape = RectangleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp
                    ),
                    modifier = Modifier.sharpBorder(1.dp, OutlineAccent)
                ) {
                    Text(
                        text = "UPDATE",
                        color = Color(0xFF1C1C1C),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Time Picker Dialog overlay
            timePickerTarget?.let { target ->
                val block = currentBlocks.find { it.id == target.first }
                if (block != null) {
                    val initialHour = if (target.second == "START") block.startHour else block.endHour
                    val initialMinute = if (target.second == "START") block.startMin else block.endMin
                    DialTimePickerDialog(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        onDismiss = { timePickerTarget = null },
                        onConfirm = { hour, minute ->
                            currentBlocks = currentBlocks.map {
                                if (it.id == block.id) {
                                    if (target.second == "START") {
                                        it.copy(startHour = hour, startMin = minute)
                                    } else {
                                        it.copy(endHour = hour, endMin = minute)
                                    }
                                } else {
                                    it
                                }
                            }
                            timePickerTarget = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Wheel number picker widget representing a tactile mechanical stopwatch digit selector.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelNumberPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(range) { range.toList() }
    val initialIndex = remember(value, items) {
        val idx = items.indexOf(value)
        if (idx != -1) idx else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(value) {
        val idx = items.indexOf(value)
        if (idx != -1 && listState.firstVisibleItemIndex != idx && !listState.isScrollInProgress) {
            listState.scrollToItem(idx)
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val index = listState.firstVisibleItemIndex
        if (index in items.indices) {
            onValueChange(items[index])
        }
    }

    Box(
        modifier = modifier
            .height(120.dp)
            .width(54.dp)
            .border(1.dp, OutlineAccent)
            .background(MutedSurface),
        contentAlignment = Alignment.Center
    ) {
        // Center selection reticle overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, MonospaceText)
                .background(Color.White.copy(alpha = 0.03f))
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val itemValue = items[index]
                val isSelected = listState.firstVisibleItemIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", itemValue),
                        color = if (isSelected) MonospaceText else SubtextGrey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isSelected) 18.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Dialog layout configured to match the "CONFIG: BINDING VOW" specifications with stopwatch-style duration pickers.
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
    var vowDays by remember { mutableStateOf(initialDays.toIntOrNull() ?: 0) }
    var vowHours by remember { mutableStateOf(initialHours.toIntOrNull() ?: 0) }
    var vowMinutes by remember { mutableStateOf(initialMinutes.toIntOrNull() ?: 0) }
    var vowSeconds by remember { mutableStateOf(initialSeconds.toIntOrNull() ?: 0) }

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
                    text = "DURATION (DD:HH:MM:SS) - MAX 99:23:59:59",
                    color = SubtextGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WheelNumberPicker(range = 0..99, value = vowDays, onValueChange = { vowDays = it })
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("DD", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WheelNumberPicker(range = 0..23, value = vowHours, onValueChange = { vowHours = it })
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("HH", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WheelNumberPicker(range = 0..59, value = vowMinutes, onValueChange = { vowMinutes = it })
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("MM", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                    Text(":", color = MonospaceText, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WheelNumberPicker(range = 0..59, value = vowSeconds, onValueChange = { vowSeconds = it })
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("SS", color = SubtextGrey, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Calculations
                val d = vowDays.toLong()
                val h = vowHours.toLong()
                val m = vowMinutes.toLong()
                val s = vowSeconds.toLong()
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
    val totalPickups = sessions.sumOf { it.pickups }
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
                StatBox(label = "DEVICE PICKUPS", value = totalPickups.toString(), modifier = Modifier.weight(1f))
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
                    text = "PICKUPS: ${session.pickups}",
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
