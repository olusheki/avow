package com.avow.app.util

import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Single source of truth for reading social-media foreground time from [UsageStatsManager].
 *
 * Both onboarding (the "how much, really?" scan) and the dashboard mascot bubble read usage
 * through here so the definition of "social media" and the aggregation logic live in one place.
 * All queries fail soft: any missing permission or platform error yields 0h / null rather than
 * throwing.
 */
object SocialUsageStats {

    private const val MS_PER_HOUR = 1000f * 60f * 60f
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Package-name fragments that identify common social media apps. */
    val SOCIAL_MEDIA_KEYWORDS = listOf(
        "instagram", "tiktok", "musically", "trill",       // Instagram, TikTok (both variants)
        "facebook", "katana",                               // Facebook
        "twitter",                                          // X / Twitter
        "snapchat", "reddit", "pinterest", "tumblr",
        "youtube", "linkedin", "threads", "barcelona"       // Threads pkg = com.instagram.barcelona
    )

    fun isSocialMediaPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return SOCIAL_MEDIA_KEYWORDS.any { p.contains(it) }
    }

    /** Aggregates foreground time across social media apps only, within [start, end], in hours. */
    fun queryHours(context: Context, start: Long, end: Long): Float {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val totalMs = stats
                ?.filter { isSocialMediaPackage(it.packageName) }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
            (totalMs / MS_PER_HOUR).coerceAtLeast(0f)
        } catch (e: Exception) {
            0f
        }
    }

    /** Convenience: social-media hours over the last 24h (used by the onboarding scan). */
    fun queryLastDayHours(context: Context): Float {
        val end = System.currentTimeMillis()
        return queryHours(context, end - DAY_MS, end).coerceAtMost(24f)
    }

    /**
     * Week-over-week change in social-media time, as a signed percentage suitable for
     * [com.avow.app.ui.MascotMessages.reflectionMessage]: negative = using less than the prior
     * week (an improvement), positive = using more.
     *
     * Returns null when there isn't a meaningful prior-week baseline to compare against (no usage
     * access, or under [minBaselineHours] last week), so the bubble simply omits the reflection
     * rather than reporting a wild percentage off near-zero.
     */
    fun weekOverWeekPercentChange(context: Context, minBaselineHours: Float = 0.5f): Int? {
        val now = System.currentTimeMillis()
        val weekMs = 7L * DAY_MS
        val lastWeek = queryHours(context, now - 2 * weekMs, now - weekMs)
        if (lastWeek < minBaselineHours) return null
        val thisWeek = queryHours(context, now - weekMs, now)
        return Math.round((thisWeek - lastWeek) / lastWeek * 100f)
    }
}
