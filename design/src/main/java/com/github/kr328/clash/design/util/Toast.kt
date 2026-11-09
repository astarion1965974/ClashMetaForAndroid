package com.github.kr328.clash.design.util

import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Error feedback is a single native COUI dialog, never a stacked toast -> details dialog. */
suspend fun Design<*>.showExceptionToast(message: CharSequence) {
    withContext(Dispatchers.Main) {
        context.showCouiAlert(context.getText(R.string.error), message)
    }
}

suspend fun Design<*>.showExceptionToast(exception: Exception) {
    showExceptionToast(exception.message ?: "Unknown")
}
