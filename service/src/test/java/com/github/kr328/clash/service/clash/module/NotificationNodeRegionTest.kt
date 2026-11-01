package com.github.kr328.clash.service.clash.module

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NotificationNodeRegionTest {
    @Test fun flagsIdentifyTheSelectedRegion() {
        mapOf("🇭🇰 IEPL 01" to "HK", "🇯🇵 Tokyo 02" to "JP", "🇸🇬 Premium" to "SG", "🇺🇸 03" to "US").forEach { (name, expected) ->
            assertEquals(name, expected, NotificationNodeRegion.regionCode(name))
        }
    }
    @Test fun chineseNamesAndCitiesAreRecognized() {
        mapOf("香港专线 01" to "HK", "日本-大阪 02" to "JP", "新加坡倍率0.5" to "SG", "美西IEPL03" to "US", "首尔01" to "KR").forEach { (name, expected) ->
            assertEquals(name, expected, NotificationNodeRegion.regionCode(name))
        }
    }
    @Test fun traditionalChineseAndRegionalVariantsAreRecognized() {
        mapOf("中國香港 01" to "HK", "中國澳門 01" to "MO", "臺灣 台北01" to "TW", "美國 紐約01" to "US").forEach { (name, expected) ->
            assertEquals(name, expected, NotificationNodeRegion.regionCode(name))
        }
    }
    @Test fun englishAndAbbreviatedSubscriptionNamesAreRecognized() {
        mapOf("Hong Kong Premium" to "HK", "Japan01" to "JP", "[hk] 01" to "HK", "SG_03" to "SG", "JPN-02" to "JP", "USA 01" to "US", "ＨＫ０１" to "HK").forEach { (name, expected) ->
            assertEquals(name, expected, NotificationNodeRegion.regionCode(name))
        }
    }
    @Test fun abbreviationsCannotMatchInsideUnrelatedWordsOrTrafficUnits() {
        listOf("Premium fast IPLC 01", "remaining traffic 100 GB", "剩余流量100GB", "高速专线 01", "francesco").forEach {
            assertNull(it, NotificationNodeRegion.regionCode(it))
        }
    }
    @Test fun flagsTakePriorityOverTransitNames() {
        assertEquals("JP", NotificationNodeRegion.regionCode("🇯🇵 香港中转 日本出口 01"))
    }
    @Test fun cnFlagsCanStillAdvertiseHongKongOrTaiwanRegions() {
        assertEquals("HK", NotificationNodeRegion.regionCode("🇨🇳 香港01"))
        assertEquals("TW", NotificationNodeRegion.regionCode("🇨🇳 台湾01"))
    }
    @Test fun conflictingRegionsDoNotInventAnExit() {
        assertNull(NotificationNodeRegion.regionCode("🇭🇰 → 🇯🇵 Relay"))
        assertNull(NotificationNodeRegion.regionCode("HK → JP Relay"))
        assertNull(NotificationNodeRegion.regionCode("Hong Kong / Japan"))
    }
    @Test fun invalidAndIncompleteFlagsDoNotClaimARegion() {
        assertNull(NotificationNodeRegion.regionCode("🇿🇿 01"))
        assertNull(NotificationNodeRegion.regionCode("🇯 01"))
    }
    @Test fun countryLabelsOmitTheRestOfTheNodeName() {
        val locale = Locale.SIMPLIFIED_CHINESE
        assertEquals("香港", NotificationNodeRegion.regionLabel("🇭🇰 香港 01 · 高速专线", locale))
        assertEquals("日本", NotificationNodeRegion.regionLabel("JP-02 x0.5 IEPL", locale))
        assertEquals("美国", NotificationNodeRegion.regionLabel("US-03", locale))
        assertEquals("台湾", NotificationNodeRegion.regionLabel("TW-01", locale))
    }
    @Test fun englishLabelsStayWithinTheCapsuleLimit() {
        assertEquals("USA", NotificationNodeRegion.regionLabel("US-01", Locale.ENGLISH))
        assertEquals("UK", NotificationNodeRegion.regionLabel("UK-01", Locale.ENGLISH))
        assertEquals("Japan", NotificationNodeRegion.regionLabel("JP-01", Locale.ENGLISH))
    }
    @Test fun unknownNodesNeverFallBackToDisplayingTheirFullName() {
        assertNull(NotificationNodeRegion.regionLabel("Premium IEPL 01", Locale.SIMPLIFIED_CHINESE))
    }
}
