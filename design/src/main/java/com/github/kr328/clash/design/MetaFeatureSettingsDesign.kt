package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.databinding.DesignSettingsMetaFeatureBinding
import com.github.kr328.clash.design.databinding.DialogAgeKeyHelperBinding
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MetaFeatureSettingsDesign(
    context: Context,
    configuration: ConfigurationOverride
) : Design<MetaFeatureSettingsDesign.Request>(context) {
    enum class Request {
        ResetOverride, ImportGeoIp, ImportGeoSite, ImportCountry, ImportASN
    }

    private val binding = DesignSettingsMetaFeatureBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun requestResetConfirm(): Boolean {
        return context.requestCouiConfirm(R.string.reset_override_settings, R.string.reset_override_settings_message)
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val booleanValues: Array<Boolean?> = arrayOf(
            null,
            true,
            false
        )
        val booleanValuesText: Array<Int> = arrayOf(
            R.string.dont_modify,
            R.string.enabled,
            R.string.disabled
        )

        val screen = preferenceScreen(context) {
            category(R.string.age_key_category)

            clickable(
                title = R.string.age_key_type_x25519,
                summary = R.string.age_key_generate_summary,
            ) {
                clicked {
                    requestAgeKeyHelper(hybrid = false)
                }
            }

            clickable(
                title = R.string.age_key_type_hybrid,
                summary = R.string.age_key_generate_summary,
            ) {
                clicked {
                    requestAgeKeyHelper(hybrid = true)
                }
            }

            category(R.string.settings)

            selectableList(
                value = configuration::unifiedDelay,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.unified_delay,
            )

            selectableList(
                value = configuration::geodataMode,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.geodata_mode,
            )

            selectableList(
                value = configuration::tcpConcurrent,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.tcp_concurrent,
            )

            selectableList(
                value = configuration::findProcessMode,
                values = arrayOf(
                    null,
                    ConfigurationOverride.FindProcessMode.Off,
                    ConfigurationOverride.FindProcessMode.Strict,
                    ConfigurationOverride.FindProcessMode.Always
                ),
                valuesText = arrayOf(
                    R.string.dont_modify,
                    R.string.off,
                    R.string.strict,
                    R.string.always,
                ),
                title = R.string.find_process_mode,
            ) {

            }

            category(R.string.sniffer_setting)

            val snifferDependencies: MutableList<Preference> = mutableListOf()

            val sniffer = selectableList(
                value = configuration.sniffer::enable,
                values = arrayOf(
                    null,
                    true,
                    false
                ),
                valuesText = arrayOf(
                    R.string.dont_modify,
                    R.string.enabled,
                    R.string.disabled
                ),
                title = R.string.strategy
            ) {
                listener = OnChangedListener {
                    if (configuration.sniffer.enable == false) {
                        snifferDependencies.forEach {
                            it.enabled = false
                        }
                    } else {
                        snifferDependencies.forEach {
                            it.enabled = true
                        }
                    }
                }
            }

            editableTextList(
                value = configuration.sniffer.sniff.http::ports,
                adapter = TextAdapter.String,
                title = R.string.sniff_http_ports,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer.sniff.http::overrideDestination,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.sniff_http_override_destination,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer.sniff.tls::ports,
                adapter = TextAdapter.String,
                title = R.string.sniff_tls_ports,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer.sniff.tls::overrideDestination,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.sniff_tls_override_destination,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer.sniff.quic::ports,
                adapter = TextAdapter.String,
                title = R.string.sniff_quic_ports,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer.sniff.quic::overrideDestination,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.sniff_quic_override_destination,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer::forceDnsMapping,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.force_dns_mapping,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer::parsePureIp,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.parse_pure_ip,
                configure = snifferDependencies::add,
            )

            selectableList(
                value = configuration.sniffer::overrideDestination,
                values = booleanValues,
                valuesText = booleanValuesText,
                title = R.string.override_destination,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer::forceDomain,
                adapter = TextAdapter.String,
                title = R.string.force_domain,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer::skipDomain,
                adapter = TextAdapter.String,
                title = R.string.skip_domain,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer::skipSrcAddress,
                adapter = TextAdapter.String,
                title = R.string.skip_src_address,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            editableTextList(
                value = configuration.sniffer::skipDstAddress,
                adapter = TextAdapter.String,
                title = R.string.skip_dst_address,
                placeholder = R.string.dont_modify,
                configure = snifferDependencies::add,
            )

            sniffer.listener?.onChanged()

            /*
            category(R.string.geox_url_setting)

            val geoxUrlDependencies: MutableList<Preference> = mutableListOf()

            editableText(
                value = configuration.geoxurl::geoip,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_geoip,
                placeholder = R.string.dont_modify,
                empty = R.string.geoip_url,
                configure = geoxUrlDependencies::add,
            )

            editableText(
                value = configuration.geoxurl::mmdb,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_mmdb,
                placeholder = R.string.dont_modify,
                empty = R.string.mmdb_url,
                configure = geoxUrlDependencies::add,
            )

            editableText(
                value = configuration.geoxurl::geosite,
                adapter = NullableTextAdapter.String,
                title = R.string.geox_geosite,
                placeholder = R.string.dont_modify,
                empty = R.string.geosite_url,
                configure = geoxUrlDependencies::add,
            )
            */

            category(R.string.geox_files)

            clickable (
                title = R.string.import_geoip_file,
                summary = R.string.press_to_import,
            ){
                clicked {
                    requests.trySend(Request.ImportGeoIp)
                }
            }

            clickable (
                title = R.string.import_geosite_file,
                summary = R.string.press_to_import,
            ){
                clicked {
                    requests.trySend(Request.ImportGeoSite)
                }
            }

            clickable (
                title = R.string.import_country_file,
                summary = R.string.press_to_import,
            ){
                clicked {
                    requests.trySend(Request.ImportCountry)
                }
            }
            
            clickable (
                title = R.string.import_asn_file,
                summary = R.string.press_to_import,
            ){
                clicked {
                    requests.trySend(Request.ImportASN)
                }
            }
        }

        binding.content.addView(screen.root)
    }

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
