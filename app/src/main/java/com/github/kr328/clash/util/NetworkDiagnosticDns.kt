package com.github.kr328.clash.util

import org.json.JSONObject

/** Core JSON call completion is NOT equivalent to successful DNS resolution. */
internal object NetworkDiagnosticDns {
    fun requireResolved(result: String): String {
        val obj = JSONObject(result)
        val error = obj.optString("error")
        val addresses = obj.optJSONArray("addresses")
        if (error.isNotBlank()) throw IllegalStateException(
            "Mihomo DNS query failed: " + error +
                " upstream=" + obj.optString("dnsUpstream") +
                " route=" + obj.optString("dnsRoute"),
        )
        if (addresses == null || addresses.length() == 0) {
            throw IllegalStateException("Mihomo DNS query returned no addresses")
        }
        return "count=" + addresses.length() +
            " route=" + obj.optString("dnsRoute") +
            " proxy=" + obj.optString("dnsProxy") +
            " upstream=" + obj.optString("dnsUpstream") +
            " elapsed_ms=" + obj.optLong("millis")
    }
}
