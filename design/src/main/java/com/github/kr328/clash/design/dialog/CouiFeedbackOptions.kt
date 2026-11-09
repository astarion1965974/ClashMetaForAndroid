package com.github.kr328.clash.design.dialog

import android.content.Context
import android.view.View
import androidx.annotation.StringRes

/** Optional action for a transient feedback request.
 * A request with an action is displayed as a native COUI dialog; plain feedback
 * uses Android's system Toast and never mounts a custom ComposeView overlay.
 */
class CouiNoticeOptions(private val context: Context) {
    var actionLabel: String? = null
        private set
    var onAction: ((View) -> Unit)? = null
        private set

    fun setAction(@StringRes label: Int, onClick: (View) -> Unit) {
        actionLabel = context.getString(label)
        onAction = onClick
    }

    fun setAction(label: CharSequence, onClick: (View) -> Unit) {
        actionLabel = label.toString()
        onAction = onClick
    }
}
