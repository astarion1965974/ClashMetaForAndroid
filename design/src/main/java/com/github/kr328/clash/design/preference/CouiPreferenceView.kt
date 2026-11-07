package com.github.kr328.clash.design.preference

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** Reusable native COUI preference row; no legacy ComposeView wrapper. */
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
    val source = remember { MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(48.dp).clip(CircleShape)
            .couiButtonFeedback(CouiTheme.isDark, source, enabled = true, list = false)
            .clickable(source, null, role = Role.Button, onClick = onClick),
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

/** Render Android icon drawables as Compose bitmaps; never mount a legacy ImageView. */
@Composable
private fun PreferenceIcon(drawable: Drawable, size: Dp) {
    val icon = remember(drawable) {
        val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(144) ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(144) ?: 96
        drawable.toBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size),
        contentScale = ContentScale.Fit)
}
