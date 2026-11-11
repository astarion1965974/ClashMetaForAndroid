package com.github.kr328.clash.service.clash.module

import org.junit.Assert.*
import org.junit.Test

class NotificationNodeResolverTest {
    private fun group(now: String, vararg children: String) =
        NotificationNodeResolver.Group("Selector", now, children.toSet())

    @Test fun managedGroupIsDefaultButAnExplicitPreferenceWins() {
        val names = listOf("Proxy", "Streaming", "Live · 默认代理")
        assertEquals("Live · 默认代理", NotificationNodeResolver.mainGroup("Rule", names, ""))
        assertEquals("Streaming", NotificationNodeResolver.mainGroup("Rule", names, "Streaming"))
    }

    @Test fun explicitMainGroupWinsOverAutomaticDetection() {
        assertEquals("Streaming", NotificationNodeResolver.mainGroup("Rule", listOf("Proxy", "Streaming"), "Streaming"))
    }

    @Test fun mainGroupDetectionRecognizesDecoratedChineseNames() {
        assertEquals("🚀 节点选择", NotificationNodeResolver.mainGroup("Rule", listOf("自动选择", "🚀 节点选择"), ""))
    }

    @Test fun missingMainGroupFallsBackAfterProfileSwitch() {
        assertEquals("Proxy", NotificationNodeResolver.mainGroup("Rule", listOf("Proxy", "Streaming"), "Old group"))
        assertNull(NotificationNodeResolver.mainGroup("Rule", emptyList(), "Old group"))
    }

    @Test fun mainResolutionDoesNotSampleUnrelatedGroups() {
        val groups = mapOf("Proxy" to group("Auto", "Auto"), "Auto" to group("HK 01"), "Streaming" to group("JP 02"))
        val queried = mutableSetOf<String>()
        val result = NotificationNodeResolver.resolveMain("Rule", groups.keys.toList(), "Proxy") { queried.add(it); groups[it] }
        assertEquals("HK 01", result?.node)
        assertEquals(setOf("Proxy", "Auto"), queried)
    }

    @Test fun mainResolutionUsesGlobalChainInGlobalMode() {
        val groups = mapOf("GLOBAL" to group("JP 02"), "Proxy" to group("HK 01"))
        assertEquals("JP 02", NotificationNodeResolver.resolveMain("Global", groups.keys.toList(), "Proxy", groups::get)?.node)
    }

    @Test fun mainResolutionNeverQueriesInDirectMode() {
        assertEquals("DIRECT", NotificationNodeResolver.resolveMain("Direct", emptyList(), "Proxy") { error("must not query") }?.node)
    }

    @Test fun globalResolvesNestedAndHiddenGroups() {
        val groups = mapOf("GLOBAL" to group("Proxy", "Proxy"), "Proxy" to group("Auto", "Auto"), "Auto" to group("HK 01"))
        val result = NotificationNodeResolver.resolve("Global", listOf("GLOBAL", "Proxy"), groups::get)
        assertEquals("HK 01", result.singleNode)
        assertEquals(1, result.entries.size)
    }

    @Test fun ruleModeDoesNotClaimASingleExitWhenGroupsDiffer() {
        val groups = mapOf("Proxy" to group("HK 01"), "Streaming" to group("JP 02"))
        val result = NotificationNodeResolver.resolve("Rule", groups.keys.toList(), groups::get)
        assertNull(result.singleNode)
        assertEquals(listOf("HK 01", "JP 02"), result.nodes)
    }

    @Test fun sameExitAcrossGroupsIsDeduplicated() {
        val groups = mapOf("Proxy" to group("HK 01"), "Streaming" to group("HK 01"), "Local" to group("DIRECT"))
        assertEquals("HK 01", NotificationNodeResolver.resolve("Rule", groups.keys.toList(), groups::get).singleNode)
    }

    @Test fun cyclicAndMissingGroupsDoNotInventANode() {
        val groups = mapOf("A" to group("B", "B"), "B" to group("A", "A"))
        val result = NotificationNodeResolver.resolve("Global", emptyList()) { groups[it] }
        assertNull(result.singleNode)
        val cycle = NotificationNodeResolver.resolve("Rule", listOf("A"), groups::get)
        assertNull(cycle.entries.single().node)
    }

    @Test fun loadBalancingIsNotReportedAsOneNode() {
        val groups = mapOf("LB" to NotificationNodeResolver.Group("LoadBalance", "HK 01", emptySet()))
        val result = NotificationNodeResolver.resolve("Rule", listOf("LB"), groups::get)
        assertTrue(result.entries.single().balanced)
        assertNull(result.singleNode)
    }

    @Test fun directModeDoesNotQueryTheCore() {
        val result = NotificationNodeResolver.resolve("Direct", emptyList()) { error("must not query") }
        assertEquals("DIRECT", result.entries.single().node)
    }

    @Test fun automaticSelectionChangesAppearOnNextSample() {
        var node = "HK 01"
        val query: (String) -> NotificationNodeResolver.Group = { group(node) }
        assertEquals("HK 01", NotificationNodeResolver.resolve("Rule", listOf("Auto"), query).singleNode)
        node = "JP 02"
        assertEquals("JP 02", NotificationNodeResolver.resolve("Rule", listOf("Auto"), query).singleNode)
    }

    @Test fun chipTruncationPreservesUnicodeCodePoints() {
        val text = NotificationNodeResolver.chipText("🇭🇰 香港 01 · 高速专线")
        assertEquals(7, text.codePointCount(0, text.length))
        assertFalse(Character.isHighSurrogate(text.last()))
        assertEquals("HK 01", NotificationNodeResolver.chipText("HK\n01"))
    }
    @org.junit.Test fun protectedGroupTakesPrecedenceOverSavedSubscriptionGroup() {
        org.junit.Assert.assertEquals("Live · 严格代理", NotificationNodeResolver.mainGroup(
            "Rule", listOf("subscription", "Live · 严格代理"), "subscription"))
    }
    @Test fun reportsFailedAutoGroupWithoutInventingANode() {
        val groups = mapOf(
            "Live · 严格代理" to group("Live · 严格自动", "Live · 严格自动"),
            "Live · 严格自动" to NotificationNodeResolver.Group("Fallback", "JP", emptySet(), unavailable = true),
        )
        val value = NotificationNodeResolver.resolveMain("Rule", groups.keys.toList(), "", groups::get)
        assertTrue(value?.unavailable == true)
        assertNull(value?.node)
    }
}
