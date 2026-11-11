package com.github.kr328.clash.service.model

import org.junit.Assert.*
import org.junit.Test

class VpnCapturePolicyTest {
    @Test fun protectedOverridesAllBypassSettings() {
        val result = VpnCapturePolicy.resolve(true, AccessControlMode.DenySelected, false, true, true, false)
        assertEquals(AccessControlMode.AcceptAll, result.mode)
        assertTrue(result.ipv6)
        assertTrue(result.hijackDns)
        assertFalse(result.bypassPrivate)
        assertFalse(result.allowBypass)
    }
    @Test fun standardKeepsUserSettings() {
        val result = VpnCapturePolicy.resolve(false, AccessControlMode.AcceptSelected, false, true, true, false)
        assertEquals(AccessControlMode.AcceptSelected, result.mode)
        assertFalse(result.ipv6)
        assertFalse(result.hijackDns)
        assertTrue(result.bypassPrivate)
        assertTrue(result.allowBypass)
    }
}
