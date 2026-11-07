package com.github.kr328.clash.design.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.kr328.clash.design.R
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelProgressBarConfigure {
    var isIndeterminate: Boolean
    var text: String?
    var progress: Int
    var max: Int
}

interface ModelProgressBarScope {
    suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit)
}

suspend fun Context.withModelProgressBar(block: suspend ModelProgressBarScope.() -> Unit) {
    val state = object : ModelProgressBarConfigure {
        override var isIndeterminate by mutableStateOf(true)
        override var text: String? by mutableStateOf(null)
        override var progress by mutableIntStateOf(0)
        override var max by mutableIntStateOf(100)
    }
    val close = showCouiContent {
        Dialog(onDismissRequest = {}, title = state.text ?: getString(R.string.initializing),
            confirmAction = CouiDialogAction(getString(R.string.initializing), {}, enabled = false),
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
            val fraction = if (state.isIndeterminate || state.max <= 0) 0f
                else (state.progress.toFloat() / state.max).coerceIn(0f, 1f)
            if (!state.isIndeterminate) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(6.dp).background(CouiTheme.colorScheme.outlineVariant, CircleShape)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(CouiTheme.colorScheme.accent, CircleShape))
                }
                Text(text = "${(fraction * 100).toInt()}%", kind = CouiTextKind.Description,
                    modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
    val scope = object : ModelProgressBarScope {
        override suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit) {
            withContext(Dispatchers.Main) { state.block() }
        }
    }
    try { scope.block() } finally { close() }
}
