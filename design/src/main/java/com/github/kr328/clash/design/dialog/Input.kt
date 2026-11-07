package com.github.kr328.clash.design.dialog

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.Validator
import com.github.kr328.clash.design.util.ValidatorAcceptAll
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.InputDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String = requestModelTextInput(initial, title, null, hint, error, validator)!!

suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String? = suspendCancellableCoroutine { continuation ->
    val close = showCouiContent { dismiss ->
        var value by remember { mutableStateOf(TextFieldValue(initial.orEmpty(), TextRange(0, initial?.length ?: 0))) }
        val valid = validator(value.text)
        fun finish(result: String?) {
            dismiss()
            if (continuation.isActive) continuation.resume(result)
        }
        InputDialog(
            value = value,
            onValueChange = { value = it },
            onDismissRequest = { finish(initial) },
            title = title.toString(),
            message = if (reset != null) getString(R.string.coui_input_back_cancel) else null,
            placeholder = hint?.toString(),
            counter = if (!valid) error?.toString() else null,
            counterIsError = !valid,
            clearContentDescription = getString(R.string.coui_clear),
            confirmAction = CouiDialogAction(getString(R.string.ok), { finish(value.text) }, enabled = valid),
            dismissAction = CouiDialogAction(reset?.toString() ?: getString(R.string.cancel), {
                finish(if (reset == null) initial else null)
            }),
        )
    }
    continuation.invokeOnCancellation { close() }
}
