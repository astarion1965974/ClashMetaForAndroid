package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.LocalView
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.dialog.CouiNoticeOptions
import com.github.kr328.clash.design.dialog.showCouiNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

abstract class Design<R>(val context: Context) :
    CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    abstract val root: View

    // Designs notify synchronously when dirty/busy state changes, before the next gesture.
    var onBackStateChanged: () -> Unit = {}

    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    suspend fun showToast(
        resId: Int,
        duration: ToastDuration,
        configure: CouiNoticeOptions.() -> Unit = {}
    ) {
        return showToast(context.getString(resId), duration, configure)
    }

    suspend fun showToast(
        message: CharSequence,
        duration: ToastDuration,
        configure: CouiNoticeOptions.() -> Unit = {}
    ) {
        withContext(Dispatchers.Main) {
            val options = CouiNoticeOptions(context).apply(configure)
            when {
                duration == ToastDuration.Indefinite ->
                    context.showCouiNotice(message, duration, options)
                options.onAction != null -> context.showCouiContent { dismiss ->
                    val anchorView = LocalView.current
                    Dialog(
                        onDismissRequest = dismiss,
                        title = context.getString(R.string.error),
                        message = message.toString(),
                        confirmAction = CouiDialogAction(
                            options.actionLabel ?: context.getString(R.string.ok),
                            { options.onAction?.invoke(anchorView); dismiss() },
                        ),
                        dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                    )
                }
                else -> Toast.makeText(
                    context, message,
                    if (duration == ToastDuration.Short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

}