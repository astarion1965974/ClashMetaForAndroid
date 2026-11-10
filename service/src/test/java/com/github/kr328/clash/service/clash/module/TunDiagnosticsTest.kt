package com.github.kr328.clash.service.clash.module

import org.junit.Assert.assertTrue
import org.junit.Test

class TunDiagnosticsTest {
    @Test fun resetAndCountProtection() {
        TunDiagnostics.reset(1400)
        TunDiagnostics.recordProtected(true)
        TunDiagnostics.recordProtected(false)
        val snapshot = TunDiagnostics.snapshot()
        assertTrue(snapshot.contains("\"vpn_mtu\":1400"))
        assertTrue(snapshot.contains("\"protect_success\":1"))
        assertTrue(snapshot.contains("\"protect_failure\":1"))
        TunDiagnostics.reset(1280)
        assertTrue(TunDiagnostics.snapshot().contains("\"protect_failure\":0"))
    }
}
