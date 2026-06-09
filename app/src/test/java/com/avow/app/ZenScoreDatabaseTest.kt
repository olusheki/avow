package com.avow.app

import com.avow.app.data.history.VowSession
import com.avow.app.data.history.VowSessionDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class ZenScoreDatabaseTest {

    @Test
    fun testRoomInsertionMock() = runBlocking {
        // Mock the DAO
        val dao = mockk<VowSessionDao>(relaxed = true)
        
        val session = VowSession(
            startTimeMillis = 1000L,
            endTimeMillis = 5000L,
            durationSeconds = 4L,
            intrusionsBlocked = 2,
            allowedScreenTimeMs = 30000L,
            zenScore = 75
        )
        
        // Setup insertion expectation
        coEvery { dao.insert(session) } returns Unit
        
        // Execute insertion
        dao.insert(session)
        
        // Verify insertion call
        coVerify(exactly = 1) { dao.insert(session) }
    }
    @Test
    fun testZenScoreCalculations() {
        // Test various mathematical scenarios for Zen Score
        // Formula: Zen Score = max(0, 100 - (Intrusions * 10) - (Allowed Screen Time (Min) / Focus Duration (Hr) * 5))
        
        // Scenario 1: Perfect focus (0 intrusions, 0 allowed screen time)
        val score1 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 0, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(100, score1)
        
        // Scenario 2: Some intrusions, no allowed screen time
        // 2 intrusions -> penalty = 20 -> Zen Score = 80
        val score2 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 2, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(80, score2)
        
        // Scenario 3: No intrusions, 12 minutes allowed screen time in 1 hour
        // Focus Duration = 1.0 hour, Allowed Screen Time = 12 mins -> ratio = 12.0 / 1.0 = 12 -> penalty = 12 * 5 = 60 -> Zen Score = 40
        val score3 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 0, allowedScreenTimeMs = 12 * 60 * 1000L, durationSeconds = 3600L)
        assertEquals(40, score3)
        
        // Scenario 4: Both intrusions and allowed screen time
        // 1 intrusion -> 10 penalty
        // 3 minutes allowed screen time in 30 minutes (0.5 hour) focus duration
        // ratio = 3 / 0.5 = 6 -> penalty = 6 * 5 = 30
        // total penalty = 40 -> Zen Score = 60
        val score4 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 1, allowedScreenTimeMs = 3 * 60 * 1000L, durationSeconds = 1800L)
        assertEquals(60, score4)
        
        // Scenario 5: Penalty exceeds 100
        val score5 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 11, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(0, score5)

        // Scenario 6: Division by zero duration handling
        val score6 = com.avow.app.util.VowValidator.calculateZenScore(intrusions = 0, allowedScreenTimeMs = 1000L, durationSeconds = 0L)
        assertEquals(100, score6)
    }
}
