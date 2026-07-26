package com.avow.app.util

import android.app.usage.UsageEvents
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

    /**
     * Exact package names counted as "social media". Exact match (not substring) so a stray
     * fragment can't sweep in unrelated apps and inflate the total. Verified against Google Play /
     * TikTok SDK docs. Add entries here to widen the definition.
     */
    val SOCIAL_MEDIA_PACKAGES = setOf(
        "com.instagram.android",      // Instagram
        "com.instagram.barcelona",    // Threads
        "com.zhiliaoapp.musically",   // TikTok (global)
        "com.ss.android.ugc.trill",   // TikTok (East/SE Asia build)
        "com.facebook.katana",        // Facebook
        "com.twitter.android",        // X / Twitter
        "com.snapchat.android",       // Snapchat
        "com.reddit.frontpage",       // Reddit
        "com.pinterest",              // Pinterest
        "com.tumblr",                 // Tumblr
        "com.google.android.youtube", // YouTube
        "com.linkedin.android",       // LinkedIn
        "com.bereal.ft"               // BeReal
    )

    fun isSocialMediaPackage(pkg: String): Boolean = pkg.lowercase() in SOCIAL_MEDIA_PACKAGES

    /** A foreground/background transition, extracted so the aggregation is unit-testable. */
    internal data class FgEvent(val type: Int, val pkg: String, val timestamp: Long)

    /**
     * Aggregates foreground time across social media apps only, within [start, end], in hours.
     *
     * Uses [UsageStatsManager.queryEvents] rather than `queryUsageStats(INTERVAL_DAILY).totalTimeInForeground`:
     * the daily buckets are not clipped to the query window, so summing them over-reports a rolling
     * 24h/7d range. Walking the resume/pause events gives the exact time inside the window.
     */
    fun queryHours(context: Context, start: Long, end: Long): Float {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val raw = usm.queryEvents(start, end)
            val events = mutableListOf<FgEvent>()
            val e = UsageEvents.Event()
            while (raw.hasNextEvent()) {
                raw.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                ) {
                    events.add(FgEvent(e.eventType, e.packageName, e.timeStamp))
                }
            }
            (socialForegroundMs(events, start, end) / MS_PER_HOUR).coerceAtLeast(0f)
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Sums the time social-media apps spent in the foreground across [events] (assumed ordered),
     * clipped to [windowStart, windowEnd]. Single-foreground model: a new foreground closes the
     * previous app's session; an app still foreground at the window end counts until [windowEnd]; a
     * background with no seen foreground (session began before the window) counts from [windowStart].
     */
    internal fun socialForegroundMs(events: List<FgEvent>, windowStart: Long, windowEnd: Long): Long {
        var total = 0L
        var fgPkg: String? = null
        var since = 0L
        for (ev in events) {
            when (ev.type) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (fgPkg != null && isSocialMediaPackage(fgPkg!!)) total += ev.timestamp - since
                    fgPkg = ev.pkg
                    since = ev.timestamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (isSocialMediaPackage(ev.pkg)) {
                        val from = if (fgPkg == ev.pkg) since else windowStart
                        total += ev.timestamp - from
                    }
                    fgPkg = null
                }
            }
        }
        if (fgPkg != null && isSocialMediaPackage(fgPkg!!)) total += windowEnd - since
        return total.coerceAtLeast(0L)
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
