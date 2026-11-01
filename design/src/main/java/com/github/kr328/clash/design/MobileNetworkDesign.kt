package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignMobileNetworkBinding
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.store.MobileNetworkSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MobileNetworkDesign(context: Context, val settings: MobileNetworkSettings,
    private val nodes: List<String>, private val running: Boolean) : Design<MobileNetworkDesign.Request>(context) {
    enum class Request { Save, BindWifi, Permissions, AppRouting }
    private val binding = DesignMobileNetworkBinding.inflate(context.layoutInflater, context.root, false)
    private var original = settings.copy()
    private var saving = false
    val dirty get() = original != settings
    override val root: View get() = binding.root
    private fun changed() { onBackStateChanged(); binding.saveView.isEnabled = dirty && !saving }
    fun markSaved() { original = settings.copy(); changed() }
    fun setSaving(value: Boolean) { saving = value; fun freeze(v: View) {
        v.isEnabled = !value
        if(v is android.view.ViewGroup) for(i in 0 until v.childCount) freeze(v.getChildAt(i))
    }; freeze(binding.content); changed() }
    fun bind(ssid: String) { settings.trustedSSIDs = (settings.trustedSSIDs + ssid).distinct(); build(); changed() }
    private fun pick(ai: Boolean) {
        if (saving) return
        val chosen = (if (ai) settings.aiNodes else settings.stableNodes).toMutableList()
        val options = (chosen + nodes).distinct()
        if (options.isEmpty()) { MaterialAlertDialogBuilder(context).setMessage(R.string.mobile_no_nodes).setPositiveButton(R.string.ok,null).show(); return }
        val dialog = MaterialAlertDialogBuilder(context).setTitle(if (ai) R.string.mobile_ai else R.string.mobile_stable)
            .setMultiChoiceItems(options.toTypedArray(), options.map { it in chosen }.toBooleanArray()) { _, index, checked ->
                if (checked) { if (options[index] !in chosen) chosen.add(options[index]) } else chosen.remove(options[index])
            }.setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.route_save,null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            if (chosen.size > 5) { android.widget.Toast.makeText(context,R.string.mobile_five,android.widget.Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (ai) settings.aiNodes = chosen.toList() else settings.stableNodes = chosen.toList()
            dialog.dismiss(); build(); changed()
        } }; dialog.show()
    }
    private fun build() {
        binding.content.removeAllViews()
        val screen = preferenceScreen(context) {
            switch(settings::enabled,title=R.string.mobile_enabled,summary=R.string.mobile_enabled_hint) { listener=OnChangedListener { changed() } }
            category(R.string.mobile_nodes)
            clickable(R.string.mobile_stable) { summary=settings.stableNodes.joinToString(" → ").ifBlank { context.getString(R.string.mobile_empty) }; clicked { pick(false) } }
            clickable(R.string.mobile_ai) { summary=settings.aiNodes.joinToString(" → ").ifBlank { context.getString(R.string.mobile_empty) }; clicked { pick(true) } }
            tips(R.string.mobile_pick_hint)
            switch(settings::secureDns,title=R.string.mobile_dns,summary=R.string.mobile_dns_hint) { listener=OnChangedListener { changed() } }
            switch(settings::wifiUpdates,title=R.string.mobile_updates,summary=R.string.mobile_updates_hint) { listener=OnChangedListener { changed() } }
            clickable(R.string.mobile_apps,summary=R.string.mobile_apps_hint) { clicked { if (!saving) requests.trySend(Request.AppRouting) } }
            category(R.string.mobile_home)
            switch(settings::trustedWifiEnabled,title=R.string.mobile_home_enabled,summary=R.string.mobile_home_hint) { listener=OnChangedListener { changed() } }
            clickable(R.string.mobile_bind) { clicked { if (!saving) requests.trySend(Request.BindWifi) } }
            clickable(R.string.mobile_bound) {
                summary=settings.trustedSSIDs.joinToString(", ").ifBlank { context.getString(R.string.mobile_none_bound) }
                clicked {
                    val all=settings.trustedSSIDs; val keep=all.toMutableSet()
                    MaterialAlertDialogBuilder(context).setTitle(R.string.mobile_bound)
                        .setMultiChoiceItems(all.toTypedArray(),BooleanArray(all.size){true}) { _,i,checked -> if(checked)keep.add(all[i])else keep.remove(all[i]) }
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.route_save){_,_->settings.trustedSSIDs=all.filter{it in keep};build();changed()}.show()
                }
            }
            clickable(R.string.mobile_permissions,summary=R.string.mobile_permission_hint) { clicked { requests.trySend(Request.Permissions) } }
        }
        binding.content.addView(screen.root)
    }
    init {
        binding.self=this; binding.activityBarLayout.applyFrom(context)
        binding.saveView.setText(if(running) R.string.route_save_reconnect else R.string.route_save)
        binding.saveView.setOnClickListener { if(!saving)requests.trySend(Request.Save) }
        build();changed()
    }
}
