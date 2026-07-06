package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.avow.app.data.VowDataStore
import com.avow.app.util.VowValidator
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DirectBootClampingTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun testClampingPositiveOverflowValue() {
        val overflowSeconds = Long.MAX_VALUE
        val clamped = VowValidator.clampRemainingSeconds(overflowSeconds)
        // The ceiling is 99:23:59:59 (see VowValidator.MAX_VOW_SECONDS).
        assertEquals(VowValidator.MAX_VOW_SECONDS, clamped)
    }

    @Test
    fun testClampingNegativeValue() {
        val negativeSeconds = -500L
        val clamped = VowValidator.clampRemainingSeconds(negativeSeconds)
        assertEquals(0L, clamped)
    }

    @Test
    fun testClampingStandardValueIsUnchanged() {
        val validSeconds = 7200L
        val clamped = VowValidator.clampRemainingSeconds(validSeconds)
        assertEquals(validSeconds, clamped)
    }

    @Test
    fun testRebootResumptionHandlesOverflowSafely() {
        val savedRemaining = Long.MAX_VALUE
        val lastUptime = 50000L
        val currentUptime = 500L
        
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )

        assertEquals(VowValidator.MAX_VOW_SECONDS, remaining)
    }

    @Test
    fun testRebootResumptionHandlesNegativeCorruptedValueSafely() {
        val savedRemaining = -9999L
        val lastUptime = 50000L
        val currentUptime = 500L
        
        val remaining = VowValidator.calculateRemainingSeconds(
            currentUptimeMillis = currentUptime,
            lastUptimeMillis = lastUptime,
            savedRemainingSeconds = savedRemaining
        )
        
        assertEquals(0L, remaining)
    }

    @Test
    fun testMissingOrCorruptedSetPreferencesOnBootResolveSafely() = runTest {
        // GIVEN: A Preferences store where BAN_DOMAIN_SET and TARGET_APP_SET are null (missing)
        val tempFile = tmpFolder.newFile("test_settings_missing_sets.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)
        
        // Write settings leaving sets out entirely (simulating older database layout or corruption)
        val isActive = false
        val remaining = 0L
        val uptime = 0L
        
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = isActive
            prefs[VowDataStore.REMAINING_VOW_SECONDS] = remaining
            prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] = uptime
            // Explicitly do not set BAN_DOMAIN_SET or TARGET_APP_SET
        }

        val prefs = testDataStore.data.first()
        val realVowDataStore = VowDataStore(mockContext)

        // WHEN: Checking signature validity or fetching sets
        val signatureValid = realVowDataStore.isSignatureValid(prefs)
        
        // THEN: The missing set preferences must default safely to empty sets and not crash
        assertTrue(signatureValid)
        
        val domainSet = prefs[VowDataStore.BAN_DOMAIN_SET] ?: emptySet()
        val appSet = prefs[VowDataStore.TARGET_APP_SET] ?: emptySet()
        
        assertEquals(emptySet<String>(), domainSet)
        assertEquals(emptySet<String>(), appSet)
    }
}
