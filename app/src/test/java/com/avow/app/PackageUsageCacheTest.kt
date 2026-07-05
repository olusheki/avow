package com.avow.app

import android.content.Context
import android.content.Intent
import com.avow.app.data.VowDataStore
import com.avow.app.service.BlockerService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PackageUsageCacheTest {

    private val mockDataStore = mockk<VowDataStore>(relaxed = true)
    private lateinit var service: BlockerService
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        service = spyk(BlockerService(), recordPrivateCalls = true)
        every { service.packageName } returns "com.avow.app"
        every { service["triggerBlackoutOverlay"](any<String>()) } returns Unit

        // Inject VowDataStore
        setPrivateField("vowDataStore", mockDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun setPrivateField(name: String, value: Any?) {
        val field = BlockerService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun getPrivateField(name: String): Any? {
        val field = BlockerService::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(service)
    }

    private fun invokeUpdateUsageStatistics(context: CoroutineContext) {
        val method = BlockerService::class.java.getDeclaredMethod("updateUsageStatistics", Continuation::class.java)
        method.isAccessible = true
        method.invoke(service, object : Continuation<Unit> {
            override val context: CoroutineContext = context
            override fun resumeWith(result: Result<Unit>) {
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: RuntimeException("Unknown error in updateUsageStatistics")
                }
            }
        })
    }

    @Test
    fun testIncrementTicks() = runTest(testDispatcher) {
        // GIVEN: active vow, limit 5 min, com.instagram.android is restricted and foregrounded
        setPrivateField("isVowActive", true)
        setPrivateField("usageLimitsUpdated", true)
        setPrivateField("allowedValue", "5")
        setPrivateField("allowedUnit", "min")
        setPrivateField("selectedInterval", "hour")
        setPrivateField("targetAppSet", setOf("com.instagram.android"))
        setPrivateField("currentForegroundPackage", "com.instagram.android")
        setPrivateField("lastIntervalStartMs", System.currentTimeMillis())

        val cache = getPrivateField("packageUsageCache") as ConcurrentHashMap<String, Long>
        cache.clear()

        // WHEN: call update statistics
        invokeUpdateUsageStatistics(coroutineContext)

        // THEN: cache has 1000ms
        assertEquals(1000L, cache["com.instagram.android"])

        // WHEN: tick again
        invokeUpdateUsageStatistics(coroutineContext)
        assertEquals(2000L, cache["com.instagram.android"])
    }

    @Test
    fun testTransitionOnAppSwitch() = runTest(testDispatcher) {
        // GIVEN: active vow, com.instagram.android foregrounded and has usage
        setPrivateField("isVowActive", true)
        setPrivateField("usageLimitsUpdated", true)
        setPrivateField("allowedValue", "5")
        setPrivateField("allowedUnit", "min")
        setPrivateField("selectedInterval", "hour")
        setPrivateField("targetAppSet", setOf("com.instagram.android", "com.facebook.katana"))
        setPrivateField("currentForegroundPackage", "com.instagram.android")
        setPrivateField("lastTrackedPackage", "com.instagram.android")
        setPrivateField("lastIntervalStartMs", System.currentTimeMillis())

        val cache = getPrivateField("packageUsageCache") as ConcurrentHashMap<String, Long>
        cache["com.instagram.android"] = 5000L

        // WHEN: app switches to com.facebook.katana
        setPrivateField("currentForegroundPackage", "com.facebook.katana")
        invokeUpdateUsageStatistics(coroutineContext)
        testScheduler.advanceUntilIdle()

        // THEN: since app switched, savePackageUsage should be called immediately
        coVerify(exactly = 1) { mockDataStore.savePackageUsage(any(), any()) }
        assertEquals("com.facebook.katana", getPrivateField("lastTrackedPackage"))
    }

    @Test
    fun testImmediateBreachSaves() = runTest(testDispatcher) {
        // GIVEN: active vow, limit 5 min (300,000ms), usage is at 299,000ms
        setPrivateField("isVowActive", true)
        setPrivateField("usageLimitsUpdated", true)
        setPrivateField("allowedValue", "5")
        setPrivateField("allowedUnit", "min")
        setPrivateField("selectedInterval", "hour")
        setPrivateField("targetAppSet", setOf("com.instagram.android"))
        setPrivateField("currentForegroundPackage", "com.instagram.android")
        setPrivateField("lastTrackedPackage", "com.instagram.android")
        setPrivateField("lastIntervalStartMs", System.currentTimeMillis())

        val cache = getPrivateField("packageUsageCache") as ConcurrentHashMap<String, Long>
        cache["com.instagram.android"] = 299000L

        // WHEN: another tick occurs, bringing usage to 300,000L (breach!)
        invokeUpdateUsageStatistics(coroutineContext)
        testScheduler.advanceUntilIdle()

        // THEN: savePackageUsage must be called immediately due to limit breach
        coVerify(exactly = 1) { mockDataStore.savePackageUsage(any(), any()) }
        // AND: triggerBlackoutOverlay is called with the usage-limit reason
        verify(exactly = 1) { service["triggerBlackoutOverlay"]("USAGE LIMIT") }
    }

    @Test
    fun testIntervalResets() = runTest(testDispatcher) {
        // GIVEN: active vow, selectedInterval is hour, lastIntervalStartMs is 2 hours ago
        setPrivateField("isVowActive", true)
        setPrivateField("usageLimitsUpdated", true)
        setPrivateField("allowedValue", "5")
        setPrivateField("allowedUnit", "min")
        setPrivateField("selectedInterval", "hour")
        setPrivateField("targetAppSet", setOf("com.instagram.android"))
        setPrivateField("currentForegroundPackage", "com.instagram.android")
        setPrivateField("lastTrackedPackage", "com.instagram.android")

        val twoHoursAgo = System.currentTimeMillis() - 2 * 3600 * 1000L
        setPrivateField("lastIntervalStartMs", twoHoursAgo)

        val cache = getPrivateField("packageUsageCache") as ConcurrentHashMap<String, Long>
        cache["com.instagram.android"] = 10000L

        // WHEN: calling updateUsageStatistics
        invokeUpdateUsageStatistics(coroutineContext)
        testScheduler.advanceUntilIdle()

        // THEN: cache should be reset/cleared immediately
        assertTrue(cache.isEmpty())

        // AND: savePackageUsage should be called immediately with the new start time
        val newStart = getPrivateField("lastIntervalStartMs") as Long
        assertTrue(newStart > twoHoursAgo)
        coVerify(exactly = 1) { mockDataStore.savePackageUsage("", newStart) }
    }
}
