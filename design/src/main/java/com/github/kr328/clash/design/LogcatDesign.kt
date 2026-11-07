package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.content.getSystemService
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full Compose/COUI log viewer; retains the old streaming and saved-log actions. */
class LogcatDesign(
    context: Context,
    private val streaming: Boolean,
) : Design<LogcatDesign.Request>(context) {
    enum class Request {
        Close, Delete, Export
    }

    private var messages by mutableStateOf<List<LogMessage>>(emptyList())
    private var visibleListState: LazyListState? = null

    override val root: View = ComposeView(context).apply {
        setContent {
            val scrollState = rememberLazyListState()
            SideEffect { visibleListState = scrollState }
            ColorOsLazySubPage(
                title = (context as? AppCompatActivity)?.title?.toString()
                    ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.logs),
                onBack = { context.colorOsNavigateBack() },
                listState = scrollState,
                reverseLayout = streaming,
                actions = {
                    if (streaming) {
                        ToolbarIcon(
                            ImageVector.vectorResource(R.drawable.ic_baseline_stop),
                            stringResource(R.string.close),
                        ) { requests.trySend(Request.Close) }
                    } else {
                        ToolbarIcon(
                            ImageVector.vectorResource(R.drawable.ic_baseline_delete),
                            stringResource(R.string.delete),
                        ) { requests.trySend(Request.Delete) }
                        ToolbarIcon(
                            ImageVector.vectorResource(R.drawable.ic_baseline_publish),
                            stringResource(R.string.export),
                        ) { requests.trySend(Request.Export) }
                    }
                },
            ) {
                itemsIndexed(messages) { _, message ->
                    CouiPreferenceRow(
                        title = "${message.level.name} · ${message.time.format(context, includeDate = false)}",
                        summary = message.message,
                        icon = null,
                        enabled = true,
                        onClick = {},
                        onLongClick = { copyMessage(message) },
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun patchMessages(newMessages: List<LogMessage>, removed: Int, appended: Int) {
        withContext(Dispatchers.Main) {
            val scroll = visibleListState
            val followTail = streaming && scroll?.firstVisibleItemIndex == 0
            messages = newMessages
            // Preserve previous reverse-layout follow-tail behavior for streaming.
            if (followTail && newMessages.isNotEmpty()) {
                scroll?.scrollToItem(newMessages.size - 1)
            }
        }
    }

    private fun copyMessage(message: LogMessage) {
        launch {
            context.getSystemService<ClipboardManager>()?.setPrimaryClip(
                ClipData.newPlainText("log_message", message.message),
            )
            showToast(R.string.copied, ToastDuration.Short)
        }
    }
}
