package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.kr328.clash.design.adapter.AppAdapter
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.databinding.DesignAccessControlBinding
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme

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
    private var isSaving by mutableStateOf(false)
    private var isRunning = running
    private var keyword by mutableStateOf("")
    private var selectedOnly by mutableStateOf(false)
    private var showSystem by mutableStateOf(uiStore.accessControlSystemApp)
    private var count by mutableStateOf("")
    private var modeHint by mutableStateOf("")
    private var modeLabel by mutableStateOf("")
    override val root: View get() = binding.root

    fun patchApps(apps: List<AppInfo>) { allApps = apps; loaded = true; refresh() }
    fun rebindAll() = refresh()
    fun setSaving(value: Boolean) { isSaving = value; adapter.saving = value; refresh() }
    fun updateRunning(running: Boolean) {
        isRunning = running
        binding.saveView.setText(if (running) R.string.route_save_reconnect else R.string.route_save)
    }
    fun select(action: AppAccessPolicy.Selection) {
        if (mode == AccessControlMode.AcceptAll || isSaving) return
        val result = AppAccessPolicy.select(selected, apps.map { it.packageName }.toSet(), action)
        selected.clear(); selected.addAll(result); refresh()
    }

    private fun refresh() {
        onBackStateChanged()
        val query = keyword.trim()
        val onlySelected = selectedOnly
        adapter.mode = mode
        adapter.apps = allApps.filter {
            (showSystem || !it.systemApp || (onlySelected && it.packageName in selected)) &&
                (!onlySelected || it.packageName in selected) &&
                (it.label.contains(query, true) || it.packageName.contains(query, true))
        }
        adapter.notifyDataSetChanged()
        count = context.getString(R.string.app_count, selected.size, apps.size)
        binding.emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyView.setText(if (loaded) R.string.app_empty else R.string.app_loading)
        modeHint = context.getString(when (mode) {
            AccessControlMode.AcceptAll -> R.string.app_hint_all
            AccessControlMode.AcceptSelected -> R.string.app_hint_allow
            AccessControlMode.DenySelected -> R.string.app_hint_bypass
        })
        modeLabel = context.getString(when (mode) {
            AccessControlMode.AcceptAll -> R.string.app_mode_all
            AccessControlMode.AcceptSelected -> R.string.app_mode_allow
            AccessControlMode.DenySelected -> R.string.app_mode_bypass
        })
        binding.saveView.isEnabled = dirty && !isSaving && loaded
        binding.mainList.isEnabled = !isSaving
        binding.saveView.setText(when {
            isSaving -> R.string.route_saving
            isRunning -> R.string.route_save_reconnect
            else -> R.string.route_save
        })
    }

    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.mainList.applyLinearAdapter(context, adapter)
        binding.menuView.setOnClickListener { if (!isSaving) menu.show(mode != AccessControlMode.AcceptAll) }
        binding.controlsView.setContent {
            CouiTheme(darkTheme = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
                AccessControls(modeLabel, modeHint, keyword, selectedOnly, showSystem, count, !isSaving,
                    onMode = { chooseMode() },
                    onKeyword = { keyword = it; refresh() },
                    onSelectedOnly = { selectedOnly = it; refresh() },
                    onSystem = { showSystem = it; uiStore.accessControlSystemApp = it; refresh() })
            }
        }
        binding.saveView.setOnClickListener { requests.trySend(Request.Save) }
        updateRunning(running); refresh()
    }

    private fun chooseMode() {
        if (isSaving) return
        val labels = listOf(R.string.app_mode_all, R.string.app_mode_allow, R.string.app_mode_bypass)
        context.showCouiContent { dismiss ->
            Dialog(onDismissRequest = dismiss, title = context.getString(R.string.access_control_mode),
                confirmAction = CouiDialogAction(context.getString(R.string.cancel), dismiss)) {
                labels.forEachIndexed { index, label ->
                    Item(onClick = { mode = AccessControlMode.values()[index]; dismiss(); refresh() },
                        trailing = { RadioButton(selected = mode.ordinal == index) }) {
                        Text(text = context.getString(label), kind = CouiTextKind.PreferenceTitle)
                    }
                }
            }
        }
    }
}
