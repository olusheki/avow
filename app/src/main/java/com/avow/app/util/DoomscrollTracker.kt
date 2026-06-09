package com.avow.app.util

class DoomscrollTracker {
    var currentSessionStartMs: Long = 0L
    var lastScrollTimeMs: Long = 0L
    var accumulatedMs: Long = 0L
    var warningSent: Boolean = false

    sealed class TrackingResult {
        object None : TrackingResult()
        object TriggerWarning : TrackingResult()
        object TriggerLockout : TrackingResult()
    }

    fun handleScroll(now: Long, isNightTime: Boolean): TrackingResult {
        if (lastScrollTimeMs == 0L || now - lastScrollTimeMs > 30000L) {
            currentSessionStartMs = now
        }
        lastScrollTimeMs = now

        val currentSessionDuration = now - currentSessionStartMs
        val totalAccumulated = accumulatedMs + currentSessionDuration

        val limitMs = if (isNightTime) 5L * 60L * 1000L else 15L * 60L * 1000L
        val warningThresholdMs = limitMs
        val lockoutThresholdMs = limitMs + 15L * 60L * 1000L

        return if (totalAccumulated >= lockoutThresholdMs) {
            accumulatedMs = 0L
            currentSessionStartMs = 0L
            lastScrollTimeMs = 0L
            warningSent = false
            TrackingResult.TriggerLockout
        } else if (totalAccumulated >= warningThresholdMs && !warningSent) {
            warningSent = true
            TrackingResult.TriggerWarning
        } else {
            TrackingResult.None
        }
    }

    class ForegroundResult(
        val newAccumulatedMs: Long,
        val saveClosedTime: Boolean,
        val resetAccumulated: Boolean
    )

    fun handleForegroundChange(
        oldPkg: String,
        newPkg: String,
        isTargetPkg: (String) -> Boolean,
        now: Long,
        lastClosedTime: Long
    ): ForegroundResult {
        val oldIsTarget = isTargetPkg(oldPkg)
        val newIsTarget = isTargetPkg(newPkg)

        if (oldIsTarget && !newIsTarget) {
            val currentSessionDuration = if (currentSessionStartMs > 0L && lastScrollTimeMs >= currentSessionStartMs) {
                lastScrollTimeMs - currentSessionStartMs
            } else {
                0L
            }
            val totalAccumulated = accumulatedMs + currentSessionDuration
            accumulatedMs = totalAccumulated
            currentSessionStartMs = 0L
            lastScrollTimeMs = 0L
            return ForegroundResult(
                newAccumulatedMs = totalAccumulated,
                saveClosedTime = true,
                resetAccumulated = false
            )
        } else if (!oldIsTarget && newIsTarget) {
            currentSessionStartMs = 0L
            lastScrollTimeMs = 0L
            if (lastClosedTime > 0L && (now - lastClosedTime) < 30L * 60L * 1000L) {
                // Resume
                return ForegroundResult(
                    newAccumulatedMs = accumulatedMs,
                    saveClosedTime = false,
                    resetAccumulated = false
                )
            } else {
                accumulatedMs = 0L
                warningSent = false
                return ForegroundResult(
                    newAccumulatedMs = 0L,
                    saveClosedTime = false,
                    resetAccumulated = true
                )
            }
        }
        return ForegroundResult(
            newAccumulatedMs = accumulatedMs,
            saveClosedTime = false,
            resetAccumulated = false
        )
    }
}
