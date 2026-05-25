package com.avow.app.data

import android.content.Context
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
        val STATE_SIGNATURE = stringPreferencesKey("state_signature")
    }
 
    /**
     * Checks if the signature of the stored preferences matches the content.
     * Allows first-launch (null signature) only if the vow is completely inactive.
     */
    fun isSignatureValid(prefs: Preferences): Boolean {
        val isActive = prefs[IS_VOW_ACTIVE] ?: false
        val remaining = prefs[REMAINING_VOW_SECONDS] ?: 0L
        val lastUptime = prefs[LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
        val domainSet = prefs[BAN_DOMAIN_SET] ?: emptySet()
        val targetAppSet = prefs[TARGET_APP_SET] ?: emptySet()
        val storedSig = prefs[STATE_SIGNATURE]
        
        if (storedSig == null) {
            return !isActive && remaining == 0L
        }
        
        val computedSig = VowValidator.computeStateSignature(isActive, remaining, lastUptime, domainSet, targetAppSet)
        return storedSig == computedSig
    }
 
    val preferencesFlow: Flow<Preferences> = context.dataStore.data.map { preferences ->
        if (!isSignatureValid(preferences)) {
            // Tampered! Fall back to strict lockout configuration to protect the system.
            val mutablePrefs = preferences.toMutablePreferences()
            mutablePrefs[IS_VOW_ACTIVE] = true
            mutablePrefs[REMAINING_VOW_SECONDS] = maxOf(preferences[REMAINING_VOW_SECONDS] ?: 0L, 7L * 24L * 3600L)
            mutablePrefs[SECURE_FOLDER_ENABLED] = true
            mutablePrefs[PRIVATE_SPACE_ENABLED] = true
            mutablePrefs[LOCK_UNINSTALL] = true
            mutablePrefs[DISALLOW_DATA_WIPE] = true
            mutablePrefs[DISABLE_SAFE_BOOT] = true
            mutablePrefs[BLOCK_PLAY_STORE] = true
            mutablePrefs[DEACTIVATE_USB_DEBUGGING] = true
            mutablePrefs
        } else {
            preferences
        }
    }
 
    suspend fun saveVowConfig(
        isVowActive: Boolean,
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
        specificDomain: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = isVowActive
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

            // Compute and save cryptographic signature
            preferences[STATE_SIGNATURE] = VowValidator.computeStateSignature(
                isVowActive, remainingVowSeconds, lastSystemUptimeMillis, banDomainSet, targetAppSet
            )
        }
    }

    suspend fun saveCountdownState(remainingSeconds: Long, lastSystemUptimeMillis: Long) {
        context.dataStore.edit { preferences ->
            val clampedSeconds = VowValidator.clampRemainingSeconds(remainingSeconds)
            preferences[REMAINING_VOW_SECONDS] = clampedSeconds
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = lastSystemUptimeMillis
            
            val isActive = preferences[IS_VOW_ACTIVE] ?: false
            val domainSet = preferences[BAN_DOMAIN_SET] ?: emptySet()
            val targetAppSet = preferences[TARGET_APP_SET] ?: emptySet()
            preferences[STATE_SIGNATURE] = VowValidator.computeStateSignature(
                isActive, clampedSeconds, lastSystemUptimeMillis, domainSet, targetAppSet
            )
        }
    }

    suspend fun saveVowActive(isActive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = isActive
            
            val remaining = preferences[REMAINING_VOW_SECONDS] ?: 0L
            val lastUptime = preferences[LAST_SYSTEM_UPTIME_MILLIS] ?: 0L
            val domainSet = preferences[BAN_DOMAIN_SET] ?: emptySet()
            val targetAppSet = preferences[TARGET_APP_SET] ?: emptySet()
            preferences[STATE_SIGNATURE] = VowValidator.computeStateSignature(
                isActive, remaining, lastUptime, domainSet, targetAppSet
            )
        }
    }

    suspend fun saveAccumulatedUsage(usageMs: Long, lastIntervalStartMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[ACCUMULATED_USAGE_MS] = usageMs
            preferences[LAST_INTERVAL_START_MS] = lastIntervalStartMs
        }
    }

    suspend fun clearVowConfig() {
        context.dataStore.edit { preferences ->
            preferences[IS_VOW_ACTIVE] = false
            preferences[REMAINING_VOW_SECONDS] = 0L
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = 0L
            preferences[ACCUMULATED_USAGE_MS] = 0L
            preferences[LAST_INTERVAL_START_MS] = 0L
            
            val domainSet = preferences[BAN_DOMAIN_SET] ?: emptySet()
            val targetAppSet = preferences[TARGET_APP_SET] ?: emptySet()
            preferences[STATE_SIGNATURE] = VowValidator.computeStateSignature(
                false, 0L, 0L, domainSet, targetAppSet
            )
        }
    }
}


// Extension to avoid compilation issues if referencing context.dataStore elsewhere
private val DataStore<Preferences>.preferencesFlow: Flow<Preferences>
    get() = data
