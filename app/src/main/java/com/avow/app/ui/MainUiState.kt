package com.avow.app.ui

import com.avow.app.model.VowBlock

enum class ScreenState {
    UNLOCKED_VAULT,
    LOCKED_VAULT,
    CONFIGURATION,
    INTRUSION_INTERCEPT,
    TEMPORARY_LOCKOUT,
    FOCUS_HISTORY
}

data class MainUiState(
    val currentState: ScreenState = ScreenState.UNLOCKED_VAULT,
    val previousState: ScreenState = ScreenState.UNLOCKED_VAULT,
    val isVowActive: Boolean = false,
    val isActiveVowMode: Boolean = false,
    val deactivationRequestTime: Long = 0L,
    val temporaryLockoutEndTime: Long = 0L,
    
    val inAppToastMessage: String? = null,
    val installedApps: List<Pair<String, String>> = emptyList(),
    
    val usageLimitsUpdated: Boolean = false,
    val allowedValue: String = "5",
    val allowedUnit: String = "min",
    val selectedInterval: String = "hour",
    val targetAppSet: Set<String> = emptySet(),
    val specificDomain: String = "",
    val isCollectiveLimit: Boolean = false,

    val frozenAllowedValue: String = "5",
    val frozenAllowedUnit: String = "min",
    val frozenInterval: String = "hour",

    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietStartMin: Int = 0,
    val quietEndHour: Int = 7,
    val quietEndMin: Int = 0,
    val quietHoursTargetAppSet: Set<String> = emptySet(),
    val quietHoursSpecificDomain: String = "",
    
    val frozenQuietHoursEnabled: Boolean = false,
    val frozenQuietStartHour: Int = 22,
    val frozenQuietStartMin: Int = 0,
    val frozenQuietEndHour: Int = 7,
    val frozenQuietEndMin: Int = 0,
    val frozenQuietHoursTargetAppSet: Set<String> = emptySet(),
    val frozenQuietHoursSpecificDomain: String = "",

    val doomscrollShieldEnabled: Boolean = false,
    val doomscrollAllTime: Boolean = false,
    val doomscrollStartHour: Int = 23,
    val doomscrollStartMin: Int = 0,
    val doomscrollEndHour: Int = 5,
    val doomscrollEndMin: Int = 0,
    val doomscrollTargetAppSet: Set<String> = emptySet(),
    val doomscrollCooldownMinutes: Int = 60,
    val doomscrollAllowanceMinutes: Int = 15,

    val frozenDoomscrollShieldEnabled: Boolean = false,
    val frozenDoomscrollAllTime: Boolean = false,
    val frozenDoomscrollStartHour: Int = 23,
    val frozenDoomscrollStartMin: Int = 0,
    val frozenDoomscrollEndHour: Int = 5,
    val frozenDoomscrollEndMin: Int = 0,
    val frozenDoomscrollTargetAppSet: Set<String> = emptySet(),
    val frozenDoomscrollCooldownMinutes: Int = 60,
    val frozenDoomscrollAllowanceMinutes: Int = 15,

    val vowBlocks: List<VowBlock> = emptyList(),
    val frozenVowBlocks: List<VowBlock> = emptyList(),
    
    val secureFolderEnabled: Boolean = false,
    val privateSpaceEnabled: Boolean = false,
    val lockUninstall: Boolean = false,
    val disallowDataWipe: Boolean = false,
    val disableSafeBoot: Boolean = false,
    val blockPlayStore: Boolean = false,
    val dynamicReinstall: Boolean = false,
    val deactivateUsbDebugging: Boolean = false,
    
    val banDomainSet: Set<String> = setOf("instagram.com"),
    
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val vowStartTimeMs: Long = 0L,
    val vowInitialDurationSeconds: Long = 0L,

    val isLoaded: Boolean = false,
    val isDeviceOwner: Boolean = false
)
