package com.github.kr328.clash.design

import com.github.kr328.clash.common.constants.DnsDiagnosticTargets

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** Snapshot of user-initiated third-party checks, NEVER a claimed leak pass/fail.
 * The app's own outbound route can differ from browser/VPN-app routes.
 */
internal data class HomePrivacyResult(
    val ippureIp: String = "",
    val fraudScore: Int? = null,
    val isp: String = "",
    val country: String = "",
    val residential: Boolean? = null,
    val ipleakIp: String = "",
    val ipleakIsp: String = "",
    val dnsResolvers: List<String> = emptyList(),
    val ippureError: String = "",
    val ipleakError: String = "",
    val dnsError: String = "",
)

internal data class HomeQuota(val total: Long, val used: Long, val remaining: Long)
internal data class HomeDnsResult(
    val domain: String,
    val address: String,
    val error: String,
    val millis: Long,
    val dnsRoute: String = "unknown",
    val dnsProxy: String = "",
    val dnsUpstream: String = "",
    val dnsSource: String = "",
)

/** One shared target registry: MainActivity probes the exact sites rendered on Home. */
object HomeDnsTargets {
    val mainland: List<String> = DnsDiagnosticTargets.mainland
    val overseas: List<String> = DnsDiagnosticTargets.overseas
    val ai: List<String> = DnsDiagnosticTargets.ai
    val domains: List<String> = DnsDiagnosticTargets.domains
}

@Composable
private fun bytes(value: Long): String = Formatter.formatFileSize(LocalContext.current, value.coerceAtLeast(0))

@Composable
internal fun HomeConnectionCard(running: Boolean, paused: Boolean, profile: String?, toggle: () -> Unit) {
    val connected = running && !paused
    val colors = CouiTheme.colorScheme
    val label = stringResource(when { paused -> R.string.mobile_home_paused; running -> R.string.coui_home_connected; else -> R.string.coui_home_disconnected })
    val action = stringResource(when { paused -> R.string.mobile_home_stop; running -> R.string.live_disconnect; else -> R.string.live_connect })
    Group {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).background(if (connected) Color(0xff16c875) else colors.onSurfaceVariant, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(text = label, kind = CouiTextKind.PreferenceTitle)
                Text(text = profile ?: stringResource(R.string.no_profile_selected), kind = CouiTextKind.PreferenceSummary,
                    color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.testTag("${LocalContext.current.packageName}:id/toggle_view")
                .heightIn(min = 48.dp).background(colors.accent, CircleShape)
                .clickable(role = Role.Button, onClick = toggle).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center) {
                Text(text = action, kind = CouiTextKind.PreferenceTitle, color = colors.onAccent)
            }
        }
    }
}

@Composable
internal fun HomeQuotaCard(quota: HomeQuota?) {
    val colors = CouiTheme.colorScheme
    Group {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.coui_quota_title), kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
                Text(text = quota?.let { bytes(it.remaining) } ?: "—", kind = CouiTextKind.PageTitle)
                Text(text = quota?.let { stringResource(R.string.coui_quota_detail, bytes(it.used), bytes(it.total)) }
                    ?: stringResource(R.string.coui_quota_missing), kind = CouiTextKind.Description,
                    color = colors.onSurfaceVariant)
            }
            val fraction = quota?.let { (it.remaining.toDouble() / it.total.toDouble()).toFloat().coerceIn(0f, 1f) } ?: 0f
            val track = colors.outlineVariant
            Canvas(Modifier.size(90.dp)) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (fraction > 0f) drawArc(Color(0xff19cc79), -90f, 360f * fraction, false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

private data class DnsWebsite(
    val domain: String,
    @StringRes val name: Int,
    @DrawableRes val icon: Int? = null,
    val symbol: String = "",
    val tint: Color,
)

private val mainlandSites = listOf(
    DnsWebsite("baidu.com", R.string.coui_dns_site_baidu, R.drawable.ic_dns_site_baidu, tint=Color(0xff4267E9)),
    DnsWebsite("bilibili.com", R.string.coui_dns_site_bilibili, R.drawable.ic_dns_site_bilibili, tint=Color(0xff00A1D6)),
    DnsWebsite("dianping.com", R.string.coui_dns_site_dianping, symbol="评", tint=Color(0xffFF7134)),
    DnsWebsite("douyin.com", R.string.coui_dns_site_douyin, R.drawable.ic_dns_site_douyin, tint=Color(0xff39C6C4)),
)
private val overseasSites = listOf(
    DnsWebsite("google.com", R.string.coui_dns_site_google, R.drawable.ic_dns_site_google, tint=Color(0xff4285F4)),
    DnsWebsite("youtube.com", R.string.coui_dns_site_youtube, R.drawable.ic_dns_site_youtube, tint=Color(0xffFF0033)),
)
private val aiSites = listOf(
    DnsWebsite("chatgpt.com", R.string.coui_dns_site_chatgpt, R.drawable.ic_dns_site_chatgpt, tint=Color(0xff10A37F)),
    DnsWebsite("grok.com", R.string.coui_dns_site_grok, R.drawable.ic_dns_site_grok, tint=Color(0xff707989)),
    DnsWebsite("claude.ai", R.string.coui_dns_site_claude, R.drawable.ic_dns_site_claude, tint=Color(0xffD97757)),
)

@Composable
private fun DnsSiteIcon(site: DnsWebsite) {
    Box(
        Modifier.size(36.dp).background(site.tint.copy(alpha=0.12f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (site.icon != null) {
            Image(
                painter = painterResource(site.icon),
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                colorFilter = ColorFilter.tint(site.tint),
            )
        } else {
            Text(text = site.symbol, kind = CouiTextKind.PreferenceTitle, color=site.tint)
        }
    }
}

@Composable
private fun DnsRouteText(result: HomeDnsResult) {
    val colors = CouiTheme.colorScheme
    val route = when (result.dnsRoute) {
        "direct" -> stringResource(R.string.coui_dns_route_direct)
        "proxy" -> stringResource(R.string.coui_dns_route_proxy,
            result.dnsProxy.ifEmpty { stringResource(R.string.coui_dns_unknown) })
        "rules" -> stringResource(R.string.coui_dns_route_rules)
        "mixed" -> stringResource(R.string.coui_dns_route_mixed)
        else -> stringResource(R.string.coui_dns_route_unknown)
    }
    Text(text = route, kind = CouiTextKind.PreferenceSummary,
        color = if (result.dnsRoute == "proxy") colors.accentOnSurface else colors.onSurfaceVariant,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (result.dnsUpstream.isNotEmpty()) {
        Text(
            text = stringResource(R.string.coui_dns_upstream, result.dnsUpstream),
            kind = CouiTextKind.Description,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DnsWebsiteRow(site: DnsWebsite, result: HomeDnsResult?) {
    val colors = CouiTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DnsSiteIcon(site)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = stringResource(site.name), kind = CouiTextKind.PreferenceTitle,
                maxLines=1, overflow=TextOverflow.Ellipsis)
            Text(text = site.domain, kind = CouiTextKind.Description, color=colors.onSurfaceVariant)
            if (result == null) {
                Text(text = stringResource(R.string.coui_dns_pending), kind = CouiTextKind.PreferenceSummary,
                    color = colors.onSurfaceVariant)
            } else {
                DnsRouteText(result)
                val detail = when {
                    result.error.contains("deadline exceeded", ignoreCase = true) ||
                        result.error.contains("i/o timeout", ignoreCase = true) ->
                        stringResource(R.string.coui_dns_timeout_detail)
                    result.error.isNotBlank() ->
                        stringResource(R.string.coui_dns_failed_detail, result.error.take(150))
                    result.address.isNotBlank() -> stringResource(R.string.coui_dns_result,
                        result.address, result.millis)
                    else -> stringResource(R.string.coui_dns_failed)
                }
                Text(text=detail, kind=CouiTextKind.Description, color=colors.onSurfaceVariant,
                    maxLines=2, overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DnsCategory(label: String, sites: List<DnsWebsite>, results: List<HomeDnsResult>) {
    val colors = CouiTheme.colorScheme
    Text(text=label, kind=CouiTextKind.PreferenceSummary, color=colors.onSurfaceVariant,
        modifier=Modifier.padding(top=15.dp, bottom=4.dp))
    sites.forEachIndexed { index, site ->
        if (index>0) Divider(startIndent = 48.dp, endIndent = 0.dp)
        DnsWebsiteRow(site, results.find { it.domain == site.domain })
    }
}

private fun dnsStrategyFingerprint(result: HomeDnsResult?): String? {
    if (result == null || result.error.isNotEmpty()) return null
    if (result.dnsRoute !in setOf("direct","proxy")) return null
    return listOf(result.dnsRoute,result.dnsProxy,result.dnsUpstream).joinToString("|")
}

@Composable
private fun DnsSplitSummary(results: List<HomeDnsResult>) {
    if (results.isEmpty()) return
    val cn = HomeDnsTargets.mainland.map { domain ->
        dnsStrategyFingerprint(results.find { it.domain == domain })
    }
    val abroad = (HomeDnsTargets.overseas + HomeDnsTargets.ai).map { domain ->
        dnsStrategyFingerprint(results.find { it.domain == domain })
    }
    val message = when {
        cn.any { it == null } || abroad.any { it == null } ->
            R.string.coui_dns_compare_unknown
        cn.distinct().size>1 || abroad.distinct().size>1 ->
            R.string.coui_dns_compare_varies
        cn.first() == abroad.first() -> R.string.coui_dns_compare_same
        else -> R.string.coui_dns_compare_different
    }
    Text(
        text=stringResource(message),
        kind=CouiTextKind.PreferenceSummary,
        color=CouiTheme.colorScheme.accentOnSurface,
        modifier=Modifier.padding(top=10.dp, bottom=4.dp),
    )
}

@Composable
internal fun HomeDnsCard(mode: String?, results: List<HomeDnsResult>, loading: Boolean, enabled: Boolean, check: () -> Unit) {
    val colors = CouiTheme.colorScheme
    Group {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text=stringResource(R.string.coui_dns_title), kind=CouiTextKind.PreferenceTitle)
                    Text(text=stringResource(R.string.coui_dns_mode, mode ?: "—"),
                        kind=CouiTextKind.Description, color=colors.onSurfaceVariant)
                }
                Box(
                    Modifier.heightIn(min=48.dp)
                        .clickable(enabled=enabled&&!loading,role=Role.Button,onClick=check)
                        .padding(horizontal=12.dp),
                    contentAlignment=Alignment.Center,
                ) {
                    Text(text=stringResource(if(loading)R.string.coui_dns_checking else R.string.coui_dns_check),
                        kind=CouiTextKind.PreferenceTitle,
                        color=if(enabled)colors.accentOnSurface else colors.onSurfaceVariant)
                }
            }
            DnsSplitSummary(results)
            DnsCategory(stringResource(R.string.coui_dns_mainland), mainlandSites, results)
            DnsCategory(stringResource(R.string.coui_dns_overseas), overseasSites, results)
            DnsCategory(stringResource(R.string.coui_dns_ai), aiSites, results)
            Text(text=stringResource(R.string.coui_dns_note), kind=CouiTextKind.Description,
                color=colors.onSurfaceVariant, modifier=Modifier.padding(top=12.dp))
        }
    }
}

/**
 * Native COUI Home privacy health dashboard. IPPure supplies an IP *risk*
 * score (not an official purity percentage); IPLeak supplies the externally
 * observed resolver addresses. Without a known-good resolver baseline, those
 * observations must never be advertised as "DNS secure/no leak".
 */
@Composable
internal fun HomePrivacyCard(
    running: Boolean,
    paused: Boolean,
    result: HomePrivacyResult?,
    loading: Boolean,
    check: () -> Unit,
    openIpleak: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val available = running && !paused
    Group {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("home-privacy-dashboard"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.coui_privacy_dashboard_title),
                    kind = CouiTextKind.PreferenceTitle,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.heightIn(min = 44.dp)
                        .clickable(enabled = available && !loading, role = Role.Button, onClick = check)
                        .testTag("home-privacy-check")
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (loading) R.string.coui_privacy_checking else R.string.coui_privacy_check
                        ),
                        kind = CouiTextKind.PreferenceTitle,
                        color = if (available && !loading) colors.accentOnSurface else colors.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(1f).background(colors.background, RoundedCornerShape(16.dp))
                        .padding(14.dp).testTag("home-privacy-risk"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.coui_privacy_risk_label),
                        kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
                    Text(
                        result?.fraudScore?.let { "$it / 100" } ?: "—",
                        kind = CouiTextKind.PageTitle,
                    )
                    Text(stringResource(R.string.coui_privacy_risk_hint),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                }
                Column(
                    Modifier.weight(1f).background(colors.background, RoundedCornerShape(16.dp))
                        .padding(14.dp).testTag("home-privacy-dns"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.coui_privacy_dns_label),
                        kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
                    Text(
                        stringResource(R.string.coui_privacy_not_verified),
                        kind = CouiTextKind.PreferenceTitle,
                    )
                    Text(
                        result?.dnsResolvers?.takeIf { it.isNotEmpty() }?.let {
                            stringResource(R.string.coui_privacy_resolver_count, it.size)
                        } ?: stringResource(R.string.coui_privacy_dns_unknown),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant,
                    )
                }
            }
            if (result != null) {
                if (result.ippureIp.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coui_privacy_ippure_ip, result.ippureIp),
                        kind = CouiTextKind.PreferenceSummary,
                    )
                    Text(
                        text = listOf(result.country, result.isp).filter { it.isNotBlank() }.joinToString(" · "),
                        kind = CouiTextKind.Description,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (result.ippureError.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coui_privacy_ippure_error, result.ippureError.take(120)),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant,
                    )
                }
                if (result.ipleakIp.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coui_privacy_ipleak_ip, result.ipleakIp),
                        kind = CouiTextKind.PreferenceSummary,
                    )
                } else if (result.ipleakError.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.coui_privacy_ipleak_error, result.ipleakError.take(120)),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant,
                    )
                }
                if (result.ippureIp.isNotBlank() && result.ipleakIp.isNotBlank() &&
                    result.ippureIp != result.ipleakIp) {
                    Text(stringResource(R.string.coui_privacy_ip_mismatch),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                }
                if (result.dnsResolvers.isNotEmpty()) {
                    Text(stringResource(R.string.coui_privacy_dns_resolvers,
                            result.dnsResolvers.take(8).joinToString(", ")),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                } else if (result.dnsError.isNotBlank()) {
                    Text(stringResource(R.string.coui_privacy_dns_error, result.dnsError.take(150)),
                        kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                }
            }
            Text(
                text = stringResource(R.string.coui_privacy_scope_note),
                kind = CouiTextKind.Description, color = colors.onSurfaceVariant,
            )
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = openIpleak)
                    .testTag("home-privacy-open-ipleak"),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.coui_privacy_open_ipleak),
                    kind = CouiTextKind.PreferenceTitle,
                    color = colors.accentOnSurface,
                )
            }
        }
    }
}
