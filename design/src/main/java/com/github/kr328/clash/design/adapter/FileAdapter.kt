package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString

class FileAdapter(
    private val context: Context,
    private val open: (File) -> Unit,
    private val more: (File) -> Unit,
) : RecyclerView.Adapter<FileAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var file: File? by mutableStateOf(null)
    }

    private var currentTime by mutableLongStateOf(System.currentTimeMillis())
    var files: List<File> = emptyList()

    fun updateElapsed() {
        currentTime = System.currentTimeMillis()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            holder.file?.let { file ->
                val icon = if (file.isDirectory) R.drawable.ic_outline_folder else R.drawable.ic_outline_article
                val summary = if (file.isDirectory) null else {
                    "${file.size.toBytesString()} · ${(currentTime - file.lastModified).elapsedIntervalString(context)}"
                }
                CouiPreferenceRow(
                    title = file.name,
                    summary = summary,
                    icon = context.getDrawableCompat(icon),
                    enabled = true,
                    onClick = { open(file) },
                    trailing = {
                        CouiRowAction(R.drawable.ic_baseline_more_vert, stringResource(R.string.more)) {
                            more(file)
                        }
                    },
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.file = files[position]
    }

    override fun getItemCount(): Int = files.size
}
