package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.CouiPreferenceView
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy
import com.itos.coui.checkbox.Checkbox

class AppAdapter(
    private val context: Context,
    private val selected: MutableSet<String>,
    private val changed: () -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {
    data class RowState(val app: AppInfo, val checked: Boolean, val editable: Boolean, val route: String)

    class Holder internal constructor(row: CouiPreferenceView) : RecyclerView.ViewHolder(row) {
        var state: RowState? by mutableStateOf(null, neverEqualPolicy())
    }

    var apps: List<AppInfo> = emptyList()
    var saving = false
    var mode = AccessControlMode.AcceptAll

    fun rebindAll() = notifyItemRangeChanged(0, itemCount)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = CouiPreferenceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = Holder(row)
        row.setOnClickListener {
            val state = holder.state ?: return@setOnClickListener
            if (!state.editable) return@setOnClickListener
            if (!selected.remove(state.app.packageName)) selected.add(state.app.packageName)
            changed()
        }
        row.setContent {
            holder.state?.let { state ->
                CouiPreferenceRow(
                    title = state.app.label,
                    summary = "${state.app.packageName}\n${state.route}",
                    icon = state.app.icon,
                    iconSize = 40.dp,
                    enabled = state.editable,
                    onClick = { row.performClick() },
                    trailing = { Checkbox(checked = state.checked, enabled = state.editable) },
                )
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        val checked = app.packageName in selected
        val editable = !saving && mode != AccessControlMode.AcceptAll
        val route = context.getString(
            if (AppAccessPolicy.usesClash(mode, checked)) R.string.app_via_clash else R.string.app_bypass,
        )
        holder.state = RowState(app, checked, editable, route)
        holder.itemView.isEnabled = editable
        holder.itemView.contentDescription = "${app.label}, ${app.packageName}, $route"
    }

    override fun getItemCount(): Int = apps.size
}
