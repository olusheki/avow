package com.avow.app

import com.avow.app.util.DoomscrollTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomscrollTrackerTest {

    private val baseTime = 100000L // Use 100s to avoid 0L timestamp checks

    private fun simulateContinuousScroll(
        tracker: DoomscrollTracker,
        startTimeMs: Long,
        endTimeMs: Long,
        isNightTime: Boolean
    ): DoomscrollTracker.TrackingResult {
        var lastResult: DoomscrollTracker.TrackingResult = DoomscrollTracker.TrackingResult.None
        var t = startTimeMs
        while (t <= endTimeMs) {
            lastResult = tracker.handleScroll(t, isNightTime)
            t += 10000L // 10s intervals
        }
        return lastResult
    }

    @Test
    fun testDaytimeTemporalLimits() {
        val tracker = DoomscrollTracker()
        val isNightTime = false

        // 1. Initial scroll at t = baseTime
        var result = tracker.handleScroll(now = baseTime, isNightTime = isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)

        // 2. Scroll continuously up to 14 minutes
        result = simulateContinuousScroll(tracker, baseTime + 10000L, baseTime + 14 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)

        // 3. Scroll to 15 minutes -> Warning should trigger!
        result = simulateContinuousScroll(tracker, baseTime + 14 * 60 * 1000L + 10000L, baseTime + 15 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)

        // 4. Continued scrolling to 20 minutes... Warning shouldn't fire repeatedly
        result = simulateContinuousScroll(tracker, baseTime + 15 * 60 * 1000L + 10000L, baseTime + 20 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)

        // 5. Scroll to 30 minutes -> Lockout should trigger!
        result = simulateContinuousScroll(tracker, baseTime + 20 * 60 * 1000L + 10000L, baseTime + 30 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerLockout)
    }

    @Test
    fun testNighttimeTemporalLimits() {
        val tracker = DoomscrollTracker()
        val isNightTime = true // 11 PM to 5 AM limit (5 minutes warning, 15 more mins lockout)

        // 1. Initial scroll at t = baseTime
        var result = tracker.handleScroll(now = baseTime, isNightTime = isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)

        // 2. Scroll continuously up to 4 minutes
        result = simulateContinuousScroll(tracker, baseTime + 10000L, baseTime + 4 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.None)

        // 3. Scroll to 5 minutes -> Warning should trigger!
        result = simulateContinuousScroll(tracker, baseTime + 4 * 60 * 1000L + 10000L, baseTime + 5 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerWarning)

        // 4. Scroll to 20 minutes -> Lockout should trigger!
        result = simulateContinuousScroll(tracker, baseTime + 5 * 60 * 1000L + 10000L, baseTime + 20 * 60 * 1000L, isNightTime)
        assertTrue(result is DoomscrollTracker.TrackingResult.TriggerLockout)
    }

    @Test
    fun testContinuousScrollingBrokenAndReset() {
        val tracker = DoomscrollTracker()
        val isNightTime = false

        // Start scrolling at t = baseTime
        tracker.handleScroll(now = baseTime, isNightTime = isNightTime)
        // Scroll at t = baseTime + 10s (continuous)
        tracker.handleScroll(now = baseTime + 10000L, isNightTime = isNightTime)
        
        // Break continuous scrolling: gap of 31 seconds (limit is 30 seconds)
        // This should reset the session start time to baseTime + 41000L, throwing away the previous 10s of duration.
        tracker.handleScroll(now = baseTime + 41000L, isNightTime = isNightTime)
        assertEquals(baseTime + 41000L, tracker.currentSessionStartMs)
        assertEquals(baseTime + 41000L, tracker.lastScrollTimeMs)
    }

    @Test
    fun testAntiEvasionResumeTrackingWithin30Minutes() {
        val tracker = DoomscrollTracker()
        val targetApp = "com.instagram.android"
        val isTargetPkg = { pkg: String -> pkg == targetApp }

        // 1. User scrolls target app for 10 minutes (600,000 ms)
        tracker.handleScroll(now = baseTime, isNightTime = false)
        simulateContinuousScroll(tracker, baseTime + 10000L, baseTime + 10 * 60 * 1000L, isNightTime = false)

        // 2. App goes out of foreground at t = baseTime + 10m
        val outResult = tracker.handleForegroundChange(
            oldPkg = targetApp,
            newPkg = "com.android.launcher",
            isTargetPkg = isTargetPkg,
            now = baseTime + 10 * 60 * 1000L,
            lastClosedTime = 0L
        )
        assertTrue(outResult.saveClosedTime)
        assertEquals(10 * 60 * 1000L, outResult.newAccumulatedMs)
        assertEquals(10 * 60 * 1000L, tracker.accumulatedMs)

        // 3. User reopens the app after 29 minutes (at t = baseTime + 39m, which is 29 mins since last closed)
        val inResult = tracker.handleForegroundChange(
            oldPkg = "com.android.launcher",
            newPkg = targetApp,
            isTargetPkg = isTargetPkg,
            now = baseTime + 39 * 60 * 1000L,
            lastClosedTime = baseTime + 10 * 60 * 1000L
        )
        assertFalse(inResult.resetAccumulated)
        assertEquals(10 * 60 * 1000L, inResult.newAccumulatedMs)
        assertEquals(10 * 60 * 1000L, tracker.accumulatedMs)
    }

    @Test
    fun testAntiEvasionResetTrackingAfter30Minutes() {
        val tracker = DoomscrollTracker()
        val targetApp = "com.instagram.android"
        val isTargetPkg = { pkg: String -> pkg == targetApp }

        // 1. User scrolls target app for 10 minutes
        tracker.handleScroll(now = baseTime, isNightTime = false)
        simulateContinuousScroll(tracker, baseTime + 10000L, baseTime + 10 * 60 * 1000L, isNightTime = false)

        // 2. App goes out of foreground at t = baseTime + 10m
        tracker.handleForegroundChange(
            oldPkg = targetApp,
            newPkg = "com.android.launcher",
            isTargetPkg = isTargetPkg,
            now = baseTime + 10 * 60 * 1000L,
            lastClosedTime = 0L
        )

        // 3. User reopens the app after 31 minutes (at t = baseTime + 41m, which is 31 mins since last closed)
        val inResult = tracker.handleForegroundChange(
            oldPkg = "com.android.launcher",
            newPkg = targetApp,
            isTargetPkg = isTargetPkg,
            now = baseTime + 41 * 60 * 1000L,
            lastClosedTime = baseTime + 10 * 60 * 1000L
        )
        assertTrue(inResult.resetAccumulated)
        assertEquals(0L, inResult.newAccumulatedMs)
        assertEquals(0L, tracker.accumulatedMs)
    }
}
