package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.design.util.format

class LogMessageAdapter(
    private val context: Context,
    private val copy: (LogMessage) -> Unit,
) : RecyclerView.Adapter<LogMessageAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var message: LogMessage? by mutableStateOf(null)
    }

    var messages: List<LogMessage> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            holder.message?.let { message ->
                CouiPreferenceRow(
                    title = "${message.level.name} · ${message.time.format(context, includeDate = false)}",
                    summary = message.message,
                    icon = null,
                    enabled = true,
                    onClick = { },
                    onLongClick = { copy(message) },
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.message = messages[position]
    }

    override fun getItemCount(): Int = messages.size
}
