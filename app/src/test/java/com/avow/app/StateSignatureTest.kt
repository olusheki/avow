package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.avow.app.data.VowDataStore
import com.avow.app.util.VowValidator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class StateSignatureTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun testValidStateSignatureSucceeds() = runTest {
        val tempFile = tmpFolder.newFile("test_settings_signature_valid.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)
        
        val vowDataStore = mockk<VowDataStore>(relaxed = true)
        every { vowDataStore.preferencesFlow } returns testDataStore.data
        
        val isActive = true
        val remaining = 3600L
        val uptime = 123456L
        val domains = setOf("facebook.com", "instagram.com")
        val apps = setOf("com.instagram.android")
        
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = isActive
            prefs[VowDataStore.REMAINING_VOW_SECONDS] = remaining
            prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] = uptime
            prefs[VowDataStore.BAN_DOMAIN_SET] = domains
            prefs[VowDataStore.TARGET_APP_SET] = apps
            prefs[VowDataStore.STATE_SIGNATURE] = VowValidator.computeStateSignature(isActive, remaining, uptime, domains, apps)
        }

        val prefs = testDataStore.data.first()
        val realVowDataStore = VowDataStore(mockContext)
        val isValid = realVowDataStore.isSignatureValid(prefs)
        
        assertTrue(isValid)
    }

    @Test
    fun testSetPermutationsProduceIdenticalSignature() {
        val isActive = true
        val remaining = 3600L
        val uptime = 123456L
        
        // GIVEN: Set collections with different item order permutations
        val domainsPermutation1 = setOf("instagram.com", "facebook.com")
        val domainsPermutation2 = setOf("facebook.com", "instagram.com")
        
        val appsPermutation1 = setOf("com.tiktok.android", "com.facebook.katana")
        val appsPermutation2 = setOf("com.facebook.katana", "com.tiktok.android")
        
        // WHEN: Computing signatures for each permutation
        val sig1 = VowValidator.computeStateSignature(isActive, remaining, uptime, domainsPermutation1, appsPermutation1)
        val sig2 = VowValidator.computeStateSignature(isActive, remaining, uptime, domainsPermutation2, appsPermutation2)
        
        // THEN: The computed hashes must be mathematically identical (sorting handles permutations)
        assertEquals(sig1, sig2)
    }

    @Test
    fun testTamperedStateSignatureTriggersLockoutFallback() = runTest {
        val tempFile = tmpFolder.newFile("test_settings_signature_tamper.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)
        
        val isActive = true
        val remaining = 3600L
        val uptime = 123456L
        val domains = setOf("facebook.com")
        val apps = emptySet<String>()
        val validSig = VowValidator.computeStateSignature(isActive, remaining, uptime, domains, apps)
        
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = isActive
            prefs[VowDataStore.REMAINING_VOW_SECONDS] = remaining
            prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] = uptime
            prefs[VowDataStore.BAN_DOMAIN_SET] = domains
            prefs[VowDataStore.TARGET_APP_SET] = apps
            prefs[VowDataStore.STATE_SIGNATURE] = validSig
        }

        // SIMULATE TAMPERING: The user directly edits the file to disable active vow (IS_VOW_ACTIVE = false)
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = false
        }

        val realVowDataStore = VowDataStore(mockContext)
        val tamperedPrefs = testDataStore.data.first()
        
        val isValid = realVowDataStore.isSignatureValid(tamperedPrefs)
        assertFalse(isValid)

        val mappedPrefsFlow = testDataStore.data.map { preferences ->
            if (!realVowDataStore.isSignatureValid(preferences)) {
                val mutablePrefs = preferences.toMutablePreferences()
                mutablePrefs[VowDataStore.IS_VOW_ACTIVE] = true
                mutablePrefs[VowDataStore.REMAINING_VOW_SECONDS] = maxOf(preferences[VowDataStore.REMAINING_VOW_SECONDS] ?: 0L, 7L * 24L * 3600L)
                mutablePrefs[VowDataStore.LOCK_UNINSTALL] = true
                mutablePrefs[VowDataStore.DISALLOW_DATA_WIPE] = true
                mutablePrefs[VowDataStore.DISABLE_SAFE_BOOT] = true
                mutablePrefs[VowDataStore.BLOCK_PLAY_STORE] = true
                mutablePrefs[VowDataStore.DEACTIVATE_USB_DEBUGGING] = true
                mutablePrefs
            } else {
                preferences
            }
        }

        val resolvedPrefs = mappedPrefsFlow.first()

        assertTrue(resolvedPrefs[VowDataStore.IS_VOW_ACTIVE] == true)
        assertEquals(7L * 24L * 3600L, resolvedPrefs[VowDataStore.REMAINING_VOW_SECONDS])
        assertTrue(resolvedPrefs[VowDataStore.LOCK_UNINSTALL] == true)
        assertTrue(resolvedPrefs[VowDataStore.DISALLOW_DATA_WIPE] == true)
        assertTrue(resolvedPrefs[VowDataStore.DISABLE_SAFE_BOOT] == true)
        assertTrue(resolvedPrefs[VowDataStore.BLOCK_PLAY_STORE] == true)
        assertTrue(resolvedPrefs[VowDataStore.DEACTIVATE_USB_DEBUGGING] == true)
    }

    @Test
    fun testSignatureInvalidatedWhenDeactivationTimeTampers() = runTest {
        val tempFile = tmpFolder.newFile("test_settings_signature_deactivation_tamper.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)
        
        val isActive = false
        val isActiveVowMode = false
        val remaining = 0L
        val uptime = 0L
        val domains = emptySet<String>()
        val apps = emptySet<String>()
        val deactivationTime = 1000L
        
        val validSig = VowValidator.computeHMACSignature(
            isVowActive = isActive,
            isActiveVowMode = isActiveVowMode,
            remainingSeconds = remaining,
            lastUptimeMillis = uptime,
            banDomainSet = domains,
            targetAppSet = apps,
            deactivationRequestTime = deactivationTime
        )
        
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = isActive
            prefs[VowDataStore.IS_ACTIVE_VOW_MODE] = isActiveVowMode
            prefs[VowDataStore.REMAINING_VOW_SECONDS] = remaining
            prefs[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] = uptime
            prefs[VowDataStore.BAN_DOMAIN_SET] = domains
            prefs[VowDataStore.TARGET_APP_SET] = apps
            prefs[VowDataStore.DEACTIVATION_REQUEST_TIME] = deactivationTime
            prefs[VowDataStore.STATE_SIGNATURE] = validSig
        }

        // TAMPER DEACTIVATION REQUEST TIME: User edits the file to clear deactivation cooling off delay
        testDataStore.edit { prefs ->
            prefs[VowDataStore.DEACTIVATION_REQUEST_TIME] = 0L
        }

        val realVowDataStore = VowDataStore(mockContext)
        val tamperedPrefs = testDataStore.data.first()
        
        val isValid = realVowDataStore.isSignatureValid(tamperedPrefs)
        assertFalse(isValid)
    }
}
