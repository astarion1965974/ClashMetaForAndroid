package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

class CouiButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var text: CharSequence by mutableStateOf("")
        private set
    private var contentEnabled by mutableStateOf(true)

    init {
        val attributes = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text))
        text = attributes.getText(0) ?: ""
        attributes.recycle()
        addView(ComposeView(context).apply {
            setContent {
                CouiTheme(darkTheme = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
                    val colors = CouiTheme.colorScheme
                    Item(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CouiPageDefaults.shape)
                        .background(colors.accent.copy(alpha = if (contentEnabled) 1f else .38f)),
                        enabled = contentEnabled, onClick = { this@CouiButtonView.performClick() }) {
                        Text(text = text.toString(), kind = CouiTextKind.PreferenceTitle, color = colors.onAccent,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setText(@StringRes resId: Int) { text = context.getText(resId) }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        contentEnabled = enabled
    }
}
