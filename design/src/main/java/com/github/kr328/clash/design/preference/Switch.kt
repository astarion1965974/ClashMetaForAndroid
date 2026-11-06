package com.github.kr328.clash.design.preference

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.itos.coui.`switch`.Switch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

interface SwitchPreference : Preference {
    var icon: Drawable?
    var title: CharSequence?
    var summary: CharSequence?
    var listener: OnChangedListener?
}

fun PreferenceScreen.switch(
    value: KMutableProperty0<Boolean>,
    @DrawableRes icon: Int? = null,
    @StringRes title: Int? = null,
    @StringRes summary: Int? = null,
    configure: SwitchPreference.() -> Unit = {},
): SwitchPreference {
    val row = CouiPreferenceView(context)
    val impl = object : SwitchPreference {
        override val view: View = row
        override var icon: Drawable? by mutableStateOf(icon?.let(context::getDrawableCompat))
        override var title: CharSequence? by mutableStateOf(title?.let(context::getText))
        override var summary: CharSequence? by mutableStateOf(summary?.let(context::getText))
        override var listener: OnChangedListener? = null
        var checked by mutableStateOf(false)
        var loaded by mutableStateOf(false)
    }

    fun updateChecked(checked: Boolean) {
        if (!row.contentEnabled || !impl.loaded || checked == impl.checked) return
        impl.checked = checked
        launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) { value.set(checked) }
            impl.listener?.onChanged()
        }
    }

    row.setContent {
        val enabled = row.contentEnabled && impl.loaded
        CouiPreferenceRow(
            title = impl.title,
            summary = impl.summary,
            icon = impl.icon,
            enabled = enabled,
            onClick = { updateChecked(!impl.checked) },
            trailing = {
                Switch(
                    checked = impl.checked,
                    onCheckedChange = ::updateChecked,
                    enabled = enabled,
                )
            },
        )
    }
    impl.configure()
    addElement(impl)

    launch(Dispatchers.Main) {
        impl.checked = withContext(Dispatchers.IO) { value.get() }
        impl.loaded = true
    }
    return impl
}
