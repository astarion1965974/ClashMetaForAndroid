package com.github.kr328.clash.service.store

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class ManagedRoutingSettingsCodecTest {
    @Test fun enabledTemplateRoundTripsEveryUserSetting() {
        val settings = ManagedRoutingSettings(
            enabled = true,
            protectionLevel = "strict",
            blockAds = true,
            domesticDns = false,
            aiProxyDns = false,
            targets = mapOf("ai" to "日本 · AI", "cn" to "DIRECT", "fallback" to "Live · 默认代理"),
            directDomains = listOf("example.cn", "xn--fiqs8s.com"),
            proxyDomains = listOf("chatgpt.com", "claude.ai"),
        )
        assertEquals(settings, ManagedRoutingSettingsCodec.decode(ManagedRoutingSettingsCodec.encode(settings)))
    }

    @Test fun defaultsAreWrittenExplicitlyForNativeGoConsumer() {
        val keys = Json.parseToJsonElement(ManagedRoutingSettingsCodec.encode(ManagedRoutingSettings())).jsonObject.keys
        assertEquals(setOf("enabled", "protectionLevel", "blockAds", "domesticDns", "aiProxyDns", "targets", "directDomains", "proxyDomains"), keys)
    }

    @Test fun olderPartialSettingsRetainExpectedDefaults() {
        val settings = ManagedRoutingSettingsCodec.decode("""{"enabled":true}""")
        assertTrue(settings.enabled)
        assertEquals("standard", settings.protectionLevel)
        assertFalse(settings.protectedMode)
        assertTrue(settings.domesticDns)
        assertTrue(settings.aiProxyDns)
        assertFalse(settings.blockAds)
        assertEquals(emptyMap<String, String>(), settings.targets)
        assertEquals(emptyList<String>(), settings.directDomains)
    }

    @Test fun unknownFieldsDoNotBlockOpeningExistingSettings() {
        val settings = ManagedRoutingSettingsCodec.decode("""{"enabled":true,"futureSetting":{"value":123}}""")
        assertTrue(settings.enabled)
    }

    @Test(expected = SerializationException::class)
    fun malformedSettingsFailInsteadOfOverwritingExistingPreferences() {
        ManagedRoutingSettingsCodec.decode("""{"enabled":"invalid"}""")
    }
}
