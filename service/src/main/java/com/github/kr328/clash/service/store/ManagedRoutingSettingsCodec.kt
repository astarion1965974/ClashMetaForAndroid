package com.github.kr328.clash.service.store

import kotlinx.serialization.json.Json

/** Use generated serializers directly so release shrinking never relies on reflection. */
internal object ManagedRoutingSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ManagedRoutingSettings.serializer()

    fun decode(value: String): ManagedRoutingSettings = json.decodeFromString(serializer, value)
    fun encode(settings: ManagedRoutingSettings): String = json.encodeToString(serializer, settings)
}
