package com.github.kr328.clash.service.model

/**
 * Parser for mnixry/direct-android-ruleset's periodically generated Android
 * application list (AGPL-3.0, data fetched by the user on demand).
 * Source: https://github.com/mnixry/direct-android-ruleset
 *
 * This parser deliberately accepts ONLY exact PROCESS-NAME entries from the
 * APP (not GAME) file. It NEVER interprets PROCESS-NAME-REGEX or prefixes, and
 * avoids routing common foreign apps outside VPN even if a store lists them.
 * Downloading or applying this list is explicitly initiated by the user.
 */
object DomesticAppRuleSource {
    const val URL =
        "https://raw.githubusercontent.com/mnixry/direct-android-ruleset/rules/%40Merged/APP.mutated.yaml"
    const val MAX_BYTES = 600_000
    private const val MAX_ENTRIES = 8_000
    private const val MIN_ENTRIES = 400

    private val processName = Regex(
        """^\s*-\s*PROCESS-NAME,\s*([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)(?:\s*(?:#.*)?)?$"""
    )
    private val foreignPrefixes = listOf(
        "com.google.", "com.microsoft.", "com.openai.", "com.anthropic.",
        "com.discord.", "org.telegram.", "com.twitter.", "com.x.",
        "org.mozilla.", "com.facebook.", "com.instagram.", "com.whatsapp.",
        "com.spotify.", "com.netflix.", "com.reddit.", "com.amazon.",
        "com.github.", "com.cloudflare.", "com.brave.browser",
    )
    private val foreignIds = setOf(
        "com.android.vending", "com.android.chrome", "org.telegram.messenger",
        "com.openai.chatgpt", "com.google.android.youtube", "com.google.android.gm",
        "com.microsoft.emmx", "com.discord",
    )

    fun parse(text: String): Set<String> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Domestic app rule file exceeds size limit"
        }
        require(text.lineSequence().firstOrNull()?.trim() == "payload:") {
            "Invalid rule provider response"
        }
        val packages = linkedSetOf<String>()
        for (line in text.lineSequence()) {
            val id = processName.matchEntire(line)?.groupValues?.get(1) ?: continue
            if (id.length > 160 || id in foreignIds ||
                foreignPrefixes.any { id.startsWith(it) }) continue
            packages.add(id)
            require(packages.size <= MAX_ENTRIES) { "Too many package entries" }
        }
        require(packages.size >= MIN_ENTRIES &&
            "com.tencent.mm" in packages && "com.eg.android.AlipayGphone" in packages) {
            "The provider returned an incomplete or unexpected rule list"
        }
        return packages
    }
}
