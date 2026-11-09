package com.github.kr328.clash.service.model

import org.junit.Assert.*
import org.junit.Test

class DomesticAppCatalogTest {
    @Test fun parsesOnlyExactAndroidProcessNames() {
        val yaml = """
            payload:
              - PROCESS-NAME,com.tencent.mm # WeChat
              - PROCESS-NAME,com.xingin.xhs # Xiaohongshu
              - PROCESS-NAME,com.tencent.mm # duplicate
              - PROCESS-NAME,com.eg.android.AlipayGphone
              - PROCESS-NAME-REGEX,^com\.tencent\..*
              - PROCESS-NAME,com.google.android.youtube
              - PROCESS-NAME,com.bytedance.lark
              - PROCESS-NAME,com.bilibili.app.in
              - PROCESS-NAME,com.openai.chatgpt
              - PROCESS-NAME,com.tencent.mm:push
              - DOMAIN-SUFFIX,example.cn
              - PROCESS-NAME,com.microsoft.teams
              - PROCESS-NAME,com.android.vending
        """.trimIndent()
        assertEquals(
            setOf("com.tencent.mm", "com.xingin.xhs", "com.eg.android.AlipayGphone"),
            DomesticAppCatalog.parseProcessPackages(yaml),
        )
    }

    @Test fun onlineCatalogIsAppliedOnlyToInstalledMatchingApps() {
        val text = """
            payload:
              - PROCESS-NAME,com.tencent.mm
              - PROCESS-NAME,com.eg.android.AlipayGphone
              - PROCESS-NAME,com.xingin.xhs
              - PROCESS-NAME,com.openai.chatgpt
        """.trimIndent()
        assertEquals(
            setOf("com.tencent.mm"),
            DomesticAppCatalog.matchInstalled(
                text, setOf("com.tencent.mm", "com.openai.chatgpt", "com.other.app"),
            ),
        )
    }

    @Test fun expandedCuratedPresetIsSubstantiallyLargerAndExcludesForeignApps() {
        val apps = DomesticAppDefaults.bypassPackages
        assertTrue("Use 150+ individually validated domestic app package IDs", apps.size >= 170)
        assertTrue(apps.containsAll(setOf(
            "com.tencent.mm", "com.eg.android.AlipayGphone",
            "com.icbc", "com.chinamworld.main", "cn.gov.tax.its",
            "com.xingin.xhs", "com.sankuai.meituan",
            "com.mt.mtxx.mtxx", "com.coolapk.market",
        )))
        assertFalse(apps.contains("com.openai.chatgpt"))
        assertFalse(apps.contains("com.android.chrome"))
        assertFalse(apps.contains("com.google.android.youtube"))
    }
}
