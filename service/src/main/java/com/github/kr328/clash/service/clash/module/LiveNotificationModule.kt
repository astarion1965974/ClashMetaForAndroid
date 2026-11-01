package com.github.kr328.clash.service.clash.module

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.bridge.Bridge
import kotlinx.serialization.json.*
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlin.coroutines.coroutineContext

/** Standard platform templates let ColorOS render the capsule and expanded live card. */
@TargetApi(36)
class LiveNotificationModule(service: Service, private val showTraffic: Boolean) : Module<Unit>(service) {
    private val manager = service.getSystemService<NotificationManager>()!!
    private val store = ServiceStore(service)

    private fun openNodes(group: String?): PendingIntent = PendingIntent.getActivity(
        service, R.id.nf_clash_status + 1,
        Intent().setClassName(service.packageName, "com.github.kr328.clash.ProxyActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Intents.EXTRA_NOTIFICATION_PROXY_GROUP, group),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
    )

    private val openApp = PendingIntent.getActivity(
        service, R.id.nf_clash_status,
        Intent().setComponent(Components.MAIN_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
    )

    private val stopVpn = PendingIntent.getBroadcast(
        service, R.id.nf_clash_status,
        Intent(Intents.ACTION_CLASH_REQUEST_STOP).setPackage(service.packageName),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
    )

    private fun promotionSettings(): PendingIntent {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, service.packageName)
        return PendingIntent.getActivity(
            service, R.id.nf_vpn_status, intent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    private var lastContent: String? = null
    private var lastRegionNode: String? = null
    private var lastRegion: String? = null
    private fun region(node: String): String? {
        if (node != lastRegionNode) { lastRegionNode = node; lastRegion = NotificationNodeRegion.regionLabel(node) }
        return lastRegion
    }

    private fun update() {
        if (!StatusProvider.profileLoaded) return
        try {
            val mode = Clash.queryTunnelState().mode
            val selection = NotificationNodeResolver.resolveMain(
                mode.name, Clash.queryGroupNames(false), store.notificationProxyGroup
            ) { name ->
                Bridge.nativeQuerySelection(name)?.let { payload ->
                    val value = Json.parseToJsonElement(payload).jsonObject
                    val selected = value.getValue("now").jsonPrimitive.content
                    NotificationNodeResolver.Group(value.getValue("type").jsonPrimitive.content, selected,
                        if (value.getValue("child").jsonPrimitive.boolean) setOf(selected) else emptySet())
                }
            }

            val paused = StatusProvider.homePaused
            val label = when {
                paused -> service.getString(R.string.live_home_title)
                mode == TunnelState.Mode.Direct -> service.getString(R.string.live_direct)
                selection?.balanced == true -> service.getString(R.string.live_multi_route)
                selection?.node == "DIRECT" -> service.getString(R.string.live_direct)
                selection?.node != null -> selection.node
                else -> service.getString(R.string.live_unavailable)
            }
            val capsuleLabel = when {
                paused -> service.getString(R.string.live_home_capsule)
                mode == TunnelState.Mode.Direct || selection?.node == "DIRECT" -> service.getString(R.string.live_direct)
                selection?.balanced == true -> service.getString(R.string.live_multi_route)
                selection?.node in setOf("REJECT", "REJECT-DROP") -> service.getString(R.string.live_blocked)
                selection?.node != null -> region(selection.node)
                    ?: service.getString(R.string.live_proxy_active)
                else -> service.getString(R.string.live_unavailable)
            }
            val modeName = service.getString(when (mode) {
                TunnelState.Mode.Direct -> R.string.live_mode_direct
                TunnelState.Mode.Global -> R.string.live_mode_global
                TunnelState.Mode.Rule -> R.string.live_mode_rule
                TunnelState.Mode.Script -> R.string.live_mode_script
            })
            val details = SpannableStringBuilder().append(
                if (mode == TunnelState.Mode.Direct) service.getString(R.string.live_mode_direct)
                else service.getString(R.string.live_main_group, selection?.group ?: "—")
            ).append("\n\n")
            val summary = if (paused) service.getString(R.string.live_home_hint) else if (showTraffic) {
                val now = Clash.queryTrafficNow()
                service.getString(R.string.live_speed_compact, now.trafficDownload(), now.trafficUpload())
            } else service.getString(R.string.live_connected)
            val speedStart = details.length
            details.append(summary)
            details.setSpan(StyleSpan(Typeface.BOLD), speedStart, details.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (showTraffic && !paused) details.append("\n").append(
                service.getString(R.string.live_total_compact, Clash.queryTrafficTotal().trafficTotal())
            )
            StatusProvider.currentProfile?.let {
                details.append(if (showTraffic) " · " else "\n").append(service.getString(R.string.live_profile, it))
            }

            val fingerprint = listOf(label, capsuleLabel, summary, details.toString(), modeName,
                selection?.group, manager.canPostPromotedNotifications()).joinToString("\u0000")
            if (fingerprint == lastContent) return
            val builder = Notification.Builder(service, StaticNotificationModule.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_service)
                .setColor(service.getColorCompat(R.color.color_live_notification))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentTitle(capsuleLabel)
                .setContentText(summary)
                .setSubText(modeName)
                .setContentIntent(openApp)
                .setStyle(Notification.BigTextStyle().setBigContentTitle(label).bigText(details))
                // The setter was added in 36.1; the standard extras contract also
                // supports base API 36 without reflection or an AndroidX upgrade.
                .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .setShortCriticalText(NotificationNodeResolver.chipText(capsuleLabel))
                .addAction(Notification.Action.Builder(null, service.getString(R.string.live_nodes), openNodes(selection?.group)).build())
                .addAction(Notification.Action.Builder(null, service.getString(R.string.live_disconnect), stopVpn).build())

            if (!manager.canPostPromotedNotifications()) {
                builder.addAction(Notification.Action.Builder(
                    null, service.getString(R.string.live_enable), promotionSettings()
                ).build())
            }

            // Same foreground ID throughout the session; no duplicate notifications.
            service.startForegroundCompat(R.id.nf_clash_status, builder.build())
            lastContent = fingerprint
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A profile/provider can be changing while sampled. Keep the existing
            // foreground notification and retry; UI failures must not stop the VPN.
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun run() = coroutineScope {
        val changes = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
            addAction(Intents.ACTION_PROXY_SELECTION_CHANGED)
            addAction(Intents.ACTION_OVERRIDE_CHANGED)
            addAction(Intents.ACTION_SERVICE_RECREATED)
        }
        val screens = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }
        var interactive = service.getSystemService<PowerManager>()?.isInteractive ?: true
        update()
        while (coroutineContext.isActive) {
            select<Unit> {
                changes.onReceive { if (interactive || StatusProvider.homePaused) update() }
                screens.onReceive {
                    interactive = it.action == Intent.ACTION_SCREEN_ON
                    if (interactive) update()
                }
                if (interactive && !StatusProvider.homePaused) onTimeout(3_000) { update() }
            }
        }
    }
}
