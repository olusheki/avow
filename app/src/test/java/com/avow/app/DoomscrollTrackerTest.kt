package com.avow.app

import com.avow.app.util.DoomscrollTracker
import org.junit.Assert.*
import org.junit.Test

class DoomscrollTrackerTest {
    @Test
    fun testHandleScroll_NormalScroll_NoTrigger() {
        val tracker = DoomscrollTracker()
        val result = tracker.handleScroll(1000L, false)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)
        assertEquals(1000L, tracker.currentSessionStartMs)
    }

    @Test
    fun testHandleScroll_WarningThreshold() {
        val tracker = DoomscrollTracker()
        var now = 1000L
        var result: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        // Scroll every 10 seconds for 15 minutes (900,000 ms)
        while (now <= 902000L) {
            result = tracker.handleScroll(now, false)
            now += 10000L
        }
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)
        assertTrue(tracker.warningSent)
    }

    @Test
    fun testHandleScroll_WarningThreshold_RestrictionActive() {
        val tracker = DoomscrollTracker()
        var now = 1000L
        var result: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        // Scroll every 10 seconds for 5 minutes (300,000 ms)
        while (now <= 302000L) {
            result = tracker.handleScroll(now, true)
            now += 10000L
        }
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)
        assertTrue(tracker.warningSent)
    }

    @Test
    fun testHandleScroll_LockoutThreshold() {
        val tracker = DoomscrollTracker()
        var now = 1000L
        var result: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        // Scroll every 10 seconds for 30 minutes (1,800,000 ms)
        while (now <= 1802000L) {
            result = tracker.handleScroll(now, false)
            now += 10000L
        }
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerLockout)
        assertEquals(0L, tracker.accumulatedMs)
    }

    @Test
    fun testHandleForegroundChange_BackgroundTarget_SavesAccumulated() {
        val tracker = DoomscrollTracker()
        tracker.handleScroll(1000L, false)
        tracker.handleScroll(5000L, false)
        
        val result = tracker.handleForegroundChange(
            oldPkg = "target.app",
            newPkg = "other.app",
            isTargetPkg = { it == "target.app" },
            now = 6000L,
            lastClosedTime = 0L
        )
        
        assertTrue(result.saveClosedTime)
        assertEquals(4000L, result.newAccumulatedMs)
    }
}
