package com.avow.app

import com.avow.app.util.DomainUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** W3-T1: host-based domain matching must catch subdomains but reject look-alikes, paths, and
 *  search text that merely mention the domain. */
class DomainUtilTest {

    @Test
    fun hostOf_stripsSchemePathQueryPortAndUserinfo() {
        assertEquals("instagram.com", DomainUtil.hostOf("instagram.com"))
        assertEquals("instagram.com", DomainUtil.hostOf("instagram.com/p/123"))
        assertEquals("www.instagram.com", DomainUtil.hostOf("https://www.instagram.com/reels"))
        assertEquals("instagram.com", DomainUtil.hostOf("instagram.com:443"))
        assertEquals("instagram.com", DomainUtil.hostOf("http://user@instagram.com/x?y=1#z"))
    }

    @Test
    fun matchesHost_matchesExactAndSubdomains() {
        assertTrue(DomainUtil.matchesHost("instagram.com", "instagram.com"))
        assertTrue(DomainUtil.matchesHost("www.instagram.com", "instagram.com"))
        assertTrue(DomainUtil.matchesHost("m.instagram.com/p/1", "instagram.com"))
        assertTrue(DomainUtil.matchesHost("https://instagram.com/reels", "instagram.com"))
    }

    @Test
    fun matchesHost_rejectsLookAlikesAndMentions() {
        // Look-alike hosts must not match.
        assertFalse(DomainUtil.matchesHost("notinstagram.com", "instagram.com"))
        assertFalse(DomainUtil.matchesHost("instagram.company.com", "instagram.com"))
        // Domain mentioned only in a path or query — host is elsewhere.
        assertFalse(DomainUtil.matchesHost("news.com/article-about-instagram.com", "instagram.com"))
        assertFalse(DomainUtil.matchesHost("google.com/search?q=instagram.com", "instagram.com"))
        // Search-box text, not a navigation.
        assertFalse(DomainUtil.matchesHost("how to quit instagram.com", "instagram.com"))
    }

    @Test
    fun matchesHost_emptyInputsAreFalse() {
        assertFalse(DomainUtil.matchesHost("", "instagram.com"))
        assertFalse(DomainUtil.matchesHost("instagram.com", ""))
    }

    @Test
    fun matches_csvMatchesAnyConfiguredDomain() {
        val csv = "instagram.com,tiktok.com"
        assertTrue(DomainUtil.matches("https://www.tiktok.com/foryou", csv))
        assertTrue(DomainUtil.matches("instagram.com", csv))
        assertFalse(DomainUtil.matches("https://youtube.com", csv))
    }
}
