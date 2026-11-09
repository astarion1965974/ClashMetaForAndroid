package com.github.kr328.clash.design.component

import android.content.Context
import com.github.kr328.clash.design.AccessControlDesign.Request
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.channels.Channel

class AccessControlMenu(
    private val context: Context,
    private val uiStore: UiStore,
    private val requests: Channel<Request>,
) {
    fun show(selectionEnabled: Boolean = true) {
        context.showCouiMenu(options(selectionEnabled))
    }

    internal fun options(selectionEnabled: Boolean = true): List<CouiMenuOption> {
        fun option(res: Int, selected: Boolean? = null, enabled: Boolean = true, action: () -> Unit) =
            CouiMenuOption(context.getString(res), selected, enabled, action)
        fun sort(value: AppInfoSort) { uiStore.accessControlSort = value; requests.trySend(Request.ReloadApps) }
        return listOf(
            option(R.string.app_bulk_all, enabled = selectionEnabled) { requests.trySend(Request.SelectAll) },
            option(R.string.app_bulk_none, enabled = selectionEnabled) { requests.trySend(Request.SelectNone) },
            option(R.string.app_bulk_invert, enabled = selectionEnabled) { requests.trySend(Request.SelectInvert) },
            option(R.string.name, uiStore.accessControlSort == AppInfoSort.Label) { sort(AppInfoSort.Label) },
            option(R.string.package_name, uiStore.accessControlSort == AppInfoSort.PackageName) { sort(AppInfoSort.PackageName) },
            option(R.string.install_time, uiStore.accessControlSort == AppInfoSort.InstallTime) { sort(AppInfoSort.InstallTime) },
            option(R.string.update_time, uiStore.accessControlSort == AppInfoSort.UpdateTime) { sort(AppInfoSort.UpdateTime) },
            option(R.string.reverse, uiStore.accessControlReverse) { uiStore.accessControlReverse = !uiStore.accessControlReverse; requests.trySend(Request.ReloadApps) },
            option(R.string.import_from_clipboard, enabled = selectionEnabled) { requests.trySend(Request.Import) },
            option(R.string.export_to_clipboard) { requests.trySend(Request.Export) },
        )
    }
}
