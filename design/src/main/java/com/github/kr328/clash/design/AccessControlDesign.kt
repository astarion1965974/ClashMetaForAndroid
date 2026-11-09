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
import com.itos.coui.theme.CouiThemeimport com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.model.AppAccessPolicy
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.layout.Group
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
    enum class Request { ReloadApps, SelectAll, SelectNone, SelectInvert, Import, Export, Save }
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
    private var modeHint by mutableStateOf("")
    private var modeLabel by mutableStateOf("")

    override val root: View = ComposeView(context).apply {
        setContent {
            version
            val editable = !savingState && mode != AccessControlMode.AcceptAll
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
                        modeLabel, modeHint, keyword, selectedOnly, showSystem, !savingState,
                        onMode = ::chooseMode,
                        onKeyword = { keyword = it; refresh() },
                        onSelectedOnly = { selectedOnly = it; refresh() },
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
                        options = menu.options(mode != AccessControlMode.AcceptAll),
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
                        val checked = app.packageName in selected
                        val route = context.getString(
                            if (AppAccessPolicy.usesClash(mode, checked)) R.string.app_via_clash
                            else R.string.app_bypass,
                        )
                        CouiPreferenceRow(
                            title = app.label,
                            summary = "${app.packageName}\n$route",
                            icon = app.icon,
                            iconSize = 40.dp,
                            enabled = editable,
                            onClick = {
                                if (editable) {
                                    if (!selected.remove(app.packageName)) selected.add(app.packageName)
                                    refresh()
                                }
                            },
                            trailing = { Checkbox(checked = checked, enabled = editable) },
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
    fun select(action: AppAccessPolicy.Selection) {
        if (mode == AccessControlMode.AcceptAll || savingState) return
        val result = AppAccessPolicy.select(
            selected, apps.map { it.packageName }.toSet(), action,
        )
        selected.clear()
        selected.addAll(result)
        refresh()
    }

    private fun refresh() {
        val query = keyword.trim()
        val selectedSet = selected.toSet()
        filteredApps = allApps.filter {
            (showSystem || !it.systemApp || (selectedOnly && it.packageName in selectedSet)) &&
                (!selectedOnly || it.packageName in selectedSet) &&
                (it.label.contains(query, true) || it.packageName.contains(query, true))
        }
        count = context.getString(R.string.app_count, selected.size, filteredApps.size)
        modeHint = context.getString(when (mode) {
            AccessControlMode.AcceptAll -> R.string.app_hint_all
            AccessControlMode.AcceptSelected -> R.string.app_hint_allow
            AccessControlMode.DenySelected -> R.string.app_hint_bypass
        })
        modeLabel = context.getString(when (mode) {
            AccessControlMode.AcceptAll -> R.string.app_mode_all
            AccessControlMode.AcceptSelected -> R.string.app_mode_allow
            AccessControlMode.DenySelected -> R.string.app_mode_bypass
        })
        version++
        onBackStateChanged()
    }

    private fun chooseMode() {
        if (savingState) return
        val labels = listOf(R.string.app_mode_all, R.string.app_mode_allow, R.string.app_mode_bypass)
        context.showCouiContent { dismiss ->
            Dialog(
                onDismissRequest = dismiss,
                title = context.getString(R.string.access_control_mode),
                confirmAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
            ) {
                labels.forEachIndexed { index, label ->
                    Item(
                        onClick = { mode = AccessControlMode.values()[index]; dismiss(); refresh() },
                        trailing = { RadioButton(selected = mode.ordinal == index) },
                    ) { Text(text = context.getString(label), kind = CouiTextKind.PreferenceTitle) }
                }
            }
        }
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
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count,
            kind = CouiTextKind.GroupTitle,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
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
