package com.github.kr328.clash.service.model

import org.junit.Assert.*
import org.junit.Test

class AppAccessPolicyTest {
    @Test fun allAppsAreCapturedEvenWithRememberedSelection() {
        assertTrue(AppAccessPolicy.usesClash(AccessControlMode.AcceptAll, false))
        assertTrue(AppAccessPolicy.usesClash(AccessControlMode.AcceptAll, true))
    }
    @Test fun allowAndBypassModesHaveOppositeMeanings() {
        assertTrue(AppAccessPolicy.usesClash(AccessControlMode.AcceptSelected, true))
        assertFalse(AppAccessPolicy.usesClash(AccessControlMode.AcceptSelected, false))
        assertFalse(AppAccessPolicy.usesClash(AccessControlMode.DenySelected, true))
        assertTrue(AppAccessPolicy.usesClash(AccessControlMode.DenySelected, false))
    }
    @Test fun selectingSearchResultsPreservesHiddenApps() {
        assertEquals(setOf("hidden", "found"), AppAccessPolicy.select(setOf("hidden"), setOf("found"), AppAccessPolicy.Selection.All))
    }
    @Test fun clearingSearchResultsPreservesHiddenApps() {
        assertEquals(setOf("hidden"), AppAccessPolicy.select(setOf("hidden", "found"), setOf("found"), AppAccessPolicy.Selection.None))
    }
    @Test fun invertOnlyTogglesVisibleResults() {
        assertEquals(setOf("hidden", "new"), AppAccessPolicy.select(setOf("hidden", "old"), setOf("old", "new"), AppAccessPolicy.Selection.Invert))
    }
    @Test fun emptySearchResultCannotClearSelection() {
        AppAccessPolicy.Selection.values().forEach { action -> assertEquals(setOf("saved"), AppAccessPolicy.select(setOf("saved"), emptySet(), action)) }
    }
    @Test fun selectingBypassFromAcceptAllDoesNotActivateStaleSelection() {
        val previous = setOf("stale-from-older-mode")
        val first = AppAccessPolicy.withRouting(
            AccessControlMode.AcceptAll, previous, "browser", throughClash = false,
        )
        assertEquals(AccessControlMode.DenySelected, first.mode)
        assertEquals(setOf("browser"), first.selected)
        assertFalse(AppAccessPolicy.usesClash(first.mode, "browser" in first.selected))
        assertTrue(AppAccessPolicy.usesClash(first.mode, "chat" in first.selected))
        assertEquals(previous, AppAccessPolicy.withRouting(
            AccessControlMode.AcceptAll, previous, "browser", throughClash = true,
        ).selected)
    }

    @Test fun explicitChoicesRespectBothLegacyPolicyModes() {
        val allowed = AppAccessPolicy.withRouting(
            AccessControlMode.AcceptSelected, setOf("chat"), "browser", true,
        )
        assertEquals(AccessControlMode.AcceptSelected, allowed.mode)
        assertEquals(setOf("chat", "browser"), allowed.selected)
        val bypassed = AppAccessPolicy.withRouting(
            allowed.mode, allowed.selected, "chat", false,
        )
        assertEquals(setOf("browser"), bypassed.selected)
        val deny = AppAccessPolicy.withRouting(
            AccessControlMode.DenySelected, setOf("music"), "browser", false,
        )
        assertEquals(setOf("music", "browser"), deny.selected)
        assertEquals(setOf("music"), AppAccessPolicy.withRouting(
            deny.mode, deny.selected, "browser", true,
        ).selected)
    }

    @Test fun otherAppsAlwaysRetainExistingRouteWhenOneAppChanges() {
        val modes = listOf(
            AccessControlMode.AcceptAll,
            AccessControlMode.AcceptSelected,
            AccessControlMode.DenySelected,
        )
        for (mode in modes) {
            val selected = setOf("existing", "outdated")
            for (through in listOf(true, false)) {
                val previous = listOf("existing", "other").associateWith {
                    AppAccessPolicy.usesClash(mode, it in selected)
                }
                val next = AppAccessPolicy.withRouting(mode, selected, "target", through)
                for ((app, expected) in previous) {
                    assertEquals(expected, AppAccessPolicy.usesClash(
                        next.mode, app in next.selected,
                    ))
                }
            }
        }
    }

}
