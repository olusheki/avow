package com.avow.app

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.avow.app.data.VowDataStore
import com.avow.app.data.dataStore
import com.avow.app.enforcement.EnforcementCapabilities
import com.avow.app.ui.MainViewModel
import com.avow.app.ui.ScreenState
import com.avow.app.util.VowValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    /** Capabilities fake that records the `activate` flag of every assertBindingVow call. */
    private class RecordingCapabilities : EnforcementCapabilities {
        val assertActivations = mutableListOf<Boolean>()
        override val supportsDeviceOwnerFeatures = true
        override val isDeviceOwnerActive = true
        override fun assertBindingVow(
            activate: Boolean, secureFolderEnabled: Boolean, privateSpaceEnabled: Boolean,
            lockUninstall: Boolean, disallowDataWipe: Boolean, disableSafeBoot: Boolean,
            blockPlayStore: Boolean, deactivateUsbDebugging: Boolean
        ): String? { assertActivations.add(activate); return null }
        override fun deactivateDeviceOwner(): String? = null
    }

    /**
     * A MainViewModel backed by a REAL temp-file DataStore, so these tests exercise the actual
     * load/clear/countdown logic rather than a stubbed flow. [seed] writes the starting state.
     */
    private suspend fun buildRealViewModel(
        caps: RecordingCapabilities,
        fileName: String,
        seed: suspend (VowDataStore) -> Unit = {}
    ): MainViewModel {
        val tempFile = tmpFolder.newFile(fileName)
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        val app = mockk<Application>(relaxed = true)
        mockkStatic("com.avow.app.data.VowDataStoreKt")
        every { app.dataStore } returns testDataStore
        val realDs = VowDataStore(app)
        seed(realDs)
        return MainViewModel(app, realDs, caps)
    }

    // Flavor-agnostic capabilities so the test doesn't depend on which flavor's factory is compiled.
    private val fakeCapabilities = object : EnforcementCapabilities {
        override val supportsDeviceOwnerFeatures = true
        override val isDeviceOwnerActive = true
        override fun assertBindingVow(
            activate: Boolean, secureFolderEnabled: Boolean, privateSpaceEnabled: Boolean,
            lockUninstall: Boolean, disallowDataWipe: Boolean, disableSafeBoot: Boolean,
            blockPlayStore: Boolean, deactivateUsbDebugging: Boolean
        ): String? = null
        override fun deactivateDeviceOwner(): String? = null
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun clearViewModel(viewModel: MainViewModel) {
        try {
            viewModel.viewModelScope.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun testUpdateState_UpdatesFlowCorrectly() = runTest {
        val app = mockk<Application>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        every { app.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { dpm.isDeviceOwnerApp(any()) } returns true

        val mockDataStore = mockk<VowDataStore>(relaxed = true)
        every { mockDataStore.preferencesFlow } returns flowOf(emptyPreferences())

        val viewModel = MainViewModel(app, mockDataStore, fakeCapabilities)
        try {
            // Wait until initialization loads state from preferences flow
            viewModel.uiState.first { it.isLoaded }

            viewModel.updateState { copy(currentState = ScreenState.CONFIGURATION) }
            
            val state = viewModel.uiState.value
            assertEquals(ScreenState.CONFIGURATION, state.currentState)
        } finally {
            clearViewModel(viewModel)
        }
    }
    
    @Test
    fun testDeviceOwner_ExposedCorrectly() = runTest {
        val app = mockk<Application>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        every { app.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { dpm.isDeviceOwnerApp(any()) } returns true

        val mockDataStore = mockk<VowDataStore>(relaxed = true)
        every { mockDataStore.preferencesFlow } returns flowOf(emptyPreferences())
        
        val viewModel = MainViewModel(app, mockDataStore, fakeCapabilities)
        try {
            // Wait until initialization loads state from preferences flow
            val state = viewModel.uiState.first { it.isLoaded }
            assertEquals(true, state.isDeviceOwner)
        } finally {
            clearViewModel(viewModel)
        }
    }

    @Test
    fun addBindingTime_startsVow_capturesBaselinesAndAssertsLocks() = runTest {
        val caps = RecordingCapabilities()
        val vm = buildRealViewModel(caps, "vm_addbinding.preferences_pb")
        try {
            vm.uiState.first { it.isLoaded }
            assertFalse(vm.uiState.value.isVowActive)

            val err = vm.addBindingTime(3600L) // 1 hour
            assertNull(err)

            val s = vm.uiState.value
            assertTrue(s.isVowActive)
            assertEquals(ScreenState.LOCKED_VAULT, s.currentState)
            assertEquals(0, s.days)
            assertEquals(1, s.hours)
            assertTrue(s.vowStartTimeMs > 0L)
            assertEquals(3600L, s.vowInitialDurationSeconds)
            // frozen baselines snapshot the live config at inflict time
            assertEquals(s.allowedValue, s.frozenAllowedValue)
            assertEquals(s.selectedInterval, s.frozenInterval)
            // device-owner locks engaged exactly once, with activate = true
            assertEquals(listOf(true), caps.assertActivations)
        } finally {
            clearViewModel(vm)
        }
    }

    @Test
    fun addBindingTime_newVow_clampsToCeiling() = runTest {
        val caps = RecordingCapabilities()
        val vm = buildRealViewModel(caps, "vm_clamp.preferences_pb")
        try {
            vm.uiState.first { it.isLoaded }
            vm.addBindingTime(200L * 86400L) // 200 days, over the 99d ceiling
            val s = vm.uiState.value
            val total = s.days * 86400L + s.hours * 3600L + s.minutes * 60L + s.seconds
            assertEquals(VowValidator.MAX_VOW_SECONDS, total)
            assertEquals(VowValidator.MAX_VOW_SECONDS, s.vowInitialDurationSeconds)
        } finally {
            clearViewModel(vm)
        }
    }

    @Test
    fun addBindingTime_addTime_accumulatesThenCaps() = runTest {
        val caps = RecordingCapabilities()
        val vm = buildRealViewModel(caps, "vm_addtime.preferences_pb")
        try {
            vm.uiState.first { it.isLoaded }
            vm.addBindingTime(3600L)       // start a 1h vow
            vm.addBindingTime(2L * 3600L)  // add 2h
            var s = vm.uiState.value
            assertEquals(3L, s.days * 24L + s.hours) // 3 hours total

            vm.addBindingTime(200L * 86400L) // push past the ceiling
            s = vm.uiState.value
            val total = s.days * 86400L + s.hours * 3600L + s.minutes * 60L + s.seconds
            assertEquals(VowValidator.MAX_VOW_SECONDS, total)
        } finally {
            clearViewModel(vm)
        }
    }

    @Test
    fun expiredVowOnLoad_liftsRestrictions() = runTest {
        val caps = RecordingCapabilities()
        val vm = buildRealViewModel(caps, "vm_expiry.preferences_pb") { ds ->
            // Active vow with zero remaining = expired; load must clear it and release the locks.
            ds.saveVowConfig(
                isVowActive = true, isActiveVowMode = false, remainingVowSeconds = 0L,
                lastSystemUptimeMillis = 0L, banDomainSet = emptySet(),
                secureFolderEnabled = false, privateSpaceEnabled = false, lockUninstall = true,
                disallowDataWipe = false, disableSafeBoot = false, blockPlayStore = false,
                dynamicReinstall = false, deactivateUsbDebugging = false, quietHoursEnabled = false,
                quietStartHour = 22, quietStartMin = 0, quietEndHour = 7, quietEndMin = 0,
                quietHoursTargetAppSet = emptySet(), quietHoursSpecificDomain = "",
                usageLimitsUpdated = false, allowedValue = "5", allowedUnit = "min",
                selectedInterval = "hour", targetAppSet = emptySet(), specificDomain = "",
                deactivationRequestTime = 0L, isCollectiveLimit = false, vowBlocksJson = ""
            )
        }
        try {
            vm.uiState.first { it.isLoaded }
            assertFalse(vm.uiState.value.isVowActive)
            // restrictions released: assertBindingVow(activate = false) was called
            assertTrue(caps.assertActivations.contains(false))
        } finally {
            clearViewModel(vm)
        }
    }
}
