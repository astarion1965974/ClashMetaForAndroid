package com.github.kr328.clash.design.preference

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.util.getHtml

interface TipsPreference : Preference {
    var text: CharSequence?
}

/** Legacy settings still build a View tree, but their help text is native COUI. */
fun PreferenceScreen.tips(
    @StringRes text: Int,
    configure: TipsPreference.() -> Unit = {},
): TipsPreference {
    val row = CouiPreferenceView(context)
    val impl = object : TipsPreference {
        override var text: CharSequence? by mutableStateOf(context.getHtml(text))
        override val view: View = row
        override var enabled: Boolean
            get() = row.isEnabled
            set(value) { row.isEnabled = value }
    }
    row.setContent {
        CouiRichText(
            value = impl.text,
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 24.dp, vertical = 12.dp,
            ),
        )
    }
    impl.configure()
    addElement(impl)
    return impl
}
