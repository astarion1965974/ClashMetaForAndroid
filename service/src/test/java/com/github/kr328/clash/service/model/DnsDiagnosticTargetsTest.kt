package com.github.kr328.clash.service.model

import com.github.kr328.clash.common.constants.DnsDiagnosticTargets
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression for the stale IClashManager allowlist: the dashboard used to
 * display nine domains, while the service only accepted the original four.
 */
class DnsDiagnosticTargetsTest {
    @Test fun allDisplayedDomesticAndAIDomainsAreAllowedForLiveQueries() {
        val expected = listOf(
            "baidu.com", "bilibili.com", "dianping.com", "douyin.com",
            "google.com", "youtube.com",
            "chatgpt.com", "grok.com", "claude.ai",
        )
        assertEquals(expected, DnsDiagnosticTargets.domains)
        assertEquals(9, expected.size)
        assertEquals(expected.size, expected.distinct().size)
        expected.forEach {
            assertTrue("Domain shown on Home must be accepted by ClashManager: $it",
                DnsDiagnosticTargets.supports(it))
        }
    }

    @Test fun arbitraryUnlistedOrMalformedDomainsAreRejected() {
        assertFalse(DnsDiagnosticTargets.supports("random.invalid"))
        assertFalse(DnsDiagnosticTargets.supports(""))
        assertFalse(DnsDiagnosticTargets.supports("evil.com\\nchatgpt.com"))
        assertFalse(DnsDiagnosticTargets.supports("chatgpt.com.evil.net"))
    }
}
