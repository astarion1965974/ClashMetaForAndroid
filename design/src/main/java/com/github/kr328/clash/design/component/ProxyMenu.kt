package com.github.kr328.clash.design.component

import android.content.Context
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.channels.Channel

class ProxyMenu(
    private val context: Context,
    private val mode: TunnelState.Mode?,
    private val uiStore: UiStore,
    private val requests: Channel<ProxyDesign.Request>,
    private val updateConfig: () -> Unit,
) {
    fun show() {
        context.showCouiMenu(options())
    }

    internal fun options(): List<CouiMenuOption> {
        fun option(group: Int, label: Int, selected: Boolean, action: () -> Unit) = CouiMenuOption(
            "${context.getString(group)} · ${context.getString(label)}", selected, action = action)
        fun line(value: Int) { uiStore.proxyLine = value; updateConfig(); requests.trySend(ProxyDesign.Request.ReloadAll) }
        fun sort(value: ProxySort) { uiStore.proxySort = value; requests.trySend(ProxyDesign.Request.ReloadAll) }
        fun changeMode(value: TunnelState.Mode?) { requests.trySend(ProxyDesign.Request.PatchMode(value)) }
        return listOf(
            option(R.string.filter, R.string.not_selectable, uiStore.proxyExcludeNotSelectable) {
                uiStore.proxyExcludeNotSelectable = !uiStore.proxyExcludeNotSelectable; requests.trySend(ProxyDesign.Request.ReLaunch)
            },
            option(R.string.mode, R.string.dont_modify, mode == null) { changeMode(null) },
            option(R.string.mode, R.string.direct_mode, mode == TunnelState.Mode.Direct) { changeMode(TunnelState.Mode.Direct) },
            option(R.string.mode, R.string.global_mode, mode == TunnelState.Mode.Global) { changeMode(TunnelState.Mode.Global) },
            option(R.string.mode, R.string.rule_mode, mode == TunnelState.Mode.Rule) { changeMode(TunnelState.Mode.Rule) },
            option(R.string.layout, R.string.single, uiStore.proxyLine == 1) { line(1) },
            option(R.string.layout, R.string.doubles, uiStore.proxyLine == 2) { line(2) },
            option(R.string.layout, R.string.multiple, uiStore.proxyLine == 3) { line(3) },
            option(R.string.sort, R.string.default_, uiStore.proxySort == ProxySort.Default) { sort(ProxySort.Default) },
            option(R.string.sort, R.string.name, uiStore.proxySort == ProxySort.Title) { sort(ProxySort.Title) },
            option(R.string.sort, R.string.delay, uiStore.proxySort == ProxySort.Delay) { sort(ProxySort.Delay) },
        )
    }
}
