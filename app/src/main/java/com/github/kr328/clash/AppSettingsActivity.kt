package com.github.kr328.clash

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import com.github.kr328.clash.design.dialog.showCouiSingleChoice
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.constants.Permissions
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.design.R as DesignR
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class AppSettingsActivity : BaseActivity<AppSettingsDesign>(), Behavior {
    override suspend fun main() {
        val design = AppSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            this,
            clashRunning,
            ::onHideIconChange,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated, Event.ProfileLoaded ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        AppSettingsDesign.Request.ReCreateAllActivities ->
                            ApplicationObserver.createdActivities.forEach { activity -> activity.recreate() }
                        AppSettingsDesign.Request.SelectNotificationGroup -> selectNotificationGroup()
                    }
                }
            }
        }
    }

    private suspend fun selectNotificationGroup() {
        if (!clashRunning) return
        val names = withClash { queryProxyGroupNames(false) }.filter { it != "GLOBAL" }
        val store = ServiceStore(this)
        val values = listOf("") + names
        val labels = listOf(getString(DesignR.string.live_notification_group_auto)) + names
        showCouiSingleChoice(
            title = getString(DesignR.string.live_notification_group),
            labels = labels,
            selectedIndex = values.indexOf(store.notificationProxyGroup).coerceAtLeast(0),
        ) { which ->
            store.notificationProxyGroup = values[which]
            sendBroadcast(
                Intent(Intents.ACTION_PROXY_SELECTION_CHANGED).setPackage(packageName),
                Permissions.RECEIVE_SELF_BROADCASTS,
            )
            recreate()
        }
    }

    override var autoRestart: Boolean
        get() {
            val status = packageManager.getComponentEnabledSetting(
                RestartReceiver::class.componentName
            )

            return status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        set(value) {
            val status = if (value)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                RestartReceiver::class.componentName,
                status,
                PackageManager.DONT_KILL_APP,
            )
        }

    private fun onHideIconChange(hide: Boolean) {
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        packageManager.setComponentEnabledSetting(
            mainActivityAlias,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}
