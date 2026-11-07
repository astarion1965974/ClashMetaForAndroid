package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.preference.NativeCouiNullablePort
import com.github.kr328.clash.design.preference.NativeCouiNullableString
import com.github.kr328.clash.design.preference.NativeCouiStringList
import com.github.kr328.clash.design.preference.NativeCouiStringMap
import com.github.kr328.clash.design.preference.NativeCouiSelect
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group

/**
 * All 34 override controls rendered as native COUI preferences.
 * The mutable ConfigurationOverride instance is still persisted by the Activity's defer hook.
 */
class OverrideSettingsDesign(
    context: Context,
    configuration: ConfigurationOverride,
) : Design<OverrideSettingsDesign.Request>(context) {
    enum class Request { ResetOverride }

    private var revision by mutableIntStateOf(0)

    override val root: View = ComposeView(context).apply {
        setContent {
            revision
            val dnsEditable = configuration.dns.enable != false
            ColorOsSubPage(
                title = stringResource(R.string.override),
                onBack = { (context as? AppCompatActivity)?.onSupportNavigateUp() },
                actions = {
                    ToolbarIcon(
                        ImageVector.vectorResource(R.drawable.ic_baseline_replay),
                        stringResource(R.string.reset),
                    ) { requestClear() }
                },
            ) {
                Group(title = stringResource(R.string.general)) {
                    NativeCouiNullablePort(
                        title = stringResource(R.string.http_port), property = configuration::httpPort,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullablePort(
                        title = stringResource(R.string.socks_port), property = configuration::socksPort,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullablePort(
                        title = stringResource(R.string.redirect_port), property = configuration::redirectPort,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullablePort(
                        title = stringResource(R.string.tproxy_port), property = configuration::tproxyPort,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullablePort(
                        title = stringResource(R.string.mixed_port), property = configuration::mixedPort,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.authentication), property = configuration::authentication,
                        placeholder = stringResource(R.string.dont_modify),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.allow_lan), value = configuration::allowLan,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.ipv6), value = configuration::ipv6,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.bind_address), property = configuration::bindAddress,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.default_),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.external_controller), property = configuration::externalController,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.default_),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.external_controller_tls), property = configuration::externalControllerTLS,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.default_),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.allow_origins), property = configuration.externalControllerCors::allowOrigins,
                        placeholder = stringResource(R.string.dont_modify),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.allow_private_network), value = configuration.externalControllerCors::allowPrivateNetwork,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.secret), property = configuration::secret,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.default_),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.mode), value = configuration::mode,
                        values = listOf<TunnelState.Mode?>(null, TunnelState.Mode.Direct, TunnelState.Mode.Global, TunnelState.Mode.Rule),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.direct_mode), stringResource(R.string.global_mode), stringResource(R.string.rule_mode)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.log_level), value = configuration::logLevel,
                        values = listOf<LogMessage.Level?>(null, LogMessage.Level.Info, LogMessage.Level.Warning, LogMessage.Level.Error, LogMessage.Level.Debug, LogMessage.Level.Silent),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.info), stringResource(R.string.warning), stringResource(R.string.error), stringResource(R.string.debug), stringResource(R.string.silent)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringMap(
                        title = stringResource(R.string.hosts), property = configuration::hosts,
                        placeholder = stringResource(R.string.dont_modify),
                        onChanged = { revision++ },
                    )
                }
                Group(title = stringResource(R.string.dns)) {
                    NativeCouiSelect(
                        title = stringResource(R.string.strategy), value = configuration.dns::enable,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.force_enable), stringResource(R.string.use_built_in)),
                        onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.prefer_h3), value = configuration.dns::preferH3,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.listen), property = configuration.dns::listen,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.disabled),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.append_system_dns), value = configuration.app::appendSystemDns,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.ipv6), value = configuration.dns::ipv6,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.use_hosts), value = configuration.dns::useHosts,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.enhanced_mode), value = configuration.dns::enhancedMode,
                        values = listOf<ConfigurationOverride.DnsEnhancedMode?>(null, ConfigurationOverride.DnsEnhancedMode.None, ConfigurationOverride.DnsEnhancedMode.FakeIp, ConfigurationOverride.DnsEnhancedMode.Mapping),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.disabled), stringResource(R.string.fakeip), stringResource(R.string.mapping)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.name_server), property = configuration.dns::nameServer,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.fallback), property = configuration.dns::fallback,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.default_name_server), property = configuration.dns::defaultServer,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.fakeip_filter), property = configuration.dns::fakeIpFilter,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.fakeip_filter_mode), value = configuration.dns::fakeIPFilterMode,
                        values = listOf<ConfigurationOverride.FilterMode?>(null, ConfigurationOverride.FilterMode.BlackList, ConfigurationOverride.FilterMode.WhiteList, ConfigurationOverride.FilterMode.Rule),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.blacklist), stringResource(R.string.whitelist), stringResource(R.string.rule)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiSelect(
                        title = stringResource(R.string.geoip_fallback), value = configuration.dns.fallbackFilter::geoIp,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiNullableString(
                        title = stringResource(R.string.geoip_fallback_code), property = configuration.dns.fallbackFilter::geoIpCode,
                        placeholder = stringResource(R.string.dont_modify), emptyLabel = stringResource(R.string.raw_cn),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.domain_fallback), property = configuration.dns.fallbackFilter::domain,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringList(
                        title = stringResource(R.string.ipcidr_fallback), property = configuration.dns.fallbackFilter::ipcidr,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                    Divider()
                    NativeCouiStringMap(
                        title = stringResource(R.string.name_server_policy), property = configuration.dns::nameserverPolicy,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = dnsEditable, onChanged = { revision++ },
                    )
                }
            }
        }
    }

    suspend fun requestResetConfirm(): Boolean =
        context.requestCouiConfirm(
            R.string.reset_override_settings, R.string.reset_override_settings_message,
        )

    fun requestClear() { requests.trySend(Request.ResetOverride) }
}
