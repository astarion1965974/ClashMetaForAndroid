package com.github.kr328.clash.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.effect.CouiFloatingRenderer

@Composable
internal fun ProxyTabs(titles: List<String>, selected: Int, backdrop: CouiBackdrop,
    backgroundColor: Color, onSelect: (Int) -> Unit) {
    val renderers = remember(titles) { titles.map { CouiFloatingRenderer.standalone() } }
    // The recorder sees only this opaque host color. Grid content never enters
    // the pinned header's backdrop, while each chip keeps native COUI glass.
    Box(Modifier.fillMaxWidth().background(backgroundColor).testTag("proxy-native-chip-row")) {
        Box(Modifier.matchParentSize().couiBackdrop(backdrop).background(backgroundColor))
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
            if (titles.isNotEmpty()) {
                ChipTabRow(
                items = titles.mapIndexed { index, title -> CouiChipTabItem(index, title) },
                selectedKey = selected.coerceIn(titles.indices),
                onSelectedKeyChange = { onSelect(it as Int) },
                backdrop = backdrop,
                renderers = renderers,
                )
            }
        }
    }
}
