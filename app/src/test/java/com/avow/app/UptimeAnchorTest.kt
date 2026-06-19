package com.avow.app

import com.avow.app.util.VowValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class UptimeAnchorTest {

    @Test
    fun testNormalCountdownDeduction() {
        // GIVEN: Saved remaining seconds = 1000s, saved uptime = 50000ms (50s)
        val savedRemaining = 1000L
        val lastUptime = 50000L
        
        // WHEN: App re-reads state at uptime = 60000ms (60s) (continuous operation, 10s elapsed)
        val currentUptime = 60000L
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )
        
        // THEN: 10 seconds must be deducted from 1000s -> 990s
        assertEquals(990L, remaining)
    }

    @Test
    fun testRebootDetectionResumesFromLastSaved() {
        // GIVEN: Saved remaining seconds = 1000s, saved uptime = 50000ms (50s)
        val savedRemaining = 1000L
        val lastUptime = 50000L
        
        // WHEN: Device reboots, and currentUptime restarts at 5000ms (5s) (currentUptime < lastUptime)
        val currentUptime = 5000L
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )
        
        // THEN: It must resume directly from 1000s and deduct the 5s elapsed since the reboot
        assertEquals(995L, remaining)
    }

    @Test
    fun testTimeDeductionDoesNotGoNegative() {
        // GIVEN: Saved remaining seconds = 5s (5000ms), saved uptime = 50000ms
        val savedRemaining = 5L
        val lastUptime = 50000L
        
        // WHEN: App reads state at uptime = 60000ms (10s elapsed)
        val currentUptime = 60000L
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )
        
        // THEN: Remaining seconds must clamp to 0 rather than -5
        assertEquals(0L, remaining)
    }

    @Test
    fun testZeroSavedRemainingReturnsZero() {
        // GIVEN: Vow is not active or has 0s remaining
        val savedRemaining = 0L
        val lastUptime = 50000L
        val currentUptime = 60000L
        
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )
        
        assertEquals(0L, remaining)
    }
}
