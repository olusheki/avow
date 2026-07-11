package com.avow.app

import com.avow.app.model.VowBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-rolled `=`/`;` serialization for [VowBlock] is on the tamper-signature path (a block list
 * round-trips through a string), so a corrupted parse would silently change what's enforced. These
 * pin the round trip, delimiter sanitization, the legacy 8-field form, and hostile input.
 */
class VowBlockSerializationTest {

    @Test
    fun roundTrip_preservesAllFields() {
        val blocks = listOf(
            VowBlock("id1", "Night", true, 22, 0, 7, 0, setOf("com.a", "com.b"), "instagram.com"),
            VowBlock("id2", "Focus", false, 9, 30, 17, 0, emptySet(), "")
        )
        assertEquals(blocks, VowBlock.deserializeList(VowBlock.serializeList(blocks)))
    }

    @Test
    fun delimiterCharsInNameAndDomain_areSanitizedNotCorrupting() {
        // '=' and ';' are the field/record delimiters; a name containing them must not shift fields.
        val block = VowBlock("id3", "a=b;c", true, 1, 2, 3, 4, setOf("com.x"), "e=f;g")
        val round = VowBlock.deserializeList(VowBlock.serializeList(listOf(block))).single()

        assertEquals("a b c", round.name)          // delimiters replaced with spaces
        assertEquals("e f g", round.specificDomain)
        assertEquals(1, round.startHour)
        assertEquals(4, round.endMin)
        assertEquals(setOf("com.x"), round.targetApps)
    }

    @Test
    fun legacyEightFieldForm_parsesWithEmptyDomain() {
        val legacy = "idL=Old=true=22=0=7=0=com.x,com.y"
        val block = VowBlock.deserializeList(legacy).single()

        assertEquals("", block.specificDomain)
        assertEquals(setOf("com.x", "com.y"), block.targetApps)
        assertEquals(22, block.startHour)
        assertTrue(block.isEnabled)
    }

    @Test
    fun malformedInput_isSkippedNotCrashed() {
        assertTrue(VowBlock.deserializeList("").isEmpty())
        assertTrue(VowBlock.deserializeList(null).isEmpty())
        assertTrue(VowBlock.deserializeList("a=b=c").isEmpty()) // too few fields → dropped

        // A valid record followed by garbage yields only the valid block.
        val valid = VowBlock.serializeList(listOf(VowBlock("id", "N", true, 1, 0, 2, 0, emptySet(), "")))
        assertEquals(1, VowBlock.deserializeList("$valid;garbage").size)
    }
}
