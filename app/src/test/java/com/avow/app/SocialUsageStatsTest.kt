package com.avow.app

import android.app.usage.UsageEvents
import com.avow.app.util.SocialUsageStats
import com.avow.app.util.SocialUsageStats.FgEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the foreground-time aggregation ([SocialUsageStats.socialForegroundMs]): only social apps
 *  count, sessions clip to the window, and switches/dangling sessions are handled. */
class SocialUsageStatsTest {

    private val FG = UsageEvents.Event.MOVE_TO_FOREGROUND
    private val BG = UsageEvents.Event.MOVE_TO_BACKGROUND
    private val IG = "com.instagram.android"   // social (keyword "instagram")
    private val TT = "com.tiktok.android"      // social (keyword "tiktok")
    private val SETTINGS = "com.android.settings" // not social

    @Test
    fun simpleSocialSession_countsForegroundSpan() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 1_000), FgEvent(BG, IG, 4_000)), 0, 10_000
        )
        assertEquals(3_000L, ms)
    }

    @Test
    fun nonSocialApp_isIgnored() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, SETTINGS, 1_000), FgEvent(BG, SETTINGS, 4_000)), 0, 10_000
        )
        assertEquals(0L, ms)
    }

    @Test
    fun stillForegroundAtEnd_countsToWindowEnd() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 8_000)), 0, 10_000
        )
        assertEquals(2_000L, ms)
    }

    @Test
    fun backgroundWithNoSeenForeground_countsFromWindowStart() {
        // Session began before the query window; the leading partial must clip to windowStart.
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(BG, IG, 2_000)), 0, 10_000
        )
        assertEquals(2_000L, ms)
    }

    @Test
    fun switchBetweenSocialApps_countsEachSpan() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 1_000), FgEvent(FG, TT, 3_000), FgEvent(BG, TT, 4_000)), 0, 10_000
        )
        assertEquals(3_000L, ms) // IG 1000..3000 (2000) + TT 3000..4000 (1000)
    }
}
