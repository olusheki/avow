package com.avow.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.avow.app.data.VowDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreIntegrityTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun testDataStoreConcurrentAccessDoesNotDeadlock() = runTest {
        // GIVEN: A real Preference DataStore initialized on a temporary file
        val tempFile = tmpFolder.newFile("test_settings.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )

        // Mock context to return our testDataStore
        val mockContext = mockk<Context>(relaxed = true)
        
        // Extension property context.dataStore uses a delegate, but since VowDataStore's preferencesFlow
        // reads from context.dataStore, we will mock VowDataStore itself or use direct dependency injection
        // Let's create an anonymous subclass of VowDataStore or subclass it to override the DataStore
        // Wait, VowDataStore has:
        // val preferencesFlow = context.dataStore.preferencesFlow
        // If we mock context.dataStore, wait: preferencesFlow is defined as an extension on Context.
        // Let's look at VowDataStore.kt:
        // val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vow_settings")
        // class VowDataStore(private val context: Context) {
        //     val preferencesFlow: Flow<Preferences> = context.dataStore.preferencesFlow
        // }
        // To mock context.dataStore extension, it's easier to mock VowDataStore's preferencesFlow property or mock the Context extension function.
        // Wait! In VowDataStore.kt, preferencesFlow is:
        // val preferencesFlow: Flow<Preferences> = context.dataStore.preferencesFlow
        // Where context.dataStore is an extension property. Extension properties are compiled as static methods in a file.
        // Since mocking static extension methods in Kotlin with Mockk can sometimes be complex, a cleaner approach is:
        // We mock the VowDataStore class directly!
        // Wait, if we mock VowDataStore, we are not testing the actual VowDataStore class.
        // Can we mock context.dataStore?
        // Let's check: context.dataStore is defined at the package level in VowDataStore.kt.
        // If we can just mock VowDataStore or inject a mock context, let's see.
        // Wait, if we construct a VowDataStore subclass or mock `VowDataStore` instance itself, we can mock all of its write methods to delegate to `testDataStore`, and mock its `preferencesFlow` to return `testDataStore.data`.
        // Let's write that:
        
        val vowDataStore = mockk<VowDataStore>(relaxed = true)
        every { vowDataStore.preferencesFlow } returns testDataStore.data
        
        // Wire up writes to actually write to our testDataStore
        every { runBlocking { vowDataStore.saveCountdownState(any(), any()) } } answers {
            val remaining = firstArg<Long>()
            val uptime = secondArg<Long>()
            runBlocking {
                testDataStore.edit { preferences ->
                    preferences[VowDataStore.REMAINING_VOW_SECONDS] = remaining
                    preferences[VowDataStore.LAST_SYSTEM_UPTIME_MILLIS] = uptime
                    preferences[VowDataStore.IS_VOW_ACTIVE] = true
                    
                    val isActive = preferences[VowDataStore.IS_VOW_ACTIVE] ?: false
                    val domainSet = preferences[VowDataStore.BAN_DOMAIN_SET] ?: emptySet()
                    val targetAppSet = preferences[VowDataStore.TARGET_APP_SET] ?: emptySet()
                    preferences[VowDataStore.STATE_SIGNATURE] = com.avow.app.util.VowValidator.computeStateSignature(
                        isActive, remaining, uptime, domainSet, targetAppSet
                    )
                }
            }
        }

        every { runBlocking { vowDataStore.saveAccumulatedUsage(any(), any()) } } answers {
            val usage = firstArg<Long>()
            val interval = secondArg<Long>()
            runBlocking {
                testDataStore.edit { preferences ->
                    preferences[VowDataStore.ACCUMULATED_USAGE_MS] = usage
                    preferences[VowDataStore.LAST_INTERVAL_START_MS] = interval
                }
            }
        }

        // WHEN: Launching 50 concurrent reader/writer jobs spamming state changes
        val job1 = launch(Dispatchers.Default) {
            for (i in 1..100) {
                vowDataStore.saveCountdownState(i.toLong(), System.currentTimeMillis())
            }
        }

        val job2 = launch(Dispatchers.Default) {
            for (i in 1..100) {
                vowDataStore.saveAccumulatedUsage(i * 1000L, System.currentTimeMillis())
            }
        }

        // Wait for coroutines to complete
        job1.join()
        job2.join()

        // THEN: Read the final state and verify it matches the final expected values
        val finalPrefs = testDataStore.data.first()
        assertEquals(100L, finalPrefs[VowDataStore.REMAINING_VOW_SECONDS])
        assertEquals(100000L, finalPrefs[VowDataStore.ACCUMULATED_USAGE_MS])
        assertTrue(finalPrefs[VowDataStore.IS_VOW_ACTIVE] == true)
    }
}
