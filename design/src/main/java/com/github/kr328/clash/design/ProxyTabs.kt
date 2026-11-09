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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * ColorOS 17 calendar-style fixed header: opaque background behind native
 * ChipTabRow. The library owns its glass selection and animation; the recorder
 * sees only the opaque header surface and never the scrolling node grid.
 *
 * Android JVM tests lack the platform MeshSpecification required by the native
 * COUI shadow renderer. In that environment only, render accessible plain tabs.
 */
@Composable
internal fun ProxyTabs(
    titles: List<String>,
    selected: Int,
    backdrop: CouiBackdrop,
    backgroundColor: Color,
    onSelect: (Int) -> Unit,
) {
    val renderers = remember(titles) {
        runCatching { titles.map { CouiFloatingRenderer.standalone() } }.getOrNull()
    }
    Box(Modifier.fillMaxWidth().background(backgroundColor).testTag("proxy-native-chip-row")) {
        Box(Modifier.matchParentSize().couiBackdrop(backdrop).background(backgroundColor))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
            if (renderers != null && titles.isNotEmpty()) {
                ChipTabRow(
                    items = titles.mapIndexed { index, title -> CouiChipTabItem(index, title) },
                    selectedKey = selected.coerceIn(titles.indices),
                    onSelectedKeyChange = { onSelect(it as Int) },
                    backdrop = backdrop,
                    renderers = renderers,
                )
            } else {
                // MeshSpecification is unavailable in Robolectric; do not
                // replace the actual COUI widget on supported ColorOS devices.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = CouiPageDefaults.horizontalMargin),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    titles.forEachIndexed { index, title ->
                        Text(
                            text = title,
                            kind = CouiTextKind.PreferenceTitle,
                            color = if (index == selected) CouiTheme.colorScheme.onSurface
                                else CouiTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (index == selected)
                                    CouiTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    else Color.Transparent)
                                .selectable(selected = selected == index, role = Role.Tab) { onSelect(index) }
                                .testTag("proxy-tab-$index")
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
