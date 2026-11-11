package com.github.kr328.clash.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.github.kr328.clash.LogcatService
import com.github.kr328.clash.common.constants.DnsDiagnosticTargets
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.ILogObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * An opt-in, bounded diagnostic. It reads no subscriptions/config contents, never
 * changes selectors or DNS settings, and never uploads any results. Full subscription
 * URLs are used only for an on-device HTTP read; report contains hostname only.
 */
internal class NetworkDiagnosticRunner(
    private val context: Context,
    private val vpnRunning: Boolean,
) {
    private data class Probe(val key: String, val ok: Boolean, val detail: String)
    private val probes = mutableListOf<Probe>()
    private val report = StringBuilder()
    private val coreLogs = Collections.synchronizedList(mutableListOf<String>())
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val timeoutMs = 7_000

    private fun now(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US,
    ).format(Date())

    private fun append(line: String) { report.appendLine(DiagnosticRedactor.clean(line)) }

    private fun errorDescription(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.take(4).joinToString(" -> ") {
            it.javaClass.simpleName + ": " + (it.message ?: "<no message>")
        }
        return DiagnosticRedactor.clean(chain).take(1200)
    }

    private suspend fun step(
        key: String,
        progress: suspend (String) -> Unit,
        operation: suspend () -> String,
    ) {
        progress("检测中：" + key)
        val started = SystemClock.elapsedRealtime()
        try {
            val details = operation()
            val elapsed = SystemClock.elapsedRealtime() - started
            val result = Probe(key, true, details)
            probes.add(result)
            append("[" + now() + "] PASS " + key + " (" + elapsed + "ms): " + details)
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - started
            val details = errorDescription(e)
            probes.add(Probe(key, false, details))
            append("[" + now() + "] FAIL " + key + " (" + elapsed + "ms): " + details)
        }
    }

    private fun physicalNetwork(): Network? =
        cm.allNetworks.firstOrNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } ?: cm.allNetworks.firstOrNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }

    private fun networkDescription(n: Network): String {
        val c = cm.getNetworkCapabilities(n)
        val p = cm.getLinkProperties(n)
        val type = when {
            c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            c?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            else -> "OTHER"
        }
        return "type=" + type +
            " validated=" + (c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false) +
            " mtu=" + (p?.mtu ?: -1) +
            " dns_count=" + (p?.dnsServers?.size ?: 0) +
            " ipv6=" + (p?.linkAddresses?.any { it.address is java.net.Inet6Address } ?: false)
    }

    private fun dns(host: String, physical: Network?): String {
        val ips = if (physical != null) physical.getAllByName(host)
            else InetAddress.getAllByName(host)
        return "count=" + ips.size +
            " answers=" + ips.take(5).joinToString(",") { it.hostAddress ?: "?" }
    }

    private fun tcpConnect(host: String, port: Int, physical: Network?): String {
        val socket = physical?.socketFactory?.createSocket() ?: Socket()
        socket.use {
            it.connect(InetSocketAddress(host, port), timeoutMs)
            return "TCP established remote=" + host + ":" + port
        }
    }

    /**
     * An HTTP response code, including 403/404, proves the TCP/TLS/HTTP exchange
     * completed; a 2xx response is not required for connection diagnostics.
     * Never include any remote HTTP body, headers or subscription path in logs.
     */
    private fun httpsProbe(
        address: String,
        physical: Network? = null,
        proxy: Proxy? = null,
    ): String {
        val url = URL(address)
        require(url.protocol == "https")
        val conn = (when {
            proxy != null -> url.openConnection(proxy)
            physical != null -> physical.openConnection(url)
            else -> url.openConnection()
        } as HttpURLConnection)
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("User-Agent", "ClashMetaLive-Diagnostic/1.0")
        return try {
            val status = conn.responseCode
            val type = conn.getHeaderField("Content-Type")?.substringBefore(';') ?: "unknown"
            var consumed = 0
            val limit = 512 * 1024
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            stream?.use { input ->
                val buffer = ByteArray(8192)
                while (consumed < limit) {
                    val read = input.read(buffer, 0, minOf(buffer.size, limit - consumed))
                    if (read < 0) break
                    consumed += read
                }
            }
            "HTTP=" + status + " content_type=" + type + " bytes_read=" + consumed +
                (if (consumed >= limit) " read_limit_reached" else " body_completed")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun <T> coreCall(operation: suspend () -> T): T =
        withTimeout(6_000) { operation() }

    suspend fun run(progress: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        append("# Clash Meta Live · Network Diagnostic (v2)")
        append("Started: " + now())
        append("Android API: " + Build.VERSION.SDK_INT)
        append("App version: " + context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        append("VPN running (app broadcast): " + vpnRunning)
        append("Privacy: URLs and credential patterns are redacted; review report before sharing.")
        append("Note: application-process requests are NOT equivalent to Chrome's VPN capture path.")
        append("Note: local mixed-port probe is attempted at 127.0.0.1:7890; skipped if unavailable.")
        append("")

        val physical = physicalNetwork()
        append("## Android network snapshot")
        append("Active: " + (cm.activeNetwork?.let(::networkDescription) ?: "none"))
        for (n in cm.allNetworks) append("Interface: " + networkDescription(n))
        append("Chosen non-VPN underlay: " + (physical?.let(::networkDescription) ?: "not found"))

        val activeUrl = runCatching {
            coreCall { withProfile { queryActive() } }
        }.getOrNull()?.takeIf { it.type == Profile.Type.Url }
            ?.source?.takeIf { it.startsWith("https://", ignoreCase = true) }

        val subscription = activeUrl?.let {
            runCatching { URL(it).takeIf { u -> u.protocol == "https" } }.getOrNull()
        }
        val subHost = subscription?.host ?: "yyds.sbyun.org"
        val subAddress = subscription?.toExternalForm() ?: "https://yyds.sbyun.org/"
        append("Subscription endpoint: " + subHost + " (path and query NEVER exported)")
        append("Actual active URL used: " + (subscription != null))
        append("")

        append("## Live core snapshot")
        var liveExit: String? = null
        if (vpnRunning) {
            step("Mihomo TUN mode", progress) {
                coreCall { withClash { queryTunnelState().mode.toString() } }
            }
            step("VPN protect counters before probes", progress) {
                coreCall { withClash { queryRuntimeDiagnostics() } }
            }
            step("Selected GLOBAL exit", progress) {
                coreCall { withClash { queryProxyGroup("GLOBAL", ProxySort.Default).now } }
            }
            step("Effective managed proxy exit", progress) {
                val chain = mutableListOf("Live · 默认代理")
                var current = "Live · 默认代理"
                for (index in 0 until 5) {
                    val selection = coreCall {
                        withClash { queryProxyGroup(current, ProxySort.Default) }
                    }
                    val next = selection.now
                    if (next.isBlank() || next == current || next in chain) break
                    chain.add(next)
                    current = next
                }
                liveExit = current.takeIf { it != "Live · 默认代理" }
                "selection_chain=" + chain.joinToString(" → ")
            }
        } else {
            append("SKIP core tests: VPN is not running")
        }

        val observer = object : ILogObserver {
            override fun newItem(log: LogMessage) {
                val message = log.message
                if (coreLogs.size >= 320) return
                val relevant = Regex(
                    "error|warn|failed|timeout|eof|closed|handshake|ssl|certificate|x509|underlay|dns|dial|connect|google|youtube|sbyun|protect",
                    RegexOption.IGNORE_CASE,
                ).containsMatchIn(message)
                if (relevant) coreLogs.add(
                    now() + " [" + log.level + "] " + DiagnosticRedactor.clean(message).take(800),
                )
            }
        }
        var captured = false
        if (vpnRunning && !LogcatService.running) {
            captured = runCatching {
                coreCall {
                    withClash { setLogObserver(observer) }
                    "attached"
                }
            }.isSuccess
        }
        append("Mihomo error capture: " + when {
            captured -> "active"
            LogcatService.running -> "skipped (existing log capture active; not overriding)"
            else -> "unavailable"
        })

        try {
            append("")
            append("## Underlay / DNS / HTTPS tests")
            if (physical != null) {
                step("underlay DNS " + subHost, progress) { dns(subHost, physical) }
                step("underlay subscription HTTPS " + subHost, progress) {
                    httpsProbe(subAddress, physical = physical)
                }
                step("underlay TCP 1.1.1.1:443", progress) {
                    tcpConnect("1.1.1.1", 443, physical)
                }
                append("")
                append("## Selected proxy server / physical Wi-Fi diagnostics")
                val selected = liveExit
                if (subscription == null || selected.isNullOrBlank() ||
                    selected in setOf("DIRECT", "REJECT", "GLOBAL")
                ) {
                    append("SKIP selected-node test: active subscription URL or selected node unavailable")
                } else {
                    var subscriptionYaml: String? = null
                    val endpoint = runCatching {
                        val yaml = NodeEndpointDiagnostic.readSubscription(physical, subscription)
                        subscriptionYaml = yaml
                        NodeEndpointDiagnostic.parseProxy(yaml, selected)
                    }.onFailure {
                        append("SKIP node endpoint discovery: " + it.javaClass.simpleName)
                    }.getOrNull()
                    if (endpoint == null) {
                        append("SKIP node address tests: node unavailable in inline subscription proxies (proxy-providers are not covered)")
                    } else {
                        append("Selected node type=" + endpoint.type + " server=[REDACTED] port=" +
                            endpoint.port + " (host and credentials never exported)")
                        var addresses: NodeEndpointDiagnostic.AddressSet? = null
                        step("Wi-Fi DNS of selected node", progress) {
                            try {
                                val resolved = NodeEndpointDiagnostic.resolve(physical, endpoint.server)
                                addresses = resolved
                                "IPv4_count=" + resolved.v4.size + " IPv6_count=" + resolved.v6.size
                            } catch (e: Exception) {
                                throw IllegalStateException("physical DNS " + e.javaClass.simpleName)
                            }
                        }
                        val addr = addresses
                        if (addr != null) {
                            for ((label, ip) in listOfNotNull(
                                addr.v4.firstOrNull()?.let { "IPv4" to it },
                                addr.v6.firstOrNull()?.let { "IPv6" to it },
                            )) {
                                if (!NodeEndpointDiagnostic.isTcp(endpoint.type)) {
                                    append("SKIP " + label + " TCP: node uses UDP-based transport (" +
                                        endpoint.type + ")")
                                    continue
                                }
                                step("Wi-Fi selected node " + label + " TCP", progress) {
                                    try {
                                        "connected_in_ms=" + NodeEndpointDiagnostic.tcp(
                                            physical, ip, endpoint.port,
                                        )
                                    } catch (e: Exception) {
                                        throw IllegalStateException("node TCP " + e.javaClass.simpleName)
                                    }
                                }
                                if (label == "IPv4" && endpoint.type in setOf(
                                    "anytls", "trojan", "vless", "vmess", "socks5", "http"
                                )) {
                                    step("Wi-Fi selected node standard TLS IPv4 (not proxy authentication)", progress) {
                                        try {
                                            NodeEndpointDiagnostic.tls(
                                                physical, ip, endpoint.port, endpoint.sni,
                                            )
                                        } catch (e: Exception) {
                                            throw IllegalStateException(
                                                "generic TLS handshake " + e.javaClass.simpleName +
                                                    " (may differ from configured SNI, ALPN or fingerprint)",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // TUIC/Hysteria are UDP based. A raw TCP probe to their
                        // destination would not diagnose a QUIC handshake, so
                        // compare an existing AnyTLS endpoint from this profile.
                        if (!NodeEndpointDiagnostic.isTcp(endpoint.type)) {
                            append("Selected node uses " + endpoint.type +
                                "/UDP: raw TCP would not validate this node's QUIC handshake")
                            val control = NodeEndpointDiagnostic.findAnyTlsControl(
                                subscriptionYaml.orEmpty(), selected,
                            )
                            if (control == null) {
                                append("SKIP AnyTLS control: no inline AnyTLS alternative")
                            } else {
                                append("AnyTLS control selected from same subscription: " +
                                    "type=" + control.type + " port=" + control.port +
                                    " server=[REDACTED] (no selector changes)")
                                var controlV4: InetAddress? = null
                                step("Wi-Fi AnyTLS control DNS", progress) {
                                    try {
                                        val resolved = NodeEndpointDiagnostic.resolve(
                                            physical, control.server,
                                        )
                                        controlV4 = resolved.v4.firstOrNull()
                                        "IPv4_count=" + resolved.v4.size +
                                            " IPv6_count=" + resolved.v6.size
                                    } catch (e: Exception) {
                                        throw IllegalStateException(
                                            "AnyTLS control DNS " + e.javaClass.simpleName,
                                        )
                                    }
                                }
                                val ip = controlV4
                                if (ip != null) {
                                    step("Wi-Fi AnyTLS control IPv4 TCP", progress) {
                                        try {
                                            "connected_in_ms=" + NodeEndpointDiagnostic.tcp(
                                                physical, ip, control.port,
                                            )
                                        } catch (e: Exception) {
                                            throw IllegalStateException(
                                                "AnyTLS control TCP " + e.javaClass.simpleName,
                                            )
                                        }
                                    }
                                    step("Wi-Fi AnyTLS control standard TLS", progress) {
                                        try {
                                            NodeEndpointDiagnostic.tls(
                                                physical, ip, control.port, control.sni,
                                            )
                                        } catch (e: Exception) {
                                            throw IllegalStateException(
                                                "AnyTLS control TLS " + e.javaClass.simpleName +
                                                " (generic TLS, not AnyTLS authentication)",
                                            )
                                        }
                                    }
                                } else {
                                    append("SKIP AnyTLS control TCP/TLS: no IPv4 result")
                                }
                            }
                        }
                    }
                }
            } else {
                append("SKIP physical network tests: no non-VPN underlay found")
            }

            step("Android process DNS " + subHost, progress) { dns(subHost, null) }
            step("Android process subscription HTTPS " + subHost, progress) {
                httpsProbe(subAddress)
            }
            step("Android process HTTPS baidu", progress) {
                httpsProbe("https://www.baidu.com/")
            }
            // TLS trust/handshake failures are reported as SSL exceptions. This
            // application-process probe is diagnostic and does not impersonate
            // the ChatGPT app's VPN capture path.
            step("Android process HTTPS ChatGPT", progress) {
                httpsProbe("https://chatgpt.com/")
            }
            if (vpnRunning) {
                for (domain in listOf("google.com", "youtube.com", "chatgpt.com")) {
                    if (!DnsDiagnosticTargets.supports(domain)) continue
                    step("Mihomo DNS " + domain, progress) {
                        val response = coreCall { withClash { queryDnsDiagnostic(domain) } }
                        NetworkDiagnosticDns.requireResolved(response)
                    }
                }
            }
            val local = runCatching { tcpConnect("127.0.0.1", 7890, null) }.isSuccess
            append("Local mixed-port 7890: " + if (local) "LISTENING" else "NOT LISTENING")
            if (local) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
                step("Mihomo HTTP proxy Google", progress) {
                    httpsProbe("https://www.google.com/generate_204", proxy = proxy)
                }
                step("Mihomo HTTP proxy YouTube", progress) {
                    httpsProbe("https://www.youtube.com/", proxy = proxy)
                }
                step("Mihomo HTTP proxy ChatGPT", progress) {
                    httpsProbe("https://chatgpt.com/", proxy = proxy)
                }
                step("Mihomo HTTP proxy subscription " + subHost, progress) {
                    httpsProbe(subAddress, proxy = proxy)
                }
            } else append("SKIP HTTP proxy test: listen port may differ; does not prove Mihomo is down")
            if (vpnRunning) {
                step("VPN protect counters after probes", progress) {
                    coreCall { withClash { queryRuntimeDiagnostics() } }
                }
            }
        } finally {
            if (captured && !LogcatService.running) runCatching {
                coreCall { withClash { setLogObserver(null) }; "detached" }
            }
        }
        append("")
        append("## Mihomo diagnostic-period connection and error lines")
        val lines = synchronized(coreLogs) { coreLogs.toList() }
        if (lines.isEmpty()) append("No relevant core log captured (not proof of no core errors).")
        else lines.forEach(::append)
        append("")
        append("## Interpretation hints (not a definitive diagnosis)")
        val underlayOk = probes.firstOrNull { it.key.startsWith("underlay subscription HTTPS") }?.ok
        val processOk = probes.firstOrNull { it.key.startsWith("Android process subscription HTTPS") }?.ok
        val proxyGoogleOk = probes.firstOrNull { it.key == "Mihomo HTTP proxy Google" }?.ok
        if (underlayOk == true && processOk == false)
            append("Physical Wi-Fi subscription works but application-process request fails: inspect VPN routing, DNS and network binding.")
        if (underlayOk == false)
            append("Physical Wi-Fi subscription test failed: inspect Wi-Fi APN/transport/TLS/MTU before blaming Mihomo.")
        if (proxyGoogleOk == false && underlayOk == true)
            append("Underlying HTTPS works but explicit Mihomo proxy fails: inspect node dial/TLS, proxy DNS and socket protection counters.")
        if (proxyGoogleOk == null)
            append("HTTP mixed port was unavailable: explicit Mihomo proxy outcome was NOT measured.")
        val nodeTcp = probes.filter { it.key.startsWith("Wi-Fi selected node ") && it.key.endsWith(" TCP") }
        if (nodeTcp.isNotEmpty() && nodeTcp.all { !it.ok })
            append("Selected node TCP could not be reached over physical Wi-Fi: compare the same node from the cellular network.")
        if (nodeTcp.any { it.ok } && proxyGoogleOk == false)
            append("Physical Wi-Fi can establish node TCP, but Mihomo proxy still fails: inspect outbound binding, configured SNI/ALPN and proxy authentication.")
        if (nodeTcp.isEmpty())
            append("No selected-node TCP was measured: UDP-based node, unsupported subscription layout or DNS failure.")
        val controlTcp = probes.firstOrNull { it.key == "Wi-Fi AnyTLS control IPv4 TCP" }
        if (controlTcp?.ok == false)
            append("AnyTLS control TCP failed on physical Wi-Fi: test the same AnyTLS node over cellular before concluding the Wi-Fi transport is blocked.")
        if (controlTcp?.ok == true && proxyGoogleOk == false)
            append("Physical Wi-Fi can reach an AnyTLS server via TCP, but selected Mihomo exit still fails: compare protocol-specific TUIC/QUIC and AnyTLS proxy authentication.")
        if (coreLogs.any { it.contains("yyds.sbyun.org") && it.contains("Live · 默认代理") })
            append("Subscription hostname entered managed proxy routing; add the subscription domain to Network > Managed Rules > Direct domains, then save/reconnect.")
        append("To reproduce a browser failure, keep VPN active; do not change network or node during the test.")
        append("Finished: " + now())
        progress("检测完成，可导出诊断日志")
        DiagnosticRedactor.clean(report.toString())
    }
}

internal object DiagnosticRedactor {
    private val credentials = Regex(
        "(?i)(password|token|secret|authorization|uuid|api[_-]?key|key|passwd)" +
            "(\\s*[:=]\\s*)([^,;\\s}]+)",
    )
    private val url = Regex("(?i)(https?://[a-z0-9.-]+(?::[0-9]+)?)(/[^\\s\\\"'<>]*)")
    private val guid = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
    )
    fun clean(source: String): String = source
        .replace(credentials) { it.groupValues[1] + it.groupValues[2] + "[REDACTED]" }
        .replace(url) { it.groupValues[1] + "/[REDACTED]" }
        .replace(guid, "[UUID-REDACTED]")
}
