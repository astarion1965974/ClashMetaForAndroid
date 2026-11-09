package com.github.kr328.clash.common.constants

/**
 * Single source of truth for the DNS sites shown on the Home dashboard.
 *
 * IPC diagnostics deliberately accept only these targets, so the service and
 * the Compose dashboard MUST consume the same registry. Previous builds had
 * four hard-coded service entries; five newer sites were silently rejected.
 */
object DnsDiagnosticTargets {
    val mainland: List<String> = listOf(
        "baidu.com", "bilibili.com", "dianping.com", "douyin.com",
    )
    val overseas: List<String> = listOf("google.com", "youtube.com")
    val ai: List<String> = listOf("chatgpt.com", "grok.com", "claude.ai")

    val domains: List<String> = mainland + overseas + ai
    private val allowed = domains.toSet()

    fun supports(domain: String): Boolean = domain in allowed
}
