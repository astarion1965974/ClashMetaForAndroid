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

class EditableTextMapAdapter<K, V>(
    private val context: Context,
    val values: MutableList<Pair<K, V>>,
    private val keyAdapter: TextAdapter<K>,
    private val valueAdapter: TextAdapter<V>,
) : RecyclerView.Adapter<EditableTextMapAdapter.Holder>() {
    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var key: String by mutableStateOf("")
        var value: String by mutableStateOf("")
    }

    fun addElement(key: String, value: String) {
        val index = values.size
        values.add(keyAdapter.to(key) to valueAdapter.to(value))
        notifyItemInserted(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setContent {
            CouiPreferenceRow(
                title = holder.key,
                summary = holder.value,
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
        val (key, value) = values[position]
        holder.key = keyAdapter.from(key)
        holder.value = valueAdapter.from(value)
    }

    override fun getItemCount(): Int = values.size
}
