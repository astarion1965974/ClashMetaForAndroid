package com.github.kr328.clash

import android.content.Intent
import android.provider.Settings
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ManagedRoutingDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.store.ManagedRoutingStore
import com.github.kr328.clash.util.restartForRouting
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class ManagedRoutingActivity : BaseActivity<ManagedRoutingDesign>() {
    private var leaving = false
    private var saving = false
    private var updating = false

    private fun confirmBack() {
        if (saving || updating || leaving || design?.dirty != true) return
        launch {
            if (requestCouiConfirm(
                    R.string.route_unsaved, R.string.route_unsaved_hint,
                    positive = R.string.route_discard,
                    negative = R.string.route_keep_editing,
                )) {
                leaving = true
                finish()
            }
        }
    }

    override suspend fun main() {
        val store = ManagedRoutingStore(this)
        val settings = try { withContext(Dispatchers.IO) { store.read() } }
        catch (e: Exception) {
            android.widget.Toast.makeText(this, e.message, android.widget.Toast.LENGTH_LONG).show()
            finish(); return
        }
        // Category exits use subscription groups; the default managed group selects individual nodes.
        val targets = if (clashRunning) runCatching {
            withClash { queryProxyGroupNames(true).filterNot { it == "GLOBAL" || it == "Live · 默认代理" } }
        }.getOrDefault(emptyList()) else emptyList()
        val design = ManagedRoutingDesign(this, settings, targets, clashRunning)
        setContentDesign(design)
        interceptBack(design, { saving || updating || (!leaving && design.dirty) }, ::confirmBack)
        suspend fun refresh() {
            val providers = if (clashRunning) runCatching { withClash { queryProviders().filter { it.type == Provider.Type.Rule && it.name.startsWith("live-") } } }.getOrDefault(emptyList()) else emptyList()
            design.setProviderStatus(providers.size, providers.map { it.updatedAt }.filter { it > 0 }.minOrNull())
        }
        refresh()
        while (isActive) select<Unit> {
            events.onReceive { design.updateRunning(clashRunning); if (it == Event.ProfileLoaded || it == Event.ClashStop) refresh() }
            design.requests.onReceive {
                when (it) {
                    ManagedRoutingDesign.Request.Refresh -> refresh()
                    ManagedRoutingDesign.Request.SystemVpn -> {
                        runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                            .onFailure { design.showToast(it.message.orEmpty(), ToastDuration.Long) }
                    }
                    ManagedRoutingDesign.Request.DirectHistory -> {
                        val payload = runCatching { withClash { queryDirectHistory() } }.getOrDefault("[]")
                        val values = runCatching { JSONArray(payload) }.getOrDefault(JSONArray())
                        val formatted = (0 until values.length()).joinToString("\n\n") { index ->
                            val item = values.getJSONObject(index)
                            val time = runCatching {
                                OffsetDateTime.parse(item.optString("time")).atZoneSameInstant(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                            }.getOrDefault("")
                            val rule = listOf(item.optString("rule"), item.optString("payload"))
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            "$time  ${item.optString("target", "—")}\n${rule.ifBlank { "未记录规则" }}"
                        }
                        design.showDirectHistory(formatted.ifBlank { getString(R.string.managed_direct_history_empty) })
                    }
                    ManagedRoutingDesign.Request.Save -> {
                        if (!saving && !updating) {
                            saving = true; design.setSaving(true)
                            try {
                                val snapshot = settings.copy()
                                withContext(Dispatchers.IO) { store.write(snapshot) }
                                design.markSaved()
                                val wasRunning = clashRunning
                                val restarted = restartForRouting(wasRunning)
                                design.showToast(if (!restarted) R.string.route_reconnect_failed else if (wasRunning) R.string.route_reconnect_requested else R.string.route_saved, ToastDuration.Long)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                design.showToast(e.message.orEmpty(), ToastDuration.Long)
                            } finally { saving = false; design.setSaving(false); design.updateRunning(clashRunning) }
                        }
                    }
                    ManagedRoutingDesign.Request.Update -> {
                        if (!saving && !updating) {
                            val providers = if (clashRunning && !design.dirty) withClash { queryProviders().filter { it.type == Provider.Type.Rule && it.name.startsWith("live-") } } else emptyList()
                            if (providers.isEmpty()) design.showToast(R.string.managed_update_offline, ToastDuration.Long)
                            else {
                                updating = true; design.setUpdating(true)
                                launch {
                                    var success = 0; var failed = 0
                                    try {
                                        design.showToast(R.string.managed_updating, ToastDuration.Short)
                                        for (provider in providers) {
                                            try { withTimeout(60_000) { withClash { updateProvider(provider.type, provider.name) } }; success++ }
                                            catch (e: Exception) { if (e is CancellationException && e !is TimeoutCancellationException) throw e; failed++ }
                                        }
                                        design.showToast(getString(R.string.managed_update_result, success, failed), ToastDuration.Long)
                                        refresh()
                                    } finally { updating = false; design.setUpdating(false) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
