package com.github.kr328.clash.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.layout.Item
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
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
    var searchExpanded by remember { mutableStateOf(false) }
    val search = rememberCouiSearchBarState(searchExpanded)
    ProvideCouiBackdrop(backdrop) {
      Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.matchParentSize().couiBackdrop(backdrop).background(CouiTheme.colorScheme.background))
        Column(Modifier.fillMaxWidth()) {
            Item(onClick = onMode, enabled = enabled, summary = {
                Text(text = hint, kind = CouiTextKind.PreferenceSummary, color = CouiTheme.colorScheme.onSurfaceVariant)
            }) { Text(text = mode, kind = CouiTextKind.PreferenceTitle) }
            SearchBar(state = search, query = keyword, onQueryChange = onKeyword,
                onExpandedChange = { searchExpanded = it }, placeholder = stringResource(R.string.app_search),
                cancelText = stringResource(R.string.cancel), clearDescription = stringResource(R.string.coui_clear),
                enabled = enabled)
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

@Composable
private fun FilterToggle(label: String, checked: Boolean, enabled: Boolean, modifier: Modifier, change: (Boolean) -> Unit) {
    Row(modifier.heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(checked = checked, enabled = enabled)
        Text(text = label, kind = CouiTextKind.PreferenceSummary, maxLines = 2)
    }
}
