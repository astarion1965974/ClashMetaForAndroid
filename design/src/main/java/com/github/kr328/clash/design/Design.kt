package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
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

    val surface = Surface()
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
            context.showCouiNotice(message, duration, options)
        }
    }

    init {
        when (context) {
            is AppCompatActivity -> {
                context.window.decorView.setOnInsertsChangedListener {
                    if (surface.insets != it) {
                        surface.insets = it
                    }
                }
            }
        }
    }
}