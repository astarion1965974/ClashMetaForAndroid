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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

@Composable
internal fun ProxyTabs(titles: List<String>, selected: Int, backdrop: CouiBackdrop,
    backgroundColor: Color, onSelect: (Int) -> Unit) {
    val renderers = remember(titles) {
        runCatching { titles.map { CouiFloatingRenderer.standalone() } }.getOrNull()
    }
    // One opaque, shared ColorOS chrome surface under both toolbar and chips.
    // Record that same solid color as the ChipTabRow backdrop source.
    Box(Modifier.fillMaxWidth().background(backgroundColor)) {
        Box(Modifier.matchParentSize().couiBackdrop(backdrop).background(backgroundColor))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        if (renderers != null && titles.isNotEmpty()) {
            ChipTabRow(items = titles.mapIndexed { index, title -> CouiChipTabItem(index, title) },
                selectedKey = selected.coerceIn(titles.indices), onSelectedKeyChange = { onSelect(it as Int) },
                backdrop = backdrop, renderers = renderers)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                titles.forEachIndexed { index, title ->
                    Text(text = title, kind = CouiTextKind.PreferenceTitle,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (selected == index) CouiTheme.colorScheme.card else CouiTheme.colorScheme.background)
                            .selectable(selected == index, role = Role.Tab) { onSelect(index) }.padding(12.dp))
                }
            }
        }
        }
    }
}
