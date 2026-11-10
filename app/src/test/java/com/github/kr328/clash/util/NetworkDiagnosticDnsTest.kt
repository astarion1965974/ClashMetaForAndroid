package com.github.kr328.clash.util

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NetworkDiagnosticDnsTest {
    @Test fun rejectsErrorJsonEvenIfNativeCallCompleted() {
        val raw = """{"addresses":[],"error":"couldn't find ip","millis":5007,"dnsRoute":"proxy","dnsUpstream":"https://1.1.1.1"}"""
        val error = assertThrows(IllegalStateException::class.java) {
            NetworkDiagnosticDns.requireResolved(raw)
        }
        assertTrue(error.message.orEmpty().contains("couldn't find ip"))
    }

    @Test fun rejectsEmptyAddressesWithoutError() {
        assertThrows(IllegalStateException::class.java) {
            NetworkDiagnosticDns.requireResolved("""{"addresses":[],"error":""}""")
        }
    }

    @Test fun acceptsResolvedDns() {
        val result = NetworkDiagnosticDns.requireResolved(
            """{"addresses":["203.0.113.10"],"error":"","dnsRoute":"proxy","millis":26}"""
        )
        assertTrue(result.contains("count=1"))
        assertTrue(result.contains("route=proxy"))
    }
}