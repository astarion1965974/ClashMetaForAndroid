package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            clashRunning,
        )

        setContentDesign(design)
        design.showRunningNotice()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        NetworkSettingsDesign.Request.StartMobileNetwork ->
                            startActivity(MobileNetworkActivity::class.intent)
                        NetworkSettingsDesign.Request.StartManagedRouting ->
                            startActivity(ManagedRoutingActivity::class.intent)
                        NetworkSettingsDesign.Request.StartAccessControlList ->
                            startActivity(AccessControlActivity::class.intent)
                    }
                }
            }
        }
    }

}
