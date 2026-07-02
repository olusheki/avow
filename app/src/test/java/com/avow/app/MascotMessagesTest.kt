package com.avow.app

import com.avow.app.data.history.VowSession
import com.avow.app.ui.MascotMessages
import com.avow.app.ui.computeMascotZenDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MascotMessagesTest {

    @Test
    fun lockedMessage_combinesOneOfEachPool() {
        val msg = MascotMessages.generateMessage(isLocked = true, random = Random(0))
        val greeting = MascotMessages.lockedGreetings.first { msg.startsWith(it) }
        val remainder = msg.removePrefix("$greeting ")
        val context = MascotMessages.lockedContexts.first { remainder.startsWith(it) }
        val tagline = remainder.removePrefix("$context ")

        assertTrue(MascotMessages.lockedTaglines.contains(tagline))
        assertEquals("$greeting $context $tagline", msg)
    }

    @Test
    fun unlockedMessage_drawsFromUnlockedPools() {
        val msg = MascotMessages.generateMessage(isLocked = false, random = Random(42))
        assertTrue(MascotMessages.unlockedGreetings.any { msg.startsWith(it) })
        assertTrue(MascotMessages.unlockedContexts.any { msg.contains(it) })
        assertTrue(MascotMessages.unlockedTaglines.any { msg.endsWith(it) })
    }

    @Test
    fun positiveStreak_usesStreakTemplate() {
        val msg = MascotMessages.generateMessage(isLocked = true, streakDays = 5)
        assertEquals(
            "Day 5. You are ${MascotMessages.SAGE_OPEN}holding the line${MascotMessages.SAGE_CLOSE}.",
            msg
        )
    }

    @Test
    fun zeroStreak_fallsBackToComposedMessage() {
        val msg = MascotMessages.generateMessage(isLocked = true, streakDays = 0, random = Random(1))
        assertFalse(msg.startsWith("Day "))
    }

    @Test
    fun reflectionMessage_choosesSemanticColorByDirection() {
        val improved = MascotMessages.reflectionMessage(-20)
        assertTrue(improved.contains(MascotMessages.SAGE_OPEN))
        assertTrue(improved.contains("down 20% screentime"))

        val regressed = MascotMessages.reflectionMessage(15)
        assertTrue(regressed.contains(MascotMessages.RED_OPEN))
        assertTrue(regressed.contains("up 15% screentime"))
    }

    @Test
    fun zenScoreMessage_choosesSemanticColorByDirection() {
        assertTrue(MascotMessages.zenScoreMessage(12).contains(MascotMessages.SAGE_OPEN))
        assertTrue(MascotMessages.zenScoreMessage(-8).contains(MascotMessages.RED_OPEN))
    }

    @Test
    fun vowCountdown_usesLargestNonZeroUnitAndPluralizes() {
        val d = MascotMessages.vowCountdownMessage(2, 5, 30, 10, Random(0))
        assertTrue(d.contains("2 days left"))

        val h = MascotMessages.vowCountdownMessage(0, 1, 30, 10, Random(0))
        assertTrue(h.contains("1 hour left"))

        val m = MascotMessages.vowCountdownMessage(0, 0, 45, 10, Random(0))
        assertTrue(m.contains("45 minutes left"))

        val s = MascotMessages.vowCountdownMessage(0, 0, 0, 9, Random(0))
        assertTrue(s.contains("9 seconds left"))
    }

    @Test
    fun generateLine_lockedWithCountdown_neverEmitsUnlockedCopy() {
        repeat(50) { seed ->
            val line = MascotMessages.generateLine(
                isLocked = true, days = 1, hours = 2, minutes = 3, seconds = 4,
                zenDelta = null, random = Random(seed.toLong())
            )
            assertFalse(MascotMessages.unlockedGreetings.any { line.startsWith(it) })
        }
    }

    @Test
    fun generateLine_canSurfaceZenTrendWhenAvailable() {
        val seen = (0 until 100).any { seed ->
            MascotMessages.generateLine(
                isLocked = false, days = 0, hours = 0, minutes = 0, seconds = 0,
                zenDelta = 15, random = Random(seed.toLong())
            ).contains("zen score")
        }
        assertTrue(seen)
    }

    @Test
    fun generateLine_canSurfaceReflectionWhenBaselineAvailable() {
        val seen = (0 until 100).any { seed ->
            MascotMessages.generateLine(
                isLocked = false, days = 0, hours = 0, minutes = 0, seconds = 0,
                reflectionPercent = -25, random = Random(seed.toLong())
            ).contains("screentime")
        }
        assertTrue(seen)
    }

    @Test
    fun generateLine_omitsReflectionWhenBaselineNull() {
        repeat(100) { seed ->
            val line = MascotMessages.generateLine(
                isLocked = false, days = 0, hours = 0, minutes = 0, seconds = 0,
                reflectionPercent = null, random = Random(seed.toLong())
            )
            assertFalse(line.contains("screentime"))
        }
    }

    @Test
    fun zenDelta_nullUntilTwoSessions_thenLatestVsPriorAverage() {
        assertNull(computeMascotZenDelta(emptyList()))
        assertNull(computeMascotZenDelta(listOf(session(zen = 80))))

        // Newest-first: latest 90 vs avg(70,50)=60 -> +30
        val delta = computeMascotZenDelta(
            listOf(session(zen = 90), session(zen = 70), session(zen = 50))
        )
        assertEquals(30, delta)
    }

    @Test
    fun generateMessage_isDeterministicForFixedSeed() {
        val a = MascotMessages.generateMessage(isLocked = false, random = Random(7))
        val b = MascotMessages.generateMessage(isLocked = false, random = Random(7))
        assertEquals(a, b)
    }

    private fun session(zen: Int) = VowSession(
        startTimeMillis = 0L,
        endTimeMillis = 0L,
        durationSeconds = 0L,
        pickups = 0,
        allowedScreenTimeMs = 0L,
        zenScore = zen
    )
}
