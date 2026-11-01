package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.remote.Remote
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

suspend fun Context.restartForRouting(running: Boolean): Boolean {
    if (!running) return true
    stopClashService()
    val stopped = withTimeoutOrNull(15_000) {
        while (Remote.broadcasts.clashRunning) delay(200)
        true
    } ?: false
    if (!stopped) return false
    return runCatching { startClashService() == null }.getOrDefault(false)
}
