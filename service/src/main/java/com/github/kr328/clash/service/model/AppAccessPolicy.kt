package com.github.kr328.clash.service.model

object AppAccessPolicy {
    enum class Selection { All, None, Invert }

    fun usesClash(mode: AccessControlMode, selected: Boolean): Boolean = when (mode) {
        AccessControlMode.AcceptAll -> true
        AccessControlMode.AcceptSelected -> selected
        AccessControlMode.DenySelected -> !selected
    }

    // Search and filters must never erase selections outside the displayed result.
    fun select(current: Set<String>, visible: Set<String>, action: Selection): Set<String> = when (action) {
        Selection.All -> current + visible
        Selection.None -> current - visible
        Selection.Invert -> (current - visible) + (visible - current)
    }
}
