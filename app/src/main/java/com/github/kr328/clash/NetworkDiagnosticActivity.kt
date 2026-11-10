package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.design.NetworkDiagnosticDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.NetworkDiagnosticRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manually triggered; performs only bounded probes and user-requested export. */
class NetworkDiagnosticActivity : BaseActivity<NetworkDiagnosticDesign>() {
    override suspend fun main() {
        val ui = NetworkDiagnosticDesign(this)
        setContentDesign(ui)
        while (isActive) {
            select<Unit> {
                events.onReceive { }
                ui.requests.onReceive { request ->
                    when (request) {
                        NetworkDiagnosticDesign.Request.Run -> {
                            if (!ui.isBusy) {
                                ui.setRunning(true)
                                launch {
                                    try {
                                        val report = NetworkDiagnosticRunner(
                                            this@NetworkDiagnosticActivity, clashRunning,
                                        ).run { line ->
                                            withContext(Dispatchers.Main) { ui.progress(line) }
                                        }
                                        ui.completed(report)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        ui.failed(e.javaClass.simpleName + ": " + (e.message ?: "Unknown"))
                                    } finally {
                                        ui.setRunning(false)
                                    }
                                }
                            }
                        }
                        NetworkDiagnosticDesign.Request.Export -> {
                            if (ui.report.isNotEmpty() && !ui.isBusy) {
                                val timestamp = SimpleDateFormat(
                                    "yyyyMMdd-HHmmss", Locale.US,
                                ).format(Date())
                                val uri = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/plain"),
                                    "ClashMeta-Network-Diagnostic-" + timestamp + ".txt",
                                )
                                if (uri != null) {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val output = contentResolver.openOutputStream(uri)
                                                ?: error("Cannot open selected document")
                                            output.bufferedWriter(Charsets.UTF_8).use { it.write(ui.report) }
                                        }
                                        ui.showToast(R.string.file_exported, ToastDuration.Long)
                                    } catch (e: Exception) {
                                        ui.showToast(e.message ?: "Export failed", ToastDuration.Long)
                                    }
                                }
                            }
                        }
                        NetworkDiagnosticDesign.Request.Copy -> {
                            if (ui.report.isNotEmpty() && !ui.isBusy) {
                                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                    ClipData.newPlainText("ClashMeta Network Diagnostic", ui.report),
                                )
                                ui.showToast(R.string.copied, ToastDuration.Short)
                            }
                        }
                    }
                }
            }
        }
    }
}
