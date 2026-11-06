package com.github.kr328.clash.design.preference

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.common.compat.getDrawableCompat

interface ClickablePreference : Preference {
    var title: CharSequence
    var icon: Drawable?
    var summary: CharSequence?

    fun clicked(clicked: () -> Unit)
}

fun PreferenceScreen.clickable(
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    @StringRes summary: Int? = null,
    configure: ClickablePreference.() -> Unit = {},
): ClickablePreference {
    val row = CouiPreferenceView(context)
    val impl = object : ClickablePreference {
        override val view: View = row
        override var title: CharSequence by mutableStateOf(context.getText(title))
        override var icon: Drawable? by mutableStateOf(icon?.let(context::getDrawableCompat))
        override var summary: CharSequence? by mutableStateOf(summary?.let(context::getText))
        var onClick: (() -> Unit)? by mutableStateOf(null)

        override fun clicked(clicked: () -> Unit) {
            onClick = clicked
        }
    }

    row.setContent {
        CouiPreferenceRow(
            title = impl.title,
            summary = impl.summary,
            icon = impl.icon,
            enabled = row.contentEnabled,
            onClick = impl.onClick,
        )
    }
    impl.configure()
    addElement(impl)
    return impl
}
