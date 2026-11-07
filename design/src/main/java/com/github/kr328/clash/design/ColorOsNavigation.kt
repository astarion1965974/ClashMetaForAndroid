package com.github.kr328.clash.design

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity

/** Route the visible COUI back action to the exact same dispatcher as system back. */
internal fun Context.colorOsNavigateBack() {
    var ctx: Context = this
    repeat(16) {
        if (ctx is OnBackPressedDispatcherOwner) {
            ctx.onBackPressedDispatcher.onBackPressed()
            return
        }
        val wrapped = ctx as? ContextWrapper
        if (wrapped == null || wrapped.baseContext === ctx) return
        ctx = wrapped.baseContext
    }
}
