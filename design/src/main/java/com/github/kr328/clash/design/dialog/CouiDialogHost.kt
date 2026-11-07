package com.github.kr328.clash.design.dialog

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.root
import com.itos.coui.theme.CouiTheme

/** Mount a Compose dialog into the activity's existing View tree. */
internal fun Context.showCouiContent(content: @Composable (() -> Unit) -> Unit): () -> Unit {
    val parent = requireNotNull(root) { "A dialog requires an activity" }
    val host = ComposeView(this)
    val close = {
        host.post {
            host.disposeComposition()
            (host.parent as? ViewGroup)?.removeView(host)
        }
        Unit
    }
    parent.addView(host, ViewGroup.LayoutParams(1, 1))
    host.setContent {
        CouiTheme(darkTheme = !resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
            content(close)
        }
    }
    return close
}
