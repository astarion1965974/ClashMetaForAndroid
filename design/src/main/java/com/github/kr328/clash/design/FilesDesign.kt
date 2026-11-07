package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.util.DirectoryBackCallback
import com.github.kr328.clash.design.util.ValidatorFileName
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** COUI configuration file manager with native lazy list and glass toolbar actions. */
class FilesDesign(context: Context) : Design<FilesDesign.Request>(context) {
    sealed class Request {
        data class OpenFile(val file: File) : Request()
        data class OpenDirectory(val file: File) : Request()
        data class RenameFile(val file: File) : Request()
        data class DeleteFile(val file: File) : Request()
        data class ImportFile(val file: File?) : Request()
        data class ExportFile(val file: File) : Request()
        object PopStack : Request()
    }

    var configurationEditable by mutableStateOf(false)
    private var currentInBaseDir by mutableStateOf(true)
    private var files by mutableStateOf<List<File>>(emptyList())
    private var currentTime by mutableLongStateOf(System.currentTimeMillis())

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsLazySubPage(
                title = (context as? AppCompatActivity)?.title?.toString()
                    ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profiles),
                onBack = { context.colorOsNavigateBack() },
                actions = {
                    if (!currentInBaseDir) ToolbarIcon(
                        ImageVector.vectorResource(R.drawable.ic_baseline_add),
                        stringResource(R.string._new),
                    ) { requestNew() }
                },
            ) {
                items(files, key = { it.id }) { file ->
                    val icon = if (file.isDirectory)
                        R.drawable.ic_outline_folder else R.drawable.ic_outline_article
                    val summary = if (file.isDirectory) null else
                        "${file.size.toBytesString()} · ${(currentTime - file.lastModified).elapsedIntervalString(context)}"
                    CouiPreferenceRow(
                        title = file.name,
                        summary = summary,
                        icon = context.getDrawableCompat(icon),
                        enabled = true,
                        onClick = { requestOpen(file) },
                        trailing = {
                            CouiRowAction(
                                R.drawable.ic_baseline_more_vert,
                                stringResource(R.string.more),
                            ) { requestMore(file) }
                        },
                    )
                }
            }
        }
    }

    suspend fun swapFiles(files: List<File>, currentInBaseDir: Boolean) {
        withContext(Dispatchers.Main) {
            this@FilesDesign.files = files
            this@FilesDesign.currentInBaseDir = currentInBaseDir
        }
    }

    fun captureBackPreview(): ByteArray? = DirectoryBackCallback.capture(root)
    fun updateElapsed() { currentTime = System.currentTimeMillis() }

    suspend fun requestFileName(name: String): String =
        context.requestModelTextInput(
            initial = name,
            title = context.getText(R.string.file_name),
            hint = context.getText(R.string.file_name),
            error = context.getText(R.string.invalid_file_name),
            validator = ValidatorFileName,
        )

    private fun requestOpen(file: File) {
        requests.trySend(
            if (file.isDirectory) Request.OpenDirectory(file) else Request.OpenFile(file),
        )
    }

    fun requestNew() { requests.trySend(Request.ImportFile(null)) }

    private fun requestMore(file: File) {
        val options = mutableListOf<CouiMenuOption>()
        fun add(resource: Int, request: Request) {
            options.add(CouiMenuOption(context.getString(resource)) { requests.trySend(request) })
        }
        if (!file.isDirectory && (!currentInBaseDir || configurationEditable))
            add(R.string.import_, Request.ImportFile(file))
        if (!file.isDirectory && file.size > 0) add(R.string.export, Request.ExportFile(file))
        if (!currentInBaseDir) {
            add(R.string.rename, Request.RenameFile(file))
            add(R.string.delete, Request.DeleteFile(file))
        }
        if (options.isNotEmpty()) context.showCouiMenu(options)
    }
}
