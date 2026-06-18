package com.avow.app

import com.avow.app.util.VowValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class VowValidatorTest {
    @Test
    fun testCalculateRemainingSeconds_NormalTick() {
        val remaining = VowValidator.calculateRemainingSeconds(
            currentRemainingSeconds = 3600,
            lastUptimeMillis = 10000,
            currentUptimeMillis = 15000
        )
        assertEquals(3595L, remaining)
    }

    @Test
    fun testCalculateRemainingSeconds_NegativeBounds() {
        val remaining = VowValidator.calculateRemainingSeconds(
            currentRemainingSeconds = 5,
            lastUptimeMillis = 10000,
            currentUptimeMillis = 20000
        )
        assertEquals(0L, remaining) // Should not go below 0
    }

    @Test
    fun testCalculateRemainingSeconds_RebootDetection() {
        // Reboot means currentUptime < lastUptime
        val remaining = VowValidator.calculateRemainingSeconds(
            currentRemainingSeconds = 3600,
            lastUptimeMillis = 500000,
            currentUptimeMillis = 10000
        )
        // Elapsed time is just currentUptimeMillis (10 seconds)
        assertEquals(3590L, remaining)
    }

    @Test
    fun testCalculateRemainingSeconds_TimeTravelDetection() {
        // If currentUptime == lastUptime but it's called, elapsed = 0
        val remaining = VowValidator.calculateRemainingSeconds(
            currentRemainingSeconds = 3600,
            lastUptimeMillis = 10000,
            currentUptimeMillis = 10000
        )
        assertEquals(3600L, remaining)
    }
}
