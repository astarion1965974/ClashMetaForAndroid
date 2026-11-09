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
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/**
 * ColorOS calendar-style pinned group selector: the compact toolbar and this
 * row have the same opaque host color, but the chips retain native COUI alpha8
 * glass, selection springs, shadows, horizontal fling and auto-reveal.
 *
 * The shared backdrop is provided by ProxyDesign's full-height opaque sibling
 * behind BOTH the toolbar and tabs. It never records the grid or chips themselves.
 */
@Composable
internal fun ProxyTabs(
    titles: List<String>,
    selected: Int,
    backgroundColor: Color,
    backdrop: CouiBackdrop,
    onSelect: (Int) -> Unit,
) {
    if (titles.isEmpty()) return

     val renderers = remember(titles.size) {
        runCatching { List(titles.size) { CouiFloatingRenderer.standalone() } }.getOrNull()
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(backgroundColor)
            .testTag("proxy-chip-solid-host"),
    ) {
        if (renderers != null) {
            ChipTabRow(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
                    .testTag("proxy-native-chip-row"),
                items = titles.mapIndexed { index, title -> CouiChipTabItem(index, title) },
                selectedKey = selected.coerceIn(titles.indices),
                onSelectedKeyChange = { onSelect(it as Int) },
                backdrop = backdrop,
                dark = CouiTheme.isDark,
                itemSpacing = 8.dp,
                renderers = renderers,
            )
        } else {
            // Only for renderers unavailable in headless/JVM environments.
            // Normal ColorOS 17 devices always use the native ChipTabRow above.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        start = CouiPageDefaults.horizontalMargin,
                        end = CouiPageDefaults.horizontalMargin,
                        top = 8.dp,
                        bottom = 12.dp,
                    )
                    .testTag("proxy-chip-headless-fallback"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titles.forEachIndexed { index, title ->
                    val active = selected == index
                    Text(
                        text = title,
                        kind = CouiTextKind.PreferenceTitle,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (active) CouiTheme.colorScheme.card else Color.Transparent)
                            .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
