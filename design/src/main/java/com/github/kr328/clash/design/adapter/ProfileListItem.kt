package com.github.kr328.clash.design.adapter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.text.Text
import com.itos.coui.text.CouiTextKind
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.dialog.CouiAnchoredMenu
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.design.util.toString
import com.github.kr328.clash.service.model.Profile
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.theme.CouiTheme

/** COUI-only profile row reused by both the primary tab and standalone configuration page. */
@Composable
internal fun ProfileListItem(
    profile: Profile,
    currentTime: Long,
    onClicked: (Profile) -> Unit,
    onMenuClicked: (Profile) -> Unit,
    menuOptions: ((Profile) -> List<CouiMenuOption>)? = null,
    updateError: String? = null,
    onErrorClicked: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val type = if (profile.pending) {
        context.getString(R.string.format_type_unsaved, profile.type.toString(context))
    } else {
        profile.type.toString(context)
    }
    val elapsed = (currentTime - profile.updatedAt).elapsedIntervalString(context)
    val uploaded = profile.upload.coerceAtLeast(0)
    val downloaded = profile.download.coerceAtLeast(0)
    val used = uploaded + downloaded.coerceAtMost(Long.MAX_VALUE - uploaded)
    val quota = if (profile.total > 1) {
        "\n${used.toBytesString()} / ${profile.total.toBytesString()}"
    } else {
        ""
    }
    val expiry = if (profile.expire > 0) "\n${profile.expire.toDateStr()}" else ""
    CouiPreferenceRow(
        title = profile.name,
        summary = "$type · $elapsed$quota$expiry",
        icon = null,
        leading = { RadioButton(selected = profile.active) },
        enabled = true,
        onClick = { onClicked(profile) },
        afterSummary = if (profile.total > 1 || updateError != null) {
            {
                val colors = CouiTheme.colorScheme
                if (profile.total > 1) {
                    val progress = (used.toDouble() / profile.total).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                            .progressSemantics(progress).background(colors.outlineVariant),
                    ) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(colors.accent))
                    }
                }
                if (updateError != null) {
                    Text(
                        text = stringResource(R.string.coui_profile_update_failed),
                        kind = CouiTextKind.PreferenceSummary,
                        color = colors.accent,
                        modifier = Modifier.clickable { onErrorClicked?.invoke() },
                        maxLines = 1,
                    )
                }
            }
        } else null,
        trailing = {
            if (menuOptions != null) {
                CouiAnchoredMenu(menuOptions(profile)) { open ->
                    CouiRowAction(R.drawable.ic_baseline_more_vert, stringResource(R.string.more), open)
                }
            } else {
                CouiRowAction(R.drawable.ic_baseline_more_vert, stringResource(R.string.more)) {
                    onMenuClicked(profile)
                }
            }
        },
    )
}
