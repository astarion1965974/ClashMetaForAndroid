package com.github.kr328.clash.design

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiSelect
import com.github.kr328.clash.design.preference.NativeCouiSwitch
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group

/** ColorOS 17 native COUI application settings; preserve all existing store callbacks. */
class AppSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    behavior: Behavior,
    running: Boolean,
    onHideIconChange: (hide: Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request { ReCreateAllActivities, SelectNotificationGroup }

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsSubPage(
                title = stringResource(R.string.app),
                onBack = { context.colorOsNavigateBack() },
            ) {
                Group(title = stringResource(R.string.behavior)) {
                    NativeCouiSwitch(
                        title = stringResource(R.string.auto_restart),
                        summary = stringResource(R.string.allow_clash_auto_restart),
                        value = behavior::autoRestart,
                        icon = R.drawable.ic_baseline_restore,
                    )
                }
                Group(title = stringResource(R.string.interface_)) {
                    NativeCouiSelect(
                        title = stringResource(R.string.dark_mode),
                        value = uiStore::darkMode,
                        values = DarkMode.values().toList(),
                        labels = listOf(
                            stringResource(R.string.follow_system_android_10),
                            stringResource(R.string.always_light),
                            stringResource(R.string.always_dark),
                        ),
                        icon = R.drawable.ic_baseline_brightness_4,
                        onChanged = { requests.trySend(Request.ReCreateAllActivities) },
                    )
                    Divider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.hide_app_icon_title),
                        summary = stringResource(R.string.hide_app_icon_desc),
                        value = uiStore::hideAppIcon,
                        icon = R.drawable.ic_baseline_hide,
                        onChanged = onHideIconChange,
                    )
                    Divider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.hide_from_recents_title),
                        summary = stringResource(R.string.hide_from_recents_desc),
                        value = uiStore::hideFromRecents,
                        icon = R.drawable.ic_baseline_stack,
                        onChanged = { requests.trySend(Request.ReCreateAllActivities) },
                    )
                }
                Group(title = stringResource(R.string.service)) {
                    NativeCouiSwitch(
                        title = stringResource(R.string.live_notification_title),
                        summary = stringResource(R.string.live_notification_summary),
                        value = srvStore::liveNotification,
                        icon = R.drawable.ic_baseline_domain,
                        enabled = !running,
                    )
                    Divider()
                    NativeCouiAction(
                        title = stringResource(R.string.live_notification_group),
                        summary = srvStore.notificationProxyGroup.ifBlank {
                            stringResource(R.string.live_notification_group_summary)
                        },
                        enabled = running,
                    ) { requests.trySend(Request.SelectNotificationGroup) }
                    Divider()
                    NativeCouiAction(
                        title = stringResource(R.string.live_notification_settings),
                        summary = stringResource(R.string.live_notification_settings_summary),
                    ) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent)
                    }
                    Divider()
                    NativeCouiSwitch(
                        title = stringResource(R.string.show_traffic),
                        summary = stringResource(R.string.show_traffic_summary),
                        value = srvStore::dynamicNotification,
                        icon = R.drawable.ic_baseline_domain,
                        enabled = !running,
                    )
                }
            }
        }
    }
}
