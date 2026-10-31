package com.github.kr328.clash.service.clash.module

/** Resolves configured selections, not a claim that every rule uses the same exit. */
internal object NotificationNodeResolver {
    data class Group(val type: String, val selected: String, val childGroups: Set<String>)
    data class Entry(val group: String, val node: String?, val balanced: Boolean = false)
    data class Selection(val entries: List<Entry>) {
        val nodes: List<String>
            get() = entries.mapNotNull { it.node }
                .filterNot { it in setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE") }
                .distinct()

        // A load-balancer must never be presented as a single fixed exit node.
        val singleNode: String?
            get() = nodes.singleOrNull().takeIf { entries.none { it.balanced || it.node == null } }
    }

    fun resolve(mode: String, names: List<String>, query: (String) -> Group?): Selection {
        if (mode == "Direct") return Selection(listOf(Entry("DIRECT", "DIRECT")))
        val cache = mutableMapOf<String, Group?>()
        fun group(name: String): Group? = cache.getOrPut(name) { query(name) }

        fun selected(name: String): Entry {
            var current = name
            val visited = mutableSetOf<String>()
            repeat(32) {
                if (!visited.add(current)) return Entry(name, null)
                val value = group(current) ?: return Entry(name, null)
                if (value.type == "LoadBalance" || value.type == "Relay") {
                    return Entry(name, null, balanced = true)
                }
                if (value.selected.isBlank()) return Entry(name, null)
                if (value.selected !in value.childGroups) return Entry(name, value.selected)
                current = value.selected
            }
            return Entry(name, null)
        }

        val visible = if (mode == "Global") listOf("GLOBAL") else names.filter { it != "GLOBAL" }
        return Selection(visible.distinct().map(::selected))
    }

    /** Platform chips are narrow. Preserve whole Unicode code points, including flags. */
    fun chipText(text: String): String {
        val clean = text.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim()
        val count = clean.codePointCount(0, clean.length)
        return if (count <= 7) clean else clean.substring(0, clean.offsetByCodePoints(0, 6)) + "…"
    }
}
