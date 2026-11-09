package com.github.kr328.clash

import android.Manifest.permission.INTERNET
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.AccessControlDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.toAppInfo
import com.github.kr328.clash.service.model.AppAccessPolicy
import com.github.kr328.clash.service.model.DomesticAppCatalog
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.restartForRouting
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.selects.select

class AccessControlActivity : BaseActivity<AccessControlDesign>() {
    private var saved = false
    private var saving = false

    private fun confirmBack() {
        if (saving || saved || design?.dirty != true) return
        launch {
            if (requestCouiConfirm(
                    R.string.route_unsaved, R.string.route_unsaved_hint,
                    positive = R.string.route_discard,
                    negative = R.string.route_keep_editing,
                )) {
                saved = true
                finish()
            }
        }
    }

    override suspend fun main() {
        val service = ServiceStore(this)
        val (selected, mode) = withContext(Dispatchers.IO) {
            service.initializeDomesticBypassIfUnconfigured()
            service.accessControlPackages.toMutableSet() to service.accessControlMode
        }
        val design = AccessControlDesign(this, uiStore, selected, mode, clashRunning)
        setContentDesign(design)
        interceptBack(design, { saving || (!saved && design.dirty) }, ::confirmBack)
        design.requests.send(AccessControlDesign.Request.ReloadApps)
        while (isActive) {
            select<Unit> {
                events.onReceive { design.updateRunning(clashRunning) }
                design.requests.onReceive {
                    when (it) {
                        AccessControlDesign.Request.ReloadApps -> design.patchApps(loadApps(selected, design.mode))
                        AccessControlDesign.Request.SelectAll -> design.select(AppAccessPolicy.Selection.All)
                        AccessControlDesign.Request.SelectNone -> design.select(AppAccessPolicy.Selection.None)
                        AccessControlDesign.Request.SelectInvert -> design.select(AppAccessPolicy.Selection.Invert)
                        AccessControlDesign.Request.ApplyDomesticDefaults -> {
                            // Optional online catalog refresh, never on cold start or
                            // on every VPN connection. Do not override previous saved
                            // explicit decisions until Save & Reconnect is pressed.
                            val installed = design.allApps.map(AppInfo::packageName).toSet()
                            saving = true
                            design.setSaving(true)
                            try {
                                val extra = fetchDomesticInstalledPackages(installed)
                                design.setSaving(false)
                                if (extra == null) {
                                    design.applyDomesticDefaults()
                                    design.showToast(
                                        R.string.app_domestic_catalog_offline, ToastDuration.Short,
                                    )
                                } else {
                                    design.applyDomesticDefaults(extra)
                                    design.showToast(
                                        getString(R.string.app_domestic_catalog_updated, extra.size),
                                        ToastDuration.Short,
                                    )
                                }
                            } finally {
                                saving = false
                                design.setSaving(false)
                            }
                        }
                        AccessControlDesign.Request.Import -> {
                            val clip = getSystemService<ClipboardManager>()?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val packages = clip.getItemAt(0)
                                    .coerceToText(this@AccessControlActivity)?.toString().orEmpty()
                                    .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
                                val installed = design.allApps.map(AppInfo::packageName).toSet()
                                val imported = installed.intersect(packages)
                                design.replaceBypassApps(imported)
                                design.showToast(
                                    getString(R.string.route_imported, imported.size), ToastDuration.Short,
                                )
                            }
                        }
                        AccessControlDesign.Request.Export -> {
                            val bypass = design.allApps.asSequence().map(AppInfo::packageName)
                                .filter { !AppAccessPolicy.usesClash(design.mode, it in selected) }
                                .sorted().joinToString("\n")
                            getSystemService<ClipboardManager>()?.setPrimaryClip(
                                ClipData.newPlainText("clash_bypass_packages", bypass),
                            )
                            design.showToast(R.string.route_exported, ToastDuration.Short)
                        }
                        AccessControlDesign.Request.Save -> {
                            saving = true; design.setSaving(true)
                            try {
                                withContext(Dispatchers.IO) {
                                    service.accessControlPackages = selected.toSet()
                                    service.accessControlMode = design.mode
                                }
                                val wasRunning = clashRunning
                                val restarted = restartForRouting(wasRunning)
                                saved = true
                                android.widget.Toast.makeText(this@AccessControlActivity, if (!restarted) R.string.route_reconnect_failed else if (wasRunning) R.string.route_reconnect_requested else R.string.route_saved, android.widget.Toast.LENGTH_LONG).show()
                                finish()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                design.showToast(e.message.orEmpty(), ToastDuration.Long)
                            } finally { saving = false; design.setSaving(false); design.updateRunning(clashRunning) }
                        }
                    }
                }
            }
        }
    }

    /** Fetch the community-maintained app-market package catalog only on an
     * explicit user action; sanitize data and import IDs from installed apps.
     * No background telemetry, network call on VPN launch, or wildcard rules.
     */
    private suspend fun fetchDomesticInstalledPackages(
        installed: Set<String>,
    ): Set<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(DomesticAppCatalog.sourceUrl).openConnection()
                as HttpURLConnection
            try {
                connection.connectTimeout = 7_000
                connection.readTimeout = 7_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "text/plain")
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Domestic catalog HTTP ${connection.responseCode}"
                }
                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }
                require(payload.length in 10_000..650_000) {
                    "Domestic catalog response size invalid"
                }
                val catalog = DomesticAppCatalog.parseProcessPackages(payload)
                require(catalog.size >= 500) { "Domestic catalog not a valid ruleset" }
                catalog.intersect(installed)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun loadApps(selected: Set<String>, mode: AccessControlMode): List<AppInfo> = withContext(Dispatchers.IO) {
        val base = compareByDescending<AppInfo> {
            !AppAccessPolicy.usesClash(mode, it.packageName in selected)
        }
        val sort = uiStore.accessControlSort
        val comparator = if (uiStore.accessControlReverse) base.thenDescending(sort) else base.then(sort)
        val pm = packageManager
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).asSequence()
            .filter { it.packageName != packageName && it.applicationInfo != null }
            .filter { it.requestedPermissions?.contains(INTERNET) == true || it.applicationInfo!!.uid < android.os.Process.FIRST_APPLICATION_UID }
            .map { it.toAppInfo(pm).copy(systemApp = it.isSystemApp) }
            .sortedWith(comparator).toList()
    }
    private val PackageInfo.isSystemApp: Boolean
        get() = (applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
}
