package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.github.kr328.clash.design.component.AccessControlMenu
import com.github.kr328.clash.design.dialog.CouiAnchoredMenu
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.theme.CouiTheme
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy
import com.github.kr328.clash.service.model.DomesticAppDefaults
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.layout.Group
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import androidx.compose.ui.unit.dp

/** Native COUI application routing list, no legacy RecyclerView or XML controls. */
class AccessControlDesign(
    context: Context,
    private val uiStore: UiStore,
    private val selected: MutableSet<String>,
    initialMode: AccessControlMode,
    running: Boolean,
) : Design<AccessControlDesign.Request>(context) {
    enum class Request { ReloadApps, SelectAll, SelectNone, SelectInvert, ApplyDomesticDefaults, Import, Export, Save }
    private val original = selected.toSet()
    private val originalMode = initialMode
    private val menu by lazy { AccessControlMenu(context, uiStore, requests) }

    var mode by mutableStateOf(initialMode)
        private set
    var allApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    private var filteredApps by mutableStateOf<List<AppInfo>>(emptyList())
    val apps get() = filteredApps
    val dirty get() = selected != original || mode != originalMode

    private var loaded by mutableStateOf(false)
    private var savingState by mutableStateOf(false)
    private var isRunning by mutableStateOf(running)
    private var keyword by mutableStateOf("")
    private var selectedOnly by mutableStateOf(false)
    private var showSystem by mutableStateOf(uiStore.accessControlSystemApp)
    private var version by mutableStateOf(0)
    private var count by mutableStateOf("")

    override val root: View = ComposeView(context).apply {
        setContent {
            version
            val editable = !savingState
            ColorOsLazyFormPage(
                title = stringResource(R.string.live_app_routing),
                saveLabel = stringResource(when {
                    savingState -> R.string.route_saving
                    isRunning -> R.string.route_save_reconnect
                    else -> R.string.route_save
                }),
                canSave = dirty && loaded && !savingState,
                saveInToolbar = true,
                onBack = { context.colorOsNavigateBack() },
                onSave = { if (dirty && loaded && !savingState) requests.trySend(Request.Save) },
            ) {
                item(key = "app-filter-controls") {
                    AccessControls(
                        keyword = keyword,
                        bypassOnly = selectedOnly,
                        system = showSystem,
                        enabled = !savingState,
                        onKeyword = { keyword = it; refresh() },
                        onBypassOnly = { selectedOnly = it; refresh() },
                        onSystem = {
                            showSystem = it
                            uiStore.accessControlSystemApp = it
                            refresh()
                        },
                    )
                }
                item(key = "app-list-actions") {
                    AppRoutingListHeader(
                        count = count,
                        options = menu.options(!savingState),
                        enabled = !savingState,
                    )
                }
                if (filteredApps.isEmpty()) {
                    item(key = "app-empty") {
                        Group {
                            Item {
                                Text(
                                    text = stringResource(if (loaded) R.string.app_empty else R.string.app_loading),
                                    kind = CouiTextKind.PreferenceSummary,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(filteredApps, key = { _, app -> app.packageName }) { index, app ->
                        val through = AppAccessPolicy.usesClash(mode, app.packageName in selected)
                        val route = stringResource(
                            if (through) R.string.app_via_clash else R.string.app_bypass,
                        )
                        CouiPreferenceRow(
                            title = app.label,
                            summary = app.packageName,
                            icon = app.icon,
                            iconSize = 40.dp,
                            enabled = editable,
                            onClick = { if (editable) chooseAppRoute(app) },
                            trailing = {
                                Text(
                                    text = route, kind = CouiTextKind.PreferenceValue,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            grouped = true,
                            firstInGroup = index == 0,
                            lastInGroup = index == filteredApps.lastIndex,
                        )
                    }
                }
            }
        }
    }

    fun patchApps(apps: List<AppInfo>) { allApps = apps; loaded = true; refresh() }
    fun rebindAll() = refresh()
    fun setSaving(value: Boolean) { savingState = value; refresh() }
    fun updateRunning(value: Boolean) { isRunning = value }

    /** This option changes only one app. The legacy VPN mode is an internal storage detail. */
    fun setAppRouting(packageName: String, throughClash: Boolean) {
        if (savingState) return
        val next = AppAccessPolicy.withRouting(mode, selected, packageName, throughClash)
        mode = next.mode
        selected.clear()
        selected.addAll(next.selected)
        restoreOriginalAllWhenReverted()
        refresh()
    }

    private fun restoreOriginalAllWhenReverted() {
        if (originalMode == AccessControlMode.AcceptAll &&
            mode == AccessControlMode.DenySelected && selected.isEmpty()) {
            mode = originalMode
            selected.addAll(original)
        }
    }

    /** Bulk operations apply to effective routes, not legacy set membership. */
    fun select(action: AppAccessPolicy.Selection) {
        if (savingState) return
        val visible = apps.map { it.packageName }.toList()
        visible.forEach { pkg ->
            val wasThrough = AppAccessPolicy.usesClash(mode, pkg in selected)
            val through = when (action) {
                AppAccessPolicy.Selection.All -> true
                AppAccessPolicy.Selection.None -> false
                AppAccessPolicy.Selection.Invert -> !wasThrough
            }
            val next = AppAccessPolicy.withRouting(mode, selected, pkg, through)
            mode = next.mode
            selected.clear()
            selected.addAll(next.selected)
        }
        restoreOriginalAllWhenReverted()
        refresh()
    }

    /** Explicit opt-in for existing users: change only curated domestic package IDs.
     * Do not override any other app's effective route or silently migrate old modes.
     */
    fun applyDomesticDefaults() {
        if (savingState) return
        DomesticAppDefaults.bypassPackages.forEach { pkg ->
            val next = AppAccessPolicy.withRouting(mode, selected, pkg, throughClash = false)
            mode = next.mode
            selected.clear()
            selected.addAll(next.selected)
        }
        refresh()
    }

    /** Clipboard import is an explicit bypass list, independent of legacy mode. */
    fun replaceBypassApps(packages: Set<String>) {
        if (savingState) return
        mode = AccessControlMode.DenySelected
        selected.clear()
        selected.addAll(packages)
        restoreOriginalAllWhenReverted()
        refresh()
    }

    private fun chooseAppRoute(app: AppInfo) {
        if (savingState) return
        context.showCouiContent { dismiss ->
            Dialog(
                onDismissRequest = dismiss,
                title = app.label.toString(),
                confirmAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
            ) {
                listOf(true to R.string.app_via_clash, false to R.string.app_bypass)
                    .forEach { (through, label) ->
                        Item(
                            onClick = {
                                setAppRouting(app.packageName, through)
                                dismiss()
                            },
                            trailing = {
                                RadioButton(
                                    selected = AppAccessPolicy.usesClash(
                                        mode, app.packageName in selected,
                                    ) == through,
                                )
                            },
                        ) {
                            Text(text = context.getString(label), kind = CouiTextKind.PreferenceTitle)
                        }
                    }
            }
        }
    }

    private fun refresh() {
        val query = keyword.trim()
        filteredApps = allApps.filter {
            (showSystem || !it.systemApp ||
                (selectedOnly && !AppAccessPolicy.usesClash(mode, it.packageName in selected))) &&
                (!selectedOnly || !AppAccessPolicy.usesClash(mode, it.packageName in selected)) &&
                (it.label.contains(query, true) || it.packageName.contains(query, true))
        }
        val bypassCount = allApps.count {
            !AppAccessPolicy.usesClash(mode, it.packageName in selected)
        }
        count = context.getString(R.string.app_count, bypassCount, filteredApps.size)
        version++
        onBackStateChanged()
    }

    init { refresh() }
}

/** COUI category header: counts and native anchored bulk menu share one line.
 * The app list below remains lazy, with one connected rounded card surface.
 */
@Composable
private fun AppRoutingListHeader(count: String, options: List<CouiMenuOption>, enabled: Boolean) {
    val colors = CouiTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    // Align count label, native SearchBar, filters and app cards to the
    // same 16dp outer column; do not use Group's 32dp title inset here.
    Row(
        Modifier.fillMaxWidth()
            .padding(
                start = CouiPageDefaults.horizontalMargin,
                end = CouiPageDefaults.horizontalMargin,
                top = CouiPageDefaults.groupSpacing,
                bottom = 8.dp,
            )
            .testTag("app-routing-count-header"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count,
            kind = CouiTextKind.GroupTitle,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag("app-routing-count-label"),
        )
        CouiAnchoredMenu(options) { open ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.card)
                    .couiButtonFeedback(CouiTheme.isDark, source, enabled)
                    .clickable(
                        interactionSource = source, indication = null, enabled = enabled,
                        role = Role.Button, onClick = open,
                    ).testTag("app-routing-bulk-action"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_more_vert),
                    contentDescription = stringResource(R.string.more),
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(
                        if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.38f),
                    ),
                )
            }
        }
    }
}
