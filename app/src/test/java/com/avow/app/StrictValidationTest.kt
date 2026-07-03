package com.avow.app

import com.avow.app.util.VowValidator
import org.junit.Assert.*
import org.junit.Test

class StrictValidationTest {

    @Test
    fun testDoomscrollWindowStricter_wideningAllowed_narrowingRejected() {
        // Baseline window 23:00–05:00 (crosses midnight).
        // Widening to 22:00–06:00 fully covers it → stricter.
        assertTrue(
            VowValidator.isDoomscrollWindowStricter(23, 0, 5, 0, 22, 0, 6, 0)
        )
        // Identical window is "as strict or stricter".
        assertTrue(
            VowValidator.isDoomscrollWindowStricter(23, 0, 5, 0, 23, 0, 5, 0)
        )
        // Narrowing to 23:30–04:30 drops covered minutes → not stricter.
        assertFalse(
            VowValidator.isDoomscrollWindowStricter(23, 0, 5, 0, 23, 30, 4, 30)
        )
        // A disjoint same-day window doesn't cover the old one.
        assertFalse(
            VowValidator.isDoomscrollWindowStricter(23, 0, 5, 0, 9, 0, 17, 0)
        )
    }

    @Test
    fun testQuietHoursNormalDuration() {
        // GIVEN: 22:00 to 07:00 (crosses midnight)
        val durationMid = VowValidator.getQuietHoursDurationMinutes(22, 0, 7, 0)
        // EXPECT: 9 hours = 540 minutes
        assertEquals(540, durationMid)

        // GIVEN: 10:00 to 18:00 (same day)
        val durationSameDay = VowValidator.getQuietHoursDurationMinutes(10, 0, 18, 0)
        // EXPECT: 8 hours = 480 minutes
        assertEquals(480, durationSameDay)
        
        // GIVEN: 23:30 to 00:30 (crosses midnight)
        val durationShortCross = VowValidator.getQuietHoursDurationMinutes(23, 30, 0, 30)
        assertEquals(60, durationShortCross)
    }

    @Test
    fun testUsageLimitRates() {
        // GIVEN: 5 minutes per hour
        val rate1 = VowValidator.getUsageLimitRate("5", "min", "hour")
        assertEquals(5.0f, rate1, 0.001f)

        // GIVEN: 2 hours per day (120 minutes per 24 hours = 5 min/hour)
        val rate2 = VowValidator.getUsageLimitRate("2", "hours", "day")
        assertEquals(5.0f, rate2, 0.001f)

        // GIVEN: Invalid numeric strings fallback to 0f
        val rateInvalid = VowValidator.getUsageLimitRate("invalid", "min", "hour")
        assertEquals(0.0f, rateInvalid, 0.001f)
    }

    @Test
    fun testStrictnessAssertions() {
        // GIVEN: Old usage limit rate is 5.0f (e.g., 5 min/hour)
        val oldRate = VowValidator.getUsageLimitRate("5", "min", "hour")

        // CASE 1: Attempt to relax usage limit (e.g. 10 min/hour)
        val looserRate = VowValidator.getUsageLimitRate("10", "min", "hour")
        assertTrue(looserRate > oldRate) // Must fail the stricter check (rate increases)

        // CASE 2: Attempt to make limit stricter (e.g. 2 min/hour)
        val stricterRate = VowValidator.getUsageLimitRate("2", "min", "hour")
        assertTrue(stricterRate < oldRate) // Passes the stricter check (rate decreases)

        // CASE 3: Attempt to keep rate identical (e.g. 5 min/hour)
        val equalRate = VowValidator.getUsageLimitRate("5", "min", "hour")
        assertTrue(equalRate <= oldRate) // Passes the stricter check (unchanged rate is allowed)
    }

    @Test
    fun testQuietHoursDurationStrictness() {
        // GIVEN: Old quiet hours duration of 540 minutes (22:00 to 07:00)
        val oldDuration = VowValidator.getQuietHoursDurationMinutes(22, 0, 7, 0)

        // CASE 1: Attempt to shorten quiet hours duration (e.g. 23:00 to 07:00 = 480 minutes)
        val shorterDuration = VowValidator.getQuietHoursDurationMinutes(23, 0, 7, 0)
        assertTrue(shorterDuration < oldDuration) // Must fail (duration shortened)

        // CASE 2: Attempt to lengthen quiet hours (e.g. 21:00 to 07:00 = 600 minutes)
        val longerDuration = VowValidator.getQuietHoursDurationMinutes(21, 0, 7, 0)
        assertTrue(longerDuration > oldDuration) // Passes (duration lengthened)
    }
}
