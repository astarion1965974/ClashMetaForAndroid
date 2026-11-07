package com.github.kr328.clash.design.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.theme.CouiTheme

class CouiFabView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    // Keep values on the View instead of capturing initializer locals inside
    // nested Compose lambdas (avoids Kotlin IR symbol-mapping failures).
    private val fabIcon: Int
    private val fabLabel: String?

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.src, android.R.attr.contentDescription))
        fabIcon = a.getResourceId(0, 0)
        fabLabel = a.getString(1)
        a.recycle()
        addView(ComposeView(context).apply {
            setContent {
                CouiTheme(darkTheme = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
                    val backdrop = rememberCouiBackdrop()
                    val renderer = if (Build.VERSION.SDK_INT >= 34) remember {
                        runCatching { CouiFloatingRenderer.standalone() }
                            .onFailure { android.util.Log.e("COUI-FAB", "Native COUI FAB material unavailable", it) }
                            .getOrNull()
                    } else null
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.matchParentSize().couiBackdrop(backdrop).background(Color.Transparent))
                        if (Build.VERSION.SDK_INT >= 34 && renderer != null) {
                            // COUI-Kit alpha7 requires a one-surface renderer for Fab.
                            // This legacy View still needs a future full-page backdrop host.
                            Fab(backdrop = backdrop, renderer = renderer,
                                onClick = { this@CouiFabView.performClick() }) {
                                if (fabIcon != 0) Image(painterResource(fabIcon), fabLabel, Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onAccent))
                            }
                        } else {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(CouiTheme.colorScheme.accent)
                                .clickable(role = Role.Button) { this@CouiFabView.performClick() }, contentAlignment = Alignment.Center) {
                                if (fabIcon != 0) Image(painterResource(fabIcon), fabLabel, Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onAccent))
                            }
                        }
                    }
                }
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    fun show() { visibility = View.VISIBLE }
    fun hide() { visibility = View.GONE }
}
