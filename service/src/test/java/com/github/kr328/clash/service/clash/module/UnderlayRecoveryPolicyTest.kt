package com.github.kr328.clash.service.clash.module

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlayRecoveryPolicyTest {
    @Test fun startupAndDuplicateCallbackDoNotCloseSessions() {
        val policy = UnderlayRecoveryPolicy()
        assertFalse(policy.shouldCloseStaleConnections(null, false))
        assertFalse(policy.shouldCloseStaleConnections("wifi-1", true))
        assertFalse(policy.shouldCloseStaleConnections("wifi-1", true))
    }

    @Test fun validatedWifiToCellularHandoverClosesOldSessionsOnce() {
        val policy = UnderlayRecoveryPolicy()
        assertFalse(policy.shouldCloseStaleConnections("wifi", true))
        assertTrue(policy.shouldCloseStaleConnections("cellular", true))
        assertFalse(policy.shouldCloseStaleConnections("cellular", true))
        assertTrue(policy.shouldCloseStaleConnections("wifi", true))
    }

    @Test fun outageAndSameNetworkReturnCloseStaleTlsConnections() {
        val policy = UnderlayRecoveryPolicy()
        assertFalse(policy.shouldCloseStaleConnections("wifi", true))
        assertFalse(policy.shouldCloseStaleConnections(null, false))
        assertFalse(policy.shouldCloseStaleConnections("", false))
        assertTrue(policy.shouldCloseStaleConnections("wifi", true))
        assertFalse(policy.shouldCloseStaleConnections("wifi", true))
    }

    @Test fun repeatedOfflineEventsNeverCloseConnections() {
        val policy = UnderlayRecoveryPolicy()
        assertFalse(policy.shouldCloseStaleConnections(null, false))
        assertFalse(policy.shouldCloseStaleConnections(null, false))
        assertFalse(policy.shouldCloseStaleConnections("cellular", true))
    }
}
