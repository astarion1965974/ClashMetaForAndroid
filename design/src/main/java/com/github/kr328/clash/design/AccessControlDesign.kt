package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import com.github.kr328.clash.design.adapter.AppAdapter
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.databinding.DesignAccessControlBinding
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy

class AccessControlDesign(
    context: Context,
    private val uiStore: UiStore,
    private val selected: MutableSet<String>,
    initialMode: AccessControlMode,
    running: Boolean,
) : Design<AccessControlDesign.Request>(context) {
    enum class Request { ReloadApps, SelectAll, SelectNone, SelectInvert, Import, Export, Save }
    private val binding = DesignAccessControlBinding.inflate(context.layoutInflater, context.root, false)
    private val original = selected.toSet()
    private val adapter = AppAdapter(context, selected) { refresh() }
    private val menu by lazy { AccessControlMenu(context, binding.menuView, uiStore, requests) }
    var mode = initialMode
        private set
    private val originalMode = initialMode
    var allApps: List<AppInfo> = emptyList()
        private set
    val apps get() = adapter.apps
    val dirty get() = selected != original || mode != originalMode
    private var loaded = false
    private var saving = false
    override val root: View get() = binding.root

    fun patchApps(apps: List<AppInfo>) { allApps = apps; loaded = true; refresh() }
    fun rebindAll() = refresh()
    fun setSaving(value: Boolean) { saving = value; adapter.saving = value; binding.modeView.isEnabled = !value; refresh() }
    fun updateRunning(running: Boolean) {
        binding.saveView.setText(if (running) R.string.route_save_reconnect else R.string.route_save)
    }
    fun select(action: AppAccessPolicy.Selection) {
        if (mode == AccessControlMode.AcceptAll || saving) return
        val result = AppAccessPolicy.select(selected, apps.map { it.packageName }.toSet(), action)
        selected.clear(); selected.addAll(result); refresh()
    }

    private fun refresh() {
        onBackStateChanged()
        val query = binding.keywordView.text?.toString().orEmpty().trim()
        val onlySelected = binding.selectedFilter.isChecked
        adapter.mode = mode
        adapter.apps = allApps.filter {
            (binding.systemFilter.isChecked || !it.systemApp || (onlySelected && it.packageName in selected)) &&
                (!onlySelected || it.packageName in selected) &&
                (it.label.contains(query, true) || it.packageName.contains(query, true))
        }
        adapter.notifyDataSetChanged()
        binding.countView.text = context.getString(R.string.app_count, selected.size, apps.size)
        binding.emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.setText(if (loaded) R.string.app_empty else R.string.app_loading)
        binding.modeHint.setText(when (mode) {
            AccessControlMode.AcceptAll -> R.string.app_hint_all
            AccessControlMode.AcceptSelected -> R.string.app_hint_allow
            AccessControlMode.DenySelected -> R.string.app_hint_bypass
        })
        binding.saveView.isEnabled = dirty && !saving && loaded
        binding.mainList.isEnabled = !saving
        if (saving) binding.saveView.setText(R.string.route_saving)
    }

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.mainList.applyLinearAdapter(context, adapter)
        binding.menuView.setOnClickListener { if (!saving) menu.show(mode != AccessControlMode.AcceptAll) }
        val modes = listOf(R.string.app_mode_all, R.string.app_mode_allow, R.string.app_mode_bypass).map(context::getString)
        binding.modeView.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modes)
        binding.modeView.setSelection(initialMode.ordinal)
        binding.modeView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mode = AccessControlMode.values()[position]; refresh()
            }
        }
        binding.keywordView.addTextChangedListener { refresh() }
        binding.systemFilter.isChecked = uiStore.accessControlSystemApp
        binding.systemFilter.setOnCheckedChangeListener { _, value -> uiStore.accessControlSystemApp = value; refresh() }
        binding.selectedFilter.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.saveView.setOnClickListener { requests.trySend(Request.Save) }
        updateRunning(running); refresh()
    }
}
