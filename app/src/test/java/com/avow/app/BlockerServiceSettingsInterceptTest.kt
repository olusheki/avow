package com.avow.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.avow.app.data.VowDataStore
import com.avow.app.service.BlockerService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockerServiceSettingsInterceptTest {

    private val mockDataStore = mockk<VowDataStore>(relaxed = true)
    private lateinit var service: BlockerService

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        service = spyk(BlockerService())
        every { service.packageName } returns "com.avow.app"
        
        // Inject the mocked VowDataStore via reflection since it is private
        val dsField = BlockerService::class.java.getDeclaredField("vowDataStore")
        dsField.isAccessible = true
        dsField.set(service, mockDataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSettingsAppBlockedWhenVowIsActive() {
        // GIVEN: Active vow mode is enabled, and a vow is running (mocking internal service state flags)
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, true)

        // Mock performGlobalAction to return true
        every { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } returns true

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens System Settings
        service.onAccessibilityEvent(event)

        // THEN: Accessibility service must execute GLOBAL_ACTION_HOME redirect
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    @Test
    fun testSettingsAppAllowedWhenVowIsInactive() {
        // GIVEN: Vow is not active
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, false)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, false)

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Settings
        service.onAccessibilityEvent(event)

        // THEN: Access is allowed (no redirect)
        verify(exactly = 0) { service.performGlobalAction(any()) }
    }

    @Test
    fun testSettingsAppBlockedDuringDeactivationCoolingOff() {
        // GIVEN: Vow is not active, but deactivation request cooling-off is active (saved timestamp 10 mins ago)
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, false)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, false)

        val deactivationRequestTimeField = BlockerService::class.java.getDeclaredField("deactivationRequestTime")
        deactivationRequestTimeField.isAccessible = true
        deactivationRequestTimeField.set(service, System.currentTimeMillis() - 10 * 60 * 1000L)

        // Mock performGlobalAction to return true
        every { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } returns true

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens System Settings
        service.onAccessibilityEvent(event)

        // THEN: Accessibility service must execute GLOBAL_ACTION_HOME redirect
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }
}
