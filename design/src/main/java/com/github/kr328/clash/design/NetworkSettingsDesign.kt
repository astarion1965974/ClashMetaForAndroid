package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiSelect
import com.github.kr328.clash.design.preference.NativeCouiSwitch
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore
import com.itos.coui.layout.Group

/** Full COUI network settings, including VPN-dependent controls and routing links. */
class NetworkSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    running: Boolean,
) : Design<NetworkSettingsDesign.Request>(context) {
    enum class Request { StartAccessControlList, StartManagedRouting, StartMobileNetwork }

    override val root: View = ComposeView(context).apply {
        setContent {
            var vpnEnabled by remember { mutableStateOf(uiStore.enableVpn) }
            ColorOsSubPage(
                title = stringResource(R.string.network),
                onBack = { context.colorOsNavigateBack() },
            ) {
                Group {
                    NativeCouiSwitch(
                        title = stringResource(R.string.route_system_traffic),
                        summary = stringResource(R.string.routing_via_vpn_service),
                        icon = R.drawable.ic_baseline_vpn_lock,
                        value = uiStore::enableVpn,
                        enabled = !running,
                        onChanged = { vpnEnabled = it },
                    )
                }
                Group(
                    title = stringResource(R.string.vpn_service_options),
                    footer = if (running) stringResource(R.string.options_unavailable) else null,
                ) {
                    val editable = !running && vpnEnabled
                    NativeCouiSwitch(
                        title = stringResource(R.string.bypass_private_network),
                        summary = stringResource(R.string.bypass_private_network_summary),
                        value = srvStore::bypassPrivateNetwork, enabled = editable,
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.dns_hijacking),
                        summary = stringResource(R.string.dns_hijacking_summary),
                        value = srvStore::dnsHijacking, enabled = editable,
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.allow_bypass),
                        summary = stringResource(R.string.allow_bypass_summary),
                        value = srvStore::allowBypass, enabled = editable,
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.allow_ipv6),
                        summary = stringResource(R.string.allow_ipv6_summary),
                        value = srvStore::allowIpv6, enabled = editable,
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.system_proxy),
                        summary = stringResource(R.string.system_proxy_summary),
                        value = srvStore::systemProxy, enabled = editable,
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.tun_stack_mode),
                        value = srvStore::tunStackMode,
                        values = listOf("system", "gvisor", "mixed", "mips"),
                        labels = listOf(
                            stringResource(R.string.tun_stack_system),
                            stringResource(R.string.tun_stack_gvisor),
                            stringResource(R.string.tun_stack_mixed),
                            stringResource(R.string.tun_stack_mips),
                        ),
                        enabled = editable,
                    )
                }
                Group {
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_title),
                        navigation = true,
                        summary = stringResource(R.string.mobile_ready),
                    ) { requests.trySend(Request.StartMobileNetwork) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.app_routing),
                        navigation = true,
                        summary = stringResource(R.string.app_routing_summary),
                    ) { requests.trySend(Request.StartAccessControlList) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.managed_routing),
                        navigation = true,
                        summary = stringResource(R.string.managed_summary),
                    ) { requests.trySend(Request.StartManagedRouting) }
                }
            }
        }
    }

}
