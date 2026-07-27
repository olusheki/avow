package com.avow.app

import android.view.accessibility.AccessibilityEvent
import com.avow.app.data.VowDataStore
import com.avow.app.service.BlockerService
import com.avow.app.util.DoomscrollTracker
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * The doomscroll glue that lives in [BlockerService] (as opposed to the pure [DoomscrollTracker],
 * covered by [DoomscrollTrackerTest]): the cooldown launch-intercept, and that a lockout stamps the
 * end time in memory *before* the async persist so the intercept can read it immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DoomscrollServiceGlueTest {

    private val mockDataStore = mockk<VowDataStore>(relaxed = true)
    private lateinit var service: BlockerService
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0

        service = spyk(BlockerService())
        every { service.packageName } returns "com.makeavow.app"
        every { service.startActivity(any()) } returns Unit
        // No base context on the spy: return null for getSystemService (POWER_SERVICE) so the
        // "screen interactive?" check treats it as unknown and proceeds.
        every { service.getSystemService(any<String>()) } returns null
        setField("vowDataStore", mockDataStore)

        // Any overlay launch builds an Intent; stub the constructor so it doesn't hit real Android.
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setFlags(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun setField(name: String, value: Any?) {
        val f = BlockerService::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(service, value)
    }

    private fun getField(name: String): Any? {
        val f = BlockerService::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(service)
    }

    private fun event(pkg: String): AccessibilityEvent {
        val e = mockk<AccessibilityEvent>(relaxed = true)
        every { e.packageName } returns pkg
        every { e.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        return e
    }

    @Test
    fun cooldownActive_reLaunchesLockoutForTargetApp() {
        // SystemClock.elapsedRealtime() is 0 in unit tests, so any positive end time is "in cooldown".
        setField("temporaryLockoutEndTime", 60_000L)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        service.onAccessibilityEvent(event("com.instagram.android"))

        verify(exactly = 1) { service.startActivity(any()) }
    }

    @Test
    fun cooldownActive_ignoresNonTargetApp() {
        setField("temporaryLockoutEndTime", 60_000L)
        setField("doomscrollTargetApps", setOf("com.instagram.android"))

        service.onAccessibilityEvent(event("com.some.other.app"))

        verify(exactly = 0) { service.startActivity(any()) }
    }

    @Test
    fun lockoutTrigger_stampsEndTimeInMemoryBeforePersist() = runTest(testDispatcher) {
        // Prime the tracker one tick short of the allowance so the next tick trips the lockout.
        setField("doomscrollShieldEnabled", true)
        setField("doomscrollAllTime", true) // restriction active regardless of the clock
        setField("doomscrollAllowanceMinutes", 1)  // 60_000ms allowance
        setField("doomscrollCooldownMinutes", 30)  // 1_800_000ms cooldown
        val tracker = getField("doomscrollTracker") as DoomscrollTracker
        tracker.accumulatedMs = 59_000L

        invokeUpdateDoomscrollStatistics(coroutineContext)
        testScheduler.advanceUntilIdle()

        // The field the app-launch intercept reads must be set synchronously (before the async save).
        val endTime = getField("temporaryLockoutEndTime") as Long
        assertTrue("expected a cooldown end time to be stamped, was $endTime", endTime > 0L)
        verify(exactly = 1) { service.startActivity(any()) } // lockout overlay launched
    }

    @Test
    fun doomscrollSeed_appliesDiskValueOnce_ignoresLaterStaleEmissions() {
        val tracker = getField("doomscrollTracker") as DoomscrollTracker

        // First emission seeds the live counter from disk.
        service.applyDoomscrollSeed(diskAccumulatedMs = 45_000L, shieldEnabled = true)
        assertEquals(45_000L, tracker.accumulatedMs)

        // The live counter advances past disk as the user keeps scrolling.
        tracker.accumulatedMs = 58_000L

        // A later emission carrying a stale (lower) disk value must NOT snap the live counter back —
        // that snap-back was the unreliable-lockout root cause.
        service.applyDoomscrollSeed(diskAccumulatedMs = 10_000L, shieldEnabled = true)
        assertEquals(58_000L, tracker.accumulatedMs)
    }

    @Test
    fun doomscrollSeed_shieldDisabled_resetsCounter() {
        val tracker = getField("doomscrollTracker") as DoomscrollTracker
        tracker.accumulatedMs = 30_000L

        service.applyDoomscrollSeed(diskAccumulatedMs = 30_000L, shieldEnabled = false)
        assertEquals(0L, tracker.accumulatedMs)
    }

    private fun invokeUpdateDoomscrollStatistics(context: CoroutineContext) {
        val method = BlockerService::class.java.getDeclaredMethod("updateDoomscrollStatistics", Continuation::class.java)
        method.isAccessible = true
        method.invoke(service, object : Continuation<Unit> {
            override val context: CoroutineContext = context
            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) throw result.exceptionOrNull()
                    ?: RuntimeException("updateDoomscrollStatistics failed")
            }
        })
    }
}
