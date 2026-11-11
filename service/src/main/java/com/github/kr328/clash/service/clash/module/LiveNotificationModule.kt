package com.github.kr328.clash.service.clash.module

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
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
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlin.coroutines.coroutineContext

/** Standard platform templates let ColorOS render the capsule and expanded live card. */
@TargetApi(36)
class LiveNotificationModule(service: Service) : Module<Unit>(service) {
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

    private var lastContent: String? = null
    private var lastRegionNode: String? = null
    private var lastRegion: String? = null
    private fun region(node: String): String? {
        if (node != lastRegionNode) { lastRegionNode = node; lastRegion = NotificationNodeRegion.regionLabel(node) }
        return lastRegion
    }

    private var quotaProfile: UUID? = null
    private var quotaRefreshedAt = -1L
    private var cachedQuota: SubscriptionQuota? = null

    /** Subscription metadata belongs to the active imported profile, not session traffic. */
    private suspend fun readQuota(): SubscriptionQuota? {
        val active = store.activeProfile
        if (active == null) {
            quotaProfile = null
            cachedQuota = null
            return null
        }
        val now = SystemClock.elapsedRealtime()
        if (quotaProfile != active || quotaRefreshedAt < 0 || now - quotaRefreshedAt >= 60_000L) {
            val result = try {
                withContext(Dispatchers.IO) {
                    ImportedDao().queryByUUID(active)?.let {
                        SubscriptionQuota.from(it.upload, it.download, it.total)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Do not lose the VPN notification while a profile is being replaced.
            }
            cachedQuota = result
            quotaProfile = active
            quotaRefreshedAt = now
        }
        return cachedQuota
    }

    private suspend fun update() {
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
                        if (value.getValue("child").jsonPrimitive.boolean) setOf(selected) else emptySet(),
                        value["unavailable"]?.jsonPrimitive?.booleanOrNull ?: false)
                }
            }

            val paused = StatusProvider.homePaused
            val nodeLabel = when {
                paused -> service.getString(R.string.live_home_title)
                mode == TunnelState.Mode.Direct -> service.getString(R.string.live_direct)
                selection?.unavailable == true -> service.getString(R.string.live_probe_failed)
                selection?.balanced == true -> service.getString(R.string.live_multi_route)
                selection?.node == "DIRECT" -> service.getString(R.string.live_direct)
                selection?.node != null -> selection.node
                else -> service.getString(R.string.live_unavailable)
            }
            val capsuleLabel = when {
                paused -> service.getString(R.string.live_home_capsule)
                mode == TunnelState.Mode.Direct || selection?.node == "DIRECT" -> service.getString(R.string.live_direct)
                selection?.unavailable == true -> service.getString(R.string.live_probe_failed)
                selection?.balanced == true -> service.getString(R.string.live_multi_route)
                selection?.node in setOf("REJECT", "REJECT-DROP") -> service.getString(R.string.live_blocked)
                selection?.node != null -> region(selection.node)
                    ?: service.getString(R.string.live_proxy_active)
                else -> service.getString(R.string.live_unavailable)
            }
            val groupLabel = service.getString(R.string.live_selected_group, selection?.group ?: "—")
            val quota = readQuota()
            val quotaLabel = quota?.let {
                service.getString(
                    R.string.live_quota_usage,
                    Formatter.formatShortFileSize(service, it.used),
                    Formatter.formatShortFileSize(service, it.total),
                    it.percentage,
                )
            } ?: service.getString(R.string.live_quota_missing)
            val fingerprint = listOf(nodeLabel, capsuleLabel, groupLabel, quotaLabel, quota?.percentage)
                .joinToString("\u0000")
            if (fingerprint == lastContent) return

            val builder = Notification.Builder(service, StaticNotificationModule.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_service)
                .setColor(service.getColorCompat(R.color.color_live_notification))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                // Title and group remain visible above the native ColorOS progress rail.
                .setContentTitle(nodeLabel)
                .setContentText(groupLabel)
                .setSubText(quotaLabel)
                .setContentIntent(openApp)
                .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
                .setShortCriticalText(NotificationNodeResolver.chipText(capsuleLabel))
                // Exactly two system-owned actions stay at the bottom of the expanded card.
                .addAction(Notification.Action.Builder(null, service.getString(R.string.live_nodes), openNodes(selection?.group)).build())
                .addAction(Notification.Action.Builder(null, service.getString(R.string.live_disconnect), stopVpn).build())

            if (quota != null) {
                // Native API 36 ProgressStyle expands to its content height on ColorOS 17.
                // Custom RemoteViews would disqualify the notification from Live Updates.
                builder.setStyle(Notification.ProgressStyle().setProgress(quota.percentage))
            } else {
                // File profiles or subscriptions without a known cap have no meaningful ratio.
                builder.setStyle(Notification.BigTextStyle()
                    .setBigContentTitle(nodeLabel).bigText("$groupLabel\n$quotaLabel"))
            }

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
                changes.onReceive {
                    if (it.action == Intents.ACTION_PROFILE_LOADED) quotaRefreshedAt = -1L
                    if (interactive || StatusProvider.homePaused) update()
                }
                screens.onReceive {
                    interactive = it.action == Intent.ACTION_SCREEN_ON
                    if (interactive) update()
                }
                // Selection changes refresh immediately through broadcasts; this slower
                // safety poll covers changes from outside the app without frequent wakeups.
                if (interactive && !StatusProvider.homePaused) onTimeout(15_000) { update() }
            }
        }
    }
}
