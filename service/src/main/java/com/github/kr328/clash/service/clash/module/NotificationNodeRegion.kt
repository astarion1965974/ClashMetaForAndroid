package com.github.kr328.clash.service.clash.module

import java.text.Normalizer
import java.util.Locale

/** Region advertised by the selected node's name; never a claim about a measured exit IP. */
internal object NotificationNodeRegion {
    private val regions = Locale.getISOCountries().toSet()
    private data class Alias(val code: String, val pattern: Regex)
    private data class Match(val code: String, val range: IntRange)

    private fun alias(code: String, text: String): Alias {
        val escaped = Regex.escape(text)
        // Latin country names must not match inside unrelated words. Node suffixes such as JP01 are allowed.
        val expression = if (text.all { it.code < 128 }) "(?<![a-zA-Z0-9])$escaped(?![a-zA-Z])" else escaped
        return Alias(code, Regex(expression, RegexOption.IGNORE_CASE))
    }

    private val names: List<Alias> by lazy {
        val all = mutableSetOf<Pair<String, String>>()
        for (code in regions) {
            for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)) {
                all += code to Locale("", code).getDisplayCountry(locale)
            }
        }
        val extra = mapOf(
            "HK" to listOf("香港", "中国香港", "中國香港", "China Hong Kong", "Hong Kong", "HongKong"),
            "MO" to listOf("澳门", "澳門", "中国澳门", "中國澳門", "Macau", "Macao"),
            "TW" to listOf("台湾", "台灣", "臺灣", "中国台湾", "中國台灣", "中國臺灣", "台北", "臺北", "Taiwan"),
            "JP" to listOf("日本", "东京", "東京", "大阪", "Japan"),
            "SG" to listOf("新加坡", "狮城", "獅城", "Singapore"),
            "US" to listOf("美国", "美國", "美西", "美东", "美東", "洛杉矶", "洛杉磯", "西雅图", "西雅圖", "纽约", "紐約", "United States", "United States of America"),
            "KR" to listOf("韩国", "韓國", "首尔", "首爾", "South Korea", "SouthKorea"),
            "KP" to listOf("朝鲜", "朝鮮", "North Korea", "NorthKorea"),
            "GB" to listOf("英国", "英國", "伦敦", "倫敦", "United Kingdom", "Britain"),
            "DE" to listOf("德国", "德國", "法兰克福", "法蘭克福", "Germany"),
            "AU" to listOf("澳大利亚", "澳大利亞", "澳洲", "悉尼", "雪梨", "Australia"),
            "CA" to listOf("加拿大", "温哥华", "溫哥華", "Canada"),
        )
        extra.forEach { (code, values) -> values.forEach { all += code to it } }
        all.filter { it.second.isNotBlank() }.map { (code, text) -> alias(code, text) }
    }

    private val abbreviations by lazy {
        // Common subscription labels, not arbitrary substrings or network lookups.
        val codes = "HK MO TW JP SG US KR KP GB CN CA AU DE FR NL RU IN TR VN TH ID MY PH IT ES CH SE FI NO DK IE PL BR NZ AE ZA IL MX AR CL AT BE PT UA RO HU CZ GR IS LU".split(' ')
        val pairs = codes.map { it to it }.toMutableList()
        pairs += listOf("GB" to "UK", "US" to "USA", "HK" to "HKG", "JP" to "JPN", "SG" to "SGP", "TW" to "TWN", "KR" to "KOR", "RU" to "RUS", "CN" to "CHN")
        pairs.map { (code, text) -> alias(code, text) }
    }

    fun regionCode(node: String): String? {
        val text = Normalizer.normalize(node, Normalizer.Form.NFKC).replace('_', ' ')
        val points = text.codePoints().toArray()
        val flags = mutableSetOf<String>()
        var i = 0
        while (i + 1 < points.size) {
            val first = points[i] - 0x1F1E6
            val second = points[i + 1] - 0x1F1E6
            if (first in 0..25 && second in 0..25) {
                val code = "${('A'.code + first).toChar()}${('A'.code + second).toChar()}"
                if (code in regions) flags += code
                i += 2
            } else i++
        }
        if (flags.size > 1) return null
        val named = namedRegions(text)
        val flag = flags.singleOrNull()
        if (flag != null) {
            // Some subscriptions use the CN flag for Hong Kong, Macao or Taiwan nodes.
            if (flag == "CN" && named.size == 1 && named.single() in setOf("HK", "MO", "TW")) return named.single()
            return flag
        }
        if (named.isNotEmpty()) return named.singleOrNull()
        val abbreviated = abbreviations.flatMap { item ->
            item.pattern.findAll(text).filterNot { match ->
                // GB may be a traffic quota rather than a country code.
                match.value.equals("GB", ignoreCase = true) && Regex("\\d\\s*$").containsMatchIn(text.take(match.range.first))
            }.map { item.code }.toList()
        }.toSet()
        return abbreviated.singleOrNull()
    }

    private fun namedRegions(text: String): Set<String> {
        val matches = names.flatMap { item -> item.pattern.findAll(text).map { Match(item.code, it.range) }.toList() }
        return matches.filterNot { smaller ->
            matches.any { larger -> larger.range.first <= smaller.range.first && larger.range.last >= smaller.range.last && larger.range != smaller.range }
        }.map { it.code }.toSet()
    }

    fun regionLabel(node: String, locale: Locale = Locale.getDefault()): String? {
        val code = regionCode(node) ?: return null
        val name = if (locale.language == "zh") when (code) {
            "HK" -> "香港"
            "MO" -> "澳门"
            "TW" -> "台湾"
            else -> Locale("", code).getDisplayCountry(locale)
        } else when (code) {
            "US" -> "USA"
            "GB" -> "UK"
            "HK" -> "HK"
            "MO" -> "Macao"
            "TW" -> "Taiwan"
            else -> Locale("", code).getDisplayCountry(locale)
        }
        // Keep a complete country/region label within the platform's compact-text limit.
        return name.takeIf { it.isNotBlank() && it.codePointCount(0, it.length) <= 7 } ?: code
    }
}
