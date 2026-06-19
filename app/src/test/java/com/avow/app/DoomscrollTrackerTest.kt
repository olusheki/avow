package com.avow.app

import com.avow.app.util.DoomscrollTracker
import org.junit.Assert.*
import org.junit.Test

class DoomscrollTrackerTest {
    @Test
    fun testTick_NoRestriction_NoAccumulation() {
        val tracker = DoomscrollTracker()
        val result = tracker.tick(isRestrictionActive = false, allowanceMs = 15L * 60 * 1000)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)
        assertEquals(0L, tracker.accumulatedMs)
    }

    @Test
    fun testTick_RestrictionActive_Accumulates() {
        val tracker = DoomscrollTracker()
        val result = tracker.tick(isRestrictionActive = true, allowanceMs = 15L * 60 * 1000)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)
        assertEquals(1000L, tracker.accumulatedMs)
    }

    @Test
    fun testTick_WarningThreshold() {
        val tracker = DoomscrollTracker()
        var result: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        
        // Tick until warning threshold (80% of allowance)
        val allowance = 15L * 60 * 1000
        val targetTicks = (allowance * 0.8 / 1000).toInt()
        
        for (i in 0 until targetTicks) {
            result = tracker.tick(isRestrictionActive = true, allowanceMs = allowance)
        }
        
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)
        assertTrue(tracker.warningSent)
    }

    @Test
    fun testTick_LockoutThreshold() {
        val tracker = DoomscrollTracker()
        var result: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        
        // Tick until lockout threshold (100% of allowance)
        val allowance = 15L * 60 * 1000
        val targetTicks = (allowance / 1000).toInt()
        
        for (i in 0 until targetTicks) {
            result = tracker.tick(isRestrictionActive = true, allowanceMs = allowance)
        }
        
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerLockout)
        assertEquals(0L, tracker.accumulatedMs)
    }

    @Test
    fun testHandleForegroundChange_BackgroundTarget_SavesAccumulated() {
        val tracker = DoomscrollTracker()
        tracker.accumulatedMs = 4000L
        
        val result = tracker.handleForegroundChange(
            oldIsTarget = true,
            newIsTarget = false,
            now = 6000L,
            lastClosedTime = 0L
        )
        
        assertTrue(result.saveClosedTime)
        assertEquals(4000L, result.newAccumulatedMs)
    }

    @Test
    fun testHandleForegroundChange_LeakyBucket_SlowDrain() {
        val tracker = DoomscrollTracker()
        tracker.accumulatedMs = 60000L // 1 minute accumulated
        tracker.warningSent = true

        // User stays away for 30 seconds
        val now = 100000L
        val lastClosedTime = 70000L // time away = 30000L

        val result = tracker.handleForegroundChange(
            oldIsTarget = false,
            newIsTarget = true,
            now = now,
            lastClosedTime = lastClosedTime
        )

        // It should drain 30 seconds, leaving 30 seconds (30000L)
        assertEquals(30000L, result.newAccumulatedMs)
        assertEquals(30000L, tracker.accumulatedMs)
        assertFalse(result.resetAccumulated)
        assertTrue(tracker.warningSent) // warning remains since we didn't drain to 0

        // User stays away for 40 seconds (drains to 0)
        tracker.accumulatedMs = 30000L
        val result2 = tracker.handleForegroundChange(
            oldIsTarget = false,
            newIsTarget = true,
            now = 100000L,
            lastClosedTime = 60000L // time away = 40000L
        )

        assertEquals(0L, result2.newAccumulatedMs)
        assertEquals(0L, tracker.accumulatedMs)
        assertTrue(result2.resetAccumulated)
        assertFalse(tracker.warningSent)
    }
}
