package com.avow.app.util

import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import java.util.Calendar
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import com.avow.app.model.VowBlock

object VowValidator {

    // 99:23:59:59 ceiling. Kept at exactly two days-digits so the countdown day column never needs
    // three digits, and so the duration wheels (max 99/23/59/59) can't produce a value above it.
    const val MAX_VOW_SECONDS = 99L * 86400L + 23L * 3600L + 59L * 60L + 59L
    
    private const val KEY_ALIAS = "vow_hmac_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    // Helper loaded lazily only when Android KeyStore is actually verified present.
    // This shields standard JVM unit tests from ClassNotFound/NoClassDefFound errors.
    private object KeyStoreHelper {
        fun getAndroidSecretKey(): Key {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }
            
            val keyGenerator = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                KEYSTORE_PROVIDER
            )
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
            ).build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }

    private var jvmTestKey: SecretKey? = null
    private fun getJvmTestKey(): SecretKey {
        var key = jvmTestKey
        if (key == null) {
            val keyGen = KeyGenerator.getInstance("HmacSHA256")
            key = keyGen.generateKey()
            jvmTestKey = key
        }
        return key
    }

    private fun getOrCreateSecretKey(): Key {
        val providers = Security.getProviders()
        val hasAndroidKeyStore = providers.any { it.name == KEYSTORE_PROVIDER }
        return if (hasAndroidKeyStore) {
            KeyStoreHelper.getAndroidSecretKey()
        } else {
            getJvmTestKey()
        }
    }

    /**
     * Validates that all minutes blocked by the old blocks are still blocked by the new blocks.
     * Handles midnight crossings correctly.
     */
    fun validateContainment(oldBlocks: List<VowBlock>, newBlocks: List<VowBlock>): Boolean {
        val oldBlockedMinutes = getBlockedMinutes(oldBlocks)
        val newBlockedMinutes = getBlockedMinutes(newBlocks)
        return oldBlockedMinutes.all { it in newBlockedMinutes }
    }

    private fun getBlockedMinutes(blocks: List<VowBlock>): Set<Int> {
        val minutes = mutableSetOf<Int>()
        for (block in blocks) {
            if (!block.isEnabled) continue
            val start = block.startHour * 60 + block.startMin
            val end = block.endHour * 60 + block.endMin
            if (start <= end) {
                for (m in start..end) {
                    minutes.add(m)
                }
            } else {
                for (m in start..1439) {
                    minutes.add(m)
                }
                for (m in 0..end) {
                    minutes.add(m)
                }
            }
        }
        return minutes
    }

    private fun windowMinutes(startHour: Int, startMin: Int, endHour: Int, endMin: Int): Set<Int> {
        val start = startHour * 60 + startMin
        val end = endHour * 60 + endMin
        val minutes = mutableSetOf<Int>()
        if (start <= end) {
            for (m in start..end) minutes.add(m)
        } else {
            for (m in start..1439) minutes.add(m)
            for (m in 0..end) minutes.add(m)
        }
        return minutes
    }

    /**
     * True if the new doomscroll window blocks at least every minute the old one did (i.e. it is as
     * strict or stricter — a widened or unchanged window). Used to allow tightening the shield while
     * a vow is locked while rejecting any narrowing. Handles midnight crossings.
     */
    fun isDoomscrollWindowStricter(
        oldStartHour: Int, oldStartMin: Int, oldEndHour: Int, oldEndMin: Int,
        newStartHour: Int, newStartMin: Int, newEndHour: Int, newEndMin: Int
    ): Boolean {
        val oldMinutes = windowMinutes(oldStartHour, oldStartMin, oldEndHour, oldEndMin)
        val newMinutes = windowMinutes(newStartHour, newStartMin, newEndHour, newEndMin)
        return oldMinutes.all { it in newMinutes }
    }

    /**
     * Computes the HMAC-SHA256 signature using the AndroidKeyStore dynamic symmetric key.
     */
    fun computeHMACSignature(
        isVowActive: Boolean,
        isActiveVowMode: Boolean,
        remainingSeconds: Long,
        lastUptimeMillis: Long,
        banDomainSet: Set<String>,
        targetAppSet: Set<String>,
        deactivationRequestTime: Long,
        secureFolderEnabled: Boolean = false,
        privateSpaceEnabled: Boolean = false,
        lockUninstall: Boolean = false,
        disallowDataWipe: Boolean = false,
        disableSafeBoot: Boolean = false,
        blockPlayStore: Boolean = false,
        dynamicReinstall: Boolean = false,
        deactivateUsbDebugging: Boolean = false,
        quietHoursEnabled: Boolean = false,
        quietStartHour: Int = 22,
        quietStartMin: Int = 0,
        quietEndHour: Int = 7,
        quietEndMin: Int = 0,
        quietHoursTargetAppSet: Set<String> = emptySet(),
        quietHoursSpecificDomain: String = "",
        usageLimitsUpdated: Boolean = false,
        allowedValue: String = "5",
        allowedUnit: String = "min",
        selectedInterval: String = "hour",
        specificDomain: String = "",
        isCollectiveLimit: Boolean = false,
        vowBlocksJson: String = "",
        temporaryLockoutEndTime: Long = 0L,
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
        isOnboardingCompleted: Boolean = false
    ): String {
        val sortedDomains = banDomainSet.sorted().joinToString(",")
        val sortedApps = targetAppSet.sorted().joinToString(",")
        val sortedQuietHoursApps = quietHoursTargetAppSet.sorted().joinToString(",")
        val sortedDoomscrollApps = doomscrollTargetAppSet.sorted().joinToString(",")
        val input = "$isVowActive|$isActiveVowMode|$remainingSeconds|$lastUptimeMillis|$sortedDomains|$sortedApps|$deactivationRequestTime|" +
                "$secureFolderEnabled|$privateSpaceEnabled|$lockUninstall|$disallowDataWipe|$disableSafeBoot|$blockPlayStore|$dynamicReinstall|" +
                "$deactivateUsbDebugging|$quietHoursEnabled|$quietStartHour|$quietStartMin|$quietEndHour|$quietEndMin|$sortedQuietHoursApps|" +
                "$quietHoursSpecificDomain|$usageLimitsUpdated|$allowedValue|$allowedUnit|$selectedInterval|$specificDomain|$isCollectiveLimit|$vowBlocksJson|$temporaryLockoutEndTime|$vowStartTimeMs|$vowInitialDurationSeconds|" +
                "$doomscrollShieldEnabled|$doomscrollAllTime|$doomscrollStartHour|$doomscrollStartMin|$doomscrollEndHour|$doomscrollEndMin|$sortedDoomscrollApps|$doomscrollCooldownMinutes|$doomscrollAllowanceMinutes|" +
                "$isOnboardingCompleted"
        
        val secretKey = getOrCreateSecretKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val digest = mac.doFinal(input.toByteArray(Charsets.UTF_8))
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * Calculates remaining seconds for a vow.
     * Accurately handles uptime resumption and prevents negative boundaries.
     */
    fun calculateRemainingSeconds(
        currentUptimeMillis: Long,
        lastUptimeMillis: Long,
        savedRemainingSeconds: Long
    ): Long {
        if (savedRemainingSeconds <= 0L) return 0L
        
        return if (currentUptimeMillis < lastUptimeMillis) {
            // Reboot detected! Deduct the time elapsed since the reboot
            val elapsedSeconds = currentUptimeMillis / 1000L
            val calculated = savedRemainingSeconds - elapsedSeconds
            clampRemainingSeconds(calculated)
        } else {
            // App was closed but device stayed on. Deduct elapsed time.
            val elapsedSeconds = (currentUptimeMillis - lastUptimeMillis) / 1000L
            val calculated = savedRemainingSeconds - elapsedSeconds
            clampRemainingSeconds(calculated)
        }
    }

    /**
     * Helper to calculate quiet hours duration in minutes.
     * Correctly handles intervals that cross midnight.
     */
    fun getQuietHoursDurationMinutes(startH: Int, startM: Int, endH: Int, endM: Int): Int {
        val start = startH * 60 + startM
        val end = endH * 60 + endM
        return if (end >= start) {
            end - start
        } else {
            (1440 - start) + end
        }
    }

    /**
     * Helper to calculate usage limits rate in equivalent minutes per hour.
     */
    fun getUsageLimitRate(valueStr: String, unit: String, interval: String): Float {
        val value = valueStr.toFloatOrNull() ?: 0f
        if (value <= 0f) return 0f
        
        val minutes = if (unit.equals("hours", ignoreCase = true) || unit.equals("hour", ignoreCase = true)) {
            value * 60f
        } else {
            value
        }
        val intervalHours = if (interval.equals("day", ignoreCase = true)) 24f else 1f
        return minutes / intervalHours
    }

    /**
     * Determines if a new usage interval has started, resetting the usage limits logic.
     */
    fun isNewUsageInterval(
        nowMs: Long,
        lastIntervalStartMs: Long,
        selectedInterval: String
    ): Boolean {
        if (lastIntervalStartMs == 0L) return true
        
        val calendarNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        val calendarLast = Calendar.getInstance().apply { timeInMillis = lastIntervalStartMs }
        
        return if (selectedInterval.equals("hour", ignoreCase = true)) {
            calendarNow.get(Calendar.HOUR_OF_DAY) != calendarLast.get(Calendar.HOUR_OF_DAY) ||
            calendarNow.get(Calendar.DAY_OF_YEAR) != calendarLast.get(Calendar.DAY_OF_YEAR) ||
            calendarNow.get(Calendar.YEAR) != calendarLast.get(Calendar.YEAR)
        } else {
            // Interval is day
            calendarNow.get(Calendar.DAY_OF_YEAR) != calendarLast.get(Calendar.DAY_OF_YEAR) ||
            calendarNow.get(Calendar.YEAR) != calendarLast.get(Calendar.YEAR)
        }
    }

    /**
     * Safely clamps remaining seconds to prevent math overflows or corrupt values (like negative values
     * or Long.MAX_VALUE) from leading to infinite lockouts.
     */
    fun clampRemainingSeconds(seconds: Long): Long {
        if (seconds < 0L) return 0L
        return minOf(seconds, MAX_VOW_SECONDS)
    }

    /**
     * Calculates the Zen Score for a focus session (0-100).
     * Formula: Zen Score = max(0, 100 - (Pickups * 10) - (fractionOfTimeOnPhone * 50))
     */
    fun calculateZenScore(pickups: Int, allowedScreenTimeMs: Long, durationSeconds: Long): Int {
        val fractionOfTimeOnPhone = if (durationSeconds > 0L) {
            val ratio = allowedScreenTimeMs.toDouble() / (durationSeconds * 1000.0)
            minOf(1.0, maxOf(0.0, ratio))
        } else {
            0.0
        }
        val penalty = (pickups * 10.0) + (fractionOfTimeOnPhone * 50.0)
        return maxOf(0, minOf(100, kotlin.math.round(100.0 - penalty).toInt()))
    }
}
