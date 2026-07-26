package com.avow.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.avow.app.data.VowDataStore
import com.avow.app.data.dataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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

/** Covers [VowDataStore.repairTamperedStateIfNeeded] (W1-T5): a tampered signature must be repaired
 *  to the softened 24h lockout AND persisted to disk, and a valid state must be left untouched. */
@OptIn(ExperimentalCoroutinesApi::class)
class TamperRepairTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var vowDataStore: VowDataStore

    @Before
    fun setup() {
        val tempFile = tmpFolder.newFile("test_settings.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        mockContext = mockk(relaxed = true)
        mockkStatic("com.avow.app.data.VowDataStoreKt")
        every { mockContext.dataStore } returns testDataStore
        vowDataStore = VowDataStore(mockContext)
    }

    @Test
    fun tamperedSignature_isRepairedAndPersisted() = runTest {
        // GIVEN: an active vow with a bogus signature (tampered on disk).
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = true
            prefs[VowDataStore.REMAINING_VOW_SECONDS] = 60L
            prefs[VowDataStore.STATE_SIGNATURE] = "tampered_signature"
        }

        // WHEN: the repair runs.
        vowDataStore.repairTamperedStateIfNeeded()

        // THEN: the softened 24h lockout (D-3) is persisted and the signature is now valid.
        val prefs = testDataStore.data.first()
        assertTrue(prefs[VowDataStore.IS_VOW_ACTIVE] ?: false)
        // Active mode is intentionally NOT forced — the vow floor drives enforcement, and forcing it
        // would strand a blameless user in permanent Active mode after the lockout lifts.
        assertFalse(prefs[VowDataStore.IS_ACTIVE_VOW_MODE] ?: false)
        assertTrue((prefs[VowDataStore.REMAINING_VOW_SECONDS] ?: 0L) >= VowDataStore.TAMPER_LOCKOUT_SECONDS)
        assertTrue(prefs[VowDataStore.BLOCK_PLAY_STORE] ?: false)
        assertTrue(vowDataStore.isSignatureValid(prefs))
    }

    @Test
    fun repair_isIdempotent() = runTest {
        testDataStore.edit { prefs ->
            prefs[VowDataStore.IS_VOW_ACTIVE] = true
            prefs[VowDataStore.STATE_SIGNATURE] = "tampered"
        }
        vowDataStore.repairTamperedStateIfNeeded()
        val afterFirst = testDataStore.data.first()[VowDataStore.STATE_SIGNATURE]
        vowDataStore.repairTamperedStateIfNeeded()
        val afterSecond = testDataStore.data.first()[VowDataStore.STATE_SIGNATURE]
        assertEquals(afterFirst, afterSecond)
    }

    @Test
    fun validState_isLeftUntouched() = runTest {
        // A fresh, all-default store has a null signature and no non-default values → valid.
        vowDataStore.repairTamperedStateIfNeeded()
        val prefs = testDataStore.data.first()
        // No strict escalation should have been written.
        assertFalse(prefs[VowDataStore.IS_VOW_ACTIVE] ?: false)
    }
}
