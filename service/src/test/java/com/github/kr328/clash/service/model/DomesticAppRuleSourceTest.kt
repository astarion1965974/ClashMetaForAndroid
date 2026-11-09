package com.github.kr328.clash.service.model

import org.junit.Assert.*
import org.junit.Test

class DomesticAppRuleSourceTest {
    private fun validPayload(extra: String = ""): String {
        val catalog = (1..550).joinToString("\n") { "  - PROCESS-NAME,cn.example.domesticapp$it # 国内应用" }
        return "payload:\n  - PROCESS-NAME,com.tencent.mm # 微信\n" +
            "  - PROCESS-NAME,com.eg.android.AlipayGphone # 支付宝\n" +
            catalog + "\n" + extra
    }

    @Test fun parsesOnlyExactPackageIdentifiersNeverProviderRegex() {
        val parsed = DomesticAppRuleSource.parse(
            validPayload(
                "  - PROCESS-NAME-REGEX,^com\\.tencent\\..*\n" +
                "  - PROCESS-NAME,com.google.android.youtube # bogus\n" +
                "  - PROCESS-NAME,com.openai.chatgpt # bogus\n" +
                "  - PROCESS-NAME,org.mozilla.firefox\n" +
                "  - PROCESS-NAME,com.microsoft.emmx\n" +
                "  - DOMAIN-SUFFIX,example.com\n" +
                "  - PROCESS-NAME,com.tencent.mobileqq # QQ\n",
            ),
        )
        assertEquals(553, parsed.size)
        assertTrue("com.tencent.mm" in parsed)
        assertTrue("com.tencent.mobileqq" in parsed)
        assertTrue("cn.example.domesticapp550" in parsed)
        assertFalse("com.google.android.youtube" in parsed)
        assertFalse("com.openai.chatgpt" in parsed)
        assertFalse("org.mozilla.firefox" in parsed)
        assertFalse("com.microsoft.emmx" in parsed)
        assertFalse(parsed.any { it.contains("*") || it.startsWith("^") })
    }

    @Test fun rejectsIncompleteOrUntrustedProviderResponses() {
        for (source in listOf(
            "<html>CloudFlare error</html>",
            "payload:\n - PROCESS-NAME,com.tencent.mm",
            "payload:\n - PROCESS-NAME-REGEX,^com\\.tencent\\..*",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                DomesticAppRuleSource.parse(source)
            }
        }
    }

    @Test fun sourceHasBoundedSizeAndDiscardsDuplicatePackageIds() {
        val parsed = DomesticAppRuleSource.parse(
            validPayload("  - PROCESS-NAME,com.tencent.mm # repeat\n"),
        )
        assertEquals(552, parsed.size)
        assertThrows(IllegalArgumentException::class.java) {
            DomesticAppRuleSource.parse("payload:\n" + "x".repeat(
                DomesticAppRuleSource.MAX_BYTES + 1,
            ))
        }
    }
}
