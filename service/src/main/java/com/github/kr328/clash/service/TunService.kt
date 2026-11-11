package com.github.kr328.clash.service

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.*
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.VpnCapturePolicy
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.store.MobileNetworkStore
import com.github.kr328.clash.service.store.ManagedRoutingStore
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import com.github.kr328.clash.service.util.parseCIDR
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

class TunService : VpnService(), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val self: TunService
        get() = this

    private var reason: String? = null

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        val close = install(CloseModule(self))
        val tun = install(TunModule(self))
        val config = install(ConfigurationModule(self))
        val network = install(NetworkObserveModule(self))
        val mobile = install(MobileNetworkModule(self))

        if (Build.VERSION.SDK_INT >= 36 && store.liveNotification)
            install(LiveNotificationModule(self))
        else if (store.dynamicNotification)
            install(DynamicNotificationModule(self))
        else
            install(StaticNotificationModule(self))

        install(AppListCacheModule(self))
        install(TimeZoneModule(self))
        install(SuspendModule(self))

        try {
            StatusProvider.homePaused = MobileNetworkModule.snapshot(self, MobileNetworkStore(self).read()).pause
            if (!StatusProvider.homePaused) tun.open()

            while (isActive) {
                val quit = select<Boolean> {
                    close.onEvent {
                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    mobile.onEvent { pause ->
                        if (pause != StatusProvider.homePaused) {
                            if (pause) { com.github.kr328.clash.core.bridge.Bridge.nativeReleaseConnections(); TunModule.requestStop() } else tun.open()
                            StatusProvider.homePaused = pause
                            sendServiceRecreated()
                        }
                        false
                    }
                    network.onEvent { n ->
                        if (Build.VERSION.SDK_INT in 22..28) @TargetApi(22) {
                            setUnderlyingNetworks(n?.let { arrayOf(it) })
                        }

                        false
                    }
                }

                if (quit) break
            }
        } catch (e: Exception) {
            Log.e("Create clash runtime: ${e.message}", e)

            reason = e.message
        } finally {
            withContext(NonCancellable) {
                tun.close()

                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (StatusProvider.serviceRunning)
            return stopSelf()

        StatusProvider.serviceRunning = true

        StaticNotificationModule.createNotificationChannel(this)
        StaticNotificationModule.notifyLoadingNotification(this)

        runtime.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendClashStarted()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        TunModule.requestStop()

        StatusProvider.serviceRunning = false
        StatusProvider.homePaused = false

        sendClashStopped(reason)

        cancelAndJoinBlocking()

        Log.i("TunService destroyed: ${reason ?: "successfully"}")

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runtime.requestGc()
    }

    private fun TunModule.open() {
        TunDiagnostics.reset(TUN_MTU)
        val store = ServiceStore(self)
        // Initialize defaults before VPN routing is built, even if the user has
        // not opened Application Routing yet. Saved policies always take priority.
        val protectedMode = ManagedRoutingStore(self).read().protectedMode
        if (!protectedMode) store.initializeDomesticBypassIfUnconfigured()
        val policy = VpnCapturePolicy.resolve(protectedMode, store.accessControlMode, store.allowIpv6,
            store.bypassPrivateNetwork, store.allowBypass, store.dnsHijacking)
        val ipv6 = policy.ipv6

        val device = with(Builder()) {
            // Interface address
            addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
            if (ipv6) {
                addAddress(TUN_GATEWAY6, TUN_SUBNET_PREFIX6)
            }

            // Route
            if (policy.bypassPrivate) {
                resources.getStringArray(R.array.bypass_private_route).map(::parseCIDR).forEach {
                    addRoute(it.ip, it.prefix)
                }
                if (ipv6) {
                    resources.getStringArray(R.array.bypass_private_route6).map(::parseCIDR).forEach {
                        addRoute(it.ip, it.prefix)
                    }
                }

                // Route of virtual DNS
                addRoute(TUN_DNS, 32)
                if (ipv6) {
                    addRoute(TUN_DNS6, 128)
                }
            } else {
                addRoute(NET_ANY, 0)
                if (ipv6) {
                    addRoute(NET_ANY6, 0)
                }
            }

            // Access Control
            // Remote Android package catalogs may contain thousands of packages,
            // including apps not yet installed. Keep the full list in preferences
            // for future installs, but submit only installed package IDs to the
            // VPN builder (which otherwise performs thousands of failed lookups).
            val mode = policy.mode
            if (mode != AccessControlMode.AcceptAll) {
                val installed = packageManager.getInstalledPackages(0)
                    .asSequence().map { it.packageName }.toSet()
                when (mode) {
                    AccessControlMode.AcceptSelected -> {
                        ((store.accessControlPackages intersect installed) + packageName).forEach {
                            runCatching { addAllowedApplication(it) }
                        }
                    }
                    AccessControlMode.DenySelected -> {
                        (store.accessControlPackages intersect installed).asSequence()
                            .filter { it != packageName }
                            .forEach { runCatching { addDisallowedApplication(it) } }
                    }
                    AccessControlMode.AcceptAll -> Unit
                }
            }

            // Blocking
            setBlocking(false)

            // Mtu
            setMtu(TUN_MTU)

            // Session Name
            setSession("Clash")

            // Virtual Dns Server
            addDnsServer(TUN_DNS)
            if (ipv6) {
                addDnsServer(TUN_DNS6)
            }

            // Open MainActivity
            setConfigureIntent(
                PendingIntent.getActivity(
                    self,
                    R.id.nf_vpn_status,
                    Intent().setComponent(Components.MAIN_ACTIVITY),
                    pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                )
            )

            // Metered
            if (Build.VERSION.SDK_INT >= 29) {
                setMetered(false)
            }

            // System Proxy
            if (Build.VERSION.SDK_INT >= 29 && store.systemProxy) {
                listenHttp()?.let {
                    setHttpProxy(
                        ProxyInfo.buildDirectProxy(
                            it.address.hostAddress,
                            it.port,
                            HTTP_PROXY_BLACK_LIST + if (store.bypassPrivateNetwork) HTTP_PROXY_LOCAL_LIST else emptyList()
                        )
                    )
                }
            }

            if (policy.allowBypass) {
                allowBypass()
            }

            TunModule.TunDevice(
                fd = establish()?.detachFd()
                    ?: throw NullPointerException("Establish VPN rejected by system"),
                stack = store.tunStackMode,
                gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX" + if (ipv6) ",$TUN_GATEWAY6/$TUN_SUBNET_PREFIX6" else "",
                portal = TUN_PORTAL + if (ipv6) ",$TUN_PORTAL6" else "",
                dns = if (policy.hijackDns) NET_ANY else (TUN_DNS + if (ipv6) ",$TUN_DNS6" else ""),
            )
        }

        attach(device)
    }

    companion object {
        // Conservative Android VPN MTU for mobile tethering and carrier tunnels.
        private const val TUN_MTU = 1400
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_SUBNET_PREFIX6 = 126
        private const val TUN_GATEWAY6 = "fdfe:dcba:9876::1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_PORTAL6 = "fdfe:dcba:9876::2"
        private const val TUN_DNS = TUN_PORTAL
        private const val TUN_DNS6 = TUN_PORTAL6
        private const val NET_ANY = "0.0.0.0"
        private const val NET_ANY6 = "::"

        private val HTTP_PROXY_LOCAL_LIST: List<String> = listOf(
            "localhost",
            "*.local",
            "127.*",
            "10.*",
            "172.16.*",
            "172.17.*",
            "172.18.*",
            "172.19.*",
            "172.2*",
            "172.30.*",
            "172.31.*",
            "192.168.*"
        )
        private val HTTP_PROXY_BLACK_LIST: List<String> = listOf(
            "*zhihu.com",
            "*zhimg.com",
            "*jd.com",
            "100ime-iat-api.xfyun.cn",
            "*360buyimg.com",
        )
    }
}
