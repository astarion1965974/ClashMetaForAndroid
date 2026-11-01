package com.github.kr328.clash

import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.ManagedRoutingDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.store.ManagedRoutingStore
import com.github.kr328.clash.util.restartForRouting
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class ManagedRoutingActivity : BaseActivity<ManagedRoutingDesign>() {
    private var leaving = false
    private var saving = false
    private var updating = false

    private fun confirmBack() {
        if (saving || updating) return
        if (!leaving && design?.dirty == true) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.route_unsaved).setMessage(R.string.route_unsaved_hint)
                .setNegativeButton(R.string.route_keep_editing, null)
                .setPositiveButton(R.string.route_discard) { _, _ -> leaving = true; finish() }.show()
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
