package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.DomesticAppDefaults
import java.util.*

class ServiceStore(context: Context) {
    private val preferences = PreferenceProvider.createSharedPreferencesFromContext(context)
    private val store = Store(preferences.asStoreProvider())

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    /**
     * Only first-run defaults. Detect actual legacy preferences, not the in-memory
     * AcceptAll default: any previously saved user mode OR package selection wins.
     * A complete curated package set intentionally includes not-yet-installed apps.
     */
    fun initializeDomesticBypassIfUnconfigured() {
        if (!DomesticAppDefaults.shouldInitialize(
                preferences.contains("access_control_mode"),
                preferences.contains("access_control_packages"),
            )) return
        accessControlPackages = DomesticAppDefaults.bypassPackages
        accessControlMode = AccessControlMode.DenySelected
    }

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = true
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "mips"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    var liveNotification by store.boolean(
        key = "live_notification",
        defaultValue = true
    )

    var notificationProxyGroup by store.string(
        key = "notification_proxy_group",
        defaultValue = ""
    )

    var defaultProfileSeeded by store.boolean(
        key = "default_profile_seeded",
        defaultValue = false
    )
}
