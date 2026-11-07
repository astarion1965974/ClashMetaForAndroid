package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.service.model.Profile
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenSettings,
        CheckDns,
    }

    private var profileName by mutableStateOf<String?>(null)
    private var quota by mutableStateOf<HomeQuota?>(null)
    private var days by mutableStateOf<List<HomeTrafficDay>>(emptyList())
    private var dns by mutableStateOf<List<HomeDnsResult>>(emptyList())
    private var dnsLoading by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var forwarded by mutableStateOf<String?>(null)
    private var modeLabel by mutableStateOf<String?>(null)
    private var hasProviders by mutableStateOf(false)
    var pausedAtHome by mutableStateOf(false)
        private set

    override val root: View = ComposeView(context).apply {
        setContent {
            ClashHome(
                running = running,
                paused = pausedAtHome,
                profileName = profileName,
                quota = quota,
                days = days,
                dns = dns,
                dnsLoading = dnsLoading,
                forwarded = forwarded,
                mode = modeLabel,
                hasProviders = hasProviders,
                onRequest = ::request,
            )
        }
    }

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            profileName = name
        }
    }

    suspend fun setProfile(profile: Profile?) = withContext(Dispatchers.Main) {
        profileName = profile?.name
        quota = profile?.takeIf { it.total > 0 }?.let {
            val upload = it.upload.coerceAtLeast(0)
            val download = it.download.coerceAtLeast(0)
            val used = if (upload > Long.MAX_VALUE - download) Long.MAX_VALUE else upload + download
            HomeQuota(it.total, used, (it.total - used.coerceAtMost(it.total)).coerceAtLeast(0))
        }
    }

    suspend fun setTrafficHistory(json: String) = withContext(Dispatchers.Main) {
        days = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                HomeTrafficDay(item.optString("date"), item.optLong("upload"),
                    item.optLong("download"), item.optBoolean("recorded"))
            }
        }.getOrDefault(emptyList())
    }

    fun updateDnsLoading(value: Boolean) { dnsLoading = value }

    fun setDnsResults(results: List<Pair<String, String>>) {
        dns = results.map { (domain, json) ->
            val item = runCatching { JSONObject(json) }.getOrDefault(JSONObject())
            HomeDnsResult(domain, item.optJSONArray("addresses")?.optString(0).orEmpty(),
                item.optString("error"), item.optLong("millis", -1))
        }
        dnsLoading = false
    }

    fun setHomePaused(paused: Boolean) {
        pausedAtHome = paused
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            if (this@MainDesign.running != running) dns = emptyList()
            this@MainDesign.running = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            val next = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
            if (modeLabel != next) dns = emptyList()
            modeLabel = next
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            hasProviders = has
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
