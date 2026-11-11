package com.github.kr328.clash.service.clash.module

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.model.MobilePolicy
import com.github.kr328.clash.service.store.MobileNetworkSettings
import com.github.kr328.clash.service.store.MobileNetworkStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

/** Network callbacks only: no polling while offline, screen-off or paused at home. */
class MobileNetworkModule(service: Service) : Module<Boolean>(service) {
    private val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private var recovery: Job? = null
    private var previousNetwork: String? = null
    private val handoverRecovery = UnderlayRecoveryPolicy()
    private val capabilityKeys = java.util.concurrent.ConcurrentHashMap<Network, String>()
    private val linkKeys = java.util.concurrent.ConcurrentHashMap<Network, String>()
    private var previousPause: Boolean? = null
    private var lastMaintenance = 0L
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { changes.trySend(Unit) }
        override fun onLost(network: Network) { capabilityKeys.remove(network); linkKeys.remove(network); changes.trySend(Unit) }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val key = listOf(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).joinToString()
            if (capabilityKeys.put(network,key) != key) changes.trySend(Unit)
        }
        override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) {
            val key = properties.linkAddresses.joinToString() + properties.dnsServers.joinToString()
            if (linkKeys.put(network,key) != key) changes.trySend(Unit)
        }
    }
    override suspend fun run() = coroutineScope {
        val broadcasts = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val profiles = receiveBroadcast(capacity = Channel.CONFLATED) { addAction(Intents.ACTION_PROFILE_LOADED) }
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        changes.trySend(Unit)
        try {
            while (isActive) {
                select<Unit> { changes.onReceive { }; broadcasts.onReceive { }; profiles.onReceive { lastMaintenance = 0 } }
                delay(1200)
                while (changes.tryReceive().isSuccess) { /* coalesce handover callbacks */ }
                val settings = runCatching { MobileNetworkStore(service).read() }.getOrDefault(MobileNetworkSettings())
                val state = snapshot(service, settings)
                Bridge.nativeMobileState(state.network != null, state.screen, state.metered, state.charging, state.pause)
                // A network change invalidates active TCP/TLS sessions, even when the
                // VPN interface stays up. Close stale sockets once on handover or
                // validated underlay recovery; never bypass certificate checking.
                val underlayChanged = handoverRecovery.shouldCloseStaleConnections(
                    state.key, state.network != null
                )
                if (underlayChanged && service is VpnService &&
                    !state.pause && !StatusProvider.homePaused && StatusProvider.profileLoaded
                ) {
                    Log.i("Underlay restored or changed: close stale proxy connections")
                    runCatching { Bridge.nativeReleaseConnections() }
                        .onFailure { Log.w("Underlay connection recovery failed", it) }
                }
                if (previousPause != state.pause) { previousPause = state.pause; if(service is VpnService) enqueueEvent(state.pause) }
                if (state.network == null || state.pause || !state.screen) {
                    recovery?.cancel(); recovery = null
                } else if (state.key != previousNetwork || lastMaintenance == 0L) {
                    recovery?.cancel()
                    recovery = launch {
                        // Only the active small mobile group; manual groups are not replaced.
                        delay(1500)
                        if (StatusProvider.profileLoaded && settings.enabled && !StatusProvider.homePaused) {
                            val mode = Clash.queryTunnelState().mode.name
                            val current = NotificationNodeResolver.mainGroup(mode, Clash.queryGroupNames(false),
                                com.github.kr328.clash.service.store.ServiceStore(service).notificationProxyGroup)
                            if (current != null) runCatching {
                                withTimeout(8_000) { CompletableDeferred<Unit>().also { Bridge.nativeProbeSelection(it,current) }.await() }
                            }

                        }
                        // Network return is the retry signal; there is no offline retry loop.
                        val now = System.currentTimeMillis()
                        if (StatusProvider.profileLoaded && now - lastMaintenance > 60_000) {
                            lastMaintenance = now
                            if (MobilePolicy.allowUpdate(settings.enabled, settings.wifiUpdates, true, state.metered, state.charging, 0)) {
                                val dao = com.github.kr328.clash.service.data.ImportedDao()
                                for (uuid in dao.queryAllUUIDs()) {
                                    val imported = dao.queryByUUID(uuid) ?: continue
                                    if (imported.interval < 15 * 60 * 1000L) continue
                                    val file = service.importedDir.resolve(uuid.toString()).resolve("config.yaml")
                                    if (file.exists() && now - file.lastModified() >= imported.interval.coerceAtLeast(24 * 60 * 60 * 1000L)) {
                                        runCatching { com.github.kr328.clash.service.ProfileReceiver.schedule(service,imported,false) }
                                            .onFailure { com.github.kr328.clash.service.ProfileReceiver.scheduleAfter(service,imported,6 * 60 * 60 * 1000L) }
                                    }
                                }
                            }
                            for (provider in Clash.queryProviders()) {
                                if (now - provider.updatedAt < 24 * 60 * 60 * 1000L) continue
                                if (!MobilePolicy.allowUpdate(settings.enabled, settings.wifiUpdates, true, state.metered,
                                        state.charging, now - provider.updatedAt)) continue
                                if (provider.vehicleType.toString() != "HTTP") continue
                                runCatching { withTimeout(60_000) { Clash.updateProvider(provider.type, provider.name).await() } }
                            }
                        }
                    }
                }
                previousNetwork = state.key
            }
        } finally { recovery?.cancel(); connectivity.unregisterNetworkCallback(callback); changes.close() }
    }
    data class State(val network: Network?, val screen: Boolean, val metered: Boolean, val charging: Boolean, val pause: Boolean, val key: String)
    companion object {
        @Suppress("DEPRECATION")
        fun currentSSID(context: Context): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
            return runCatching {
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid
                    ?.removeSurrounding("\"")?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }
            }.getOrNull()
        }
        fun snapshot(service: Service, settings: MobileNetworkSettings): State {
            val cm = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.allNetworks.filter { n -> cm.getNetworkCapabilities(n)?.let {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true }.minByOrNull { if (cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) 0 else 1 }
            val caps = network?.let(cm::getNetworkCapabilities)
            val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val identityAvailable = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            val ssid = if (service is VpnService && settings.enabled && settings.trustedWifiEnabled && wifi && identityAvailable) currentSSID(service) else null
            val lockdown = Build.VERSION.SDK_INT >= 29 && (service as? VpnService)?.isLockdownEnabled == true
            val battery = service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            return State(network, (service.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive,
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true, plugged,
                MobilePolicy.pauseAtHome(settings.enabled, settings.trustedWifiEnabled, ssid, settings.trustedSSIDs, lockdown),
                network?.let { n -> cm.getLinkProperties(n)?.let { p -> "$n:${p.linkAddresses}:${p.dnsServers}" } ?: n.toString() }.orEmpty())
        }
    }
}
