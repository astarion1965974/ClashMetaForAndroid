package com.github.kr328.clash.design.preference

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

/** COUI section heading shared by existing preference screens. */
fun PreferenceScreen.category(@StringRes text: Int) {
    val row = CouiPreferenceView(context)
    row.setContent {
        Text(
            text = stringResource(text),
            kind = CouiTextKind.GroupTitle,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(
                start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp,
            ),
        )
    }
    addElement(object : Preference {
        override val view: View = row
        override var enabled: Boolean
            get() = row.isEnabled
            set(value) { row.isEnabled = value }
    })
}
