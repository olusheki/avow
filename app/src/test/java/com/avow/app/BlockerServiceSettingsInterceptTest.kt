package com.avow.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.avow.app.data.VowDataStore
import com.avow.app.model.VowBlock
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
        every { service.packageName } returns "com.makeavow.app"
        
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

    private fun setField(name: String, value: Any?) {
        val f = BlockerService::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(service, value)
    }

    /** An always-on (00:00–23:59) enabled scheduled block targeting Instagram. */
    private fun allDayInstagramBlock() = listOf(
        VowBlock(
            id = "1", name = "All day", isEnabled = true,
            startHour = 0, startMin = 0, endHour = 23, endMin = 59,
            targetApps = setOf("com.instagram.android"), specificDomain = ""
        )
    )

    @Test
    fun testActiveModeEnforcesScheduledBlockWithoutVow() {
        // GIVEN: Active mode is on but NO vow is running — rules should still enforce on schedule.
        setField("isVowActive", false)
        setField("isActiveVowMode", true)
        setField("vowBlocks", allDayInstagramBlock())

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens Instagram within the block window, with no vow set
        service.onAccessibilityEvent(event)

        // THEN: it is blocked — Active mode removes the vow requirement
        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun testPassiveModeWithoutVowDoesNotEnforce() {
        // GIVEN: Passive mode, no vow — the same enabled block must do nothing until a vow is set.
        setField("isVowActive", false)
        setField("isActiveVowMode", false)
        setField("vowBlocks", allDayInstagramBlock())

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: no blackout — passive rules require a running vow
        verify(exactly = 0) { service.startActivity(any()) }
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
    fun testChromeBannedDomainBlocksWhenVowActive() {
        // GIVEN: a vow is active and instagram.com is in the global ban set (the immediate-block
        // path — a usage-limit domain instead blocks only when its time budget is exceeded).
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val banDomainSetField = BlockerService::class.java.getDeclaredField("banDomainSet")
        banDomainSetField.isAccessible = true
        banDomainSetField.set(service, setOf("instagram.com"))

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

    @Test
    fun testSettingsBlockedDuringPassiveVowForSelfScreen() {
        // GIVEN: a PASSIVE vow (active mode off) that is running.
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)
        val isActiveVowModeField = BlockerService::class.java.getDeclaredField("isActiveVowMode")
        isActiveVowModeField.isAccessible = true
        isActiveVowModeField.set(service, false)

        every { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } returns true

        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { rootNode.text } returns "aVow"
        every { rootNode.contentDescription } returns null
        every { rootNode.childCount } returns 0
        every { service.rootInActiveWindow } returns rootNode

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.settings"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: User opens the aVow app-info / accessibility screen during a passive vow
        service.onAccessibilityEvent(event)

        // THEN: it is bounced — disabling the service must be blocked during any running vow
        verify(exactly = 1) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    @Test
    fun testNeverBlockablePackageIsNotBlockedEvenWhenScheduled() {
        // GIVEN: an active-mode all-day block that (mis)targets the launcher, and the launcher is in
        // the never-blockable set — blocking it could trap the user on an aVow-only device.
        setField("isVowActive", false)
        setField("isActiveVowMode", true)
        setField("vowBlocks", listOf(
            VowBlock(
                id = "1", name = "All day", isEnabled = true,
                startHour = 0, startMin = 0, endHour = 23, endMin = 59,
                targetApps = setOf("com.android.launcher"), specificDomain = ""
            )
        ))
        setField("neverBlockablePackages", setOf("com.android.launcher"))

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: the launcher comes to the foreground within the block window
        service.onAccessibilityEvent(event)

        // THEN: it is NOT blocked — the guard overrides the scheduled block (compare with
        // testActiveModeEnforcesScheduledBlockWithoutVow, which DOES block Instagram).
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testDoomscrollTargetInPictureInPictureIsBlocked() {
        // GIVEN: the doomscroll shield is on (all-day) for Instagram, and Instagram is sitting in a
        // PiP window while a benign app (the launcher) is the full-screen foreground — the reported
        // drag-to-PiP bypass, where the pop-out keeps auto-playing off-screen.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        val pipRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { pipRoot.packageName } returns "com.instagram.android"
        val pipWindow = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { pipWindow.isInPictureInPictureMode } returns true
        every { pipWindow.root } returns pipRoot
        every { service.windows } returns listOf(pipWindow)

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // WHEN: the foreground switches to the launcher but Instagram keeps playing in PiP
        service.onAccessibilityEvent(event)

        // THEN: the evasion cooldown lockout fires against the PiP target
        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun testDoomscrollTargetInSplitScreenIsBlocked() {
        // GIVEN: the shield is on (all-day) for Instagram, and the screen is split between another
        // app (focused) and Instagram — using it side-by-side to dodge the foreground check.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))
        setField("currentForegroundPackage", "com.some.otherapp")

        val igRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { igRoot.packageName } returns "com.instagram.android"
        val igWin = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { igWin.isInPictureInPictureMode } returns false
        every { igWin.type } returns android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        every { igWin.isActive } returns true
        every { igWin.root } returns igRoot

        val otherRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { otherRoot.packageName } returns "com.some.otherapp"
        val otherWin = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { otherWin.isInPictureInPictureMode } returns false
        every { otherWin.type } returns android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        every { otherWin.isActive } returns false
        every { otherWin.root } returns otherRoot

        every { service.windows } returns listOf(igWin, otherWin)

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.some.otherapp"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: the evasion cooldown lockout fires against the non-focused split-screen target
        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun testSingleForegroundDoomscrollTargetDoesNotTriggerEvasionLockout() {
        // GIVEN: a doomscroll target used normally (single fullscreen app, no PiP, no split) — the
        // evasion path must NOT fire; normal foreground use is metered, not immediately locked.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        val igRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { igRoot.packageName } returns "com.instagram.android"
        val igWin = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { igWin.isInPictureInPictureMode } returns false
        every { igWin.type } returns android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        every { igWin.root } returns igRoot
        every { service.windows } returns listOf(igWin)

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.instagram.android"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: no evasion lockout — a single fullscreen target isn't evading anything
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testDoomscrollTargetInRecentsCarouselDoesNotTriggerEvasionLockout() {
        // GIVEN: the shield is on for Instagram, and the user opens the recents/overview carousel to
        // swipe the app away. The launcher renders each recent app as its own inactive TYPE_APPLICATION
        // snapshot window, so appWindowCount >= 2 even though there is NO split-screen. None of the
        // snapshots is the active window (the overview UI is). This previously false-triggered a
        // penalty for someone merely closing the app.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))
        setField("currentForegroundPackage", "com.some.launcher")

        val igRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { igRoot.packageName } returns "com.instagram.android"
        val igWin = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { igWin.isInPictureInPictureMode } returns false
        every { igWin.type } returns android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        every { igWin.isActive } returns false
        every { igWin.root } returns igRoot

        val otherRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { otherRoot.packageName } returns "com.some.otherapp"
        val otherWin = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { otherWin.isInPictureInPictureMode } returns false
        every { otherWin.type } returns android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
        every { otherWin.isActive } returns false
        every { otherWin.root } returns otherRoot

        every { service.windows } returns listOf(igWin, otherWin)

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.some.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: no evasion lockout — inactive snapshots in the app switcher are not evasion
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testEvasionSuspendsTargetWhenDeviceOwner() {
        // GIVEN: full flavor, Device Owner active, and a doomscroll target caught in PiP.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        val caps = mockk<com.avow.app.enforcement.EnforcementCapabilities>(relaxed = true)
        every { caps.supportsDeviceOwnerFeatures } returns true
        every { caps.isDeviceOwnerActive } returns true
        setField("capabilities", caps)

        val pipRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { pipRoot.packageName } returns "com.instagram.android"
        val pipWindow = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { pipWindow.isInPictureInPictureMode } returns true
        every { pipWindow.root } returns pipRoot
        every { service.windows } returns listOf(pipWindow)

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: the evading app is actually suspended (the full-flavor "prevent" path).
        verify { caps.setAppsSuspended(setOf("com.instagram.android"), true) }
    }

    @Test
    fun testEvasionOnLiteWithVowAddsPenaltyNotCooldown() {
        // GIVEN: lite flavor (no device-owner powers), a running vow, and a doomscroll target in PiP.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))
        setField("isVowActive", true)
        setField("vowStartTimeMs", 1000L)

        val caps = mockk<com.avow.app.enforcement.EnforcementCapabilities>(relaxed = true)
        every { caps.supportsDeviceOwnerFeatures } returns false
        setField("capabilities", caps)

        val pipRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { pipRoot.packageName } returns "com.instagram.android"
        val pipWindow = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { pipWindow.isInPictureInPictureMode } returns true
        every { pipWindow.root } returns pipRoot
        every { service.windows } returns listOf(pipWindow)

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: the vow is penalized (async), and NO cooldown-lockout overlay is launched.
        coVerify(timeout = 2000) { mockDataStore.applyEvasionVowPenalty(3600L) }
        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun testReconcileReleasesSuspensionWhenCooldownExpired() {
        val caps = mockk<com.avow.app.enforcement.EnforcementCapabilities>(relaxed = true)
        every { caps.isDeviceOwnerActive } returns true
        setField("capabilities", caps)
        setField("evasionSuspendedPackages", setOf("com.instagram.android"))
        setField("temporaryLockoutEndTime", 0L) // no active cooldown (elapsedRealtime is 0 in tests)

        val m = BlockerService::class.java.getDeclaredMethod("reconcileEvasionSuspension")
        m.isAccessible = true
        m.invoke(service)

        verify { caps.setAppsSuspended(setOf("com.instagram.android"), false) }
    }

    @Test
    fun testReconcileKeepsSuspensionWhileCooldownActive() {
        val caps = mockk<com.avow.app.enforcement.EnforcementCapabilities>(relaxed = true)
        every { caps.isDeviceOwnerActive } returns true
        setField("capabilities", caps)
        setField("evasionSuspendedPackages", setOf("com.instagram.android"))
        setField("temporaryLockoutEndTime", 60_000L) // cooldown still running

        val m = BlockerService::class.java.getDeclaredMethod("reconcileEvasionSuspension")
        m.isAccessible = true
        m.invoke(service)

        verify(exactly = 0) { caps.setAppsSuspended(any(), false) }
        verify { caps.setAppsSuspended(setOf("com.instagram.android"), true) }
    }

    @Test
    fun testNonTargetInPictureInPictureIsIgnored() {
        // GIVEN: the shield is on for Instagram, but a non-target app (a video player) is in PiP —
        // legitimate PiP must not be blocked.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        val pipRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { pipRoot.packageName } returns "org.videolan.vlc"
        val pipWindow = mockk<android.view.accessibility.AccessibilityWindowInfo>(relaxed = true)
        every { pipWindow.isInPictureInPictureMode } returns true
        every { pipWindow.root } returns pipRoot
        every { service.windows } returns listOf(pipWindow)

        every { service.startActivity(any()) } returns Unit

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.launcher"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        service.onAccessibilityEvent(event)

        // THEN: no block — only restricted targets in PiP are caught
        verify(exactly = 0) { service.startActivity(any()) }
    }
}
