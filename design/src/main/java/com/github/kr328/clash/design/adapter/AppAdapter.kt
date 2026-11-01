package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterAppBinding
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy

class AppAdapter(
    private val context: Context,
    private val selected: MutableSet<String>,
    private val changed: () -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {
    class Holder(val binding: AdapterAppBinding) : RecyclerView.ViewHolder(binding.root)
    var apps: List<AppInfo> = emptyList()
    var saving = false
    var mode = AccessControlMode.AcceptAll

    fun rebindAll() = notifyItemRangeChanged(0, itemCount)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        AdapterAppBinding.inflate(context.layoutInflater, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = apps[position]
        val checked = current.packageName in selected
        val editable = !saving && mode != AccessControlMode.AcceptAll
        holder.binding.apply {
            app = current
            selected = checked
            switchView.isEnabled = editable
            routeView.setText(if (AppAccessPolicy.usesClash(mode, checked)) R.string.app_via_clash else R.string.app_bypass)
            routeView.setTextColor(context.resolveThemedColor(
                if (AppAccessPolicy.usesClash(mode, checked)) com.google.android.material.R.attr.colorPrimary
                else R.attr.colorLiveMuted
            ))
            executePendingBindings()
            root.isEnabled = editable
            root.contentDescription = "${current.label}, ${current.packageName}, ${routeView.text}"
            root.setOnClickListener {
                if (!editable) return@setOnClickListener
                if (!this@AppAdapter.selected.remove(current.packageName)) this@AppAdapter.selected.add(current.packageName)
                changed()
            }
        }
    }
    override fun getItemCount() = apps.size
}
