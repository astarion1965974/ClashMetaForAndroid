package com.github.kr328.clash.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

@Composable
internal fun AccessControls(
    mode: String, hint: String, keyword: String, selectedOnly: Boolean, system: Boolean,
    count: String, enabled: Boolean, onMode: () -> Unit, onKeyword: (String) -> Unit,
    onSelectedOnly: (Boolean) -> Unit, onSystem: (Boolean) -> Unit,
) {
    val backdrop = rememberCouiBackdrop()
    ProvideCouiBackdrop(backdrop) {
      Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.matchParentSize().couiBackdrop(backdrop).background(CouiTheme.colorScheme.background))
        Column(Modifier.fillMaxWidth()) {
            Item(onClick = onMode, enabled = enabled, summary = {
                Text(text = hint, kind = CouiTextKind.PreferenceSummary, color = CouiTheme.colorScheme.onSurfaceVariant)
            }) { Text(text = mode, kind = CouiTextKind.PreferenceTitle) }
            StableSearchField(keyword, enabled, onKeyword)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterToggle(stringResource(R.string.app_selected_only), selectedOnly, enabled, Modifier.weight(1f), onSelectedOnly)
                FilterToggle(stringResource(R.string.app_system), system, enabled, Modifier.weight(1f), onSystem)
            }
            Text(text = count, kind = CouiTextKind.Description, color = CouiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
      }
    }
}


/**
 * Use a standard Compose input surface instead of COUI SearchBar's native
 * MeshSpecification renderer. The latter throws on Robolectric API 35 and
 * prevents the entire app-routing screen from rendering.
 */
@Composable
private fun StableSearchField(keyword: String, enabled: Boolean, onKeyword: (String) -> Unit) {
    val colors = CouiTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(colors.container)
            .heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = keyword,
            onValueChange = onKeyword,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f).testTag("access_control_search"),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (keyword.isEmpty()) {
                        Text(
                            text = stringResource(R.string.app_search),
                            kind = CouiTextKind.PreferenceSummary,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (keyword.isNotEmpty()) {
            Text(
                text = stringResource(R.string.coui_clear),
                kind = CouiTextKind.PreferenceSummary,
                color = colors.accentOnSurface,
                modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { onKeyword("") }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, enabled: Boolean, modifier: Modifier, change: (Boolean) -> Unit) {
    Row(modifier.heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = checked, enabled = enabled)
        Text(text = label, kind = CouiTextKind.PreferenceSummary, maxLines = 2)
    }
}
