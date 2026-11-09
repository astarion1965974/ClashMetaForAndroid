package com.github.kr328.clash.design.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor

/**
 * Alpha8 Demo popup contract: a real on-screen anchor owns the Menu, which
 * uses COUI's native motion, floating material and outside-dismiss behavior.
 * Programmatic selection dialogs remain Dialogs; toolbar/row overflow does not.
 */
@Composable
internal fun CouiAnchoredMenu(
    options: List<CouiMenuOption>,
    content: @Composable (open: () -> Unit) -> Unit,
) {
    val anchor = rememberCouiMenuAnchor()
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.couiMenuAnchor(anchor)) {
        content { if (options.isNotEmpty()) anchor.open { expanded = true } }
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            items = options.mapIndexed { index, option ->
                CouiMenuItem(
                    index, option.label,
                    selected = option.selected == true,
                    enabled = option.enabled,
                )
            },
            onItemClick = { item ->
                expanded = false
                options.getOrNull(item.value)?.takeIf { it.enabled }?.action?.invoke()
            },
        )
    }
}
