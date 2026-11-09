package com.github.kr328.clash.util

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NetworkPrivacyDiagnosticsTest {
    @Test fun parsesOfficialIppureRiskScoreWithoutInventingPurity() {
        val sample = """{"ip":"203.0.113.8","fraudScore":75,
            "asOrganization":"Example ASN","country":"Japan",
            "isResidential":false,"isBroadcast":false}"""
        val info = NetworkPrivacyDiagnostics.parseIppure(sample)
        assertEquals("203.0.113.8", info.ip)
        assertEquals(75, info.riskScore)
        assertEquals("Japan", info.country)
        assertEquals("Example ASN", info.isp)
        assertEquals(false, info.residential)
        assertNull(NetworkPrivacyDiagnostics.parseIppure("""{"ip":"203.0.113.8"}""").riskScore)
        assertNull(NetworkPrivacyDiagnostics.parseIppure("""{"fraudScore":150}""").riskScore)
    }

    @Test fun parsesOfficialIpleakJsonAndDnsMap() {
        val exit = NetworkPrivacyDiagnostics.parseIpleak(
            """{"ip":"198.51.100.2","isp_name":"Example ISP"}"""
        )
        assertEquals("198.51.100.2", exit.ip)
        assertEquals("Example ISP", exit.isp)

        val ips = NetworkPrivacyDiagnostics.parseDnsResolvers(
            """{"session":"123","ip":{"162.158.1.2":2,"203.0.113.55":1}}"""
        )
        assertEquals(setOf("162.158.1.2", "203.0.113.55"), ips)
    }

    @Test fun emptyIpleakResponseMeansUnknownNotNoLeak() {
        // The IPLeak service returns [] when a recursive resolver was not
        // observed (e.g. some Google Public DNS sessions). Do NOT mark safe.
        assertTrue(NetworkPrivacyDiagnostics.parseDnsResolvers(
            """{"session":"123","ip":[]}"""
        ).isEmpty())
        assertTrue(NetworkPrivacyDiagnostics.parseDnsResolvers(
            """{"session":"123","ip":{}}"""
        ).isEmpty())
    }
}
