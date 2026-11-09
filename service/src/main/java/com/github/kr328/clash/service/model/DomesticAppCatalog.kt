package com.github.kr328.clash.service.model

/**
 * Parser for the upstream Android PROCESS-NAME catalog.
 *
 * Source: https://github.com/mnixry/direct-android-ruleset
 * Rules branch: @Merged/APP.mutated.yaml (AGPL-3.0).
 *
 * Explicit user-triggered updates only: never fetch this at VPN startup.
 * We match only *installed* app IDs and never apply broad process-name regexes.
 * Treat external lists as untrusted; do not use them to bypass known foreign apps.
 */
object DomesticAppCatalog {
    const val sourceUrl =
        "https://raw.githubusercontent.com/mnixry/direct-android-ruleset/refs/heads/rules/@Merged/APP.mutated.yaml"

    private val exactRule = Regex(
        """^\s*-\s*PROCESS-NAME,([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)\s*(?:#.*)?$""",
    )
    private val excludedPrefixes = listOf(
        "com.google.", "com.microsoft.", "com.openai.", "com.anthropic.",
        "com.discord.", "org.telegram.", "org.mozilla.", "com.facebook.",
        "com.whatsapp.", "com.amazon.", "com.netflix.", "com.spotify.",
    )
    private val excludedExact = setOf(
        "com.android.chrome", "com.android.vending", "com.android.browser",
        "com.bytedance.lark", // Lark (international), not domestic Feishu
        "com.bilibili.app.in", // Bilibili international
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android", "com.reddit.frontpage",
    )

    fun parseProcessPackages(yaml: String): Set<String> =
        yaml.lineSequence()
            .mapNotNull { exactRule.matchEntire(it)?.groupValues?.get(1) }
            .filterNot { it in excludedExact || excludedPrefixes.any(it::startsWith) }
            .toSet()

    /**
     * Only installed package IDs are imported so a 3,000-entry upstream file
     * cannot turn into thousands of VPN Builder/SharedPreferences entries.
     */
    fun matchInstalled(yaml: String, installed: Set<String>): Set<String> =
        parseProcessPackages(yaml).intersect(installed)
}
