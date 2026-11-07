package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.itos.coui.radiobutton.RadioButton

/**
 * Legacy ListView bridge using real COUI rows instead of platform
 * simple_list_item_1 with an opaque Material accent rectangle.
 */
class PopupListAdapter(
    private val context: Context,
    private val texts: List<CharSequence>,
    private val selected: Int,
) : BaseAdapter() {
    private class Row(context: Context) : FrameLayout(context) {
        var label by mutableStateOf("")
        var checked by mutableStateOf(false)

        init {
            val view = CouiPreferenceView(context)
            view.setContent {
                CouiPreferenceRow(
                    title = label,
                    summary = null,
                    icon = null,
                    leading = { RadioButton(selected = checked) },
                    enabled = true,
                    onClick = null,
                )
            }
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    override fun getCount(): Int = texts.size
    override fun getItem(position: Int): Any = texts[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = (convertView as? Row) ?: Row(context)
        row.label = texts[position].toString()
        row.checked = position == selected
        return row
    }
}
