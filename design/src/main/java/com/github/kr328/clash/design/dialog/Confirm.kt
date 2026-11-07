package com.github.kr328.clash.design.dialog

import android.content.Context
import androidx.annotation.StringRes
import com.github.kr328.clash.design.R
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** COUI confirmation; can use route-specific positive and negative labels. */
suspend fun Context.requestCouiConfirm(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes positive: Int = R.string.ok,
    @StringRes negative: Int = R.string.cancel,
): Boolean = requestCouiConfirmText(title, getString(message), positive, negative)

/** Supports formatted content, such as the currently selected Wi-Fi SSID. */
suspend fun Context.requestCouiConfirmText(
    @StringRes title: Int,
    message: CharSequence,
    @StringRes positive: Int = R.string.ok,
    @StringRes negative: Int = R.string.cancel,
): Boolean = suspendCancellableCoroutine { continuation ->
    val close = showCouiContent { dismiss ->
        fun finish(value: Boolean) {
            dismiss()
            if (continuation.isActive) continuation.resume(value)
        }
        Dialog(
            onDismissRequest = { finish(false) },
            title = getString(title),
            message = message.toString(),
            confirmAction = CouiDialogAction(getString(positive), { finish(true) }),
            dismissAction = CouiDialogAction(getString(negative), { finish(false) }),
        )
    }
    continuation.invokeOnCancellation { close() }
}

fun Context.showCouiAlert(title: CharSequence, message: CharSequence) {
    showCouiContent { dismiss ->
        Dialog(
            onDismissRequest = dismiss,
            title = title.toString(),
            message = message.toString(),
            confirmAction = CouiDialogAction(getString(R.string.ok), dismiss),
        )
    }
}
