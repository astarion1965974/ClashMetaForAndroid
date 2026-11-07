package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.databinding.DesignManagedRoutingBinding
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import java.net.IDN
import java.text.DateFormat
import java.util.Date

class ManagedRoutingDesign(
    context: Context,
    val settings: ManagedRoutingSettings,
    private val targets: List<String>,
    running: Boolean,
) : Design<ManagedRoutingDesign.Request>(context) {
    enum class Request { Save, Update, Refresh }
    private val binding = DesignManagedRoutingBinding.inflate(context.layoutInflater, context.root, false)
    private var original = settings.copy()
    private var saving = false
    private var updating = false
    private var status: CharSequence = context.getString(R.string.managed_status_unloaded)
    private var statusPreference: ClickablePreference? = null
    private var updatePreference: ClickablePreference? = null
    val dirty get() = original != settings
    override val root: View get() = binding.root

    private fun changed() { onBackStateChanged(); binding.saveView.isEnabled = dirty && !saving && !updating }
    fun markSaved() { original = settings.copy(); changed() }
    fun setSaving(value: Boolean) {
        saving = value
        fun freeze(view: View) {
            view.isEnabled = !value
            if (view is ViewGroup) for (i in 0 until view.childCount) freeze(view.getChildAt(i))
        }
        freeze(binding.content)
        binding.saveView.setText(if (value) R.string.route_saving else R.string.route_save)
        changed()
    }
    fun updateRunning(value: Boolean) { if (!saving) binding.saveView.setText(if (value) R.string.route_save_reconnect else R.string.route_save) }
    fun setUpdating(value: Boolean) { updating = value; updatePreference?.enabled = !value; changed() }
    fun setProviderStatus(count: Int, oldestUpdate: Long?) {
        status = if (count == 0) context.getString(R.string.managed_status_unloaded)
        else context.getString(R.string.managed_status_loaded, count,
            oldestUpdate?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "—")
        statusPreference?.summary = status
    }

    private fun editDomains(direct: Boolean) {
        if (saving || updating) return
        context.showCouiContent { dismiss ->
            var input by remember { mutableStateOf((if (direct) settings.directDomains else settings.proxyDomains).joinToString("\n")) }
            var invalid by remember { mutableStateOf(false) }
            Dialog(onDismissRequest = dismiss, title = context.getString(if (direct) R.string.managed_direct else R.string.managed_proxy),
                message = context.getString(R.string.managed_domain_hint),
                dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                confirmAction = CouiDialogAction(context.getString(R.string.route_save), {
                try {
                    val domains = input.lineSequence().map(String::trim).filter(String::isNotEmpty)
                        .map { IDN.toASCII(it, IDN.USE_STD3_ASCII_RULES).lowercase() }.distinct().toList()
                    val other = if (direct) settings.proxyDomains else settings.directDomains
                    require(domains.none { it in other || it.length > 253 || it.contains(Regex("[^a-z0-9.-]")) || it.split('.').any { part -> part.isEmpty() || part.length > 63 || part.startsWith('-') || part.endsWith('-') } })
                    if (direct) settings.directDomains = domains else settings.proxyDomains = domains
                    dismiss(); buildScreen(); changed()
                } catch (_: IllegalArgumentException) { invalid = true }
                })) {
                BasicTextField(value = input, onValueChange = { input = it; invalid = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 100.dp)
                        .background(CouiTheme.colorScheme.background).padding(12.dp),
                    textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = CouiTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CouiTheme.colorScheme.accent), minLines = 4, maxLines = 10)
                if (invalid) Text(text = context.getString(R.string.managed_domain_invalid), kind = CouiTextKind.Description,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }
    }

    private fun buildScreen() {
        binding.content.removeAllViews()
        val screen = preferenceScreen(context) {
            switch(settings::enabled, title = R.string.managed_enabled, summary = R.string.managed_mode_hint) {
                listener = OnChangedListener { changed() }
            }
            category(R.string.managed_categories)
            val categories = listOf("cn" to R.string.managed_cn, "ai" to R.string.managed_ai,
                "media" to R.string.managed_media, "social" to R.string.managed_social,
                "dev" to R.string.managed_dev, "foreign" to R.string.managed_foreign,
                "fallback" to R.string.managed_fallback)
            val options = (listOf("Live · 默认代理", "DIRECT", "REJECT") + targets).distinct().filterNot { it.contains(',') }
            fun label(value: String): String = when(value) {
                "Live · 默认代理" -> context.getString(R.string.managed_default_proxy)
                "DIRECT" -> context.getString(R.string.managed_direct_out)
                "REJECT" -> context.getString(R.string.managed_reject_out)
                else -> value
            }
            for ((key, titleRes) in categories) {
                clickable(titleRes) {
                    val initial = settings.targets[key] ?: if (key == "cn") "DIRECT" else "Live · 默认代理"
                    summary = label(initial)
                    clicked {
                        if (!saving && !updating) context.showCouiMenu(options.map { option ->
                            CouiMenuOption(label(option), selected = option == initial) {
                                settings.targets = settings.targets + (key to option)
                                buildScreen(); changed()
                            }
                        })
                    }
                }
            }
            tips(R.string.managed_targets_note)
            switch(settings::blockAds, title = R.string.managed_ads) { listener = OnChangedListener { changed() } }
            switch(settings::domesticDns, title = R.string.managed_dns, summary = R.string.managed_dns_hint) { listener = OnChangedListener { changed() } }
            category(R.string.managed_exceptions)
            clickable(R.string.managed_direct) {
                summary = if (settings.directDomains.isEmpty()) context.getString(R.string.managed_no_exceptions) else context.getString(R.string.managed_exception_count, settings.directDomains.size)
                clicked { editDomains(true) }
            }
            clickable(R.string.managed_proxy) {
                summary = if (settings.proxyDomains.isEmpty()) context.getString(R.string.managed_no_exceptions) else context.getString(R.string.managed_exception_count, settings.proxyDomains.size)
                clicked { editDomains(false) }
            }
            category(R.string.managed_source)
            tips(R.string.managed_source_hint)
            tips(R.string.managed_order)
            statusPreference = clickable(R.string.managed_source) { summary = status; clicked { requests.trySend(Request.Refresh) } }
            updatePreference = clickable(R.string.managed_update) {
                enabled = !updating
                clicked { if (!saving && !updating) requests.trySend(Request.Update) }
            }
            clickable(R.string.managed_reset) {
                clicked {
                    if (!saving && !updating) context.showCouiContent { dismiss ->
                        Dialog(onDismissRequest = dismiss, title = context.getString(R.string.managed_reset),
                            message = context.getString(R.string.managed_reset_hint),
                            dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                            confirmAction = CouiDialogAction(context.getString(R.string.reset), {
                            settings.targets = emptyMap(); settings.directDomains = emptyList(); settings.proxyDomains = emptyList()
                            settings.blockAds = false; settings.domesticDns = true
                            dismiss(); buildScreen(); changed()
                        }))
                    }
                }
            }
        }
        binding.content.addView(screen.root)
    }
    init {
        binding.self = this
        binding.activityBarLayout.applyFrom(context)
        binding.saveView.setOnClickListener { requests.trySend(Request.Save) }
        buildScreen(); updateRunning(running); changed()
    }
}
