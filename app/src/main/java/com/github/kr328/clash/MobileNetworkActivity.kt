package com.github.kr328.clash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.dialog.requestCouiConfirmText
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class MobileNetworkActivity : BaseActivity<MobileNetworkDesign>() {
    private var saving=false
    private var leaving=false
    private fun confirmBack() {
        if (saving || leaving || design?.dirty != true) return
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
    private suspend fun permissions() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(ActivityResultContracts.RequestMultiplePermissions(),arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED) {
            if (requestCouiConfirm(
                    R.string.mobile_permissions, R.string.mobile_permission_hint,
                    positive = R.string.settings,
                )) {
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ))
            }
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
                    else if (requestCouiConfirmText(
                            R.string.mobile_bind, getString(R.string.mobile_bind_confirm, ssid),
                        )) design.bind(ssid)
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
