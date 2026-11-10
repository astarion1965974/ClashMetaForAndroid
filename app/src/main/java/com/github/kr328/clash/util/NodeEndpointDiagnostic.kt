package com.github.kr328.clash.util

import android.net.Network
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Manual, bounded physical-network comparison. The subscription bytes and
 * server host are used only in memory. No node credentials or addresses are
 * returned by the public result.
 *
 * The parser supports conventional block/inline 'proxies:' YAML entries and
 * deliberately skips proxy-provider-only / unsupported layouts.
 */
internal object NodeEndpointDiagnostic {
    data class Endpoint(
        val nodeName: String,
        val type: String,
        val server: String,
        val port: Int,
        val sni: String,
    )
    data class AddressSet(val v4: List<InetAddress>, val v6: List<InetAddress>)

    private const val MaxYamlBytes = 1_048_576
    private const val TimeoutMs = 6_000
    private val fieldNames = setOf("name", "server", "port", "type", "sni", "servername")
    private val inlineField = Regex(
        """(?:^|,\s*)(name|server|port|type|sni|servername)\s*:\s*("[^"]*"|'[^']*'|[^,}]+)""",
        RegexOption.IGNORE_CASE,
    )
    private fun unquote(value: String) =
        value.trim().removeSuffix(",").trim().removeSurrounding("\"").removeSurrounding("'")

    fun parseProxy(yaml: String, nodeName: String): Endpoint? {
        if (nodeName.isBlank()) return null
        val lines = yaml.lineSequence()
        var inside = false
        var fields = linkedMapOf<String, String>()
        var found: Endpoint? = null
        fun toEndpoint(): Endpoint? {
            val name = fields["name"] ?: return null
            if (name != nodeName) return null
            val server = fields["server"]?.takeIf { it.isNotBlank() } ?: return null
            // Do not allow URLs, paths or injectable host strings into a socket dial.
            if (server.length > 253 || server.any { it.isWhitespace() || it in "/@?#" }) return null
            val port = fields["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return Endpoint(name, fields["type"]?.lowercase() ?: "unknown",
                server, port, fields["sni"] ?: fields["servername"] ?: server)
        }
        for (line in lines) {
            val t = line.trim()
            if (!inside) {
                if (t == "proxies:") inside = true
                continue
            }
            if (line.isNotBlank() && line.first() != ' ' && line.first() != '\t') break
            if (t.startsWith("- ")) {
                if (found == null) found = toEndpoint()
                fields = linkedMapOf()
                val raw = t.removePrefix("- ").trim().removePrefix("{").trim().removeSuffix("}").trim()
                if (raw.startsWith("name:")) {
                    val rest = raw.substringAfter(":").trim()
                    fields["name"] = unquote(rest.substringBefore(","))
                }
                for (match in inlineField.findAll(raw)) {
                    fields[match.groupValues[1].lowercase()] = unquote(match.groupValues[2])
                }
            } else {
                val key = t.substringBefore(':', "").lowercase()
                if (key in fieldNames) {
                    fields[key] = unquote(t.substringAfter(':'))
                }
            }
        }
        return found ?: toEndpoint()
    }

    /**
     * Probe a second, TCP-based node without changing the active selection.
     * Prefer AnyTLS from the same named region where possible, then an
     * AnyTLS endpoint on TCP/443, then the first other AnyTLS endpoint.
     */
    fun findAnyTlsControl(yaml: String, selectedName: String): Endpoint? {
        var inside = false
        val names = mutableListOf<String>()
        for (line in yaml.lineSequence()) {
            val t = line.trim()
            if (!inside) {
                if (t == "proxies:") inside = true
                continue
            }
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")) break
            if (!t.startsWith("- ")) continue
            val raw = t.removePrefix("- ").trim().removePrefix("{").trim()
            // Quoted names may contain punctuation, so match until the closing quote.
            val match = Regex("""^name\s*:\s*("[^"]*"|'[^']*'|[^,}]+)""")
                .find(raw) ?: continue
            val name = unquote(match.groupValues[1])
            if (name != selectedName) names.add(name)
        }
        val available = names.mapNotNull { parseProxy(yaml, it) }
            .filter { it.type == "anytls" }
        val region = selectedName.takeWhile { it != '[' && it != '【' && it != ' ' }
        return available.firstOrNull { region.isNotBlank() && it.nodeName.startsWith(region) }
            ?: available.firstOrNull { it.port == 443 }
            ?: available.firstOrNull()
    }

    fun readSubscription(network: Network, url: URL): String {
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS required" }
        val c = network.openConnection(url) as HttpURLConnection
        c.connectTimeout = TimeoutMs
        c.readTimeout = TimeoutMs
        c.instanceFollowRedirects = false
        return try {
            require(c.responseCode in 200..299) { "HTTP " + c.responseCode }
            val bytes = c.inputStream.use { stream ->
                val data = stream.readNBytes(MaxYamlBytes + 1)
                require(data.size <= MaxYamlBytes) { "Subscription exceeds safe diagnostic limit" }
                data
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    fun resolve(network: Network, host: String): AddressSet {
        val all = network.getAllByName(host)
        return AddressSet(all.filterIsInstance<Inet4Address>(), all.filterIsInstance<Inet6Address>())
    }

    fun isTcp(type: String): Boolean = type.lowercase() !in setOf(
        "tuic", "hysteria", "hysteria2", "hy2", "wireguard", "wg",
    )

    fun tcp(network: Network, address: InetAddress, port: Int): Long {
        val start = SystemClock.elapsedRealtime()
        network.socketFactory.createSocket().use { sock ->
            sock.connect(InetSocketAddress(address, port), TimeoutMs)
        }
        return SystemClock.elapsedRealtime() - start
    }

    /**
     * Generic TLS/SNI only; this is not an AnyTLS/Trojan/VLESS authentication
     * test. Fingerprint, ALPN and trust overrides may change its result.
     */
    fun tls(network: Network, address: InetAddress, port: Int, sni: String): String {
        network.socketFactory.createSocket().use { raw ->
            raw.connect(InetSocketAddress(address, port), TimeoutMs)
            raw.soTimeout = TimeoutMs
            (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, sni, port, false).use { socket ->
                    val tls = socket as SSLSocket
                    tls.soTimeout = TimeoutMs
                    tls.startHandshake()
                    return "standard_TLS=" + tls.session.protocol + " cipher=" + tls.session.cipherSuite
                }
        }
    }
}
