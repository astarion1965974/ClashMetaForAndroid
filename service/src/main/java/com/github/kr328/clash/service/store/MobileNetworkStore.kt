package com.github.kr328.clash.service.store

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MobileNetworkSettings(
    var enabled: Boolean = true,
    var secureDns: Boolean = true,
    var wifiUpdates: Boolean = true,
    var stableNodes: List<String> = emptyList(),
    var aiNodes: List<String> = emptyList(),
    var trustedWifiEnabled: Boolean = false,
    var trustedSSIDs: List<String> = emptyList(),
)
object MobileNetworkCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(s: MobileNetworkSettings) = json.encodeToString(MobileNetworkSettings.serializer(), s)
    fun decode(s: String) = json.decodeFromString(MobileNetworkSettings.serializer(), s)
}
class MobileNetworkStore(context: Context) {
    private val file = AtomicFile(context.filesDir.resolve("clash/mobile-network.json"))
    fun read(): MobileNetworkSettings = if (!file.baseFile.exists()) MobileNetworkSettings()
        else file.openRead().bufferedReader().use { MobileNetworkCodec.decode(it.readText()) }
    fun write(s: MobileNetworkSettings) {
        require(s.stableNodes.size <= 5 && s.aiNodes.size <= 5)
        val bytes = MobileNetworkCodec.encode(s).toByteArray()
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
