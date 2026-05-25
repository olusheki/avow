package com.avow.app.util

import java.security.MessageDigest
import java.util.Calendar

object VowValidator {

    private const val SALT = "aVow_Enterprise_Security_Anchor_Salt_2026"
    const val MAX_VOW_SECONDS = 30L * 24L * 3600L // 30 days maximum

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
            // Reboot detected! Resume directly from saved remaining duration
            clampRemainingSeconds(savedRemainingSeconds)
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
     * Generates a SHA-256 verification hash to represent the current configuration state.
     * Prevents user database or preference file tampering (such as editing XML directly).
     */
    fun computeStateSignature(
        isVowActive: Boolean,
        remainingSeconds: Long,
        lastUptimeMillis: Long,
        banDomainSet: Set<String>,
        targetAppSet: Set<String>
    ): String {
        val sortedDomains = banDomainSet.sorted().joinToString(",")
        val sortedApps = targetAppSet.sorted().joinToString(",")
        val input = "$isVowActive|$remainingSeconds|$lastUptimeMillis|$sortedDomains|$sortedApps|$SALT"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
