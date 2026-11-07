package com.github.kr328.clash.design.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Action support matching the old notification contract without a Material Snackbar. */
class CouiNoticeOptions(private val context: Context) {
    var actionLabel: String? = null
        private set
    var onAction: ((View) -> Unit)? = null
        private set

    fun setAction(@StringRes label: Int, onClick: (View) -> Unit) {
        actionLabel = context.getString(label)
        onAction = onClick
    }

    fun setAction(label: CharSequence, onClick: (View) -> Unit) {
        actionLabel = label.toString()
        onAction = onClick
    }
}

/** A native COUI card overlay, removed on timeout or tap. No legacy Material widgets. */
fun Context.showCouiNotice(
    message: CharSequence,
    duration: ToastDuration,
    options: CouiNoticeOptions = CouiNoticeOptions(this),
) {
    var ownerContext: Context = this
    while (ownerContext is ContextWrapper && ownerContext !is Activity) {
        ownerContext = ownerContext.baseContext
    }
    val owner = ownerContext as? Activity ?: return
    val parent = owner.findViewById<ViewGroup>(android.R.id.content) ?: return
    val tag = "clash-live-coui-notice"
    parent.findViewWithTag<View>(tag)?.let { stale ->
        (stale.parent as? ViewGroup)?.removeView(stale)
    }
    val host = ComposeView(owner).apply { this.tag = tag }
    fun close() {
        (host.parent as? ViewGroup)?.removeView(host)
        host.disposeComposition()
    }
    val layout = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
    )
    parent.addView(host, layout)
    host.setContent {
        CouiTheme(darkTheme = !owner.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
            val colors = CouiTheme.colorScheme
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(12.dp, CouiPageDefaults.shape)
                    .clip(CouiPageDefaults.shape).background(colors.card)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = message.toString(),
                    kind = CouiTextKind.PreferenceSummary,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                val action = options.onAction
                val label = options.actionLabel
                if (action != null && label != null) {
                    Text(
                        text = label,
                        kind = CouiTextKind.PreferenceTitle,
                        color = colors.accent,
                        modifier = Modifier.clickable(role = Role.Button) {
                            close()
                            action(host)
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
    val timeout = when (duration) {
        ToastDuration.Short -> 2500L
        ToastDuration.Long -> 4500L
        ToastDuration.Indefinite -> null
    }
    if (timeout != null) (owner as? LifecycleOwner)?.lifecycleScope?.launch {
        delay(timeout)
        if (host.parent != null) close()
    }
}
