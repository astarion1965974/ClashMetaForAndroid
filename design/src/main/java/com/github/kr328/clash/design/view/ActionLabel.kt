package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView

class ActionLabel @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes) {
    private lateinit var row: CouiPreferenceView
    private var hasClick by mutableStateOf(false)

    var icon: Drawable? by mutableStateOf(null)
    var text: CharSequence? by mutableStateOf(null)
    var subtext: CharSequence? by mutableStateOf(null)

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (::row.isInitialized) row.isEnabled = enabled
    }

    override fun setOnClickListener(listener: View.OnClickListener?) {
        super.setOnClickListener(listener)
        hasClick = listener != null
    }

    init {
        row = CouiPreferenceView(context)
        row.isEnabled = isEnabled
        row.setContent {
            CouiPreferenceRow(
                title = text,
                summary = subtext,
                icon = icon,
                enabled = row.contentEnabled,
                onClick = if (hasClick) ({ performClick() }) else null,
            )
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        context.theme.obtainStyledAttributes(
            attributeSet, R.styleable.ActionLabel, defStyleAttr, defStyleRes,
        ).apply {
            try {
                icon = getDrawable(R.styleable.ActionLabel_icon)
                text = getString(R.styleable.ActionLabel_text)
                subtext = getString(R.styleable.ActionLabel_subtext)
            } finally {
                recycle()
            }
        }
    }
}
