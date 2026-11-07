package com.github.kr328.clash.design.component

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

class ProxyView(context: Context, config: ProxyViewConfig) : FrameLayout(context) {
    constructor(context: Context) : this(context, ProxyViewConfig(context, 2))

    var state: ProxyViewState? by mutableStateOf(null, neverEqualPolicy())
    var selectable by mutableStateOf(false)

    init {
        val row = CouiPreferenceView(context)
        row.setContent {
            state?.let { current ->
                val colors = CouiTheme.colorScheme
                val selected = current.selected
                val shape = CouiPageDefaults.shape
                val margin = if (config.proxyLine == 1) 12.dp else 4.dp
                Item(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 4.dp)
                        .clip(shape)
                        .background(if (selected) colors.accent.copy(alpha = 0.10f) else colors.card)
                        .then(if (selected) Modifier.border(1.dp, colors.accent, shape) else Modifier),
                    enabled = selectable,
                    onClick = if (selectable) ({ performClick() }) else null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    summary = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (current.subtitle.isNotEmpty()) {
                                Text(
                                    text = current.subtitle,
                                    kind = CouiTextKind.PreferenceSummary,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (current.delayText.isNotEmpty()) {
                                Text(
                                    text = current.delayText,
                                    kind = CouiTextKind.PreferenceSummary,
                                    color = if (selected) colors.accent else colors.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                ) {
                    Text(
                        text = current.title,
                        kind = CouiTextKind.PreferenceTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
}
