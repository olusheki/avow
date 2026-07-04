package com.avow.app.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.Preferences
import com.avow.app.data.VowDataStore
import com.avow.app.enforcement.CapabilitiesFactory
import com.avow.app.enforcement.EnforcementCapabilities
import com.avow.app.model.VowBlock
import com.avow.app.util.VowValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val vowDataStore: com.avow.app.data.VowDataStore = com.avow.app.data.VowDataStore(application),
    private val capabilities: EnforcementCapabilities = CapabilitiesFactory.create(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Guards the one-time full reconciliation so ongoing DataStore emissions take the light path.
    private var hasLoadedOnce = false

    init {
        loadInstalledApps()
        viewModelScope.launch {
            // Persist the tamper fallback before we start reading, so a tampered state is repaired
            // even when the accessibility service isn't running to do it.
            try {
                vowDataStore.repairTamperedStateIfNeeded()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Tamper repair failed", e)
            }
        }
        loadState()
        startCountdownTicker()
    }

    private fun loadInstalledApps() {
        // Off the main thread: querying + loading labels for every launchable app is slow enough
        // to jank startup. The StateFlow update is thread-safe, so publishing from IO is fine.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val list = resolveInfos.map {
                val packageName = it.activityInfo.packageName
                val label = it.loadLabel(pm).toString()
                packageName to label
            }.distinctBy { it.first }.sortedBy { it.second }

            val apps = if (list.isEmpty()) {
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
            updateState { copy(installedApps = apps) }
        }
    }

    private fun loadState() {
        viewModelScope.launch {
            // Observe the DataStore continuously so state the BlockerService writes (temporary
            // lockouts, doomscroll accumulation, tamper/boot escalation) reaches the UI without a
            // process restart. The first emission does a full reconcile; later ones take the light
            // path so user-edited config stays UI-owned (one-way UI -> DataStore).
            vowDataStore.preferencesFlow.collect { prefs ->
                if (hasLoadedOnce) applyLiveUpdate(prefs) else applyInitialLoad(prefs)
            }
        }
    }

    /**
     * Live sync of the handful of fields the service owns. Deliberately does NOT touch user config,
     * the countdown digits (the ticker owns those), the frozen* baselines, or the current screen —
     * only the service-written values, plus a full re-reconcile if a vow was force-activated
     * underneath us (tamper fallback / boot).
     */
    private fun applyLiveUpdate(prefs: Preferences) {
        val prefVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
        if (prefVowActive && !_uiState.value.isVowActive) {
            applyInitialLoad(prefs)
            return
        }
        val lockoutEnd = prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
        val doomAcc = prefs[VowDataStore.DOOMSCROLL_ACCUMULATED_MS] ?: 0L
        _uiState.update { it.copy(temporaryLockoutEndTime = lockoutEnd, doomscrollAccumulatedMs = doomAcc) }
    }

    /**
     * Full first-load reconciliation: maps every field, runs the countdown/DeviceAdmin catch-up,
     * and picks the initial screen. Runs once, or again if a tamper/boot escalation forces a vow.
     */
    private fun applyInitialLoad(prefs: Preferences) {
        hasLoadedOnce = true
        viewModelScope.launch {
            try {
                val isVowActive = prefs[VowDataStore.IS_VOW_ACTIVE] ?: false
                val isActiveVowMode = prefs[VowDataStore.IS_ACTIVE_VOW_MODE] ?: false
                val deactivationRequestTime = prefs[VowDataStore.DEACTIVATION_REQUEST_TIME] ?: 0L
                val banDomainSet = prefs[VowDataStore.BAN_DOMAIN_SET] ?: setOf("instagram.com")
                val secureFolderEnabled = prefs[VowDataStore.SECURE_FOLDER_ENABLED] ?: false
                val privateSpaceEnabled = prefs[VowDataStore.PRIVATE_SPACE_ENABLED] ?: false
                val lockUninstall = prefs[VowDataStore.LOCK_UNINSTALL] ?: false
                val disallowDataWipe = prefs[VowDataStore.DISALLOW_DATA_WIPE] ?: false
                val disableSafeBoot = prefs[VowDataStore.DISABLE_SAFE_BOOT] ?: false
                val blockPlayStore = prefs[VowDataStore.BLOCK_PLAY_STORE] ?: false
                val dynamicReinstall = prefs[VowDataStore.DYNAMIC_REINSTALL] ?: false
                val deactivateUsbDebugging = prefs[VowDataStore.DEACTIVATE_USB_DEBUGGING] ?: false
                val quietHoursEnabled = prefs[VowDataStore.QUIET_HOURS_ENABLED] ?: false
                val quietStartHour = prefs[VowDataStore.QUIET_START_HOUR] ?: 22
                val quietStartMin = prefs[VowDataStore.QUIET_START_MIN] ?: 0
                val quietEndHour = prefs[VowDataStore.QUIET_END_HOUR] ?: 7
                val quietEndMin = prefs[VowDataStore.QUIET_END_MIN] ?: 0
                val quietHoursTargetAppSet = prefs[VowDataStore.QUIET_HOURS_TARGET_APP_SET] ?: emptySet()
                val quietHoursSpecificDomain = prefs[VowDataStore.QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
                val vowStartTimeMs = prefs[VowDataStore.VOW_START_TIME_MS] ?: 0L
                val vowInitialDurationSeconds = prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS] ?: 0L
                val vpnDomainBlockingEnabled = prefs[VowDataStore.VPN_DOMAIN_BLOCKING_ENABLED] ?: false
                val temporaryLockoutEndTime = prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
                val doomscrollShieldEnabled = prefs[VowDataStore.DOOMSCROLL_SHIELD_ENABLED] ?: false
                val doomscrollAllTime = prefs[VowDataStore.DOOMSCROLL_ALL_TIME] ?: false
                val doomscrollStartHour = prefs[VowDataStore.DOOMSCROLL_START_HOUR] ?: 23
                val doomscrollStartMin = prefs[VowDataStore.DOOMSCROLL_START_MIN] ?: 0
                val doomscrollEndHour = prefs[VowDataStore.DOOMSCROLL_END_HOUR] ?: 5
                val doomscrollEndMin = prefs[VowDataStore.DOOMSCROLL_END_MIN] ?: 0
                val doomscrollTargetAppSet = prefs[VowDataStore.DOOMSCROLL_TARGET_APP_SET] ?: emptySet()
                val doomscrollCooldownMinutes = prefs[VowDataStore.DOOMSCROLL_COOLDOWN_MINUTES] ?: 60
                val doomscrollAllowanceMinutes = prefs[VowDataStore.DOOMSCROLL_ALLOWANCE_MINUTES] ?: 15
                val doomscrollAccumulatedMs = prefs[VowDataStore.DOOMSCROLL_ACCUMULATED_MS] ?: 0L
                
                var vowBlocks = VowBlock.deserializeList(prefs[VowDataStore.VOW_BLOCKS_JSON] ?: "")
                if (vowBlocks.isEmpty()) {
                    vowBlocks = listOf(
                        VowBlock(
                            id = UUID.randomUUID().toString(),
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

                val usageLimitsUpdated = prefs[VowDataStore.USAGE_LIMITS_UPDATED] ?: false
                val allowedValue = prefs[VowDataStore.ALLOWED_VALUE] ?: "5"
                val allowedUnit = prefs[VowDataStore.ALLOWED_UNIT] ?: "min"
                val selectedInterval = prefs[VowDataStore.SELECTED_INTERVAL] ?: "hour"
                val targetAppSet = prefs[VowDataStore.TARGET_APP_SET] ?: emptySet()
                val specificDomain = prefs[VowDataStore.SPECIFIC_DOMAIN] ?: ""
                val isCollectiveLimit = prefs[VowDataStore.IS_COLLECTIVE_LIMIT] ?: false
                val isOnboardingCompleted = prefs[VowDataStore.IS_ONBOARDING_COMPLETED] ?: false

                val isDeviceOwner = capabilities.isDeviceOwnerActive

                var days = 0
                var hours = 0
                var minutes = 0
                var seconds = 0
                var finalIsVowActive = isVowActive
                val savedRemaining = prefs[VowDataStore.REMAINING_VOW_SECONDS] ?: 0L
                val currentUptime = SystemClock.elapsedRealtime()
                var currentState = ScreenState.UNLOCKED_VAULT
                if (temporaryLockoutEndTime > currentUptime) {
                    currentState = ScreenState.TEMPORARY_LOCKOUT
                } else if (isVowActive) {
                    currentState = ScreenState.LOCKED_VAULT
                } else if (!isOnboardingCompleted) {
                    currentState = ScreenState.ONBOARDING
                }

                if (isVowActive) {
                    val lastUptime = prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
                    
                    val finalRemaining = if (savedRemaining > 0) {
                        VowValidator.calculateRemainingSeconds(currentUptime, lastUptime, savedRemaining)
                    } else {
                        0L
                    }
                    
                    if (finalRemaining > 0) {
                        days = (finalRemaining / 86400).toInt()
                        hours = ((finalRemaining % 86400) / 3600).toInt()
                        minutes = ((finalRemaining % 3600) / 60).toInt()
                        if (currentState != ScreenState.TEMPORARY_LOCKOUT) {
                            currentState = ScreenState.LOCKED_VAULT
                        }
                        seconds = (finalRemaining % 60).toInt()
                    } else {
                        finalIsVowActive = false
                        capabilities.assertBindingVow(
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
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        isVowActive = finalIsVowActive,
                        isActiveVowMode = isActiveVowMode,
                        deactivationRequestTime = deactivationRequestTime,
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
                        isCollectiveLimit = isCollectiveLimit,
                        vowBlocks = vowBlocks,
                        frozenVowBlocks = vowBlocks,
                        frozenAllowedValue = allowedValue,
                        frozenAllowedUnit = allowedUnit,
                        frozenInterval = selectedInterval,
                        frozenQuietHoursEnabled = quietHoursEnabled,
                        frozenQuietStartHour = quietStartHour,
                        frozenQuietStartMin = quietStartMin,
                        frozenQuietEndHour = quietEndHour,
                        frozenQuietEndMin = quietEndMin,
                        frozenQuietHoursTargetAppSet = quietHoursTargetAppSet,
                        frozenQuietHoursSpecificDomain = quietHoursSpecificDomain,
                        doomscrollShieldEnabled = doomscrollShieldEnabled,
                        doomscrollAllTime = doomscrollAllTime,
                        doomscrollStartHour = doomscrollStartHour,
                        doomscrollStartMin = doomscrollStartMin,
                        doomscrollEndHour = doomscrollEndHour,
                        doomscrollEndMin = doomscrollEndMin,
                        doomscrollTargetAppSet = doomscrollTargetAppSet,
                        doomscrollCooldownMinutes = doomscrollCooldownMinutes,
                        doomscrollAllowanceMinutes = doomscrollAllowanceMinutes,
                        doomscrollAccumulatedMs = doomscrollAccumulatedMs,
                        frozenDoomscrollShieldEnabled = doomscrollShieldEnabled,
                        frozenDoomscrollAllTime = doomscrollAllTime,
                        frozenDoomscrollStartHour = doomscrollStartHour,
                        frozenDoomscrollStartMin = doomscrollStartMin,
                        frozenDoomscrollEndHour = doomscrollEndHour,
                        frozenDoomscrollEndMin = doomscrollEndMin,
                        frozenDoomscrollTargetAppSet = doomscrollTargetAppSet,
                        frozenDoomscrollCooldownMinutes = doomscrollCooldownMinutes,
                        frozenDoomscrollAllowanceMinutes = doomscrollAllowanceMinutes,
                        days = days,
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                        vowStartTimeMs = vowStartTimeMs,
                        vowInitialDurationSeconds = vowInitialDurationSeconds,
                        temporaryLockoutEndTime = temporaryLockoutEndTime,
                        currentState = if (state.currentState == ScreenState.INTRUSION_INTERCEPT || state.currentState == ScreenState.TEMPORARY_LOCKOUT) {
                            if (currentState == ScreenState.TEMPORARY_LOCKOUT) {
                                currentState
                            } else {
                                state.currentState
                            }
                        } else {
                            currentState
                        },
                        isDeviceOwner = isDeviceOwner,
                        deviceOwnerSupported = capabilities.supportsDeviceOwnerFeatures,
                        vpnDomainBlockingEnabled = vpnDomainBlockingEnabled,
                        isOnboardingCompleted = isOnboardingCompleted,
                        isLoaded = true
                    )
                }
                // Re-establish the domain-filter VPN on launch if the user had it on (VPN consent
                // persists; establish() no-ops/stops itself if consent was revoked).
                if (vpnDomainBlockingEnabled) {
                    com.avow.app.vpn.DomainVpnService.start(getApplication())
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load DataStore settings", e)
                _uiState.update { it.copy(isLoaded = true) }
            }
        }
    }

    private fun startCountdownTicker() {
        viewModelScope.launch {
            var lastCheckUptime = SystemClock.elapsedRealtime()
            while (true) {
                delay(1000L)
                val state = _uiState.value
                
                // Temporary lockout exit check. endTime == 0 (nothing enforced / read failed)
                // also exits — the service still intercepts target apps, and staying stuck on
                // the lockout screen with no countdown is worse.
                if (state.currentState == ScreenState.TEMPORARY_LOCKOUT) {
                    if (state.temporaryLockoutEndTime == 0L || SystemClock.elapsedRealtime() >= state.temporaryLockoutEndTime) {
                        updateState { copy(currentState = if (isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT, temporaryLockoutEndTime = 0L) }
                    }
                }
                
                // Vow countdown ticker
                if (state.isVowActive && state.isLoaded) {
                    val currentUptime = SystemClock.elapsedRealtime()
                    val elapsedMs = currentUptime - lastCheckUptime
                    if (elapsedMs >= 1000L) {
                        val elapsedSeconds = elapsedMs / 1000L
                        lastCheckUptime += elapsedSeconds * 1000L
                        
                        var totalRemaining = state.days * 86400L + state.hours * 3600L + state.minutes * 60L + state.seconds
                        totalRemaining -= elapsedSeconds
                        
                        if (totalRemaining <= 0L) {
                            capabilities.assertBindingVow(
                                activate = false,
                                secureFolderEnabled = state.secureFolderEnabled,
                                privateSpaceEnabled = state.privateSpaceEnabled,
                                lockUninstall = state.lockUninstall,
                                disallowDataWipe = state.disallowDataWipe,
                                disableSafeBoot = state.disableSafeBoot,
                                blockPlayStore = state.blockPlayStore,
                                deactivateUsbDebugging = state.deactivateUsbDebugging
                            )
                            viewModelScope.launch { vowDataStore.clearVowConfig() }
                            showToast("aVow: Temporal lock has expired. Restrictions cleared.")
                            updateState {
                                copy(
                                    isVowActive = false,
                                    // Keep the user's Passive/Active choice after the vow ends —
                                    // it's a preference, not tied to a single vow's lifecycle.
                                    currentState = ScreenState.UNLOCKED_VAULT,
                                    isCollectiveLimit = false,
                                    vowStartTimeMs = 0L,
                                    vowInitialDurationSeconds = 0L,
                                    days = 0, hours = 0, minutes = 0, seconds = 0
                                )
                            }
                        } else {
                            val d = (totalRemaining / 86400).toInt()
                            val h = ((totalRemaining % 86400) / 3600).toInt()
                            val m = ((totalRemaining % 3600) / 60).toInt()
                            val s = (totalRemaining % 60).toInt()
                            
                            updateState { 
                                copy(days = d, hours = h, minutes = m, seconds = s) 
                            }
                        }
                    }
                } else {
                    lastCheckUptime = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    fun updateState(block: MainUiState.() -> MainUiState) {
        _uiState.update(block)
    }

    fun saveConfigToDataStore() {
        val state = _uiState.value
        if (!state.isLoaded) return
        viewModelScope.launch {
            try {
                vowDataStore.saveVowConfig(
                    isVowActive = state.isVowActive,
                    isActiveVowMode = state.isActiveVowMode,
                    remainingVowSeconds = VowValidator.clampRemainingSeconds(state.days * 86400L + state.hours * 3600L + state.minutes * 60L + state.seconds),
                    lastSystemUptimeMillis = SystemClock.elapsedRealtime(),
                    banDomainSet = state.banDomainSet,
                    secureFolderEnabled = state.secureFolderEnabled,
                    privateSpaceEnabled = state.privateSpaceEnabled,
                    lockUninstall = state.lockUninstall,
                    disallowDataWipe = state.disallowDataWipe,
                    disableSafeBoot = state.disableSafeBoot,
                    blockPlayStore = state.blockPlayStore,
                    dynamicReinstall = state.dynamicReinstall,
                    deactivateUsbDebugging = state.deactivateUsbDebugging,
                    quietHoursEnabled = state.quietHoursEnabled,
                    quietStartHour = state.quietStartHour,
                    quietStartMin = state.quietStartMin,
                    quietEndHour = state.quietEndHour,
                    quietEndMin = state.quietEndMin,
                    quietHoursTargetAppSet = state.quietHoursTargetAppSet,
                    quietHoursSpecificDomain = state.quietHoursSpecificDomain,
                    usageLimitsUpdated = state.usageLimitsUpdated,
                    allowedValue = state.allowedValue,
                    allowedUnit = state.allowedUnit,
                    selectedInterval = state.selectedInterval,
                    targetAppSet = state.targetAppSet,
                    specificDomain = state.specificDomain,
                    deactivationRequestTime = state.deactivationRequestTime,
                    isCollectiveLimit = state.isCollectiveLimit,
                    vowBlocksJson = VowBlock.serializeList(state.vowBlocks),
                    vowStartTimeMs = state.vowStartTimeMs,
                    vowInitialDurationSeconds = state.vowInitialDurationSeconds,
                    doomscrollShieldEnabled = state.doomscrollShieldEnabled,
                    doomscrollAllTime = state.doomscrollAllTime,
                    doomscrollStartHour = state.doomscrollStartHour,
                    doomscrollStartMin = state.doomscrollStartMin,
                    doomscrollEndHour = state.doomscrollEndHour,
                    doomscrollEndMin = state.doomscrollEndMin,
                    doomscrollTargetAppSet = state.doomscrollTargetAppSet,
                    doomscrollCooldownMinutes = state.doomscrollCooldownMinutes,
                    doomscrollAllowanceMinutes = state.doomscrollAllowanceMinutes
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to auto-save settings", e)
            }
        }
    }

    fun showToast(message: String) {
        updateState { copy(inAppToastMessage = message) }
    }

    fun clearToast() {
        updateState { copy(inAppToastMessage = null) }
    }

    /** Persists the VPN toggle and starts the domain-filter service. Call only after VPN consent. */
    fun enableVpnDomainBlocking() {
        updateState { copy(vpnDomainBlockingEnabled = true) }
        viewModelScope.launch {
            try { vowDataStore.saveVpnDomainBlockingEnabled(true) } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to save VPN pref", e)
            }
        }
        com.avow.app.vpn.DomainVpnService.start(getApplication())
    }

    fun disableVpnDomainBlocking() {
        updateState { copy(vpnDomainBlockingEnabled = false) }
        viewModelScope.launch {
            try { vowDataStore.saveVpnDomainBlockingEnabled(false) } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to save VPN pref", e)
            }
        }
        com.avow.app.vpn.DomainVpnService.stop(getApplication())
    }

    fun setScreenState(state: ScreenState) {
        // Re-triggering the same state (e.g. repeated intrusion intercepts) must not overwrite
        // previousState with itself, or the exit gesture would lead right back to the overlay.
        updateState {
            copy(
                previousState = if (currentState == state) previousState else currentState,
                currentState = state
            )
        }
    }

    /**
     * Enters the temporary (doomscroll) lockout screen. The lockout end time is re-read from the
     * DataStore because the service persists it after this ViewModel's one-shot load, so the
     * in-memory copy is usually stale (previously this left the screen stuck with endTime = 0).
     */
    fun enterTemporaryLockout() {
        viewModelScope.launch {
            val end = try {
                vowDataStore.preferencesFlow.first()[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to read lockout end time", e)
                0L
            }
            // Set end time and screen state together so the ticker's exit check never sees the
            // lockout screen with a still-unloaded (zero) end time.
            updateState {
                copy(
                    previousState = if (currentState == ScreenState.TEMPORARY_LOCKOUT) previousState else currentState,
                    temporaryLockoutEndTime = maxOf(temporaryLockoutEndTime, end),
                    currentState = ScreenState.TEMPORARY_LOCKOUT
                )
            }
        }
    }

    /**
     * Persists any selections made during onboarding, marks onboarding as completed,
     * and transitions to the unlocked vault dashboard.
     */
    fun completeOnboarding() {
        updateState { copy(isOnboardingCompleted = true, currentState = ScreenState.UNLOCKED_VAULT) }
        // Persist the blocking selections (domains / apps / vow mode) chosen during onboarding.
        saveConfigToDataStore()
        viewModelScope.launch {
            try {
                vowDataStore.setOnboardingCompleted(true)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to persist onboarding completion", e)
            }
        }
    }
    
    fun requestDeactivation() {
        val currentTime = System.currentTimeMillis()
        val state = _uiState.value
        if (state.deactivationRequestTime == 0L) {
            updateState { copy(deactivationRequestTime = currentTime) }
            saveConfigToDataStore()
            showToast("Deactivation requested. 24-hour cooling-off period initiated.")
        } else {
            val elapsed = currentTime - state.deactivationRequestTime
            val remainingMs = 24L * 3600L * 1000L - elapsed
            if (remainingMs > 0) {
                val remainingHours = remainingMs / (3600L * 1000L)
                val remainingMins = (remainingMs % (3600L * 1000L)) / (60L * 1000L)
                val remainingSecs = (remainingMs % (60L * 1000L)) / 1000L
                showToast("Cooling-off active. Try again in ${remainingHours}h ${remainingMins}m ${remainingSecs}s.")
            } else {
                val err = capabilities.deactivateDeviceOwner()
                if (err != null) {
                    showToast(err)
                } else {
                    showToast("aVow: System Authority Deactivated. Natively Uninstallable.")
                    updateState { copy(deactivationRequestTime = 0L) }
                    saveConfigToDataStore()
                }
            }
        }
    }

    fun addBindingTime(additionalSeconds: Long): String? {
        val state = _uiState.value
        if (state.isVowActive) {
            val currentTotalSeconds = state.days * 86400L + state.hours * 3600L + state.minutes * 60L + state.seconds
            val newTotalSeconds = currentTotalSeconds + additionalSeconds
            val d = (newTotalSeconds / 86400).toInt()
            val h = ((newTotalSeconds % 86400) / 3600).toInt()
            val m = ((newTotalSeconds % 3600) / 60).toInt()
            val s = (newTotalSeconds % 60).toInt()
            
            updateState { 
                copy(
                    days = d, hours = h, minutes = m, seconds = s,
                    vowInitialDurationSeconds = vowInitialDurationSeconds + additionalSeconds
                ) 
            }
            viewModelScope.launch {
                vowDataStore.saveCountdownState(newTotalSeconds, SystemClock.elapsedRealtime(), additionalSeconds)
            }
            showToast("Added time to the active Vow.")
            return null
        } else {
            val err = capabilities.assertBindingVow(
                activate = true,
                secureFolderEnabled = state.secureFolderEnabled,
                privateSpaceEnabled = state.privateSpaceEnabled,
                lockUninstall = state.lockUninstall,
                disallowDataWipe = state.disallowDataWipe,
                disableSafeBoot = state.disableSafeBoot,
                blockPlayStore = state.blockPlayStore,
                deactivateUsbDebugging = state.deactivateUsbDebugging
            )
            if (err != null) {
                return err
            }
            val d = (additionalSeconds / 86400).toInt()
            val h = ((additionalSeconds % 86400) / 3600).toInt()
            val m = ((additionalSeconds % 3600) / 60).toInt()
            val s = (additionalSeconds % 60).toInt()
            
            val startMs = System.currentTimeMillis()
            updateState { 
                copy(
                    isVowActive = true,
                    currentState = ScreenState.LOCKED_VAULT,
                    days = d, hours = h, minutes = m, seconds = s,
                    vowStartTimeMs = startMs,
                    vowInitialDurationSeconds = additionalSeconds,
                    frozenAllowedValue = allowedValue,
                    frozenAllowedUnit = allowedUnit,
                    frozenInterval = selectedInterval,
                    frozenQuietHoursEnabled = quietHoursEnabled,
                    frozenQuietStartHour = quietStartHour,
                    frozenQuietStartMin = quietStartMin,
                    frozenQuietEndHour = quietEndHour,
                    frozenQuietEndMin = quietEndMin,
                    frozenQuietHoursTargetAppSet = quietHoursTargetAppSet,
                    frozenQuietHoursSpecificDomain = quietHoursSpecificDomain,
                    frozenVowBlocks = vowBlocks,
                    frozenDoomscrollShieldEnabled = doomscrollShieldEnabled,
                    frozenDoomscrollAllTime = doomscrollAllTime,
                    frozenDoomscrollStartHour = doomscrollStartHour,
                    frozenDoomscrollStartMin = doomscrollStartMin,
                    frozenDoomscrollEndHour = doomscrollEndHour,
                    frozenDoomscrollEndMin = doomscrollEndMin,
                    frozenDoomscrollTargetAppSet = doomscrollTargetAppSet,
                    frozenDoomscrollCooldownMinutes = doomscrollCooldownMinutes,
                    frozenDoomscrollAllowanceMinutes = doomscrollAllowanceMinutes
                ) 
            }
            saveConfigToDataStore()
            showToast("Binding Vow Inflicted - System Authority Active")
            return null
        }
    }
}
