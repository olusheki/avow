package com.avow.app.util

class DoomscrollTracker {
    var accumulatedMs: Long = 0L
    var warningSent: Boolean = false

    sealed class TrackingResult {
        object None : TrackingResult()
        object TriggerWarning : TrackingResult()
        object TriggerLockout : TrackingResult()
    }

    fun tick(isRestrictionActive: Boolean, allowanceMs: Long): TrackingResult {
        if (isRestrictionActive) {
            accumulatedMs += 1000L
            
            if (accumulatedMs >= allowanceMs) {
                accumulatedMs = 0L
                warningSent = false
                return TrackingResult.TriggerLockout
            } else if (accumulatedMs >= (allowanceMs * 0.8).toLong() && !warningSent) {
                warningSent = true
                return TrackingResult.TriggerWarning
            }
        }
        return TrackingResult.None
    }

    class ForegroundResult(
        val newAccumulatedMs: Long,
        val saveClosedTime: Boolean,
        val resetAccumulated: Boolean
    )

    fun handleForegroundChange(
        oldIsTarget: Boolean,
        newIsTarget: Boolean,
        now: Long,
        lastClosedTime: Long
    ): ForegroundResult {
        if (oldIsTarget && !newIsTarget) {
            // Leaving the target app
            return ForegroundResult(
                newAccumulatedMs = accumulatedMs,
                saveClosedTime = true,
                resetAccumulated = false
            )
        } else if (!oldIsTarget && newIsTarget) {
            // Entering the target app
            if (lastClosedTime > 0L) {
                val timeAway = now - lastClosedTime
                accumulatedMs = maxOf(0L, accumulatedMs - timeAway)
                if (accumulatedMs == 0L) {
                    warningSent = false
                }
            } else {
                accumulatedMs = 0L
                warningSent = false
            }
            return ForegroundResult(
                newAccumulatedMs = accumulatedMs,
                saveClosedTime = false,
                resetAccumulated = (accumulatedMs == 0L)
            )
        }
        
        return ForegroundResult(
            newAccumulatedMs = accumulatedMs,
            saveClosedTime = false,
            resetAccumulated = false
        )
    }
}
