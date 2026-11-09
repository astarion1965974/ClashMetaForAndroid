package com.github.kr328.clash.design

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.dialog.CouiAnchoredMenu
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiAlert
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure COUI proxy selection: native glass TopAppBar, COUI chip tab groups,
 * and lazy proxy cards. The Clash core request and state contracts are unchanged.
 */
class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    private val groupNames: List<String>,
    private val uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()
        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private data class ProxyGroup(
        val proxies: List<Proxy> = emptyList(),
        val selectable: Boolean = false,
        val selectedName: String = "?",
        val links: Map<String, String> = emptyMap(),
        val testing: Boolean = false,
    )
    private var groups by mutableStateOf(List(groupNames.size) { ProxyGroup() })
    private var selectedPage by mutableIntStateOf(
        groupNames.indexOf(uiStore.proxyLastGroup).coerceAtLeast(0),
    )
    private var proxyLine by mutableIntStateOf(uiStore.proxyLine)
    private val menu by lazy {
        ProxyMenu(context, overrideMode, uiStore, requests) {
            proxyLine = uiStore.proxyLine
        }
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
            val density = LocalDensity.current
            val groupScrollStates = groupNames.map { name ->
                key(name) { rememberLazyGridState() }
            }
            var tabsHeightPx by remember { mutableIntStateOf(0) }
            val tabsBackdrop = rememberCouiBackdrop()
            CouiTheme(darkTheme = dark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ColorOsCompactTopBar(
                            title = stringResource(R.string.proxy),
                            navigationIcon = {
                                ToolbarIcon(
                                    com.itos.coui.icons.CouiIcons.Filled.Back,
                                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                                ) { context.colorOsNavigateBack() }
                            },
                            actions = {
                                if (groupNames.isNotEmpty()) {
                                    val testing = groups.getOrNull(selectedPage)?.testing == true
                                    if (!testing) ToolbarIcon(
                                        ImageVector.vectorResource(R.drawable.ic_baseline_flash_on),
                                        stringResource(R.string.delay_test),
                                    ) { requestUrlTesting() }
                                }
                                CouiAnchoredMenu(menu.options()) { open ->
                                    ToolbarIcon(
                                        ImageVector.vectorResource(R.drawable.ic_baseline_more_vert),
                                        stringResource(R.string.more),
                                        open,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    val scrollPadding = padding
                    if (groupNames.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(scrollPadding).consumeWindowInsets(padding)) {
                            Text(
                                text = stringResource(R.string.proxy_empty_tips),
                                kind = CouiTextKind.PreferenceSummary,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        val groupIndex = selectedPage.coerceIn(groupNames.indices)
                        val current = groups.getOrNull(groupIndex)
                        val gridState = groupScrollStates[groupIndex]
                        BoxWithConstraints(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
                            // Keep a solid source behind the native COUI ChipTab material, as DemoChipSamples does.
                            Box(Modifier.matchParentSize().couiBackdrop(tabsBackdrop)
                                .background(if (dark) Color.Black else Color.White))
                            val margin = CouiPageDefaults.horizontalMargin
                            val minCellWidth = if (density.fontScale > 1.15f) 176.dp else 148.dp
                            val availableWidth = (maxWidth - margin * 2).coerceAtLeast(minCellWidth)
                            val capacity = ((availableWidth.value + 12f) / (minCellWidth.value + 12f))
                                .toInt().coerceAtLeast(1)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(minOf(proxyLine.coerceIn(1, 3), capacity)),
                                state = gridState,
                                modifier = Modifier.fillMaxSize()
                                    .background(CouiTheme.colorScheme.background)
                                    .selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(
                                    start = scrollPadding.calculateLeftPadding(LocalLayoutDirection.current) + margin,
                                    top = scrollPadding.calculateTopPadding() + with(density) { tabsHeightPx.toDp() } + 8.dp,
                                    end = scrollPadding.calculateRightPadding(LocalLayoutDirection.current) + margin,
                                    bottom = scrollPadding.calculateBottomPadding() + 24.dp,
                                ),
                            ) {
                                itemsIndexed(
                                    current?.proxies.orEmpty(),
                                    key = { index, proxy -> "${index}:${proxy.name}" },
                                ) { _, proxy ->
                                    val description = if (proxy.isGroup) {
                                        val linked = current?.links?.get(proxy.name)
                                        if (linked != null) "${proxy.type} (${linked.ifEmpty { "*" }})"
                                        else proxy.type
                                    } else proxy.subtitle
                                    val delayText = proxy.delay.takeIf {
                                        it in 0..Short.MAX_VALUE
                                    }?.let { " · ${it} ms" }.orEmpty()
                                    ProxyNodeCard(
                                        name = proxy.name,
                                        title = if (proxy.isGroup) proxy.name else proxy.title,
                                        summary = description + delayText,
                                        selected = proxy.name == current?.selectedName,
                                        enabled = current?.selectable == true,
                                        onSelect = {
                                            requests.trySend(Request.Select(groupIndex, proxy.name))
                                        },
                                    )
                                }
                            }
                            Box(
                                Modifier.align(Alignment.TopCenter)
                                    .padding(top = scrollPadding.calculateTopPadding())
                                    .onSizeChanged { tabsHeightPx = it.height }
                                    .testTag("proxy-group-tabs"),
                            ) {
                                ProxyTabs(groupNames, groupIndex, tabsBackdrop) { index ->
                                    selectedPage = index
                                    uiStore.proxyLastGroup = groupNames[index]
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>,
    ) {
        withContext(Dispatchers.Main) {
            if (position !in groups.indices) return@withContext
            groups = groups.toMutableList().also {
                it[position] = ProxyGroup(proxies, selectable, parent.now, links.mapValues { entry -> entry.value.now }, testing = false)
            }
        }
    }

    /** Create new snapshot values so Compose updates both the selected card and linked group labels. */
    fun updateSelectedProxy(position: Int, name: String) {
        if (position !in groups.indices) return
        val groupName = groupNames[position]
        groups = groups.mapIndexed { index, group ->
            val updatedLinks = group.links + (groupName to name)
            if (index == position) group.copy(selectedName = name, links = updatedLinks)
            else group.copy(links = updatedLinks)
        }
    }

    suspend fun showModeSwitchTips() {
        showToast(R.string.mode_switch_tips, ToastDuration.Long)
    }

    fun requestUrlTesting() {
        if (selectedPage !in groups.indices || groups[selectedPage].testing) return
        val index = selectedPage
        groups = groups.toMutableList().also {
            it[index] = it[index].copy(testing = true)
        }
        requests.trySend(Request.UrlTest(index))
    }
}

/**
 * COUI card-grid selector. The native radio supplies the selection animation;
 * the whole card retains single-selection semantics and native press feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProxyNodeCard(
    name: String,
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val shape = CouiPageDefaults.shape
    CouiAnchoredMenu(
        listOf(
            CouiMenuOption(context.getString(R.string.coui_node_full_name)) {
                context.showCouiAlert(context.getString(R.string.coui_node_full_name), name)
            },
            CouiMenuOption(context.getString(R.string.coui_node_copy_name)) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText("proxy name", name))
            },
        ),
    ) { open ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(shape)
                .background(colors.card)
                .couiButtonFeedback(CouiTheme.isDark, source, enabled, list = true)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    enabled = true,
                    role = Role.RadioButton,
                    onClick = { if (enabled) onSelect() },
                    onLongClick = open,
                )
                .semantics { this.selected = selected }
                .testTag("proxy-node-$name")
                .padding(horizontal = CouiPageDefaults.rowPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                kind = CouiTextKind.PreferenceTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    kind = CouiTextKind.PreferenceSummary,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RadioButton(selected = selected, enabled = enabled)
            }
        }
    }
}
