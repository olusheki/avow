package com.avow.app.data

import android.content.Context
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.avow.app.util.VowValidator

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vow_settings")

class VowDataStore(private val context: Context) {

    companion object {
        val IS_VOW_ACTIVE = booleanPreferencesKey("is_vow_active")
        val IS_ACTIVE_VOW_MODE = booleanPreferencesKey("is_active_vow_mode")
        val DEACTIVATION_REQUEST_TIME = longPreferencesKey("deactivation_request_time")
        val REMAINING_VOW_SECONDS = longPreferencesKey("remaining_vow_seconds")
        val LAST_SYSTEM_UPTIME_MILLIS = longPreferencesKey("last_system_uptime_millis")
        val BAN_DOMAIN_SET = stringSetPreferencesKey("ban_domain_set")
        val SECURE_FOLDER_ENABLED = booleanPreferencesKey("secure_folder_enabled")
        val PRIVATE_SPACE_ENABLED = booleanPreferencesKey("private_space_enabled")
        val LOCK_UNINSTALL = booleanPreferencesKey("lock_uninstall")
        val DISALLOW_DATA_WIPE = booleanPreferencesKey("disallow_data_wipe")
        val DISABLE_SAFE_BOOT = booleanPreferencesKey("disable_safe_boot")
        val BLOCK_PLAY_STORE = booleanPreferencesKey("block_play_store")
        val DYNAMIC_REINSTALL = booleanPreferencesKey("dynamic_reinstall")
        val DEACTIVATE_USB_DEBUGGING = booleanPreferencesKey("deactivate_usb_debugging")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_START_MIN = intPreferencesKey("quiet_start_min")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val QUIET_END_MIN = intPreferencesKey("quiet_end_min")
        val USAGE_LIMITS_UPDATED = booleanPreferencesKey("usage_limits_updated")
        val ALLOWED_VALUE = stringPreferencesKey("allowed_value")
        val ALLOWED_UNIT = stringPreferencesKey("allowed_unit")
        val SELECTED_INTERVAL = stringPreferencesKey("selected_interval")
        val TARGET_APP_SET = stringSetPreferencesKey("target_app_set")
        val SPECIFIC_DOMAIN = stringPreferencesKey("specific_domain")
        val QUIET_HOURS_TARGET_APP_SET = stringSetPreferencesKey("quiet_hours_target_app_set")
        val QUIET_HOURS_SPECIFIC_DOMAIN = stringPreferencesKey("quiet_hours_specific_domain")
        val ACCUMULATED_USAGE_MS = longPreferencesKey("accumulated_usage_ms")
        val LAST_INTERVAL_START_MS = longPreferencesKey("last_interval_start_ms")
        val IS_COLLECTIVE_LIMIT = booleanPreferencesKey("is_collective_limit")
        val PACKAGE_USAGE_JSON = stringPreferencesKey("package_usage_json")
        val VOW_BLOCKS_JSON = stringPreferencesKey("vow_blocks_json")
        val STATE_SIGNATURE = stringPreferencesKey("state_signature")
        val DOOMSCROLL_LAST_CLOSED_TIME = longPreferencesKey("doomscroll_last_closed_time")
        val DOOMSCROLL_ACCUMULATED_MS = longPreferencesKey("doomscroll_accumulated_ms")
        val TEMPORARY_LOCKOUT_END_TIME = longPreferencesKey("temporary_lockout_end_time")
        val VOW_START_TIME_MS = longPreferencesKey("vow_start_time_ms")
        val VOW_INITIAL_DURATION_SECONDS = longPreferencesKey("vow_initial_duration_seconds")
        val VOW_INTRUSIONS_COUNT = intPreferencesKey("vow_intrusions_count")
        val VOW_ALLOWED_SCREEN_TIME_MS = longPreferencesKey("vow_allowed_screen_time_ms")
    }
 
    /**
     * Checks if the signature of the stored preferences matches the content.
     * Allows first-launch (null signature) only if the vow is completely inactive.
     */
    private fun computeSignatureFromPrefs(prefs: Preferences): String {
        val isActive = prefs[IS_VOW_ACTIVE] ?: false
        val isActiveVowMode = prefs[IS_ACTIVE_VOW_MODE] ?: false
        val remaining = prefs[REMAINING_VOW_SECONDS] ?: 0L
        val lastUptime = prefs[LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
        val domainSet = prefs[BAN_DOMAIN_SET] ?: emptySet()
        val targetAppSet = prefs[TARGET_APP_SET] ?: emptySet()
        val deactivationRequestTime = prefs[DEACTIVATION_REQUEST_TIME] ?: 0L
        
        val secureFolderEnabled = prefs[SECURE_FOLDER_ENABLED] ?: false
        val privateSpaceEnabled = prefs[PRIVATE_SPACE_ENABLED] ?: false
        val lockUninstall = prefs[LOCK_UNINSTALL] ?: false
        val disallowDataWipe = prefs[DISALLOW_DATA_WIPE] ?: false
        val disableSafeBoot = prefs[DISABLE_SAFE_BOOT] ?: false
        val blockPlayStore = prefs[BLOCK_PLAY_STORE] ?: false
        val dynamicReinstall = prefs[DYNAMIC_REINSTALL] ?: false
        val deactivateUsbDebugging = prefs[DEACTIVATE_USB_DEBUGGING] ?: false
        val quietHoursEnabled = prefs[QUIET_HOURS_ENABLED] ?: false
        val quietStartHour = prefs[QUIET_START_HOUR] ?: 22
        val quietStartMin = prefs[QUIET_START_MIN] ?: 0
        val quietEndHour = prefs[QUIET_END_HOUR] ?: 7
        val quietEndMin = prefs[QUIET_END_MIN] ?: 0
        val quietHoursTargetAppSet = prefs[QUIET_HOURS_TARGET_APP_SET] ?: setOf("All Social Media")
        val quietHoursSpecificDomain = prefs[QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
        val usageLimitsUpdated = prefs[USAGE_LIMITS_UPDATED] ?: false
        val allowedValue = prefs[ALLOWED_VALUE] ?: "5"
        val allowedUnit = prefs[ALLOWED_UNIT] ?: "min"
        val selectedInterval = prefs[SELECTED_INTERVAL] ?: "hour"
        val specificDomain = prefs[SPECIFIC_DOMAIN] ?: ""
        val isCollectiveLimit = prefs[IS_COLLECTIVE_LIMIT] ?: false
        val vowBlocksJson = prefs[VOW_BLOCKS_JSON] ?: ""
        val temporaryLockoutEndTime = prefs[TEMPORARY_LOCKOUT_END_TIME] ?: 0L
        val vowStartTimeMs = prefs[VOW_START_TIME_MS] ?: 0L
        val vowInitialDurationSeconds = prefs[VOW_INITIAL_DURATION_SECONDS] ?: 0L

        return VowValidator.computeHMACSignature(
            isVowActive = isActive,
            isActiveVowMode = isActiveVowMode,
            remainingSeconds = remaining,
            lastUptimeMillis = lastUptime,
            banDomainSet = domainSet,
            targetAppSet = targetAppSet,
            deactivationRequestTime = deactivationRequestTime,
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
            specificDomain = specificDomain,
            isCollectiveLimit = isCollectiveLimit,
            vowBlocksJson = vowBlocksJson,
            temporaryLockoutEndTime = temporaryLockoutEndTime,
            vowStartTimeMs = vowStartTimeMs,
            vowInitialDurationSeconds = vowInitialDurationSeconds
        )
    }

    /**
     * Checks if the signature of the stored preferences matches the content.
     * Allows first-launch (null signature) only if the vow is completely inactive.
     */
    fun isSignatureValid(prefs: Preferences): Boolean {
        val isActive = prefs[IS_VOW_ACTIVE] ?: false
        val isActiveVowMode = prefs[IS_ACTIVE_VOW_MODE] ?: false
        val remaining = prefs[REMAINING_VOW_SECONDS] ?: 0L
        val lastUptime = prefs[LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
        val domainSet = prefs[BAN_DOMAIN_SET] ?: emptySet()
        val targetAppSet = prefs[TARGET_APP_SET] ?: emptySet()
        val deactivationRequestTime = prefs[DEACTIVATION_REQUEST_TIME] ?: 0L
        val storedSig = prefs[STATE_SIGNATURE]
        
        val secureFolderEnabled = prefs[SECURE_FOLDER_ENABLED] ?: false
        val privateSpaceEnabled = prefs[PRIVATE_SPACE_ENABLED] ?: false
        val lockUninstall = prefs[LOCK_UNINSTALL] ?: false
        val disallowDataWipe = prefs[DISALLOW_DATA_WIPE] ?: false
        val disableSafeBoot = prefs[DISABLE_SAFE_BOOT] ?: false
        val blockPlayStore = prefs[BLOCK_PLAY_STORE] ?: false
        val dynamicReinstall = prefs[DYNAMIC_REINSTALL] ?: false
        val deactivateUsbDebugging = prefs[DEACTIVATE_USB_DEBUGGING] ?: false
        val quietHoursEnabled = prefs[QUIET_HOURS_ENABLED] ?: false
        val quietStartHour = prefs[QUIET_START_HOUR] ?: 22
        val quietStartMin = prefs[QUIET_START_MIN] ?: 0
        val quietEndHour = prefs[QUIET_END_HOUR] ?: 7
        val quietEndMin = prefs[QUIET_END_MIN] ?: 0
        val quietHoursTargetAppSet = prefs[QUIET_HOURS_TARGET_APP_SET] ?: setOf("All Social Media")
        val quietHoursSpecificDomain = prefs[QUIET_HOURS_SPECIFIC_DOMAIN] ?: ""
        val usageLimitsUpdated = prefs[USAGE_LIMITS_UPDATED] ?: false
        val allowedValue = prefs[ALLOWED_VALUE] ?: "5"
        val allowedUnit = prefs[ALLOWED_UNIT] ?: "min"
        val selectedInterval = prefs[SELECTED_INTERVAL] ?: "hour"
        val specificDomain = prefs[SPECIFIC_DOMAIN] ?: ""
        val isCollectiveLimit = prefs[IS_COLLECTIVE_LIMIT] ?: false
        val vowBlocksJson = prefs[VOW_BLOCKS_JSON] ?: ""
        val temporaryLockoutEndTime = prefs[TEMPORARY_LOCKOUT_END_TIME] ?: 0L
        val vowStartTimeMs = prefs[VOW_START_TIME_MS] ?: 0L
        val vowInitialDurationSeconds = prefs[VOW_INITIAL_DURATION_SECONDS] ?: 0L

        if (storedSig == null) {
            val hasNonDefault = isActive ||
                    isActiveVowMode ||
                    remaining != 0L ||
                    lastUptime != 0L ||
                    deactivationRequestTime != 0L ||
                    domainSet.isNotEmpty() ||
                    targetAppSet.isNotEmpty() ||
                    secureFolderEnabled ||
                    privateSpaceEnabled ||
                    lockUninstall ||
                    disallowDataWipe ||
                    disableSafeBoot ||
                    blockPlayStore ||
                    dynamicReinstall ||
                    deactivateUsbDebugging ||
                    quietHoursEnabled ||
                    quietStartHour != 22 ||
                    quietStartMin != 0 ||
                    quietEndHour != 7 ||
                    quietEndMin != 0 ||
                    quietHoursTargetAppSet != setOf("All Social Media") ||
                    quietHoursSpecificDomain != "" ||
                    usageLimitsUpdated ||
                    allowedValue != "5" ||
                    allowedUnit != "min" ||
                    selectedInterval != "hour" ||
                    specificDomain != "" ||
                    isCollectiveLimit ||
                    vowBlocksJson != "" ||
                    temporaryLockoutEndTime != 0L ||
                    vowStartTimeMs != 0L ||
                    vowInitialDurationSeconds != 0L
            return !hasNonDefault
        }
        
        val computedSig = computeSignatureFromPrefs(prefs)
        return storedSig == computedSig
    }
 
    val preferencesFlow: Flow<Preferences> = context.dataStore.data.map { preferences ->
        val isUnlocked = try {
            val userManager = context.getSystemService(UserManager::class.java)
            userManager?.isUserUnlocked ?: true
        } catch (e: Throwable) {
            true
        }
        if (!isUnlocked) {
            preferences
        } else {
            try {
                if (!isSignatureValid(preferences)) {
                    // Tampered! Fall back to strict lockout configuration to protect the system.
                    val mutablePrefs = preferences.toMutablePreferences()
                    mutablePrefs[IS_VOW_ACTIVE] = true
                    mutablePrefs[IS_ACTIVE_VOW_MODE] = true
                    val newRemaining = maxOf(preferences[REMAINING_VOW_SECONDS] ?: 0L, 7L * 24L * 3600L)
                    mutablePrefs[REMAINING_VOW_SECONDS] = newRemaining
                    mutablePrefs[SECURE_FOLDER_ENABLED] = true
                    mutablePrefs[PRIVATE_SPACE_ENABLED] = true
                    mutablePrefs[LOCK_UNINSTALL] = true
                    mutablePrefs[DISALLOW_DATA_WIPE] = true
                    mutablePrefs[DISABLE_SAFE_BOOT] = true
                    mutablePrefs[BLOCK_PLAY_STORE] = true
                    mutablePrefs[DEACTIVATE_USB_DEBUGGING] = true
                    
                    // Recompute signature for safety
                    mutablePrefs[STATE_SIGNATURE] = computeSignatureFromPrefs(mutablePrefs)
                    mutablePrefs
                } else {
                    preferences
                }
            } catch (e: Throwable) {
                throw IllegalStateException("Cryptographic key storage is inaccessible. Security anchor compromised.", e)
            }
        }
    }
 
    suspend fun saveVowConfig(
        isVowActive: Boolean,
        isActiveVowMode: Boolean,
        remainingVowSeconds: Long,
        lastSystemUptimeMillis: Long,
        banDomainSet: Set<String>,
        secureFolderEnabled: Boolean,
        privateSpaceEnabled: Boolean,
        lockUninstall: Boolean,
        disallowDataWipe: Boolean,
        disableSafeBoot: Boolean,
        blockPlayStore: Boolean,
        dynamicReinstall: Boolean,
        deactivateUsbDebugging: Boolean,
        quietHoursEnabled: Boolean,
        quietStartHour: Int,
        quietStartMin: Int,
        quietEndHour: Int,
        quietEndMin: Int,
        quietHoursTargetAppSet: Set<String>,
        quietHoursSpecificDomain: String,
        usageLimitsUpdated: Boolean,
        allowedValue: String,
        allowedUnit: String,
        selectedInterval: String,
        targetAppSet: Set<String>,
        specificDomain: String,
        deactivationRequestTime: Long,
        isCollectiveLimit: Boolean,
        vowBlocksJson: String,
        temporaryLockoutEndTime: Long = 0L,
        vowStartTimeMs: Long = 0L,
        vowInitialDurationSeconds: Long = 0L
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = isVowActive
            preferences[IS_ACTIVE_VOW_MODE] = isActiveVowMode
            preferences[REMAINING_VOW_SECONDS] = remainingVowSeconds
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = lastSystemUptimeMillis
            preferences[BAN_DOMAIN_SET] = banDomainSet
            preferences[SECURE_FOLDER_ENABLED] = secureFolderEnabled
            preferences[PRIVATE_SPACE_ENABLED] = privateSpaceEnabled
            preferences[LOCK_UNINSTALL] = lockUninstall
            preferences[DISALLOW_DATA_WIPE] = disallowDataWipe
            preferences[DISABLE_SAFE_BOOT] = disableSafeBoot
            preferences[BLOCK_PLAY_STORE] = blockPlayStore
            preferences[DYNAMIC_REINSTALL] = dynamicReinstall
            preferences[DEACTIVATE_USB_DEBUGGING] = deactivateUsbDebugging
            preferences[QUIET_HOURS_ENABLED] = quietHoursEnabled
            preferences[QUIET_START_HOUR] = quietStartHour
            preferences[QUIET_START_MIN] = quietStartMin
            preferences[QUIET_END_HOUR] = quietEndHour
            preferences[QUIET_END_MIN] = quietEndMin
            preferences[QUIET_HOURS_TARGET_APP_SET] = quietHoursTargetAppSet
            preferences[QUIET_HOURS_SPECIFIC_DOMAIN] = quietHoursSpecificDomain
            preferences[USAGE_LIMITS_UPDATED] = usageLimitsUpdated
            preferences[ALLOWED_VALUE] = allowedValue
            preferences[ALLOWED_UNIT] = allowedUnit
            preferences[SELECTED_INTERVAL] = selectedInterval
            preferences[TARGET_APP_SET] = targetAppSet
            preferences[SPECIFIC_DOMAIN] = specificDomain
            preferences[DEACTIVATION_REQUEST_TIME] = deactivationRequestTime
            preferences[IS_COLLECTIVE_LIMIT] = isCollectiveLimit
            preferences[VOW_BLOCKS_JSON] = vowBlocksJson
            preferences[TEMPORARY_LOCKOUT_END_TIME] = temporaryLockoutEndTime
            preferences[VOW_START_TIME_MS] = vowStartTimeMs
            preferences[VOW_INITIAL_DURATION_SECONDS] = vowInitialDurationSeconds

            // Compute and save cryptographic signature using HMAC-SHA256
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveCountdownState(remainingSeconds: Long, lastSystemUptimeMillis: Long) {
        context.dataStore.edit { preferences ->
            val clampedSeconds = VowValidator.clampRemainingSeconds(remainingSeconds)
            preferences[REMAINING_VOW_SECONDS] = clampedSeconds
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = lastSystemUptimeMillis
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveVowActive(isActive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = isActive
            preferences[IS_ACTIVE_VOW_MODE] = isActive
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveDeactivationRequestTime(timeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[DEACTIVATION_REQUEST_TIME] = timeMs
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveAccumulatedUsage(usageMs: Long, lastIntervalStartMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[ACCUMULATED_USAGE_MS] = usageMs
            preferences[LAST_INTERVAL_START_MS] = lastIntervalStartMs
        }
    }

    suspend fun saveTemporaryLockoutEndTime(endTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[TEMPORARY_LOCKOUT_END_TIME] = endTime
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveDoomscrollAccumulatedMs(accumulatedMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[DOOMSCROLL_ACCUMULATED_MS] = accumulatedMs
        }
    }

    suspend fun saveDoomscrollLastClosedTime(lastClosedTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[DOOMSCROLL_LAST_CLOSED_TIME] = lastClosedTime
        }
    }

    suspend fun clearVowConfig() {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = false
            preferences[IS_ACTIVE_VOW_MODE] = false
            preferences[REMAINING_VOW_SECONDS] = 0L
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = 0L
            preferences[ACCUMULATED_USAGE_MS] = 0L
            preferences[LAST_INTERVAL_START_MS] = 0L
            preferences[DEACTIVATION_REQUEST_TIME] = 0L
            preferences[IS_COLLECTIVE_LIMIT] = false
            preferences[PACKAGE_USAGE_JSON] = ""
            preferences[VOW_BLOCKS_JSON] = ""
            preferences[TEMPORARY_LOCKOUT_END_TIME] = 0L
            preferences[DOOMSCROLL_LAST_CLOSED_TIME] = 0L
            preferences[DOOMSCROLL_ACCUMULATED_MS] = 0L
            preferences[VOW_START_TIME_MS] = 0L
            preferences[VOW_INITIAL_DURATION_SECONDS] = 0L
            preferences[VOW_INTRUSIONS_COUNT] = 0
            preferences[VOW_ALLOWED_SCREEN_TIME_MS] = 0L
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun incrementIntrusionsCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[VOW_INTRUSIONS_COUNT] ?: 0
            preferences[VOW_INTRUSIONS_COUNT] = current + 1
        }
    }

    suspend fun addAllowedScreenTimeMs(ms: Long) {
        context.dataStore.edit { preferences ->
            val current = preferences[VOW_ALLOWED_SCREEN_TIME_MS] ?: 0L
            preferences[VOW_ALLOWED_SCREEN_TIME_MS] = current + ms
        }
    }

    suspend fun savePackageUsage(packageUsageJson: String, lastIntervalStartMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PACKAGE_USAGE_JSON] = packageUsageJson
            preferences[LAST_INTERVAL_START_MS] = lastIntervalStartMs
        }
    }
}


// Extension to avoid compilation issues if referencing context.dataStore elsewhere
private val DataStore<Preferences>.preferencesFlow: Flow<Preferences>
    get() = data

object PackageUsageSerializer {
    @Synchronized
    fun serialize(map: Map<String, Long>): String {
        return map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }
    }

    @Synchronized
    fun deserialize(serialized: String?): Map<String, Long> {
        if (serialized.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, Long>()
        val pairs = serialized.split(";")
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val pkg = parts[0]
                val usage = parts[1].toLongOrNull()
                if (usage != null) {
                    result[pkg] = usage
                }
            }
        }
        return result
    }
}
