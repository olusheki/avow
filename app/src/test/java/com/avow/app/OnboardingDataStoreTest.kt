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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    /** The onboarding flag participates in the tamper signature, so flipping it must change the hash. */
    @Test
    fun testOnboardingFlagAffectsSignature() {
        val sigNotCompleted = VowValidator.computeHMACSignature(
            isVowActive = false,
            isActiveVowMode = false,
            remainingSeconds = 0L,
            lastUptimeMillis = 0L,
            banDomainSet = emptySet(),
            targetAppSet = emptySet(),
            deactivationRequestTime = 0L,
            isOnboardingCompleted = false
        )
        val sigCompleted = VowValidator.computeHMACSignature(
            isVowActive = false,
            isActiveVowMode = false,
            remainingSeconds = 0L,
            lastUptimeMillis = 0L,
            banDomainSet = emptySet(),
            targetAppSet = emptySet(),
            deactivationRequestTime = 0L,
            isOnboardingCompleted = true
        )
        assertNotEquals(sigNotCompleted, sigCompleted)
    }

    /** A completed-onboarding state with a correctly recomputed signature must validate. */
    @Test
    fun testCompletedOnboardingWithValidSignatureIsValid() = runTest {
        val tempFile = tmpFolder.newFile("test_onboarding_valid.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)

        // Signature computed over all-default state with onboarding completed = true. The defaults
        // here mirror computeSignatureFromPrefs, so isSignatureValid should accept it.
        val validSig = VowValidator.computeHMACSignature(
            isVowActive = false,
            isActiveVowMode = false,
            remainingSeconds = 0L,
            lastUptimeMillis = 0L,
            banDomainSet = emptySet(),
            targetAppSet = emptySet(),
            deactivationRequestTime = 0L,
            isOnboardingCompleted = true
        )

        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_ONBOARDING_COMPLETED] = true
            prefs[VowDataStore.STATE_SIGNATURE] = validSig
        }

        val realVowDataStore = VowDataStore(mockContext)
        val prefs = testDataStore.data.first()

        assertTrue(realVowDataStore.isSignatureValid(prefs))
        assertTrue(prefs[VowDataStore.IS_ONBOARDING_COMPLETED] == true)
    }

    /** Tampering the onboarding flag without updating the signature must be detected. */
    @Test
    fun testTamperedOnboardingFlagIsInvalid() = runTest {
        val tempFile = tmpFolder.newFile("test_onboarding_tamper.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)

        val validSig = VowValidator.computeHMACSignature(
            isVowActive = false,
            isActiveVowMode = false,
            remainingSeconds = 0L,
            lastUptimeMillis = 0L,
            banDomainSet = emptySet(),
            targetAppSet = emptySet(),
            deactivationRequestTime = 0L,
            isOnboardingCompleted = true
        )

        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_ONBOARDING_COMPLETED] = true
            prefs[VowDataStore.STATE_SIGNATURE] = validSig
        }

        // TAMPER: flip onboarding back to false without recomputing the signature.
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_ONBOARDING_COMPLETED] = false
        }

        val realVowDataStore = VowDataStore(mockContext)
        val tamperedPrefs = testDataStore.data.first()

        assertFalse(realVowDataStore.isSignatureValid(tamperedPrefs))
    }

    /** A pristine first launch (no signature, onboarding not completed) is treated as valid. */
    @Test
    fun testFreshFirstLaunchIsValid() = runTest {
        val tempFile = tmpFolder.newFile("test_onboarding_fresh.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)

        val realVowDataStore = VowDataStore(mockContext)
        val prefs = testDataStore.data.first()

        assertTrue(realVowDataStore.isSignatureValid(prefs))
    }
}
