package com.cj.tapblok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteDomainRulesTest {
    @Test
    fun normalizesUrlsToRegistrableDomainInput() {
        assertEquals("youtube.com", WebsiteDomainRules.normalizeDomain("https://www.youtube.com/watch?v=1"))
        assertEquals("reddit.com", WebsiteDomainRules.normalizeDomain("*.reddit.com"))
        assertEquals("example.co.uk", WebsiteDomainRules.normalizeDomain("example.co.uk:443"))
    }

    @Test
    fun rejectsPartialOrInvalidDomains() {
        assertNull(WebsiteDomainRules.normalizeDomain("youtube"))
        assertNull(WebsiteDomainRules.normalizeDomain("-bad.com"))
        assertNull(WebsiteDomainRules.normalizeDomain("bad_domain.com"))
    }

    @Test
    fun blockedDomainMatchesSubdomainsOnly() {
        val blocked = setOf("youtube.com")

        assertTrue(WebsiteDomainRules.isBlocked("youtube.com", blocked))
        assertTrue(WebsiteDomainRules.isBlocked("m.youtube.com", blocked))
        assertFalse(WebsiteDomainRules.isBlocked("notyoutube.com", blocked))
    }
}
