package com.github.kr328.clash.design.preference

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiContent
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

interface SelectableListPreference<T> : ClickablePreference {
    var selected: Int

    var listener: OnChangedListener?
}

fun <T> PreferenceScreen.selectableList(
    value: KMutableProperty0<T>,
    values: Array<T>,
    valuesText: Array<Int>,
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    configure: SelectableListPreference<T>.() -> Unit = {},
): SelectableListPreference<T> {
    val impl = object : SelectableListPreference<T>, ClickablePreference by clickable(title, icon) {
        override var selected: Int = 0
            set(value) {
                field = value

                this.summary = context.getText(valuesText[value])
            }
        override var listener: OnChangedListener? = null
    }

    impl.configure()

    launch(Dispatchers.Main) {
        val initial = withContext(Dispatchers.IO) {
            value.get()
        }

        impl.selected = values.indexOf(initial)

        impl.clicked {
            popupSelectMenu(impl, value, valuesText.map { context.getText(it) }, values)
        }
    }

    return impl
}

private fun <T> PreferenceScreen.popupSelectMenu(
    impl: SelectableListPreference<T>,
    value: KMutableProperty0<T>,
    valuesText: List<CharSequence>,
    values: Array<T>,
) {
    context.showCouiContent { dismiss ->
        Dialog(onDismissRequest = dismiss, title = impl.title.toString(),
            confirmAction = CouiDialogAction(context.getString(R.string.cancel), dismiss)) {
            valuesText.forEachIndexed { position, label ->
                Item(onClick = {
                    dismiss()
                    launch(Dispatchers.Main) {
                        withContext(Dispatchers.IO) { value.set(values[position]) }
                        impl.selected = position
                        impl.listener?.onChanged()
                    }
                }, trailing = { RadioButton(selected = position == impl.selected) }) {
                    Text(text = label.toString(), kind = CouiTextKind.PreferenceTitle)
                }
            }
        }
    }
}
