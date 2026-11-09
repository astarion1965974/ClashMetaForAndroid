package com.github.kr328.clash.design

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * Fixed opaque proxy-group selector. Its background is exactly the same as
 * the compact title bar above it. No ChipTabRow floating renderers, glass,
 * backdrop sampling, gradient or blur are used in this header.
 */
@Composable
internal fun ProxyTabs(
    titles: List<String>,
    selected: Int,
    backgroundColor: Color,
    onSelect: (Int) -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(backgroundColor)
            .horizontalScroll(scrollState)
            .padding(
                start = CouiPageDefaults.horizontalMargin,
                end = CouiPageDefaults.horizontalMargin,
                top = 8.dp,
                bottom = 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.onSurface.copy(alpha = 0.10f) else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                    .testTag("proxy-tab-$index")
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    kind = CouiTextKind.PreferenceTitle,
                    color = if (active) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
