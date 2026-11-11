package com.github.kr328.clash.service.store

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable

@Serializable
data class ManagedRoutingSettings(
    var enabled: Boolean = false,
    var protectionLevel: String = "standard",
    var domesticIpDirect: Boolean = true,
    var blockAds: Boolean = false,
    var domesticDns: Boolean = true,
    var aiProxyDns: Boolean = true,
    var targets: Map<String, String> = emptyMap(),
    var directDomains: List<String> = emptyList(),
    var proxyDomains: List<String> = emptyList(),
) {
    val protectedMode: Boolean get() = enabled && protectionLevel != "standard"
}

class ManagedRoutingStore(context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("clash/managed-routing.json"))

    fun read(): ManagedRoutingSettings {
        if (!file.baseFile.exists()) return ManagedRoutingSettings(protectionLevel = "strict")
        return file.openRead().bufferedReader().use { ManagedRoutingSettingsCodec.decode(it.readText()) }
    }

    fun write(settings: ManagedRoutingSettings) {
        val data = ManagedRoutingSettingsCodec.encode(settings).toByteArray(Charsets.UTF_8)
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(data)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
