package com.avow.app

import com.avow.app.worker.ReminderInputs
import com.avow.app.worker.ReminderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    private val now = 10_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun inputs(
        isVowActive: Boolean = false,
        onboardingCompleted: Boolean = true,
        lastAppOpenMs: Long = now - 5 * day,
        lastReminderShownMs: Long = 0L
    ) = ReminderInputs(isVowActive, onboardingCompleted, lastAppOpenMs, lastReminderShownMs)

    @Test
    fun remindsWhenIdlePastThresholdAndNeverReminded() {
        assertTrue(ReminderPolicy.shouldRemind(now, inputs()))
    }

    @Test
    fun neverRemindsBeforeOnboardingComplete() {
        assertFalse(ReminderPolicy.shouldRemind(now, inputs(onboardingCompleted = false)))
    }

    @Test
    fun neverRemindsDuringActiveVow() {
        assertFalse(ReminderPolicy.shouldRemind(now, inputs(isVowActive = true)))
    }

    @Test
    fun neverRemindsWithoutAnAppOpenBaseline() {
        assertFalse(ReminderPolicy.shouldRemind(now, inputs(lastAppOpenMs = 0L)))
    }

    @Test
    fun doesNotRemindWhenRecentlyActive() {
        // Opened the app 1 day ago — well under the 3-day idle threshold.
        assertFalse(ReminderPolicy.shouldRemind(now, inputs(lastAppOpenMs = now - 1 * day)))
    }

    @Test
    fun idleAtExactThresholdReminds() {
        val atThreshold = now - ReminderPolicy.IDLE_THRESHOLD_MS
        assertTrue(ReminderPolicy.shouldRemind(now, inputs(lastAppOpenMs = atThreshold)))
    }

    @Test
    fun rateLimitsWhenRecentlyReminded() {
        // Idle enough, but a nudge went out 1 day ago (< 2-day min interval).
        assertFalse(
            ReminderPolicy.shouldRemind(now, inputs(lastReminderShownMs = now - 1 * day))
        )
    }

    @Test
    fun remindsAgainAfterRateLimitWindow() {
        // Last nudge was 3 days ago — past the 2-day minimum interval.
        assertTrue(
            ReminderPolicy.shouldRemind(now, inputs(lastReminderShownMs = now - 3 * day))
        )
    }
}
