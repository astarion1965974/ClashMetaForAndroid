package com.github.kr328.clash.service.model

import org.junit.Assert.*
import org.junit.Test

class DomesticAppDefaultsTest {
    @Test fun defaultsApplyOnlyWhenNoAccessControlPreferenceExists() {
        assertTrue(DomesticAppDefaults.shouldInitialize(false, false))
        assertFalse(DomesticAppDefaults.shouldInitialize(true, false))
        assertFalse(DomesticAppDefaults.shouldInitialize(false, true))
        assertFalse(DomesticAppDefaults.shouldInitialize(true, true))
    }

    @Test fun defaultPackageIdsAreExactAndDoNotCaptureForeignApps() {
        val ids = DomesticAppDefaults.bypassPackages
        assertTrue(ids.containsAll(listOf(
            "com.tencent.mm", "com.eg.android.AlipayGphone",
            "com.ss.android.ugc.aweme", "com.dianping.v1",
            "com.xingin.xhs", "com.autonavi.minimap", "tv.danmaku.bili",
        )))
        assertFalse(ids.contains("com.openai.chatgpt"))
        assertFalse(ids.contains("com.google.android.youtube"))
        assertFalse(ids.contains("com.google.android.gm"))
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() && !it.contains('*') && !it.endsWith('.') })
    }

    @Test fun futureInstallDefaultsAndExplicitThroughOverridesRemainPossible() {
        val saved = AppAccessPolicy.withRouting(
            AccessControlMode.DenySelected,
            DomesticAppDefaults.bypassPackages,
            "com.tencent.mm",
            throughClash = true,
        )
        assertFalse("Manually allowed WeChat should no longer be bypassed",
            "com.tencent.mm" in saved.selected)
        assertTrue("Future Douyin installs retain their default bypass",
            "com.ss.android.ugc.aweme" in saved.selected)
        assertTrue("Unlisted ChatGPT remains routed through Clash",
            AppAccessPolicy.usesClash(saved.mode, "com.openai.chatgpt" in saved.selected))
    }
}
