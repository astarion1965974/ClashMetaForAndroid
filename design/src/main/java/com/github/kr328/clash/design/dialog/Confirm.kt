package com.github.kr328.clash.design.dialog

import android.content.Context
import androidx.annotation.StringRes
import com.github.kr328.clash.design.R
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestCouiConfirm(@StringRes title: Int, @StringRes message: Int): Boolean =
    suspendCancellableCoroutine { continuation ->
        val close = showCouiContent { dismiss ->
            fun finish(value: Boolean) { dismiss(); if (continuation.isActive) continuation.resume(value) }
            Dialog(onDismissRequest = { finish(false) }, title = getString(title), message = getString(message),
                confirmAction = CouiDialogAction(getString(R.string.ok), { finish(true) }),
                dismissAction = CouiDialogAction(getString(R.string.cancel), { finish(false) }))
        }
        continuation.invokeOnCancellation { close() }
    }

fun Context.showCouiAlert(title: CharSequence, message: CharSequence) {
    showCouiContent { dismiss ->
        Dialog(onDismissRequest = dismiss, title = title.toString(), message = message.toString(),
            confirmAction = CouiDialogAction(getString(R.string.ok), dismiss))
    }
}
