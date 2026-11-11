package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiSwitch
import com.github.kr328.clash.design.preference.CouiRichText
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import com.github.kr328.clash.design.util.getHtml
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import java.net.IDN
import java.text.DateFormat
import java.util.Date

/** Native COUI managed routing form; updates preserve the existing Store contract. */
class ManagedRoutingDesign(
    context: Context,
    val settings: ManagedRoutingSettings,
    private val targets: List<String>,
    running: Boolean,
) : Design<ManagedRoutingDesign.Request>(context) {
    enum class Request { Save, Update, Refresh, SystemVpn, DirectHistory }
    private var original = settings.copy()
    private var savingState by mutableStateOf(false)
    private var updatingState by mutableStateOf(false)
    private var runningNow by mutableStateOf(running)
    private var status by mutableStateOf<CharSequence>(context.getString(R.string.managed_status_unloaded))
    private var revision by mutableIntStateOf(0)

    val dirty: Boolean get() = original != settings
    private fun changed() {
        revision++
        onBackStateChanged()
    }
    fun markSaved() { original = settings.copy(); changed() }
    fun setSaving(value: Boolean) { savingState = value; changed() }
    fun updateRunning(value: Boolean) { runningNow = value }
    fun setUpdating(value: Boolean) { updatingState = value; changed() }
    fun setProviderStatus(count: Int, oldestUpdate: Long?) {
        status = if (count == 0) context.getString(R.string.managed_status_unloaded)
        else context.getString(
            R.string.managed_status_loaded, count,
            oldestUpdate?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            } ?: "—",
        )
    }

    fun showDirectHistory(message: String) {
        context.showCouiContent { dismiss ->
            Dialog(
                onDismissRequest = dismiss,
                title = context.getString(R.string.managed_direct_history),
                message = context.getString(R.string.managed_direct_history_hint),
                confirmAction = CouiDialogAction(context.getString(R.string.close), dismiss),
            ) {
                Text(
                    text = message,
                    kind = CouiTextKind.Description,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        .heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    private fun targetName(value: String): String = when (value) {
        "Live · 默认代理" -> context.getString(R.string.managed_default_proxy)
        "DIRECT" -> context.getString(R.string.managed_direct_out)
        "REJECT" -> context.getString(R.string.managed_reject_out)
        else -> value
    }

    private fun defaultTarget(category: String): String = when (category) {
        "cn" -> "DIRECT"
        "claude", "muse" -> settings.targets["ai"] ?: "Live · 默认代理"
        else -> "Live · 默认代理"
    }

    private fun protectionName(value: String): String = context.getString(when (value) {
        "strict" -> R.string.managed_protection_strict
        "full" -> R.string.managed_protection_full
        else -> R.string.managed_protection_standard
    })

    private fun chooseProtection() {
        if (savingState || updatingState) return
        context.showCouiMenu(listOf("standard", "strict", "full").map { level ->
            CouiMenuOption(protectionName(level), selected = level == settings.protectionLevel) {
                settings.protectionLevel = level
                settings.enabled = true
                changed()
            }
        })
    }

    private fun chooseTarget(category: String) {
        if (savingState || updatingState) return
        val current = settings.targets[category]
            ?: defaultTarget(category)
        val options = (listOf("Live · 默认代理", "DIRECT", "REJECT") + targets)
            .distinct().filterNot { it.contains(',') }
        context.showCouiMenu(options.map { choice ->
            CouiMenuOption(targetName(choice), selected = choice == current) {
                settings.targets = settings.targets + (category to choice)
                changed()
            }
        })
    }

    private fun editDomains(direct: Boolean) {
        if (savingState || updatingState) return
        context.showCouiContent { dismiss ->
            var input by remember {
                mutableStateOf((if (direct) settings.directDomains else settings.proxyDomains).joinToString("\n"))
            }
            var invalid by remember { mutableStateOf(false) }
            Dialog(
                onDismissRequest = dismiss,
                title = context.getString(if (direct && settings.protectedMode) R.string.managed_direct_verified else if (direct) R.string.managed_direct else if (settings.protectedMode) R.string.managed_proxy_protected else R.string.managed_proxy),
                message = context.getString(if (direct && settings.protectedMode) R.string.managed_direct_verified_hint else R.string.managed_domain_hint),
                dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                confirmAction = CouiDialogAction(context.getString(R.string.route_save), {
                    try {
                        val domains = input.lineSequence().map(String::trim).filter(String::isNotEmpty)
                            .map { IDN.toASCII(it, IDN.USE_STD3_ASCII_RULES).lowercase() }
                            .distinct().toList()
                        val other = if (direct) settings.proxyDomains else settings.directDomains
                        require(domains.none {
                            it in other || it.length > 253 ||
                                it.contains(Regex("[^a-z0-9.-]")) ||
                                it.split('.').any { part ->
                                    part.isEmpty() || part.length > 63 ||
                                        part.startsWith('-') || part.endsWith('-')
                                }
                        })
                        if (direct) settings.directDomains = domains else settings.proxyDomains = domains
                        dismiss()
                        changed()
                    } catch (_: IllegalArgumentException) {
                        invalid = true
                    }
                }),
            ) {
                BasicTextField(
                    value = input, onValueChange = { input = it; invalid = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .heightIn(min = 100.dp)
                        .background(CouiTheme.colorScheme.background).padding(12.dp),
                    textStyle = rememberCouiTextStyle(CouiTextKind.Body)
                        .copy(color = CouiTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CouiTheme.colorScheme.accent),
                    minLines = 4, maxLines = 10,
                )
                if (invalid) Text(
                    text = context.getString(R.string.managed_domain_invalid),
                    kind = CouiTextKind.Description,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    private fun requestReset() {
        if (savingState || updatingState) return
        context.showCouiContent { dismiss ->
            Dialog(
                onDismissRequest = dismiss,
                title = context.getString(R.string.managed_reset),
                message = context.getString(R.string.managed_reset_hint),
                dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                confirmAction = CouiDialogAction(context.getString(R.string.reset), {
                    settings.targets = emptyMap()
                    settings.directDomains = emptyList()
                    settings.proxyDomains = emptyList()
                    settings.blockAds = false
                    settings.domesticDns = true
                    settings.aiProxyDns = true
                    settings.domesticIpDirect = true
                    dismiss()
                    changed()
                }),
            )
        }
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            revision
            val editable = !savingState && !updatingState
            val categories = listOf(
                "cn" to R.string.managed_cn,
                "ai" to R.string.managed_ai,
                "claude" to R.string.managed_claude,
                "muse" to R.string.managed_muse,
                "media" to R.string.managed_media,
                "social" to R.string.managed_social,
                "dev" to R.string.managed_dev,
                "foreign" to R.string.managed_foreign,
                "fallback" to R.string.managed_fallback,
            )
            ColorOsFormPage(
                title = stringResource(R.string.managed_routing),
                saveLabel = stringResource(when {
                    savingState -> R.string.route_saving
                    runningNow -> R.string.route_save_reconnect
                    else -> R.string.route_save
                }),
                canSave = dirty && editable,
                saveInToolbar = true,
                onBack = { context.colorOsNavigateBack() },
                onSave = { if (dirty && editable) requests.trySend(Request.Save) },
            ) {
                Group {
                    NativeCouiSwitch(
                        title = stringResource(R.string.managed_enabled),
                        summary = stringResource(R.string.managed_mode_hint),
                        value = settings::enabled, enabled = editable, onChanged = { changed() },
                    )
                }
                Group(footer = stringResource(R.string.managed_protection_hint)) {
                    NativeCouiAction(
                        title = stringResource(R.string.managed_protection),
                        summary = protectionName(settings.protectionLevel), enabled = editable,
                    ) { chooseProtection() }
                }
                if (settings.protectedMode) Group {
                    NativeCouiAction(
                        title = stringResource(R.string.managed_protection_forced),
                        summary = stringResource(R.string.managed_protection_forced_hint), enabled = false,
                    ) { }
                }
                if (settings.protectedMode && settings.protectionLevel == "strict") Group {
                    NativeCouiSwitch(
                        title = stringResource(R.string.managed_domestic_ip_direct),
                        summary = stringResource(R.string.managed_domestic_ip_direct_hint),
                        value = settings::domesticIpDirect, enabled = editable, onChanged = { changed() },
                    )
                }
                if (!settings.protectedMode) Group(title = stringResource(R.string.managed_categories)) {
                    categories.forEachIndexed { index, (category, titleRes) ->
                        if (index > 0) SettingDivider()
                        val choice = settings.targets[category]
                            ?: defaultTarget(category)
                        NativeCouiAction(
                            title = stringResource(titleRes),
                            summary = targetName(choice), enabled = editable,
                        ) { chooseTarget(category) }
                    }
                }
                Group(footer = stringResource(R.string.managed_targets_note)) {
                    NativeCouiSwitch(
                        title = stringResource(R.string.managed_ads),
                        value = settings::blockAds, enabled = editable, onChanged = { changed() },
                    )
                    if (!settings.protectedMode) {
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.managed_dns),
                        summary = stringResource(R.string.managed_dns_hint),
                        value = settings::domesticDns, enabled = editable, onChanged = { changed() },
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.managed_ai_proxy_dns),
                        summary = stringResource(R.string.managed_ai_proxy_dns_hint),
                        value = settings::aiProxyDns, enabled = editable, onChanged = { changed() },
                    )
                    }
                }
                Group(title = stringResource(R.string.managed_exceptions)) {
                    NativeCouiAction(
                        title = stringResource(if (settings.protectedMode) R.string.managed_direct_verified else R.string.managed_direct),
                        summary = if (settings.directDomains.isEmpty())
                            stringResource(R.string.managed_no_exceptions)
                        else context.getString(R.string.managed_exception_count, settings.directDomains.size),
                        enabled = editable && settings.protectionLevel != "full",
                    ) { editDomains(true) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(if (settings.protectedMode) R.string.managed_proxy_protected else R.string.managed_proxy),
                        summary = if (settings.proxyDomains.isEmpty())
                            stringResource(R.string.managed_no_exceptions)
                        else context.getString(R.string.managed_exception_count, settings.proxyDomains.size),
                        enabled = editable,
                    ) { editDomains(false) }
                }
                Group {
                    NativeCouiAction(
                        title = stringResource(R.string.managed_direct_history),
                        summary = stringResource(R.string.managed_direct_history_hint),
                        enabled = runningNow,
                    ) { requests.trySend(Request.DirectHistory) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.managed_system_lockdown),
                        summary = stringResource(R.string.managed_system_lockdown_hint),
                    ) { requests.trySend(Request.SystemVpn) }
                }
                Group(
                    title = stringResource(R.string.managed_source),
                    footer = stringResource(R.string.managed_source_hint),
                ) {
                    NativeCouiAction(
                        title = stringResource(R.string.managed_source),
                        summary = status.toString(),
                        enabled = !savingState,
                    ) { requests.trySend(Request.Refresh) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.managed_update),
                        enabled = editable,
                    ) { requests.trySend(Request.Update) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.managed_reset),
                        enabled = editable,
                    ) { requestReset() }
                }
                if (!settings.protectedMode) Group {
                    Text(
                        text = stringResource(R.string.managed_order),
                        kind = CouiTextKind.PreferenceSummary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
