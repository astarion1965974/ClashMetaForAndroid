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
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.restartForRouting
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class AccessControlActivity : BaseActivity<AccessControlDesign>() {
    private var saved = false
    private var saving = false

    override fun onBackPressed() {
        if (saving) return
        if (!saved && design?.dirty == true) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.route_unsaved)
                .setMessage(R.string.route_unsaved_hint)
                .setNegativeButton(R.string.route_keep_editing, null)
                .setPositiveButton(R.string.route_discard) { _, _ -> saved = true; finish() }.show()
        } else super.onBackPressed()
    }

    override suspend fun main() {
        val service = ServiceStore(this)
        val selected = withContext(Dispatchers.IO) { service.accessControlPackages.toMutableSet() }
        val mode = withContext(Dispatchers.IO) { service.accessControlMode }
        val design = AccessControlDesign(this, uiStore, selected, mode, clashRunning)
        setContentDesign(design)
        design.requests.send(AccessControlDesign.Request.ReloadApps)
        while (isActive) {
            select<Unit> {
                events.onReceive { design.updateRunning(clashRunning) }
                design.requests.onReceive {
                    when (it) {
                        AccessControlDesign.Request.ReloadApps -> design.patchApps(loadApps(selected))
                        AccessControlDesign.Request.SelectAll -> design.select(AppAccessPolicy.Selection.All)
                        AccessControlDesign.Request.SelectNone -> design.select(AppAccessPolicy.Selection.None)
                        AccessControlDesign.Request.SelectInvert -> design.select(AppAccessPolicy.Selection.Invert)
                        AccessControlDesign.Request.Import -> {
                            if (design.mode != AccessControlMode.AcceptAll) {
                                val clip = getSystemService<ClipboardManager>()?.primaryClip
                                val packages = if (clip != null && clip.itemCount > 0)
                                    clip.getItemAt(0).coerceToText(this@AccessControlActivity)?.toString().orEmpty()
                                        .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
                                else emptySet()
                                val imported = design.allApps.map(AppInfo::packageName).toSet().intersect(packages)
                                if (clip != null) { selected.clear(); selected.addAll(imported) }
                                design.rebindAll()
                                design.showToast(getString(R.string.route_imported, imported.size), ToastDuration.Short)
                            }
                        }
                        AccessControlDesign.Request.Export -> {
                            getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("packages", selected.sorted().joinToString("\n")))
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

    private suspend fun loadApps(selected: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val base = compareByDescending<AppInfo> { it.packageName in selected }
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
