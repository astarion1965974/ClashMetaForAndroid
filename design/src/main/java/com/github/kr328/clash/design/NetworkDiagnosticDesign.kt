package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** Dedicated ColorOS 17 native-style diagnostics surface. */
class NetworkDiagnosticDesign(context: Context) : Design<NetworkDiagnosticDesign.Request>(context) {
    enum class Request { Run, Export, Copy }

    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf(context.getString(R.string.diagnostic_idle))
    private var lastSteps by mutableStateOf(emptyList<String>())
    private var currentReport by mutableStateOf("")

    val report: String get() = currentReport
    val isBusy: Boolean get() = busy

    fun setRunning(value: Boolean) { busy = value }
    fun progress(line: String) {
        progress = line
        lastSteps = (lastSteps + line).takeLast(12)
    }
    fun completed(text: String) {
        currentReport = text
        progress = context.getString(R.string.diagnostic_finished)
    }
    fun failed(message: String) {
        progress = context.getString(R.string.diagnostic_failed) + ": " + message
        lastSteps = (lastSteps + progress).takeLast(12)
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsSubPage(
                title = stringResource(R.string.diagnostic_title),
                onBack = { context.colorOsNavigateBack() },
            ) {
                Group(
                    title = stringResource(R.string.diagnostic_actions),
                    footer = stringResource(R.string.diagnostic_privacy),
                ) {
                    NativeCouiAction(
                        title = stringResource(R.string.diagnostic_run),
                        summary = stringResource(R.string.diagnostic_run_hint),
                        enabled = !busy,
                    ) { requests.trySend(Request.Run) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.diagnostic_export),
                        summary = stringResource(R.string.diagnostic_export_hint),
                        enabled = !busy && currentReport.isNotEmpty(),
                    ) { requests.trySend(Request.Export) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.diagnostic_copy),
                        summary = stringResource(R.string.diagnostic_copy_hint),
                        enabled = !busy && currentReport.isNotEmpty(),
                    ) { requests.trySend(Request.Copy) }
                }
                Group(title = stringResource(R.string.diagnostic_status)) {
                    Text(
                        text = progress,
                        kind = CouiTextKind.PreferenceTitle,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                if (lastSteps.isNotEmpty()) {
                    Group(title = stringResource(R.string.diagnostic_steps)) {
                        Text(
                            text = lastSteps.joinToString("\n"),
                            kind = CouiTextKind.Description,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
                if (currentReport.isNotEmpty() && !busy) {
                    Group(title = stringResource(R.string.diagnostic_preview)) {
                        Text(
                            text = currentReport.take(2800),
                            kind = CouiTextKind.Description,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}
