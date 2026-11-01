package com.github.kr328.clash.service.model

/** Pure decisions shared with tests. Unknown Wi-Fi identity always keeps the VPN. */
object MobilePolicy {
    fun pauseAtHome(enabled: Boolean, trustedEnabled: Boolean, ssid: String?, trusted: List<String>, lockdown: Boolean) =
        enabled && trustedEnabled && !lockdown && !ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid in trusted
    fun allowUpdate(enabled: Boolean, preferWifi: Boolean, connected: Boolean, metered: Boolean, charging: Boolean, ageMillis: Long) =
        !enabled || connected && (!preferWifi || !metered || charging || ageMillis >= 72 * 60 * 60 * 1000L)
    fun shouldRefreshHome(started: Boolean, running: Boolean) = started && running
}
