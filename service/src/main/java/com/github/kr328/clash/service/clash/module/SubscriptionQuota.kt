package com.github.kr328.clash.service.clash.module

import kotlin.math.roundToInt

/** Profile subscription allowance (upload + download), not the live session total. */
internal data class SubscriptionQuota(val used: Long, val total: Long, val percentage: Int) {
    companion object {
        fun from(upload: Long, download: Long, total: Long): SubscriptionQuota? {
            if (total <= 0L) return null
            val up = upload.coerceAtLeast(0L)
            val down = download.coerceAtLeast(0L)
            val used = if (up > Long.MAX_VALUE - down) Long.MAX_VALUE else up + down
            val percentage = (used.toDouble() / total.toDouble() * 100.0)
                .roundToInt().coerceIn(0, 100)
            return SubscriptionQuota(used, total, percentage)
        }
    }
}
