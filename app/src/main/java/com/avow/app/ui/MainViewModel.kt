package com.avow.app.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avow.app.data.VowDataStore
import com.avow.app.model.VowBlock
import com.avow.app.receiver.DeviceAdmin
import com.avow.app.util.VowValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val vowDataStore = VowDataStore(application)

    init {
        loadInstalledApps()
        loadState()
        startCountdownTicker()
    }

    private fun loadInstalledApps() {
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

    private fun loadState() {
        viewModelScope.launch {
            try {
                val prefs = vowDataStore.preferencesFlow.first()
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
                val temporaryLockoutEndTime = prefs[VowDataStore.TEMPORARY_LOCKOUT_END_TIME] ?: 0L
                val doomscrollShieldEnabled = prefs[VowDataStore.DOOMSCROLL_SHIELD_ENABLED] ?: false
                val doomscrollAllTime = prefs[VowDataStore.DOOMSCROLL_ALL_TIME] ?: false
                val doomscrollStartHour = prefs[VowDataStore.DOOMSCROLL_START_HOUR] ?: 23
                val doomscrollStartMin = prefs[VowDataStore.DOOMSCROLL_START_MIN] ?: 0
                val doomscrollEndHour = prefs[VowDataStore.DOOMSCROLL_END_HOUR] ?: 5
                val doomscrollEndMin = prefs[VowDataStore.DOOMSCROLL_END_MIN] ?: 0
                val doomscrollTargetAppSet = prefs[VowDataStore.DOOMSCROLL_TARGET_APP_SET] ?: emptySet()
                val doomscrollCooldownMinutes = prefs[VowDataStore.DOOMSCROLL_COOLDOWN_MINUTES] ?: 60
                
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

                val dpm = getApplication<Application>().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val isDeviceOwner = dpm.isDeviceOwnerApp(getApplication<Application>().packageName)

                var days = 0
                var hours = 0
                var minutes = 0
                var seconds = 0
                var finalIsVowActive = isVowActive
                var currentState = ScreenState.UNLOCKED_VAULT

                val savedRemaining = prefs[VowDataStore.REMAINING_VOW_SECONDS] ?: 0L
                if (isVowActive) {
                    val lastUptime = prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
                    val currentUptime = SystemClock.elapsedRealtime()
                    
                    val finalRemaining = if (savedRemaining > 0) {
                        VowValidator.calculateRemainingSeconds(currentUptime, lastUptime, savedRemaining)
                    } else {
                        0L
                    }
                    
                    if (finalRemaining > 0) {
                        days = (finalRemaining / 86400).toInt()
                        hours = ((finalRemaining % 86400) / 3600).toInt()
                        minutes = ((finalRemaining % 3600) / 60).toInt()
                        seconds = (finalRemaining % 60).toInt()
                        currentState = ScreenState.LOCKED_VAULT
                    } else {
                        finalIsVowActive = false
                        DeviceAdmin.assertBindingVow(
                            context = getApplication(),
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
                        frozenDoomscrollShieldEnabled = doomscrollShieldEnabled,
                        frozenDoomscrollAllTime = doomscrollAllTime,
                        frozenDoomscrollStartHour = doomscrollStartHour,
                        frozenDoomscrollStartMin = doomscrollStartMin,
                        frozenDoomscrollEndHour = doomscrollEndHour,
                        frozenDoomscrollEndMin = doomscrollEndMin,
                        frozenDoomscrollTargetAppSet = doomscrollTargetAppSet,
                        frozenDoomscrollCooldownMinutes = doomscrollCooldownMinutes,
                        days = days,
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                        vowStartTimeMs = vowStartTimeMs,
                        vowInitialDurationSeconds = vowInitialDurationSeconds,
                        temporaryLockoutEndTime = temporaryLockoutEndTime,
                        currentState = currentState,
                        isDeviceOwner = isDeviceOwner,
                        isLoaded = true
                    )
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
                
                // Temporary lockout exit check
                if (state.currentState == ScreenState.TEMPORARY_LOCKOUT) {
                    if (System.currentTimeMillis() >= state.temporaryLockoutEndTime) {
                        updateState { copy(currentState = if (isVowActive) ScreenState.LOCKED_VAULT else ScreenState.UNLOCKED_VAULT) }
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
                            DeviceAdmin.assertBindingVow(
                                context = getApplication(),
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
                                    isActiveVowMode = false, 
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
                    temporaryLockoutEndTime = state.temporaryLockoutEndTime,
                    doomscrollShieldEnabled = state.doomscrollShieldEnabled,
                    doomscrollAllTime = state.doomscrollAllTime,
                    doomscrollStartHour = state.doomscrollStartHour,
                    doomscrollStartMin = state.doomscrollStartMin,
                    doomscrollEndHour = state.doomscrollEndHour,
                    doomscrollEndMin = state.doomscrollEndMin,
                    doomscrollTargetAppSet = state.doomscrollTargetAppSet,
                    doomscrollCooldownMinutes = state.doomscrollCooldownMinutes
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

    fun setScreenState(state: ScreenState) {
        updateState { copy(previousState = currentState, currentState = state) }
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
                showToast("Cooling-off active. Try again in \${remainingHours}h \${remainingMins}m \${remainingSecs}s.")
            } else {
                val err = DeviceAdmin.deactivateDeviceOwner(getApplication())
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
            val err = DeviceAdmin.assertBindingVow(
                context = getApplication(),
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
                    frozenDoomscrollCooldownMinutes = doomscrollCooldownMinutes
                ) 
            }
            saveConfigToDataStore()
            showToast("Binding Vow Inflicted - System Authority Active")
            return null
        }
    }
}
