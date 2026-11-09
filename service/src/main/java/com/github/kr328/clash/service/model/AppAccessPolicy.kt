package com.github.kr328.clash.service.model

object AppAccessPolicy {
    enum class Selection { All, None, Invert }

    fun usesClash(mode: AccessControlMode, selected: Boolean): Boolean = when (mode) {
        AccessControlMode.AcceptAll -> true
        AccessControlMode.AcceptSelected -> selected
        AccessControlMode.DenySelected -> !selected
    }

    /**
     * Represent an explicit per-app decision using the existing VPN allow/deny
     * model, without changing the routing of any OTHER installed application.
     *
     * AcceptAll may contain stale selections from an earlier mode; those must
     * never become active when the first bypass exception is created.
     */
    data class RoutingState(val mode: AccessControlMode, val selected: Set<String>)

    fun withRouting(
        mode: AccessControlMode,
        selected: Set<String>,
        packageName: String,
        throughClash: Boolean,
    ): RoutingState = when (mode) {
        AccessControlMode.AcceptAll -> if (throughClash) {
            RoutingState(mode, selected)
        } else {
            RoutingState(AccessControlMode.DenySelected, setOf(packageName))
        }
        AccessControlMode.AcceptSelected -> RoutingState(
            mode, if (throughClash) selected + packageName else selected - packageName,
        )
        AccessControlMode.DenySelected -> RoutingState(
            mode, if (throughClash) selected - packageName else selected + packageName,
        )
    }

    // Search and filters must never erase selections outside the displayed result.
    fun select(current: Set<String>, visible: Set<String>, action: Selection): Set<String> = when (action) {
        Selection.All -> current + visible
        Selection.None -> current - visible
        Selection.Invert -> (current - visible) + (visible - current)
    }
}
