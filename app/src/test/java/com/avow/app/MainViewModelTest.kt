package com.avow.app

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.avow.app.ui.MainViewModel
import com.avow.app.ui.ScreenState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

    @Test
    fun testUpdateState_UpdatesFlowCorrectly() = runTest {
        val app = mockk<Application>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        every { app.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { dpm.isDeviceOwnerApp(any()) } returns true
        
        val viewModel = MainViewModel(app)
        
        viewModel.updateState { copy(currentState = ScreenState.CONFIGURATION) }
        
        val state = viewModel.uiState.first()
        assertEquals(ScreenState.CONFIGURATION, state.currentState)
    }
    
    @Test
    fun testDeviceOwner_ExposedCorrectly() = runTest {
        val app = mockk<Application>(relaxed = true)
        val dpm = mockk<DevicePolicyManager>(relaxed = true)
        every { app.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { dpm.isDeviceOwnerApp(any()) } returns true
        
        val viewModel = MainViewModel(app)
        
        val state = viewModel.uiState.first()
        assertEquals(true, state.isDeviceOwner)
    }
}
