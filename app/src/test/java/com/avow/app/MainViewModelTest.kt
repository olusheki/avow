package com.avow.app

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.avow.app.data.VowDataStore
import com.avow.app.ui.MainViewModel
import com.avow.app.ui.ScreenState
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

        val viewModel = MainViewModel(app, mockDataStore)
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
        
        val viewModel = MainViewModel(app, mockDataStore)
        try {
            // Wait until initialization loads state from preferences flow
            val state = viewModel.uiState.first { it.isLoaded }
            assertEquals(true, state.isDeviceOwner)
        } finally {
            clearViewModel(viewModel)
        }
    }
}
