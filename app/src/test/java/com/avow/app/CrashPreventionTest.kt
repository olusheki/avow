package com.avow.app

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.avow.app.service.BlockerService
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrashPreventionTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testOnAccessibilityEventCatchesExceptionsGracefully() {
        // GIVEN: A BlockerService instance
        val service = spyk(BlockerService())

        // Force a NullPointerException or IllegalStateException when rootInActiveWindow is called
        every { service.rootInActiveWindow } throws IllegalStateException("Simulated Node Recycled Exception")

        // Mock accessibility event for Chrome
        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns "com.android.chrome"
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // Inject active vow fields inside BlockerService using reflection
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val banDomainSetField = BlockerService::class.java.getDeclaredField("banDomainSet")
        banDomainSetField.isAccessible = true
        banDomainSetField.set(service, setOf("instagram.com"))

        // WHEN: Calling onAccessibilityEvent which runs URL extraction
        // THEN: It must not throw any exception to the caller
        try {
            service.onAccessibilityEvent(event)
        } catch (t: Throwable) {
            org.junit.Assert.fail("onAccessibilityEvent threw an uncaught exception: ${t.message}. Soft-brick risk!")
        }
        
        // Verify that it logged the error, proving it didn't just silently swallow it without acting
        verify { Log.e("BlockerService", "Uncaught exception in accessibility event receiver, safely recovered.", any()) }
    }

    @Test
    fun testNullPackageNameDoesNotCrash() {
        val service = BlockerService()
        val isVowActiveField = BlockerService::class.java.getDeclaredField("isVowActive")
        isVowActiveField.isAccessible = true
        isVowActiveField.set(service, true)

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns null

        try {
            service.onAccessibilityEvent(event)
        } catch (t: Throwable) {
            org.junit.Assert.fail("onAccessibilityEvent crashed on null package name: ${t.message}")
        }
    }
}
