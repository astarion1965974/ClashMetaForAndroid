package com.github.kr328.clash.design

import android.os.Build

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.itos.coui.layout.Group
import com.itos.coui.layout.Divider
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.icons.CouiIcons
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * Per-app routing controls, keeping the original native glass SearchBar and
 * filtering Group, without a redundant global access-control-mode selector.
 * The backdrop source records only the non-floating page content.
 */
@Composable
internal fun AccessControls(
    keyword: String, bypassOnly: Boolean, system: Boolean, enabled: Boolean,
    onKeyword: (String) -> Unit,
    onBypassOnly: (Boolean) -> Unit, onSystem: (Boolean) -> Unit,
) {
    val backdrop = rememberCouiBackdrop()
    var searchExpanded by remember { mutableStateOf(false) }
    val searchState = rememberCouiSearchBarState(searchExpanded)
    val density = LocalDensity.current
    var searchHeightPx by remember { mutableIntStateOf(0) }

    ProvideCouiBackdrop(backdrop) {
        Box(Modifier.fillMaxWidth().testTag("app-routing-search-and-filters")) {
            Column(
                Modifier.fillMaxWidth().couiBackdrop(backdrop)
                    .background(CouiTheme.colorScheme.background),
            ) {
                Spacer(Modifier.fillMaxWidth().height(with(density) { searchHeightPx.toDp() }))
                Group {
                    FilterToggle(
                        stringResource(R.string.app_selected_only),
                        bypassOnly, enabled, onBypassOnly,
                    )
                    Divider()
                    FilterToggle(
                        stringResource(R.string.app_system),
                        system, enabled, onSystem,
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { searchHeightPx = it.height },
            ) {
                if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                    SearchBar(
                        state = searchState,
                        query = keyword,
                        onQueryChange = onKeyword,
                        onExpandedChange = { searchExpanded = it },
                        placeholder = stringResource(R.string.app_search),
                        cancelText = stringResource(R.string.cancel),
                        clearDescription = stringResource(R.string.coui_clear),
                        enabled = enabled,
                    )
                } else {
                    StableSearchField(keyword, enabled, onKeyword)
                }
            }
        }
    }
}


/**
 * Headless Robolectric test fallback only; ColorOS 17 uses native COUI glass search.
 */
@Composable
private fun StableSearchField(keyword: String, enabled: Boolean, onKeyword: (String) -> Unit) {
    val colors = CouiTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 8.dp)
            .testTag("app-routing-search-container")
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

/** Full-width native COUI preference row with a single click owner. */
@Composable
private fun FilterToggle(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Item(
        enabled = enabled,
        onClick = { change(!checked) },
        trailing = { Checkbox(checked = checked, enabled = enabled) },
    ) {
        Text(text = label, kind = CouiTextKind.PreferenceTitle)
    }
}
