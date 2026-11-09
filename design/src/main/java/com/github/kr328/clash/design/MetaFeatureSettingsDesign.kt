package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiSelect
import com.github.kr328.clash.design.preference.NativeCouiStringList
import com.github.kr328.clash.design.ui.ToastDuration
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Native COUI Meta features, including the original COUI AGE key helper. */
class MetaFeatureSettingsDesign(
    context: Context,
    configuration: ConfigurationOverride,
) : Design<MetaFeatureSettingsDesign.Request>(context) {
    enum class Request { ResetOverride, ImportGeoIp, ImportGeoSite, ImportCountry, ImportASN }

    private var revision by mutableIntStateOf(0)
    override val root: View = ComposeView(context).apply {
        setContent {
            revision
            val snifferEditable = configuration.sniffer.enable != false
            ColorOsSubPage(
                title = stringResource(R.string.meta_features),
                onBack = { context.colorOsNavigateBack() },
                actions = {
                    ToolbarIcon(
                        ImageVector.vectorResource(R.drawable.ic_baseline_replay),
                        stringResource(R.string.reset),
                    ) { requestClear() }
                },
            ) {
                Group(title = stringResource(R.string.age_key_category)) {
                    NativeCouiAction(
                        title = stringResource(R.string.age_key_type_x25519),
                        summary = stringResource(R.string.age_key_generate_summary),
                    ) { requestAgeKeyHelper(hybrid = false) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.age_key_type_hybrid),
                        summary = stringResource(R.string.age_key_generate_summary),
                    ) { requestAgeKeyHelper(hybrid = true) }
                }
                Group(title = stringResource(R.string.settings)) {
                    NativeCouiSelect(
                        title = stringResource(R.string.unified_delay), value = configuration::unifiedDelay,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.geodata_mode), value = configuration::geodataMode,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.tcp_concurrent), value = configuration::tcpConcurrent,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.find_process_mode), value = configuration::findProcessMode,
                        values = listOf<ConfigurationOverride.FindProcessMode?>(null, ConfigurationOverride.FindProcessMode.Off, ConfigurationOverride.FindProcessMode.Strict, ConfigurationOverride.FindProcessMode.Always),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.off), stringResource(R.string.strict), stringResource(R.string.always)),
                        onChanged = { revision++ },
                    )
                }
                Group(title = stringResource(R.string.sniffer_setting)) {
                    NativeCouiSelect(
                        title = stringResource(R.string.strategy), value = configuration.sniffer::enable,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.sniff_http_ports), property = configuration.sniffer.sniff.http::ports,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.sniff_http_override_destination), value = configuration.sniffer.sniff.http::overrideDestination,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.sniff_tls_ports), property = configuration.sniffer.sniff.tls::ports,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.sniff_tls_override_destination), value = configuration.sniffer.sniff.tls::overrideDestination,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.sniff_quic_ports), property = configuration.sniffer.sniff.quic::ports,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.sniff_quic_override_destination), value = configuration.sniffer.sniff.quic::overrideDestination,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.force_dns_mapping), value = configuration.sniffer::forceDnsMapping,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.parse_pure_ip), value = configuration.sniffer::parsePureIp,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiSelect(
                        title = stringResource(R.string.override_destination), value = configuration.sniffer::overrideDestination,
                        values = listOf<Boolean?>(null, true, false),
                        labels = listOf(stringResource(R.string.dont_modify), stringResource(R.string.enabled), stringResource(R.string.disabled)),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.force_domain), property = configuration.sniffer::forceDomain,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.skip_domain), property = configuration.sniffer::skipDomain,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.skip_src_address), property = configuration.sniffer::skipSrcAddress,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                    SettingDivider()
                    NativeCouiStringList(
                        title = stringResource(R.string.skip_dst_address), property = configuration.sniffer::skipDstAddress,
                        placeholder = stringResource(R.string.dont_modify),
                        enabled = snifferEditable, onChanged = { revision++ },
                    )
                }
                Group(title = stringResource(R.string.geox_files)) {
                    NativeCouiAction(
                        title = stringResource(R.string.import_geoip_file),
                        summary = stringResource(R.string.press_to_import),
                    ) { requests.trySend(Request.ImportGeoIp) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.import_geosite_file),
                        summary = stringResource(R.string.press_to_import),
                    ) { requests.trySend(Request.ImportGeoSite) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.import_country_file),
                        summary = stringResource(R.string.press_to_import),
                    ) { requests.trySend(Request.ImportCountry) }
                    SettingDivider()
                    NativeCouiAction(
                        title = stringResource(R.string.import_asn_file),
                        summary = stringResource(R.string.press_to_import),
                    ) { requests.trySend(Request.ImportASN) }
                }
            }
        }
    }

    suspend fun requestResetConfirm(): Boolean =
        context.requestCouiConfirm(
            R.string.reset_override_settings, R.string.reset_override_settings_message,
        )

    private fun requestAgeKeyHelper(hybrid: Boolean) {
        context.showCouiContent { dismiss ->
            var secret by remember { mutableStateOf("") }
            var publicKey by remember { mutableStateOf("") }
            fun copy(label: String, value: String) {
                if (value.isBlank()) return
                val data = ClipData.newPlainText(label, value)
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)
                launch { showToast(R.string.copied, ToastDuration.Short) }
            }
            Dialog(onDismissRequest = dismiss, title = context.getString(if (hybrid) R.string.age_key_type_hybrid else R.string.age_key_type_x25519),
                confirmAction = CouiDialogAction(context.getString(R.string.close), dismiss)) {
                Item(onClick = { launch(Dispatchers.Main) {
                    secret = context.requestModelTextInput(secret, context.getText(R.string.age_secret_key),
                        error = context.getText(R.string.age_secret_key_error),
                        validator = { it.isBlank() || runCatching { Clash.veritySecretKeys(it) }.getOrDefault(false) })
                } }, summary = { Text(text = secret.ifBlank { "—" }, kind = CouiTextKind.PreferenceSummary,
                    color = CouiTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }) {
                    Text(text = context.getString(R.string.age_secret_key), kind = CouiTextKind.PreferenceTitle)
                }
                Item(onClick = {
                    val keyPair = if (hybrid) {
                        Clash.genHybridKeyPair()
                    } else {
                        Clash.genX25519KeyPair()
                    }
                    secret = keyPair.secretKey; publicKey = keyPair.publicKey
                }) { Text(text = context.getString(R.string.age_key_generate), kind = CouiTextKind.PreferenceTitle) }
                Item(onClick = { copy("age_secret_key", secret) }) {
                    Text(text = "${context.getString(R.string.age_key_copy)} · ${context.getString(R.string.age_secret_key)}", kind = CouiTextKind.PreferenceTitle)
                }
                Item(onClick = { launch(Dispatchers.Main) {
                    publicKey = context.requestModelTextInput(publicKey, context.getText(R.string.age_public_key),
                        error = context.getText(R.string.age_public_key_error),
                        validator = { it.isBlank() || runCatching { Clash.verityPublicKeys(it) }.getOrDefault(false) })
                } }, summary = { Text(text = publicKey.ifBlank { "—" }, kind = CouiTextKind.PreferenceSummary,
                    color = CouiTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }) {
                    Text(text = context.getString(R.string.age_public_key), kind = CouiTextKind.PreferenceTitle)
                }
                Item(onClick = { publicKey = Clash.toPublicKeys(secret).firstOrNull() ?: "" }) {
                    Text(text = context.getString(R.string.age_key_to_public), kind = CouiTextKind.PreferenceTitle)
                }
                Item(onClick = { copy("age_public_key", publicKey) }) {
                    Text(text = "${context.getString(R.string.age_key_copy)} · ${context.getString(R.string.age_public_key)}", kind = CouiTextKind.PreferenceTitle)
                }
            }
        }
    }

    fun requestClear() {
        requests.trySend(Request.ResetOverride)
    }
}
