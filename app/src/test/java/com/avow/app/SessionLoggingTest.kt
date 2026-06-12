package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.avow.app.data.VowDataStore
import com.avow.app.data.dataStore
import com.avow.app.data.history.VowDatabase
import com.avow.app.data.history.VowSession
import com.avow.app.data.history.VowSessionDao
import com.avow.app.util.VowValidator
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLoggingTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var testDataStore: androidx.datastore.core.DataStore<Preferences>
    private lateinit var vowDataStore: VowDataStore
    private lateinit var mockDb: VowDatabase
    private lateinit var mockDao: VowSessionDao

    @Before
    fun setup() {
        val tempFile = tmpFolder.newFile("test_settings.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )

        mockContext = mockk<Context>(relaxed = true)
        
        // Mock the Context.dataStore extension property
        mockkStatic("com.avow.app.data.VowDataStoreKt")
        every { mockContext.dataStore } returns testDataStore

        // Mock android.util.Log to prevent "Method e not mocked" runtime exceptions
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockDb = mockk()
        mockDao = mockk(relaxed = true)
        every { mockDb.vowSessionDao() } returns mockDao
        
        mockkObject(VowDatabase.Companion)
        every { VowDatabase.getDatabase(any()) } returns mockDb

        vowDataStore = VowDataStore(mockContext)
    }

    @Test
    fun testSaveVowConfigWithResetStats() = runTest {
        // Save initial config with some custom non-zero stats
        testDataStore.edit { preferences ->
            preferences[VowDataStore.VOW_INTRUSIONS_COUNT] = 5
            preferences[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS] = 10000L
        }

        // Call saveVowConfig with resetStats = true
        vowDataStore.saveVowConfig(
            isVowActive = true,
            isActiveVowMode = true,
            remainingVowSeconds = 3600L,
            lastSystemUptimeMillis = 500L,
            banDomainSet = emptySet(),
            secureFolderEnabled = false,
            privateSpaceEnabled = false,
            lockUninstall = false,
            disallowDataWipe = false,
            disableSafeBoot = false,
            blockPlayStore = false,
            dynamicReinstall = false,
            deactivateUsbDebugging = false,
            quietHoursEnabled = false,
            quietStartHour = 0,
            quietStartMin = 0,
            quietEndHour = 0,
            quietEndMin = 0,
            quietHoursTargetAppSet = emptySet(),
            quietHoursSpecificDomain = "",
            usageLimitsUpdated = false,
            allowedValue = "5",
            allowedUnit = "min",
            selectedInterval = "hour",
            targetAppSet = emptySet(),
            specificDomain = "",
            deactivationRequestTime = 0L,
            isCollectiveLimit = false,
            vowBlocksJson = "",
            resetStats = true
        )

        // Verify that stats are reset to 0/0L
        val prefs = testDataStore.data.first()
        assertEquals(0, prefs[VowDataStore.VOW_INTRUSIONS_COUNT])
        assertEquals(0L, prefs[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS])
    }

    @Test
    fun testSaveVowConfigWithoutResetStats() = runTest {
        // Save initial config with some custom non-zero stats
        testDataStore.edit { preferences ->
            preferences[VowDataStore.VOW_INTRUSIONS_COUNT] = 5
            preferences[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS] = 10000L
        }

        // Call saveVowConfig with resetStats = false (default)
        vowDataStore.saveVowConfig(
            isVowActive = true,
            isActiveVowMode = true,
            remainingVowSeconds = 3600L,
            lastSystemUptimeMillis = 500L,
            banDomainSet = emptySet(),
            secureFolderEnabled = false,
            privateSpaceEnabled = false,
            lockUninstall = false,
            disallowDataWipe = false,
            disableSafeBoot = false,
            blockPlayStore = false,
            dynamicReinstall = false,
            deactivateUsbDebugging = false,
            quietHoursEnabled = false,
            quietStartHour = 0,
            quietStartMin = 0,
            quietEndHour = 0,
            quietEndMin = 0,
            quietHoursTargetAppSet = emptySet(),
            quietHoursSpecificDomain = "",
            usageLimitsUpdated = false,
            allowedValue = "5",
            allowedUnit = "min",
            selectedInterval = "hour",
            targetAppSet = emptySet(),
            specificDomain = "",
            deactivationRequestTime = 0L,
            isCollectiveLimit = false,
            vowBlocksJson = ""
        )

        // Verify that stats are NOT reset
        val prefs = testDataStore.data.first()
        assertEquals(5, prefs[VowDataStore.VOW_INTRUSIONS_COUNT])
        assertEquals(10000L, prefs[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS])
    }

    @Test
    fun testClearVowConfigLogsSession() = runTest {
        val capturedSession = slot<VowSession>()
        coEvery { mockDao.insert(capture(capturedSession)) } just Runs

        val startTime = System.currentTimeMillis() - 60000L
        val initialDuration = 60L
        val intrusions = 3
        val allowedScreenTime = 5000L

        // GIVEN: Preferences has an active vow and some recorded stats
        testDataStore.edit { preferences ->
            preferences[VowDataStore.IS_VOW_ACTIVE] = true
            preferences[VowDataStore.VOW_START_TIME_MS] = startTime
            preferences[VowDataStore.VOW_INITIAL_DURATION_SECONDS] = initialDuration
            preferences[VowDataStore.VOW_INTRUSIONS_COUNT] = intrusions
            preferences[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS] = allowedScreenTime
            
            // Recompute signature so it is valid
            preferences[VowDataStore.STATE_SIGNATURE] = "dummy"
        }

        // WHEN: clearVowConfig is called
        vowDataStore.clearVowConfig()

        // THEN: Database dao insert is invoked with the expected Session data
        coVerify(exactly = 1) { mockDao.insert(any()) }
        
        val session = capturedSession.captured
        assertEquals(startTime, session.startTimeMillis)
        assertEquals(initialDuration, session.durationSeconds)
        assertEquals(intrusions, session.intrusionsBlocked)
        assertEquals(allowedScreenTime, session.allowedScreenTimeMs)
        assertTrue(session.endTimeMillis >= startTime)

        // THEN: All preferences are reset to default
        val prefs = testDataStore.data.first()
        assertFalse(prefs[VowDataStore.IS_VOW_ACTIVE] ?: false)
        assertEquals(0L, prefs[VowDataStore.VOW_START_TIME_MS] ?: 0L)
        assertEquals(0L, prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS] ?: 0L)
        assertEquals(0, prefs[VowDataStore.VOW_INTRUSIONS_COUNT] ?: 0)
        assertEquals(0L, prefs[VowDataStore.VOW_ALLOWED_SCREEN_TIME_MS] ?: 0L)
    }

    @Test
    fun testClearVowConfigNoActiveVowDoesNotLog() = runTest {
        // GIVEN: Preferences has an inactive vow
        testDataStore.edit { preferences ->
            preferences[VowDataStore.IS_VOW_ACTIVE] = false
        }

        // WHEN: clearVowConfig is called
        vowDataStore.clearVowConfig()

        // THEN: Database dao insert is NOT called
        coVerify(exactly = 0) { mockDao.insert(any()) }
    }

    @Test
    fun testSaveCountdownStateAccumulatesInitialDuration() = runTest {
        // GIVEN: Preferences has an initial duration of 1000 seconds
        testDataStore.edit { preferences ->
            preferences[VowDataStore.VOW_INITIAL_DURATION_SECONDS] = 1000L
        }

        // WHEN: saveCountdownState is called with additional duration of 500 seconds
        vowDataStore.saveCountdownState(remainingSeconds = 1500L, lastSystemUptimeMillis = 12345L, additionalDurationSeconds = 500L)

        // THEN: VOW_INITIAL_DURATION_SECONDS is updated to 1500L
        val prefs = testDataStore.data.first()
        assertEquals(1500L, prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS])
        assertEquals(1500L, prefs[VowDataStore.REMAINING_VOW_SECONDS])
    }

    @Test
    fun testClearVowConfigDatabaseFailureStillClearsPreferences() = runTest {
        // GIVEN: Database insertion fails (throws SQLite Exception)
        coEvery { mockDao.insert(any()) } throws RuntimeException("Database write failed")

        testDataStore.edit { preferences ->
            preferences[VowDataStore.IS_VOW_ACTIVE] = true
            preferences[VowDataStore.VOW_START_TIME_MS] = System.currentTimeMillis() - 5000L
            preferences[VowDataStore.VOW_INITIAL_DURATION_SECONDS] = 5L
        }

        // WHEN: clearVowConfig is called
        vowDataStore.clearVowConfig()

        // THEN: Preferences are still successfully cleared to prevent user from being locked out
        val prefs = testDataStore.data.first()
        assertFalse(prefs[VowDataStore.IS_VOW_ACTIVE] ?: false)
        assertEquals(0L, prefs[VowDataStore.VOW_START_TIME_MS] ?: 0L)
        assertEquals(0L, prefs[VowDataStore.VOW_INITIAL_DURATION_SECONDS] ?: 0L)
    }

    @Test
    fun testCalculateZenScoreEdgeCases() {
        // 1. Zero duration (focus duration = 0)
        // Penalty should only come from intrusions, screen time penalty is 0 because focusDurationHr is 0.0
        // formula: zen = max(0, 100 - (intrusions * 10))
        val zenZeroDurationNoIntrusions = VowValidator.calculateZenScore(intrusions = 0, allowedScreenTimeMs = 5000L, durationSeconds = 0L)
        assertEquals(100, zenZeroDurationNoIntrusions)

        val zenZeroDurationWithIntrusions = VowValidator.calculateZenScore(intrusions = 3, allowedScreenTimeMs = 5000L, durationSeconds = 0L)
        assertEquals(70, zenZeroDurationWithIntrusions)

        // 2. Negative duration (time rollback handling)
        // Due to maxOf(1L, durationSecs) clamping in clearVowConfig, duration will be at least 1L.
        // Let's test negative values directly in calculateZenScore: focusDurationHr will be negative, resulting in a negative penalty or positive add-back,
        // but let's see how the formula behaves:
        // focusDurationHr = -10.0 / 3600.0
        // allowedScreenTimeMin = 1.0
        // penalty = (intrusions * 10) + (allowedScreenTimeMin / focusDurationHr) * 5
        // zen score is clamped between 0 and 100.
        val zenNegativeDuration = VowValidator.calculateZenScore(intrusions = 0, allowedScreenTimeMs = 60000L, durationSeconds = -3600L)
        // Allowed screen time min = 1.0. Focus duration hr = -1.0. (1.0 / -1.0) * 5.0 = -5.0. Penalty = -5.0. Zen = 100 - (-5) = 105, clamped to 100.
        assertEquals(100, zenNegativeDuration)

        // 3. Extreme intrusion count (clamps to 0)
        val zenExtremeIntrusions = VowValidator.calculateZenScore(intrusions = 20, allowedScreenTimeMs = 0L, durationSeconds = 3600L)
        assertEquals(0, zenExtremeIntrusions)
    }
}
