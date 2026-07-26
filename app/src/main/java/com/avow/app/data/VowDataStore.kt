package com.avow.app.data

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.avow.app.util.VowValidator
import com.avow.app.worker.ReminderInputs

// A corrupt prefs file would otherwise make every read/write throw forever, hard-bricking the app.
// Fail OPEN: replace it with empty prefs (which validate as a clean first-launch state, no tamper
// escalation) — losing an in-flight vow is the safe direction versus a permanently unusable app.
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vow_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class VowDataStore(private val context: Context) {

    companion object {
        private const val TAG = "VowDataStore"

        // D-3: an invalid signature can be innocent (an OS update / security event invalidating the
        // AndroidKeyStore key), which is indistinguishable from real tampering. First detection now
        // applies a *softened* 24h lockout instead of the former 7-day one, plus a clear notification.
        const val TAMPER_LOCKOUT_SECONDS = 24L * 3600L

        // Why a temporary lockout is showing, so the overlay can word itself correctly. Cosmetic /
        // not security-signed. DOOMSCROLL = the scroll allowance ran out; EVASION = a blocked app was
        // caught running in a pop-out / side-by-side window to dodge enforcement.
        const val LOCKOUT_REASON_DOOMSCROLL = "DOOMSCROLL"
        const val LOCKOUT_REASON_EVASION = "EVASION"

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
        // elapsedRealtime()-based (uptime since boot), NOT wall-clock — so it's meaningless after a
        // reboot (uptime restarts near zero) and BootReceiver clears it. Read/enforced across
        // BlockerService, MainViewModel, and BootReceiver; keep all comparisons on elapsedRealtime.
        val TEMPORARY_LOCKOUT_END_TIME = longPreferencesKey("temporary_lockout_end_time")
        // Cosmetic reason string for the active temporary lockout (not part of the tamper signature).
        val TEMPORARY_LOCKOUT_REASON = stringPreferencesKey("temporary_lockout_reason")
        val VOW_START_TIME_MS = longPreferencesKey("vow_start_time_ms")
        val VOW_INITIAL_DURATION_SECONDS = longPreferencesKey("vow_initial_duration_seconds")
        val VOW_PICKUPS_COUNT = intPreferencesKey("vow_pickups_count")
        val VOW_ALLOWED_SCREEN_TIME_MS = longPreferencesKey("vow_allowed_screen_time_ms")
        val DOOMSCROLL_SHIELD_ENABLED = booleanPreferencesKey("doomscroll_shield_enabled")
        val DOOMSCROLL_ALL_TIME = booleanPreferencesKey("doomscroll_all_time")
        val DOOMSCROLL_START_HOUR = intPreferencesKey("doomscroll_start_hour")
        val DOOMSCROLL_START_MIN = intPreferencesKey("doomscroll_start_min")
        val DOOMSCROLL_END_HOUR = intPreferencesKey("doomscroll_end_hour")
        val DOOMSCROLL_END_MIN = intPreferencesKey("doomscroll_end_min")
        val DOOMSCROLL_TARGET_APP_SET = stringSetPreferencesKey("doomscroll_target_app_set")
        val DOOMSCROLL_COOLDOWN_MINUTES = intPreferencesKey("doomscroll_cooldown_minutes")
        val DOOMSCROLL_ALLOWANCE_MINUTES = intPreferencesKey("doomscroll_allowance_minutes")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        // User preference (not security-signed): whether the browser-agnostic domain-filter VPN is on.
        val VPN_DOMAIN_BLOCKING_ENABLED = booleanPreferencesKey("vpn_domain_blocking_enabled")
        // Idle-nudge bookkeeping (not security-signed): last app open + last reminder shown.
        val LAST_APP_OPEN_MS = longPreferencesKey("last_app_open_ms")
        val LAST_REMINDER_SHOWN_MS = longPreferencesKey("last_reminder_shown_ms")
        // Packages the full flavor suspended for pop-out/split evasion (not security-signed). Persisted
        // so the service can un-suspend them after a process death even if the target set later changes.
        val EVASION_SUSPENDED_PACKAGES = stringSetPreferencesKey("evasion_suspended_packages")
        // Lite pop-out penalty bookkeeping. VOW_START_TIME of the vow already penalized (once per vow),
        // and a nonce the ViewModel watches to re-sync its live countdown after the service extends the
        // persisted vow. Not security-signed (the extended REMAINING/anchor they piggyback on IS signed).
        val EVASION_PENALIZED_VOW_START = longPreferencesKey("evasion_penalized_vow_start")
        val EVASION_PENALTY_NONCE = longPreferencesKey("evasion_penalty_nonce")
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
        val quietHoursTargetAppSet = prefs[QUIET_HOURS_TARGET_APP_SET] ?: emptySet()
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
        val doomscrollShieldEnabled = prefs[DOOMSCROLL_SHIELD_ENABLED] ?: false
        val doomscrollAllTime = prefs[DOOMSCROLL_ALL_TIME] ?: false
        val doomscrollStartHour = prefs[DOOMSCROLL_START_HOUR] ?: 23
        val doomscrollStartMin = prefs[DOOMSCROLL_START_MIN] ?: 0
        val doomscrollEndHour = prefs[DOOMSCROLL_END_HOUR] ?: 5
        val doomscrollEndMin = prefs[DOOMSCROLL_END_MIN] ?: 0
        val doomscrollTargetAppSet = prefs[DOOMSCROLL_TARGET_APP_SET] ?: emptySet()
        val doomscrollCooldownMinutes = prefs[DOOMSCROLL_COOLDOWN_MINUTES] ?: 60
        val doomscrollAllowanceMinutes = prefs[DOOMSCROLL_ALLOWANCE_MINUTES] ?: 15
        val isOnboardingCompleted = prefs[IS_ONBOARDING_COMPLETED] ?: false

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
            vowInitialDurationSeconds = vowInitialDurationSeconds,
            doomscrollShieldEnabled = doomscrollShieldEnabled,
            doomscrollAllTime = doomscrollAllTime,
            doomscrollStartHour = doomscrollStartHour,
            doomscrollStartMin = doomscrollStartMin,
            doomscrollEndHour = doomscrollEndHour,
            doomscrollEndMin = doomscrollEndMin,
            doomscrollTargetAppSet = doomscrollTargetAppSet,
            doomscrollCooldownMinutes = doomscrollCooldownMinutes,
            doomscrollAllowanceMinutes = doomscrollAllowanceMinutes,
            isOnboardingCompleted = isOnboardingCompleted
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
        val quietHoursTargetAppSet = prefs[QUIET_HOURS_TARGET_APP_SET] ?: emptySet()
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
        val doomscrollShieldEnabled = prefs[DOOMSCROLL_SHIELD_ENABLED] ?: false
        val doomscrollAllTime = prefs[DOOMSCROLL_ALL_TIME] ?: false
        val doomscrollStartHour = prefs[DOOMSCROLL_START_HOUR] ?: 23
        val doomscrollStartMin = prefs[DOOMSCROLL_START_MIN] ?: 0
        val doomscrollEndHour = prefs[DOOMSCROLL_END_HOUR] ?: 5
        val doomscrollEndMin = prefs[DOOMSCROLL_END_MIN] ?: 0
        val doomscrollTargetAppSet = prefs[DOOMSCROLL_TARGET_APP_SET] ?: emptySet()
        val doomscrollCooldownMinutes = prefs[DOOMSCROLL_COOLDOWN_MINUTES] ?: 60
        val doomscrollAllowanceMinutes = prefs[DOOMSCROLL_ALLOWANCE_MINUTES] ?: 15
        val isOnboardingCompleted = prefs[IS_ONBOARDING_COMPLETED] ?: false

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
                    quietHoursTargetAppSet.isNotEmpty() ||
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
                    vowInitialDurationSeconds != 0L ||
                    doomscrollShieldEnabled ||
                    doomscrollAllTime ||
                    doomscrollStartHour != 23 ||
                    doomscrollStartMin != 0 ||
                    doomscrollEndHour != 5 ||
                    doomscrollEndMin != 0 ||
                    doomscrollTargetAppSet.isNotEmpty() ||
                    doomscrollCooldownMinutes != 60 ||
                    doomscrollAllowanceMinutes != 15 ||
                    isOnboardingCompleted
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
            val valid = try {
                isSignatureValid(preferences)
            } catch (e: Throwable) {
                // The key storage is unreadable (an OS update or security event can invalidate the
                // AndroidKeyStore key). We can't verify the signature, so fail OPEN and emit the raw
                // prefs rather than crash-loop every collector. Enforcement still runs from
                // BlockerService's in-memory state; only the cryptographic anchor is unavailable.
                Log.e(TAG, "Signature check failed (key storage inaccessible); emitting raw prefs", e)
                null
            }
            if (valid == false) {
                // Signature invalid — tampering, OR an innocent key invalidation we cannot tell apart.
                // Fall back to a *softened* 24h lockout (not the old 7-day) so a blameless user isn't
                // punished; BlockerService posts an explanatory notification when it persists this.
                val mutablePrefs = preferences.toMutablePreferences()
                applyTamperLockout(mutablePrefs)
                mutablePrefs[STATE_SIGNATURE] = computeSignatureFromPrefs(mutablePrefs)
                mutablePrefs
            } else {
                preferences
            }
        }
    }

    /** The softened tamper/key-loss fallback state (D-3): a 24h vow floor + the protective locks. */
    private fun applyTamperLockout(prefs: MutablePreferences) {
        prefs[IS_VOW_ACTIVE] = true
        // Deliberately NOT forcing IS_ACTIVE_VOW_MODE: the 24h vow floor alone makes enforcement
        // active for the window, and clearVowConfig preserves Active mode — so forcing it here would
        // strand a blameless user (an OS update can invalidate the key) in permanent Active mode
        // after the lockout lifts.
        prefs[REMAINING_VOW_SECONDS] = maxOf(prefs[REMAINING_VOW_SECONDS] ?: 0L, TAMPER_LOCKOUT_SECONDS)
        // Anchor the countdown to NOW, or the "24h" is measured against a stale uptime and can read
        // as already-elapsed (instant, toothless expiry) or otherwise mis-count.
        prefs[LAST_SYSTEM_UPTIME_MILLIS] = android.os.SystemClock.elapsedRealtime()
        prefs[SECURE_FOLDER_ENABLED] = true
        prefs[PRIVATE_SPACE_ENABLED] = true
        prefs[LOCK_UNINSTALL] = true
        prefs[DISALLOW_DATA_WIPE] = true
        prefs[DISABLE_SAFE_BOOT] = true
        prefs[BLOCK_PLAY_STORE] = true
        prefs[DEACTIVATE_USB_DEBUGGING] = true
    }

    /**
     * Persists the strict-lockout fallback when the stored signature is invalid. [preferencesFlow]
     * already emits the corrected state in-memory for immediate safety, but never wrote it back — so
     * it only "stuck" on the next unrelated save. Call once at startup. Idempotent: once a valid
     * signature is written, subsequent calls are no-ops (so it can't loop or fight a valid state).
     */
    suspend fun repairTamperedStateIfNeeded(): Boolean {
        val isUnlocked = try {
            context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        } catch (e: Throwable) {
            true
        }
        if (!isUnlocked) return false
        var applied = false
        context.dataStore.edit { preferences ->
            val valid = try {
                isSignatureValid(preferences)
            } catch (e: Throwable) {
                // Key storage unreadable — leave the state untouched rather than escalate on an
                // unverifiable signature (matches the fail-open read path).
                Log.e(TAG, "Signature check failed during repair; leaving state untouched", e)
                return@edit
            }
            if (valid) return@edit
            applyTamperLockout(preferences)
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
            applied = true
        }
        return applied
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
        vowStartTimeMs: Long = 0L,
        vowInitialDurationSeconds: Long = 0L,
        doomscrollShieldEnabled: Boolean = false,
        doomscrollAllTime: Boolean = false,
        doomscrollStartHour: Int = 23,
        doomscrollStartMin: Int = 0,
        doomscrollEndHour: Int = 5,
        doomscrollEndMin: Int = 0,
        doomscrollTargetAppSet: Set<String> = emptySet(),
        doomscrollCooldownMinutes: Int = 60,
        doomscrollAllowanceMinutes: Int = 15,
        resetStats: Boolean = false
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
            preferences[VOW_START_TIME_MS] = vowStartTimeMs
            preferences[VOW_INITIAL_DURATION_SECONDS] = vowInitialDurationSeconds
            preferences[DOOMSCROLL_SHIELD_ENABLED] = doomscrollShieldEnabled
            preferences[DOOMSCROLL_ALL_TIME] = doomscrollAllTime
            preferences[DOOMSCROLL_START_HOUR] = doomscrollStartHour
            preferences[DOOMSCROLL_START_MIN] = doomscrollStartMin
            preferences[DOOMSCROLL_END_HOUR] = doomscrollEndHour
            preferences[DOOMSCROLL_END_MIN] = doomscrollEndMin
            preferences[DOOMSCROLL_TARGET_APP_SET] = doomscrollTargetAppSet
            preferences[DOOMSCROLL_COOLDOWN_MINUTES] = doomscrollCooldownMinutes
            preferences[DOOMSCROLL_ALLOWANCE_MINUTES] = doomscrollAllowanceMinutes

            if (resetStats) {
                preferences[VOW_PICKUPS_COUNT] = 0
                preferences[VOW_ALLOWED_SCREEN_TIME_MS] = 0L
            }

            // Compute and save cryptographic signature using HMAC-SHA256
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveCountdownState(remainingSeconds: Long, lastSystemUptimeMillis: Long, additionalDurationSeconds: Long = 0L) {
        context.dataStore.edit { preferences ->
            val clampedSeconds = VowValidator.clampRemainingSeconds(remainingSeconds)
            preferences[REMAINING_VOW_SECONDS] = clampedSeconds
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = lastSystemUptimeMillis
            
            if (additionalDurationSeconds > 0L) {
                val currentInitial = preferences[VOW_INITIAL_DURATION_SECONDS] ?: 0L
                preferences[VOW_INITIAL_DURATION_SECONDS] = currentInitial + additionalDurationSeconds
            }
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    /**
     * Lite pop-out/split penalty: adds [penaltySeconds] to the *current* vow (re-anchoring the
     * countdown to now) and bumps [EVASION_PENALTY_NONCE] so a running ViewModel re-syncs its live
     * countdown — otherwise the ViewModel's ticker would expire the vow at the original time and the
     * penalty would be silently defeated. Applied at most once per vow (keyed on VOW_START_TIME), and
     * only while a real, running vow exists. Returns true iff a penalty was actually applied.
     */
    suspend fun applyEvasionVowPenalty(penaltySeconds: Long): Boolean {
        var applied = false
        context.dataStore.edit { prefs ->
            if (prefs[IS_VOW_ACTIVE] != true) return@edit
            val vowStart = prefs[VOW_START_TIME_MS] ?: 0L
            if (vowStart == 0L) return@edit                       // no vow identity to guard once-per-vow
            if (prefs[EVASION_PENALIZED_VOW_START] == vowStart) return@edit  // already penalized this vow
            val now = android.os.SystemClock.elapsedRealtime()
            val live = VowValidator.calculateRemainingSeconds(now, prefs[LAST_SYSTEM_UPTIME_MILLIS] ?: 0L, prefs[REMAINING_VOW_SECONDS] ?: 0L)
            if (live <= 0L) return@edit                           // vow effectively over; nothing to extend
            prefs[REMAINING_VOW_SECONDS] = VowValidator.clampRemainingSeconds(live + penaltySeconds)
            prefs[LAST_SYSTEM_UPTIME_MILLIS] = now
            prefs[VOW_INITIAL_DURATION_SECONDS] = (prefs[VOW_INITIAL_DURATION_SECONDS] ?: 0L) + penaltySeconds
            prefs[EVASION_PENALIZED_VOW_START] = vowStart
            prefs[EVASION_PENALTY_NONCE] = System.currentTimeMillis()
            prefs[STATE_SIGNATURE] = computeSignatureFromPrefs(prefs)
            applied = true
        }
        return applied
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_COMPLETED] = completed
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveDeactivationRequestTime(timeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[DEACTIVATION_REQUEST_TIME] = timeMs
            
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    suspend fun saveTemporaryLockoutEndTime(endTime: Long, reason: String = LOCKOUT_REASON_DOOMSCROLL) {
        context.dataStore.edit { preferences ->
            preferences[TEMPORARY_LOCKOUT_END_TIME] = endTime
            // Clearing the lockout (endTime <= 0) drops the reason; otherwise record why it fired.
            if (endTime <= 0L) preferences.remove(TEMPORARY_LOCKOUT_REASON)
            else preferences[TEMPORARY_LOCKOUT_REASON] = reason
            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }
    }

    /** Records which packages the full flavor currently has suspended for evasion (empty = none). */
    suspend fun saveEvasionSuspendedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            if (packages.isEmpty()) preferences.remove(EVASION_SUSPENDED_PACKAGES)
            else preferences[EVASION_SUSPENDED_PACKAGES] = packages
        }
    }

    suspend fun saveVpnDomainBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VPN_DOMAIN_BLOCKING_ENABLED] = enabled
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

    /** Records that the user opened the app now, resetting the idle-nudge clock. */
    suspend fun saveLastAppOpen(nowMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_APP_OPEN_MS] = nowMs
        }
    }

    /** Records that an idle "set a vow" reminder was shown, to rate-limit further nudges. */
    suspend fun saveLastReminderShown(nowMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_REMINDER_SHOWN_MS] = nowMs
        }
    }

    /** Snapshot of everything the idle-reminder worker needs to decide whether to nudge. */
    suspend fun readReminderInputs(): ReminderInputs {
        val prefs = context.dataStore.data.first()
        return ReminderInputs(
            isVowActive = prefs[IS_VOW_ACTIVE] ?: false,
            onboardingCompleted = prefs[IS_ONBOARDING_COMPLETED] ?: false,
            lastAppOpenMs = prefs[LAST_APP_OPEN_MS] ?: 0L,
            lastReminderShownMs = prefs[LAST_REMINDER_SHOWN_MS] ?: 0L
        )
    }

    suspend fun clearVowConfig() {
        var sessionToLog: com.avow.app.data.history.VowSession? = null
        
        context.dataStore.edit { preferences ->
            val isActive = preferences[IS_VOW_ACTIVE] ?: false
            if (isActive) {
                val startTime = preferences[VOW_START_TIME_MS] ?: 0L
                val initialDurationSeconds = preferences[VOW_INITIAL_DURATION_SECONDS] ?: 0L
                val pickups = preferences[VOW_PICKUPS_COUNT] ?: 0
                val allowedScreenTime = preferences[VOW_ALLOWED_SCREEN_TIME_MS] ?: 0L
                val endTime = System.currentTimeMillis()
                
                // Clamp duration to prevent negative values on clock rollbacks
                val durationSecs = maxOf(1L, if (initialDurationSeconds > 0) initialDurationSeconds else ((endTime - startTime) / 1000))
                val zen = VowValidator.calculateZenScore(
                    pickups = pickups,
                    allowedScreenTimeMs = allowedScreenTime,
                    durationSeconds = durationSecs
                )
                
                sessionToLog = com.avow.app.data.history.VowSession(
                    startTimeMillis = startTime,
                    endTimeMillis = endTime,
                    durationSeconds = durationSecs,
                    pickups = pickups,
                    allowedScreenTimeMs = allowedScreenTime,
                    zenScore = zen
                )
            }

            // Clear only transient vow-session state. User configuration (doomscroll shield
            // settings, scheduled blocks) must survive vow completion — previously it was wiped
            // here, silently disabling the shield and deleting the user's blocks after every vow.
            // An in-flight doomscroll cooldown (TEMPORARY_LOCKOUT_END_TIME) is also preserved:
            // it is a doomscroll consequence, independent of the vow that just ended.
            preferences[IS_VOW_ACTIVE] = false
            // IS_ACTIVE_VOW_MODE is intentionally preserved — it's the user's Passive/Active
            // preference and should persist across vows, not reset to Passive on completion.
            preferences[REMAINING_VOW_SECONDS] = 0L
            preferences[LAST_SYSTEM_UPTIME_MILLIS] = 0L
            preferences[ACCUMULATED_USAGE_MS] = 0L
            preferences[LAST_INTERVAL_START_MS] = 0L
            preferences[DEACTIVATION_REQUEST_TIME] = 0L
            preferences[IS_COLLECTIVE_LIMIT] = false
            preferences[PACKAGE_USAGE_JSON] = ""
            preferences[DOOMSCROLL_LAST_CLOSED_TIME] = 0L
            preferences[DOOMSCROLL_ACCUMULATED_MS] = 0L
            preferences[VOW_START_TIME_MS] = 0L
            preferences[VOW_INITIAL_DURATION_SECONDS] = 0L
            preferences[VOW_PICKUPS_COUNT] = 0
            preferences[VOW_ALLOWED_SCREEN_TIME_MS] = 0L

            preferences[STATE_SIGNATURE] = computeSignatureFromPrefs(preferences)
        }

        sessionToLog?.let { session ->
            try {
                com.avow.app.data.history.VowDatabase.getDatabase(context).vowSessionDao().insert(session)
            } catch (e: Exception) {
                android.util.Log.e("VowDataStore", "Failed to log focus session to database", e)
            }
        }
    }

    suspend fun incrementPickupsCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[VOW_PICKUPS_COUNT] ?: 0
            preferences[VOW_PICKUPS_COUNT] = current + 1
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
