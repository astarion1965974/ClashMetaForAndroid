package com.github.kr328.clash.service.clash.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionQuotaTest {
    @Test fun uploadAndDownloadAreCombined() {
        assertEquals(SubscriptionQuota(40L, 100L, 40), SubscriptionQuota.from(10, 30, 100))
    }

    @Test fun overageNeverOverflowsTheDisplayedProgress() {
        assertEquals(100, SubscriptionQuota.from(Long.MAX_VALUE, 10, 20)!!.percentage)
    }

    @Test fun unlimitedOrUnknownSubscriptionsDoNotShowFakeProgress() {
        assertNull(SubscriptionQuota.from(20, 10, 0))
        assertNull(SubscriptionQuota.from(20, 10, -1))
    }

    @Test fun invalidNegativeCountersAreClamped() {
        assertEquals(SubscriptionQuota(25L, 100L, 25), SubscriptionQuota.from(-100, 25, 100))
    }
}
