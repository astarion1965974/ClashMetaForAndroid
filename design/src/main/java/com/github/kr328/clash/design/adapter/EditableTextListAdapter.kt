package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.design.preference.CouiRowAction
import com.github.kr328.clash.design.preference.TextAdapter

class EditableTextListAdapter<T>(
    private val context: Context,
    val values: MutableList<T>,
    private val adapter: TextAdapter<T>,
) : RecyclerView.Adapter<EditableTextListAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var text: String by mutableStateOf("")
    }

    fun addElement(text: String) {
        val index = values.size
        values.add(adapter.to(text))
        notifyItemInserted(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            CouiPreferenceRow(
                title = holder.text,
                summary = null,
                icon = null,
                enabled = true,
                onClick = null,
                trailing = {
                    CouiRowAction(R.drawable.ic_baseline_close, stringResource(R.string.delete)) {
                        val index = holder.bindingAdapterPosition
                        if (index != RecyclerView.NO_POSITION) {
                            values.removeAt(index)
                            notifyItemRemoved(index)
                        }
                    }
                },
            )
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text = adapter.from(values[position])
    }

    override fun getItemCount(): Int = values.size
}
