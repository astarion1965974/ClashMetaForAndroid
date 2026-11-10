package com.github.kr328.clash.service.clash.module

import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * Runtime counters only. No socket addresses, credentials, profile contents or
 * traffic payloads are stored. Reset when the Android VPN is recreated.
 */
object TunDiagnostics {
    private val protectedSockets = AtomicLong()
    private val failedSockets = AtomicLong()
    @Volatile private var lastFailureMs = 0L
    @Volatile private var startedMs = 0L
    @Volatile private var mtu = 0

    fun reset(vpnMtu: Int) {
        protectedSockets.set(0)
        failedSockets.set(0)
        lastFailureMs = 0L
        startedMs = System.currentTimeMillis()
        mtu = vpnMtu
    }

    fun recordProtected(ok: Boolean) {
        if (ok) protectedSockets.incrementAndGet()
        else {
            failedSockets.incrementAndGet()
            lastFailureMs = System.currentTimeMillis()
        }
    }

    fun snapshot(): String = JSONObject().apply {
        put("vpn_mtu", mtu)
        put("vpn_started_epoch_ms", startedMs)
        put("protect_success", protectedSockets.get())
        put("protect_failure", failedSockets.get())
        put("last_protect_failure_epoch_ms", lastFailureMs)
    }.toString()
}
