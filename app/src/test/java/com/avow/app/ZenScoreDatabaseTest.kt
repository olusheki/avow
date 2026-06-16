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
            pickups = 2,
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
        // Formula: Zen Score = max(0, 100 - (Pickups * 10) - (fractionOfTimeOnPhone * 50))
        
        // Scenario 1: Perfect focus (0 pickups, 0 allowed screen time)
        val score1 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 0, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(100, score1)
        
        // Scenario 2: Some pickups, no allowed screen time
        // 2 pickups -> penalty = 20 -> Zen Score = 80
        val score2 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 2, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(80, score2)
        
        // Scenario 3: No pickups, 12 minutes allowed screen time in 1 hour
        // Focus Duration = 1.0 hour, Allowed Screen Time = 12 mins -> ratio = 12 / 60 = 0.2 -> penalty = 0.2 * 50 = 10 -> Zen Score = 90
        val score3 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 0, allowedScreenTimeMs = 12 * 60 * 1000L, durationSeconds = 3600L)
        assertEquals(90, score3)
        
        // Scenario 4: Both pickups and allowed screen time
        // 1 pickup -> 10 penalty
        // 3 minutes allowed screen time in 30 minutes (1800 seconds) focus duration -> ratio = 180000 / 1800000 = 0.1 -> penalty = 0.1 * 50 = 5
        // total penalty = 15 -> Zen Score = 85
        val score4 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 1, allowedScreenTimeMs = 3 * 60 * 1000L, durationSeconds = 1800L)
        assertEquals(85, score4)
        
        // Scenario 5: Penalty exceeds 100
        val score5 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 11, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(0, score5)
 
        // Scenario 6: Division by zero duration handling
        val score6 = com.avow.app.util.VowValidator.calculateZenScore(pickups = 0, allowedScreenTimeMs = 1000L, durationSeconds = 0L)
        assertEquals(100, score6)
    }
}
