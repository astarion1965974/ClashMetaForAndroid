package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.dialog.showCouiAlert
import com.github.kr328.clash.design.dialog.showCouiMultiChoice
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiSwitch
import com.github.kr328.clash.service.store.MobileNetworkSettings
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** ColorOS 17 native mobile-network settings; the configuration model is unchanged. */
class MobileNetworkDesign(
    context: Context,
    val settings: MobileNetworkSettings,
    private val nodes: List<String>,
    private val running: Boolean,
) : Design<MobileNetworkDesign.Request>(context) {
    enum class Request { Save, BindWifi, Permissions, AppRouting }

    private var original = settings.copy()
    private var savingState by mutableStateOf(false)
    private var revision by mutableIntStateOf(0)
    val dirty: Boolean get() = original != settings

    private fun changed() {
        revision++
        onBackStateChanged()
    }

    fun markSaved() {
        original = settings.copy()
        changed()
    }

    fun setSaving(value: Boolean) {
        savingState = value
        changed()
    }

    fun bind(ssid: String) {
        settings.trustedSSIDs = (settings.trustedSSIDs + ssid).distinct()
        changed()
    }

    private fun pick(ai: Boolean) {
        if (savingState) return
        val chosen = (if (ai) settings.aiNodes else settings.stableNodes).toMutableList()
        val options = (chosen + nodes).distinct()
        if (options.isEmpty()) {
            context.showCouiAlert(
                context.getText(if (ai) R.string.mobile_ai else R.string.mobile_stable),
                context.getText(R.string.mobile_no_nodes),
            )
            return
        }
        context.showCouiMultiChoice(
            context.getString(if (ai) R.string.mobile_ai else R.string.mobile_stable),
            options, chosen,
        ) { result ->
            if (result.size > 5) {
                android.widget.Toast.makeText(context, R.string.mobile_five, android.widget.Toast.LENGTH_SHORT).show()
                false
            } else {
                if (ai) settings.aiNodes = result else settings.stableNodes = result
                changed()
                true
            }
        }
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            // Depend on revision to recompose when a settings data class field is modified.
            revision
            val interactive = !savingState
            ColorOsFormPage(
                title = stringResource(R.string.mobile_title),
                saveLabel = stringResource(when {
                    savingState -> R.string.route_saving
                    running -> R.string.route_save_reconnect
                    else -> R.string.route_save
                }),
                canSave = dirty && interactive,
                onBack = { context.colorOsNavigateBack() },
                onSave = { if (dirty && interactive) requests.trySend(Request.Save) },
            ) {
                Group {
                    NativeCouiSwitch(
                        title = stringResource(R.string.mobile_enabled),
                        summary = stringResource(R.string.mobile_enabled_hint),
                        value = settings::enabled, enabled = interactive,
                        onChanged = { changed() },
                    )
                }
                Group(title = stringResource(R.string.mobile_nodes)) {
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_stable),
                        summary = settings.stableNodes.joinToString(" → ")
                            .ifBlank { context.getString(R.string.mobile_empty) },
                        enabled = interactive,
                    ) { pick(false) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_ai),
                        summary = settings.aiNodes.joinToString(" → ")
                            .ifBlank { context.getString(R.string.mobile_empty) },
                        enabled = interactive,
                    ) { pick(true) }
                }
                Group(footer = stringResource(R.string.mobile_pick_hint)) {
                    NativeCouiSwitch(
                        title = stringResource(R.string.mobile_dns),
                        summary = stringResource(R.string.mobile_dns_hint),
                        value = settings::secureDns, enabled = interactive,
                        onChanged = { changed() },
                    )
                    SettingDivider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.mobile_updates),
                        summary = stringResource(R.string.mobile_updates_hint),
                        value = settings::wifiUpdates, enabled = interactive,
                        onChanged = { changed() },
                    )
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_apps),
                        summary = stringResource(R.string.mobile_apps_hint),
                        navigation = true,
                        enabled = interactive,
                    ) { requests.trySend(Request.AppRouting) }
                }
                Group(title = stringResource(R.string.mobile_home)) {
                    NativeCouiSwitch(
                        title = stringResource(R.string.mobile_home_enabled),
                        summary = stringResource(R.string.mobile_home_hint),
                        value = settings::trustedWifiEnabled, enabled = interactive,
                        onChanged = { changed() },
                    )
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_bind), enabled = interactive,
                    ) { requests.trySend(Request.BindWifi) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_bound),
                        summary = settings.trustedSSIDs.joinToString(", ")
                            .ifBlank { context.getString(R.string.mobile_none_bound) },
                        enabled = interactive,
                    ) {
                        val all = settings.trustedSSIDs
                        context.showCouiMultiChoice(
                            context.getString(R.string.mobile_bound), all, all,
                        ) { keep ->
                            settings.trustedSSIDs = all.filter { it in keep }
                            changed()
                            true
                        }
                    }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.mobile_permissions),
                        summary = stringResource(R.string.mobile_permission_hint),
                        enabled = interactive,
                    ) { requests.trySend(Request.Permissions) }
                }
            }
        }
    }
}
