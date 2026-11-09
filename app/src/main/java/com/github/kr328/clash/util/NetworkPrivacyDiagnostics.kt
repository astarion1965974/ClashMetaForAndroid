package com.github.kr328.clash.util

import com.github.kr328.clash.design.HomePrivacyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Explicit user-triggered network privacy sample. Never runs automatically.
 *
 * IPPure: https://my.ippure.com/v1/info (fraudScore, lower = lower risk)
 * IPLeak: https://ipleak.net/json/ (the caller's public IP)
 * DNS: documented https://{40-char-session}-{unique-label}.ipleak.net/dnsdetection/
 *
 * This tests the Android application process's route, NOT every other
 * application (browser, Private DNS, IPv6 and per-app VPN exclusions can differ).
 * No observed recursive DNS set alone supports a "no leak" verdict.
 */
internal object NetworkPrivacyDiagnostics {
    private const val IpPureUrl = "https://my.ippure.com/v1/info"
    private const val IpLeakUrl = "https://ipleak.net/json/"
    private const val TimeoutMs = 6_000
    private const val MaxResponseChars = 32_768

    data class PureInfo(
        val ip: String, val riskScore: Int?, val isp: String,
        val country: String, val residential: Boolean?,
    )
    data class LeakInfo(val ip: String, val isp: String)

    fun parseIppure(json: String): PureInfo {
        val obj = JSONObject(json)
        val risk = if (obj.has("fraudScore") && !obj.isNull("fraudScore"))
            obj.optInt("fraudScore", -1).takeIf { it in 0..100 } else null
        return PureInfo(
            ip = obj.optString("ip"),
            riskScore = risk,
            isp = obj.optString("asOrganization"),
            country = obj.optString("country"),
            residential = if (obj.has("isResidential") && !obj.isNull("isResidential"))
                obj.optBoolean("isResidential") else null,
        )
    }

    fun parseIpleak(json: String): LeakInfo {
        val obj = JSONObject(json)
        return LeakInfo(obj.optString("ip"), obj.optString("isp_name"))
    }

    /** IPLeak uses [] for no observed resolvers, {} or an object for results. */
    fun parseDnsResolvers(json: String): Set<String> {
        val ip = JSONObject(json).opt("ip")
        if (ip !is JSONObject) return emptySet()
        val found = linkedSetOf<String>()
        val keys = ip.keys()
        while (keys.hasNext()) {
            val address = keys.next()
            if (address.isNotBlank() && ip.optInt(address, 0) > 0) found.add(address)
        }
        return found
    }

    private fun sessionId(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            bytes.forEach {
                append(hex[(it.toInt() ushr 4) and 15])
                append(hex[it.toInt() and 15])
            }
        }
    }

    private fun httpsJson(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = TimeoutMs
            readTimeout = TimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ClashMetaLive/1.0")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) error("HTTP $status")
            connection.inputStream.bufferedReader().use { reader ->
                // Avoid unbounded consumption if an external service changes.
                reader.readText().take(MaxResponseChars)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun check(): HomePrivacyResult = withContext(Dispatchers.IO) {
        val pure = runCatching { parseIppure(httpsJson(IpPureUrl)) }
        val leak = runCatching { parseIpleak(httpsJson(IpLeakUrl)) }

        // Fresh DNS names avoid client-side cache reuse, as required by IPLeak.
        // Each /dnsdetection/ response reports resolvers observed for the
        // shared 40-char session; a missing result MUST remain "unknown".
        val session = sessionId()
        val resolvers = linkedSetOf<String>()
        val dnsErrors = mutableListOf<String>()
        repeat(3) { probe ->
            val label = "check${probe + 1}"
            val address = "https://$session-$label.ipleak.net/dnsdetection/"
            runCatching { parseDnsResolvers(httpsJson(address)) }
                .onSuccess { resolvers.addAll(it) }
                .onFailure { dnsErrors.add(it.message ?: it.javaClass.simpleName) }
        }

        HomePrivacyResult(
            ippureIp = pure.getOrNull()?.ip.orEmpty(),
            fraudScore = pure.getOrNull()?.riskScore,
            isp = pure.getOrNull()?.isp.orEmpty(),
            country = pure.getOrNull()?.country.orEmpty(),
            residential = pure.getOrNull()?.residential,
            ipleakIp = leak.getOrNull()?.ip.orEmpty(),
            ipleakIsp = leak.getOrNull()?.isp.orEmpty(),
            dnsResolvers = resolvers.toList(),
            ippureError = pure.exceptionOrNull()?.message.orEmpty(),
            ipleakError = leak.exceptionOrNull()?.message.orEmpty(),
            dnsError = if (resolvers.isNotEmpty()) "" else
                dnsErrors.distinct().take(2).joinToString("; ")
                    .ifBlank { "No recursive resolver reported; not proof of no leak" },
        )
    }
}
