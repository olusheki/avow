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
    private val IG = "com.instagram.android"     // social
    private val TT = "com.zhiliaoapp.musically"  // social (TikTok's real package)
    private val SETTINGS = "com.android.settings" // not social

    @Test
    fun simpleSocialSession_countsForegroundSpan() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 1_000), FgEvent(BG, IG, 4_000)), 10_000
        )
        assertEquals(3_000L, ms)
    }

    @Test
    fun nonSocialApp_isIgnored() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, SETTINGS, 1_000), FgEvent(BG, SETTINGS, 4_000)), 10_000
        )
        assertEquals(0L, ms)
    }

    @Test
    fun stillForegroundAtEnd_countsToWindowEnd() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 8_000)), 10_000
        )
        assertEquals(2_000L, ms)
    }

    @Test
    fun laggingBackgroundForSwitchedAwayApp_doesNotOvercount() {
        // The real-device failure that pinned the total at 24h: a social app resumes, the user
        // switches away, then a LATE pause for that app arrives. The stale pause must add nothing —
        // not time credited back to the window start.
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(
                FgEvent(FG, IG, 1_000),
                FgEvent(FG, "com.android.launcher", 3_000), // switch away → closes IG at 3000
                FgEvent(BG, IG, 5_000)                       // lagging pause for IG
            ), 10_000
        )
        assertEquals(2_000L, ms) // only IG 1000..3000
    }

    @Test
    fun switchBetweenSocialApps_countsEachSpan() {
        val ms = SocialUsageStats.socialForegroundMs(
            listOf(FgEvent(FG, IG, 1_000), FgEvent(FG, TT, 3_000), FgEvent(BG, TT, 4_000)), 10_000
        )
        assertEquals(3_000L, ms) // IG 1000..3000 (2000) + TT 3000..4000 (1000)
    }
}
