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
}
