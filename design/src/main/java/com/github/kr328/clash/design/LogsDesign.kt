package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.util.format
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fully native COUI saved-logs page, preserving logcat and deletion requests. */
class LogsDesign(context: Context) : Design<LogsDesign.Request>(context) {
    sealed class Request {
        object StartLogcat : Request()
        object DeleteAll : Request()
        data class OpenFile(val file: LogFile) : Request()
    }

    private var logFiles by mutableStateOf<List<LogFile>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsLazySubPage(
                title = stringResource(R.string.logs),
                onBack = { (context as? AppCompatActivity)?.onSupportNavigateUp() },
                actions = {
                    ToolbarIcon(
                        ImageVector.vectorResource(R.drawable.ic_baseline_clear_all),
                        stringResource(R.string.delete_all_logs),
                    ) { requests.trySend(Request.DeleteAll) }
                },
            ) {
                item(key = "start-logcat") {
                    Group {
                        PageRow(
                            R.drawable.ic_baseline_adb,
                            stringResource(R.string.clash_logcat),
                            stringResource(R.string.tap_to_start),
                        ) { requests.trySend(Request.StartLogcat) }
                    }
                }
                item(key = "history-heading") {
                    Text(
                        text = stringResource(R.string.history),
                        kind = CouiTextKind.GroupTitle,
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp,
                        ),
                    )
                }
                items(logFiles, key = { it.fileName }) { file ->
                    CouiPreferenceRow(
                        title = file.fileName,
                        summary = file.date.format(context),
                        icon = null,
                        enabled = true,
                        onClick = { requests.trySend(Request.OpenFile(file)) },
                    )
                }
            }
        }
    }

    suspend fun patchLogs(logs: List<LogFile>) {
        withContext(Dispatchers.Main) {
            logFiles = logs
        }
    }

    suspend fun requestDeleteAll(): Boolean = withContext(Dispatchers.Main) {
        context.requestCouiConfirm(R.string.delete_all_logs, R.string.delete_all_logs_warn)
    }
}
