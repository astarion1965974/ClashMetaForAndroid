package com.github.kr328.clash.service.clash.module

/**
 * Detect a real underlay handover or interruption before discarding stale
 * TCP/TLS connections. Initial startup and duplicate callbacks are not handovers.
 *
 * The VPN itself remains active; only connections bound to an obsolete
 * physical network are closed so clients can establish fresh TLS sessions.
 */
internal class UnderlayRecoveryPolicy {
    private var lastValidKey: String? = null
    private var interrupted = false

    fun shouldCloseStaleConnections(networkKey: String?, usable: Boolean): Boolean {
        if (!usable || networkKey.isNullOrBlank()) {
            if (lastValidKey != null) interrupted = true
            return false
        }
        val old = lastValidKey
        lastValidKey = networkKey
        if (old == null) {
            interrupted = false
            return false
        }
        val changed = interrupted || old != networkKey
        interrupted = false
        return changed
    }
}
