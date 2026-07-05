package com.avow.app.worker

/** Everything the idle-reminder worker needs to decide whether to nudge the user. */
data class ReminderInputs(
    val isVowActive: Boolean,
    val onboardingCompleted: Boolean,
    val lastAppOpenMs: Long,
    val lastReminderShownMs: Long
)

/**
 * Pure decision logic for the idle "set a vow" nudge, kept free of Android so it can be unit-tested.
 *
 * We nudge only when the user has drifted away: onboarding done, no vow currently running, and the
 * app hasn't been opened for a while. We never nag more than once per [MIN_REMINDER_INTERVAL_MS].
 */
object ReminderPolicy {
    const val IDLE_THRESHOLD_MS = 3L * 24 * 60 * 60 * 1000   // 3 days since last open
    const val MIN_REMINDER_INTERVAL_MS = 2L * 24 * 60 * 60 * 1000 // at most one nudge / 2 days

    fun shouldRemind(now: Long, inputs: ReminderInputs): Boolean {
        if (!inputs.onboardingCompleted) return false
        // Someone mid-vow is already committed; nagging them adds nothing.
        if (inputs.isVowActive) return false
        // No baseline open recorded yet (never opened, or fresh install) — nothing to measure idle from.
        if (inputs.lastAppOpenMs <= 0L) return false
        if (now - inputs.lastAppOpenMs < IDLE_THRESHOLD_MS) return false
        if (inputs.lastReminderShownMs > 0L &&
            now - inputs.lastReminderShownMs < MIN_REMINDER_INTERVAL_MS
        ) return false
        return true
    }
}
