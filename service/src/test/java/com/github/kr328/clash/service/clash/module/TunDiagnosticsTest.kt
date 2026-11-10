package com.github.kr328.clash.service.clash.module

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class TunDiagnosticsTest {
    @Test fun resetAndCountProtection() {
        TunDiagnostics.reset(1400)
        TunDiagnostics.recordProtected(true)
        TunDiagnostics.recordProtected(false)
        val snapshot = JSONObject(TunDiagnostics.snapshot())
        assertEquals(1400, snapshot.getInt("vpn_mtu"))
        assertEquals(1L, snapshot.getLong("protect_success"))
        assertEquals(1L, snapshot.getLong("protect_failure"))
        TunDiagnostics.reset(1280)
        val reset = JSONObject(TunDiagnostics.snapshot())
        assertEquals(0L, reset.getLong("protect_failure"))
    }
}
