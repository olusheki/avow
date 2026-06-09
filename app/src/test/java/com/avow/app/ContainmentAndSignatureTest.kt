package com.avow.app

import com.avow.app.model.VowBlock
import com.avow.app.util.VowValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ContainmentAndSignatureTest {

    private fun createBlock(
        isEnabled: Boolean,
        startHour: Int,
        startMin: Int,
        endHour: Int,
        endMin: Int,
        name: String = "Quiet Hours"
    ): VowBlock {
        return VowBlock(
            id = UUID.randomUUID().toString(),
            name = name,
            isEnabled = isEnabled,
            startHour = startHour,
            startMin = startMin,
            endHour = endHour,
            endMin = endMin,
            targetApps = setOf("com.instagram.android"),
            specificDomain = "instagram.com"
        )
    }

    @Test
    fun testContainmentShiftedConfiguration() {
        // Old schedule: 22:00 to 07:00 (crosses midnight)
        val oldBlocks = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        // New schedule: 23:00 to 08:00 (shifted)
        val newBlocks = listOf(createBlock(isEnabled = true, startHour = 23, startMin = 0, endHour = 8, endMin = 0))

        // This should fail containment because minutes from 22:00 to 23:00 are no longer blocked.
        assertFalse(VowValidator.validateContainment(oldBlocks, newBlocks))
    }

    @Test
    fun testContainmentExpandedConfiguration() {
        // Old schedule: 22:00 to 07:00
        val oldBlocks = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        // New schedule: 21:00 to 08:00 (expanded)
        val newBlocks = listOf(createBlock(isEnabled = true, startHour = 21, startMin = 0, endHour = 8, endMin = 0))

        // This should pass containment because all minutes in the old block (22:00 - 07:00) are subset of the new block (21:00 - 08:00)
        assertTrue(VowValidator.validateContainment(oldBlocks, newBlocks))
    }

    @Test
    fun testContainmentSubsetConfiguration() {
        // Old schedule: 22:00 to 07:00
        val oldBlocks = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        // New schedule: 23:00 to 06:00 (shrunk/subset of time)
        val newBlocks = listOf(createBlock(isEnabled = true, startHour = 23, startMin = 0, endHour = 6, endMin = 0))

        // This should fail containment because the new schedule blocks fewer minutes.
        assertFalse(VowValidator.validateContainment(oldBlocks, newBlocks))
    }

    @Test
    fun testMultipleBlocksContainment() {
        // Old schedule: One block from 22:00 to 07:00
        val oldBlocks = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        
        // New schedule: Split into two blocks that cover the same range plus more (e.g. 22:00-02:00 and 01:00-07:30)
        val newBlocks = listOf(
            createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 2, endMin = 0, name = "Block 1"),
            createBlock(isEnabled = true, startHour = 1, startMin = 0, endHour = 7, endMin = 30, name = "Block 2")
        )

        // This should pass because the union of new blocks fully covers 22:00 to 07:00.
        assertTrue(VowValidator.validateContainment(oldBlocks, newBlocks))
    }

    @Test
    fun testDisabledBlocksContainment() {
        // If a block was enabled in the old config, but disabled in the new one, containment must fail.
        val oldBlocks = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        val newBlocks = listOf(createBlock(isEnabled = false, startHour = 22, startMin = 0, endHour = 7, endMin = 0))

        assertFalse(VowValidator.validateContainment(oldBlocks, newBlocks))
    }

    @Test
    fun testSignatureIncludesVowBlocksJson() {
        val vowBlocksList1 = listOf(createBlock(isEnabled = true, startHour = 22, startMin = 0, endHour = 7, endMin = 0))
        val vowBlocksList2 = listOf(createBlock(isEnabled = false, startHour = 22, startMin = 0, endHour = 7, endMin = 0))

        val json1 = VowBlock.serializeList(vowBlocksList1)
        val json2 = VowBlock.serializeList(vowBlocksList2)

        val sig1 = VowValidator.computeHMACSignature(
            isVowActive = true,
            isActiveVowMode = true,
            remainingSeconds = 3600L,
            lastUptimeMillis = 123456L,
            banDomainSet = setOf("instagram.com"),
            targetAppSet = setOf("com.instagram.android"),
            deactivationRequestTime = 0L,
            vowBlocksJson = json1
        )

        val sig2 = VowValidator.computeHMACSignature(
            isVowActive = true,
            isActiveVowMode = true,
            remainingSeconds = 3600L,
            lastUptimeMillis = 123456L,
            banDomainSet = setOf("instagram.com"),
            targetAppSet = setOf("com.instagram.android"),
            deactivationRequestTime = 0L,
            vowBlocksJson = json2
        )

        // The signatures must be different because the vowBlocksJson is different.
        assertFalse(sig1 == sig2)
    }
}
