package com.github.kr328.clash.service.store

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ManagedRoutingSettings(
    var enabled: Boolean = false,
    var blockAds: Boolean = false,
    var domesticDns: Boolean = true,
    var targets: Map<String, String> = emptyMap(),
    var directDomains: List<String> = emptyList(),
    var proxyDomains: List<String> = emptyList(),
)

class ManagedRoutingStore(context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("clash/managed-routing.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(): ManagedRoutingSettings {
        if (!file.baseFile.exists()) return ManagedRoutingSettings()
        return file.openRead().bufferedReader().use { json.decodeFromString<ManagedRoutingSettings>(it.readText()) }
    }

    fun write(settings: ManagedRoutingSettings) {
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(json.encodeToString(settings).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}
