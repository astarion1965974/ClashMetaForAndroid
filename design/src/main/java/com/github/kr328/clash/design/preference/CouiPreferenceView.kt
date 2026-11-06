package com.github.kr328.clash.design.preference

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.R as AppCompatR
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** Hosts a COUI row while retaining the View enabled state used by existing screens. */
internal class CouiPreferenceView(context: Context) : FrameLayout(context) {
    var contentEnabled by mutableStateOf(true)
        private set

    private val composeView = ComposeView(context)

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        contentEnabled = enabled
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            val dark = !context.resolveThemedBoolean(AppCompatR.attr.isLightTheme)
            CouiTheme(darkTheme = dark, content = content)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CouiPreferenceRow(
    title: CharSequence?,
    summary: CharSequence?,
    icon: Drawable?,
    iconSize: Dp = 24.dp,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    afterSummary: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = CouiTheme.colorScheme
    val longClickModifier = if (onClick != null && onLongClick != null) {
        Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Item(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(CouiPageDefaults.shape).background(colors.card).then(longClickModifier),
        icon = leading ?: icon?.let { drawable -> { PreferenceIcon(drawable, iconSize) } },
        iconSize = iconSize,
        summary = if (!summary.isNullOrEmpty() || afterSummary != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!summary.isNullOrEmpty()) {
                        Text(
                            text = summary.toString(),
                            kind = CouiTextKind.PreferenceSummary,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    afterSummary?.invoke()
                }
            }
        } else null,
        trailing = trailing,
        enabled = enabled,
        onClick = if (onLongClick == null) onClick else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (!title.isNullOrEmpty()) {
            Text(text = title.toString(), kind = CouiTextKind.PreferenceTitle)
        }
    }
}

@Composable
internal fun CouiRowAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun PreferenceIcon(drawable: Drawable, size: Dp) {
    AndroidView(
        modifier = Modifier.size(size),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        update = { it.setImageDrawable(drawable) },
    )
}
