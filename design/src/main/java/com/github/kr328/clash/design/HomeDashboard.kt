package com.github.kr328.clash.design

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
    val mainland = listOf("baidu.com", "bilibili.com", "dianping.com", "douyin.com")
    val overseas = listOf("google.com", "youtube.com")
    val ai = listOf("chatgpt.com", "grok.com", "claude.ai")
    val domains = mainland + overseas + ai
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
                    result.error.isNotBlank() -> stringResource(R.string.coui_dns_failed)
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
