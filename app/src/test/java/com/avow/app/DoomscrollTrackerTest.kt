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
        tracker.handleScroll(1000L, false)
        // Move time by 15 mins (900,000 ms)
        val result = tracker.handleScroll(901000L, false)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)
        assertTrue(tracker.warningSent)
    }

    @Test
    fun testHandleScroll_LockoutThreshold() {
        val tracker = DoomscrollTracker()
        tracker.handleScroll(1000L, false)
        tracker.handleScroll(901000L, false) // Warning
        // Move time to 30 mins (1,800,000 ms)
        val result = tracker.handleScroll(1801000L, false)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerLockout)
        assertEquals(0L, tracker.accumulatedMs) // Reset after lockout
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
