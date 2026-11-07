package com.github.kr328.clash.design.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.TopAppBarSurface

class CouiIconButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var contentEnabled by mutableStateOf(true)
    // Avoid captured initializer locals in nested Compose lambdas (Kotlin IR).
    private val fabIcon: Int
    private val fabLabel: String?
    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.src, android.R.attr.contentDescription))
        fabIcon = a.getResourceId(0, 0)
        fabLabel = a.getString(1)
        a.recycle()
        background = null
        setPadding(0, 0, 0, 0)
        addView(ComposeView(context).apply {
            setContent {
                CouiTheme(darkTheme = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
                    val backdrop = rememberCouiBackdrop()
                    val renderer = if (Build.VERSION.SDK_INT >= 34) remember {
                        runCatching { CouiFloatingRenderer.standalone() }.getOrNull()
                    } else null
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.matchParentSize().couiBackdrop(backdrop).background(Color.Transparent))
                        if (Build.VERSION.SDK_INT >= 34 && renderer != null) {
                            TopAppBarSurface(backdrop = backdrop, gradientColor = Color.Transparent) {
                                TopAppBarButton(onClick = { this@CouiIconButtonView.performClick() },
                                    enabled = contentEnabled, renderer = renderer) {
                                    if (fabIcon != 0) Image(painterResource(fabIcon), fabLabel, Modifier.size(24.dp),
                                        colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurface))
                                }
                            }
                        } else {
                            Box(Modifier.size(48.dp).clip(CircleShape)
                                .clickable(enabled = contentEnabled, role = Role.Button) { this@CouiIconButtonView.performClick() },
                                contentAlignment = Alignment.Center) {
                                if (fabIcon != 0) Image(painterResource(fabIcon), fabLabel, Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurface))
                            }
                        }
                    }
                }
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); contentEnabled = enabled }
}
