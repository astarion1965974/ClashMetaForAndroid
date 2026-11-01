package com.github.kr328.clash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.MobileNetworkDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.clash.module.MobileNetworkModule
import com.github.kr328.clash.service.store.MobileNetworkStore
import com.github.kr328.clash.util.restartForRouting
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class MobileNetworkActivity : BaseActivity<MobileNetworkDesign>() {
    private var saving=false
    private var leaving=false
    private fun confirmBack() {
        if(saving)return
        if(!leaving && design?.dirty==true) MaterialAlertDialogBuilder(this).setTitle(R.string.route_unsaved)
            .setMessage(R.string.route_unsaved_hint).setNegativeButton(R.string.route_keep_editing,null)
            .setPositiveButton(R.string.route_discard){_,_->leaving=true;finish()}.show()
    }
    private suspend fun permissions() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(ActivityResultContracts.RequestMultiplePermissions(),arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if(Build.VERSION.SDK_INT>=29 && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.mobile_permissions).setMessage(R.string.mobile_permission_hint)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.settings){_,_->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}.show()
        }
    }
    override suspend fun main() {
        val store=MobileNetworkStore(this)
        val settings=withContext(Dispatchers.IO){store.read()}
        val nodes=if(clashRunning) runCatching { withClash {
            (queryProxyGroupNames(false) + "GLOBAL").distinct().flatMap { queryProxyGroup(it,com.github.kr328.clash.core.model.ProxySort.Default).proxies }
                .filter { !it.isGroup && it.name !in setOf("DIRECT","REJECT","REJECT-DROP","PASS","COMPATIBLE") }.map { it.name }.distinct().sorted()
        } }.getOrDefault(emptyList()) else emptyList()
        val design=MobileNetworkDesign(this,settings,nodes,clashRunning);setContentDesign(design)
        interceptBack(design, { saving || (!leaving && design.dirty) }, ::confirmBack)
        while(isActive) select<Unit> {
            events.onReceive { }
            design.requests.onReceive { when(it) {
                MobileNetworkDesign.Request.Permissions -> permissions()
                MobileNetworkDesign.Request.BindWifi -> {
                    permissions()
                    val ssid=MobileNetworkModule.currentSSID(this@MobileNetworkActivity)
                    if(ssid==null) design.showToast(R.string.mobile_wifi_missing,ToastDuration.Long)
                    else MaterialAlertDialogBuilder(this@MobileNetworkActivity).setTitle(R.string.mobile_bind).setMessage(getString(R.string.mobile_bind_confirm,ssid))
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok){_,_->design.bind(ssid)}.show()
                }
                MobileNetworkDesign.Request.AppRouting -> startActivity(AccessControlActivity::class.intent)
                MobileNetworkDesign.Request.Save -> if(!saving) {
                    saving=true;design.setSaving(true)
                    try {
                        withContext(Dispatchers.IO){store.write(settings.copy())};design.markSaved()
                        val running=clashRunning;val result=restartForRouting(running)
                        design.showToast(if(!result)R.string.route_reconnect_failed else if(running)R.string.route_reconnect_requested else R.string.route_saved,ToastDuration.Long)
                    } catch(e:Exception){if(e is CancellationException)throw e;design.showToast(e.message.orEmpty(),ToastDuration.Long)}
                    finally {saving=false;design.setSaving(false)}
                }
            } }
        }
    }
}
