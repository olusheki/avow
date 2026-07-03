package com.avow.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

        // The intercept is now scoped to Settings screens *about aVow*: stub a window that names it.
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.text } returns "aVow"
        every { rootNode.contentDescription } returns null
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens the aVow app-info / accessibility screen in Settings
        service.onAccessibilityEvent(event)

        // THEN: Accessibility service must execute GLOBAL_ACTION_HOME redirect
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    @Test
    fun testGeneralSettingsAllowedDuringActiveVow() {
        // GIVEN: an active-mode vow, but the Settings window is NOT about aVow (e.g. Wi-Fi).
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)
        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, true)

        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.text } returns "Wi-Fi"
        every { rootNode.contentDescription } returns null
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens a general Settings screen
        service.onAccessibilityEvent(event)

        // THEN: it is NOT bounced — only aVow-specific screens are blocked
        verify(exactly = 0) { service.performGlobalAction(any()) }
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

        // Scoped intercept: the cooling-off window still bounces the aVow-specific Settings screen.
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.text } returns "aVow"
        every { rootNode.contentDescription } returns null
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens the aVow Settings screen during cooling-off
        service.onAccessibilityEvent(event)

        // THEN: Accessibility service must execute GLOBAL_ACTION_HOME redirect
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    @Test
    fun testActiveVowContinuousLockoutBlocksTargetApps() {
        // GIVEN: Active vow mode is enabled, and a vow is active
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, true)

        // GIVEN: "com.instagram.android" is in the targetAppSet
        val targetAppSetField = BlockerService::class.java.getDeclaredField("targetAppSet")
        targetAppSetField.isAccessible = true
        targetAppSetField.set(service, setOf("com.instagram.android"))

        // Mock Intent constructor and builder methods to prevent "Intent not mocked" exception
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)

        // Mock startActivity to verify redirect overlay launch
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Instagram
        service.onAccessibilityEvent(event)

        // THEN: Blackout overlay is triggered (startActivity called)
        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun testPassiveVowDoesNotBlockTargetAppsOutsideQuietHoursOrLimits() {
        // GIVEN: Vow is active, but Active Vow Mode is disabled (Passive Vow)
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, false)

        val targetAppSetField = BlockerService::class.java.getDeclaredField("targetAppSet")
        targetAppSetField.isAccessible = true
        targetAppSetField.set(service, setOf("com.instagram.android"))

        // Quiet hours is disabled
        val quietHoursEnabledField = BlockerService::class.java.getDeclaredField("quietHoursEnabled")
        quietHoursEnabledField.isAccessible = true
        quietHoursEnabledField.set(service, false)

        // Usage limits disabled
        val usageLimitsUpdatedField = BlockerService::class.java.getDeclaredField("usageLimitsUpdated")
        usageLimitsUpdatedField.isAccessible = true
        usageLimitsUpdatedField.set(service, false)

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Instagram
        service.onAccessibilityEvent(event)

        // THEN: No redirection to blackout overlay
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testActiveVowModeWithoutActiveVowDoesNotBlockTargetApps() {
        // GIVEN: Active vow mode is enabled, but isVowActive is false
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, false)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, true)

        val targetAppSetField = BlockerService::class.java.getDeclaredField("targetAppSet")
        targetAppSetField.isAccessible = true
        targetAppSetField.set(service, setOf("com.instagram.android"))

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Instagram
        service.onAccessibilityEvent(event)

        // THEN: Redirection is NOT triggered (since vow is not active)
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testChromeIncognitoTriggersBlackoutOverlay() {
        // GIVEN: Vow is active
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        // Mock AccessibilityNodeInfo. Detection now keys on the toolbar badge's content description
        // (not arbitrary page text), so the incognito signal is set there.
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.text } returns null
        every { rootNode.contentDescription } returns "Incognito"
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        // Mock Intent constructor and builder methods to prevent "Intent not mocked" exception
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)

        // Mock startActivity to verify redirect overlay launch
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.chrome"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Chrome in Incognito mode
        service.onAccessibilityEvent(event)

        // THEN: Blackout overlay is triggered
        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun testChromeSpecificDomainBlocksWhenVowActive() {
        // GIVEN: Vow is active, and a specific domain is banned
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, true)

        val specificDomainField = BlockerService::class.java.getDeclaredField("specificDomain")
        specificDomainField.isAccessible = true
        specificDomainField.set(service, "instagram.com")

        // Mock AccessibilityNodeInfo containing the URL text "instagram.com/p/123"
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.className } returns "android.widget.EditText"
        every { rootNode.text } returns "instagram.com/p/123"
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        // Mock Intent constructor and builder methods to prevent "Intent not mocked" exception
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)

        // Mock startActivity to verify redirect overlay launch
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.chrome"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Chrome on a banned domain
        service.onAccessibilityEvent(event)

        // THEN: Blackout overlay is triggered
        verify(exactly = 1) { service.startActivity(any()) }
    }
}
