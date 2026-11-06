package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenSettings,
    }

    private var profileName by mutableStateOf<String?>(null)
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

    fun setHomePaused(paused: Boolean) {
        pausedAtHome = paused
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
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
            modeLabel = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
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
