package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.avow.app.data.VowDataStore
import com.avow.app.data.dataStore
import com.avow.app.receiver.BootReceiver
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
 * [BootReceiver.reconcileBootState] is the boot-recovery core: it must clear the reboot-stale
 * doomscroll cooldown (an elapsedRealtime value that over-enforces once uptime restarts) and report
 * whether an active vow means MainActivity should relaunch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @After
    fun tearDown() = unmockkAll()

    private fun realDataStore(fileName: String): VowDataStore {
        val tempFile = tmpFolder.newFile(fileName)
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val mockContext = mockk<Context>(relaxed = true)
        mockkStatic("com.avow.app.data.VowDataStoreKt")
        every { mockContext.dataStore } returns testDataStore
        return VowDataStore(mockContext)
    }

    /** Writes a validly-signed vow state so the read flow doesn't apply the tamper fallback. */
    private suspend fun VowDataStore.seedVow(isVowActive: Boolean) = saveVowConfig(
        isVowActive = isVowActive, isActiveVowMode = false, remainingVowSeconds = 3600L,
        lastSystemUptimeMillis = 0L, banDomainSet = emptySet(),
        secureFolderEnabled = false, privateSpaceEnabled = false, lockUninstall = false,
        disallowDataWipe = false, disableSafeBoot = false, blockPlayStore = false,
        dynamicReinstall = false, deactivateUsbDebugging = false, quietHoursEnabled = false,
        quietStartHour = 22, quietStartMin = 0, quietEndHour = 7, quietEndMin = 0,
        quietHoursTargetAppSet = emptySet(), quietHoursSpecificDomain = "",
        usageLimitsUpdated = false, allowedValue = "5", allowedUnit = "min",
        selectedInterval = "hour", targetAppSet = emptySet(), specificDomain = "",
        deactivationRequestTime = 0L, isCollectiveLimit = false, vowBlocksJson = ""
    )

    @Test
    fun reconcileBootState_activeVow_clearsStaleLockout_reportsRelaunch() = runTest {
        val ds = realDataStore("boot_active.preferences_pb")
        ds.seedVow(isVowActive = true)
        ds.saveTemporaryLockoutEndTime(999_999L) // reboot-stale cooldown

        val relaunch = ds.reconcileBootState()

        assertTrue(relaunch)
        assertEquals(0L, ds.preferencesFlow.first()[VowDataStore.TEMPORARY_LOCKOUT_END_TIME])
    }

    @Test
    fun reconcileBootState_noVow_reportsNoRelaunch() = runTest {
        val ds = realDataStore("boot_inactive.preferences_pb")
        ds.seedVow(isVowActive = false)

        assertFalse(ds.reconcileBootState())
    }

    private suspend fun VowDataStore.reconcileBootState(): Boolean =
        BootReceiver().reconcileBootState(this)
}
