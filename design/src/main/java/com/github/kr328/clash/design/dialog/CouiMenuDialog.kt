package com.github.kr328.clash.design.dialog

import android.content.Context
import com.github.kr328.clash.design.R
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

internal data class CouiMenuOption(
    val label: String,
    val selected: Boolean? = null,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

internal fun Context.showCouiMenu(options: List<CouiMenuOption>) {
    showCouiContent { dismiss ->
        Dialog(onDismissRequest = dismiss, title = getString(R.string.more),
            confirmAction = CouiDialogAction(getString(R.string.cancel), dismiss)) {
            options.forEach { option ->
                Item(enabled = option.enabled, onClick = { dismiss(); option.action() },
                    trailing = { option.selected?.let { Checkbox(checked = it, enabled = option.enabled) } }) {
                    Text(text = option.label, kind = CouiTextKind.PreferenceTitle)
                }
            }
        }
    }
}

internal fun Context.showCouiMultiChoice(
    title: String,
    options: List<String>,
    initial: Collection<String>,
    onSave: (List<String>) -> Boolean,
) {
    showCouiContent { dismiss ->
        val chosen = remember { mutableStateListOf<String>().apply { addAll(initial) } }
        Dialog(onDismissRequest = dismiss, title = title,
            confirmAction = CouiDialogAction(getString(R.string.route_save), { if (onSave(chosen.toList())) dismiss() }),
            dismissAction = CouiDialogAction(getString(R.string.cancel), dismiss)) {
            options.forEach { value ->
                Item(onClick = { if (value in chosen) chosen.remove(value) else chosen.add(value) },
                    trailing = { Checkbox(checked = value in chosen) }) {
                    Text(text = value, kind = CouiTextKind.PreferenceTitle)
                }
            }
        }
    }
}

/** Native COUI single-choice menu reusable by the application activity. */
fun Context.showCouiSingleChoice(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    showCouiContent { dismiss ->
        Dialog(
            onDismissRequest = dismiss,
            title = title,
            confirmAction = CouiDialogAction(getString(R.string.cancel), dismiss),
        ) {
            labels.forEachIndexed { index, label ->
                Item(
                    onClick = { dismiss(); onSelected(index) },
                    trailing = { RadioButton(selected = index == selectedIndex) },
                ) {
                    Text(text = label, kind = CouiTextKind.PreferenceTitle)
                }
            }
        }
    }
}
