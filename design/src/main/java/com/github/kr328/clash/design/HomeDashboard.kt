package com.github.kr328.clash.design

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

internal data class HomeQuota(val total: Long, val used: Long, val remaining: Long)
internal data class HomeTrafficDay(val date: String, val upload: Long, val download: Long, val recorded: Boolean)
internal data class HomeDnsResult(val domain: String, val address: String, val error: String, val millis: Long)

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

@Composable
internal fun HomeTrafficCard(days: List<HomeTrafficDay>, forwarded: String?, connected: Boolean) {
    val colors = CouiTheme.colorScheme
    val recorded = days.any { it.recorded }
    Group {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.coui_traffic_week), kind = CouiTextKind.PreferenceTitle)
                Text(text = forwarded.takeIf { connected } ?: "—", kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
            }
            Text(text = stringResource(R.string.coui_traffic_legend), kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
            if (!recorded) {
                Text(text = stringResource(R.string.coui_traffic_empty), kind = CouiTextKind.Description,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                val maxValue = days.maxOfOrNull { maxOf(it.upload, it.download) }?.coerceAtLeast(1L) ?: 1L
                Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                    val cell = size.width / 7f
                    val width = (cell * .18f).coerceAtMost(12.dp.toPx())
                    days.takeLast(7).forEachIndexed { index, day ->
                        if (day.recorded) {
                            val up = size.height * (day.upload.toDouble() / maxValue).toFloat()
                            val down = size.height * (day.download.toDouble() / maxValue).toFloat()
                            val x = cell * index + cell * .28f
                            drawRoundRect(Color(0xff19c879), Offset(x, size.height - down), Size(width, down), CornerRadius(width / 2))
                            drawRoundRect(Color(0xff508af5), Offset(x + width + 3.dp.toPx(), size.height - up), Size(width, up), CornerRadius(width / 2))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    days.takeLast(7).forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(text = day.date.takeLast(2), kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(text = stringResource(R.string.coui_traffic_note), kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HomeDnsCard(mode: String?, results: List<HomeDnsResult>, loading: Boolean, enabled: Boolean, check: () -> Unit) {
    val colors = CouiTheme.colorScheme
    Group {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.coui_dns_title), kind = CouiTextKind.PreferenceTitle)
                    Text(text = stringResource(R.string.coui_dns_mode, mode ?: "—"), kind = CouiTextKind.Description, color = colors.onSurfaceVariant)
                }
                Box(Modifier.heightIn(min = 48.dp).clickable(enabled = enabled && !loading, role = Role.Button, onClick = check)
                    .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(if (loading) R.string.coui_dns_checking else R.string.coui_dns_check),
                        kind = CouiTextKind.PreferenceTitle, color = if (enabled) colors.accentOnSurface else colors.onSurfaceVariant)
                }
            }
            listOf("baidu.com", "bilibili.com", "google.com", "youtube.com").forEachIndexed { index, domain ->
                if (index > 0) Divider()
                val result = results.find { it.domain == domain }
                val detail = when {
                    result == null -> stringResource(R.string.coui_dns_pending)
                    result.address.isNotEmpty() -> stringResource(R.string.coui_dns_result, result.address, result.millis)
                    else -> stringResource(R.string.coui_dns_failed)
                }
                Item(summary = { Text(text = detail, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }) {
                    Text(text = domain, kind = CouiTextKind.PreferenceTitle)
                }
            }
            Text(text = stringResource(R.string.coui_dns_note), kind = CouiTextKind.Description,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
