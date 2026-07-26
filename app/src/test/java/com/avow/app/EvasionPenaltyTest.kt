package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.avow.app.data.VowDataStore
import com.avow.app.data.dataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [VowDataStore.applyEvasionVowPenalty] — the lite pop-out penalty: +1h to the running vow, at most
 * once per vow. (SystemClock.elapsedRealtime() is 0 in JVM tests, so with lastUptime = 0 the live
 * remaining equals the stored remaining, making the arithmetic deterministic.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvasionPenaltyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() = unmockkAll()

    private fun realDs(name: String): VowDataStore {
        val f = tmp.newFile(name)
        val ds = PreferenceDataStoreFactory.create(produceFile = { f })
        val ctx = mockk<Context>(relaxed = true)
        mockkStatic("com.avow.app.data.VowDataStoreKt")
        every { ctx.dataStore } returns ds
        return VowDataStore(ctx)
    }

    private suspend fun VowDataStore.seedVow(remaining: Long, start: Long) = saveVowConfig(
        isVowActive = true, isActiveVowMode = false, remainingVowSeconds = remaining,
        lastSystemUptimeMillis = 0L, banDomainSet = emptySet(),
        secureFolderEnabled = false, privateSpaceEnabled = false, lockUninstall = false,
        disallowDataWipe = false, disableSafeBoot = false, blockPlayStore = false,
        dynamicReinstall = false, deactivateUsbDebugging = false, quietHoursEnabled = false,
        quietStartHour = 22, quietStartMin = 0, quietEndHour = 7, quietEndMin = 0,
        quietHoursTargetAppSet = emptySet(), quietHoursSpecificDomain = "",
        usageLimitsUpdated = false, allowedValue = "5", allowedUnit = "min",
        selectedInterval = "hour", targetAppSet = emptySet(), specificDomain = "",
        deactivationRequestTime = 0L, isCollectiveLimit = false, vowBlocksJson = "",
        vowStartTimeMs = start, vowInitialDurationSeconds = remaining
    )

    @Test
    fun penalty_extendsVowByAnHour_oncePerVow() = runTest {
        val ds = realDs("penalty_once.preferences_pb")
        ds.seedVow(remaining = 3600L, start = 1000L)

        assertTrue(ds.applyEvasionVowPenalty(3600L))
        assertEquals(7200L, ds.preferencesFlow.first()[VowDataStore.REMAINING_VOW_SECONDS])

        // Same vow → no additional penalty (matches "even if they use PiP again").
        assertFalse(ds.applyEvasionVowPenalty(3600L))
        assertEquals(7200L, ds.preferencesFlow.first()[VowDataStore.REMAINING_VOW_SECONDS])
    }

    @Test
    fun penalty_appliesAgainForANewVow() = runTest {
        val ds = realDs("penalty_newvow.preferences_pb")
        ds.seedVow(remaining = 3600L, start = 1000L)
        assertTrue(ds.applyEvasionVowPenalty(3600L))

        // A fresh vow (new start time) resets the once-per-vow guard.
        ds.seedVow(remaining = 3600L, start = 2000L)
        assertTrue(ds.applyEvasionVowPenalty(3600L))
        assertEquals(7200L, ds.preferencesFlow.first()[VowDataStore.REMAINING_VOW_SECONDS])
    }

    @Test
    fun penalty_bumpsNonceSoTheViewModelReSyncs() = runTest {
        val ds = realDs("penalty_nonce.preferences_pb")
        ds.seedVow(remaining = 3600L, start = 1000L)
        val before = ds.preferencesFlow.first()[VowDataStore.EVASION_PENALTY_NONCE] ?: 0L
        ds.applyEvasionVowPenalty(3600L)
        val after = ds.preferencesFlow.first()[VowDataStore.EVASION_PENALTY_NONCE] ?: 0L
        assertTrue("nonce must change so the ViewModel re-reads the countdown", after != before)
    }

    @Test
    fun penalty_withNoActiveVow_isNoOp() = runTest {
        val ds = realDs("penalty_novow.preferences_pb")
        assertFalse(ds.applyEvasionVowPenalty(3600L))
    }
}
