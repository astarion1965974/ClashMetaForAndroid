package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.resolveThemedBoolean
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
        val parent: ProxyState = ProxyState("?"),
        val links: Map<String, ProxyState> = emptyMap(),
        val testing: Boolean = false,
    )
    private var groups by mutableStateOf(List(groupNames.size) { ProxyGroup() })
    private var selectedPage by mutableIntStateOf(
        groupNames.indexOf(uiStore.proxyLastGroup).coerceAtLeast(0),
    )
    private var proxyLine by mutableIntStateOf(uiStore.proxyLine)
    private var revision by mutableIntStateOf(0)
    private val menu by lazy {
        ProxyMenu(context, overrideMode, uiStore, requests) {
            proxyLine = uiStore.proxyLine
        }
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            revision
            val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
            val density = LocalDensity.current
            val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            val gridState = rememberLazyGridState()
            CouiTheme(darkTheme = dark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        ColorOsLargeTopBar(
                            title = stringResource(R.string.proxy),
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                ToolbarIcon(
                                    com.itos.coui.icons.CouiIcons.Back,
                                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                                ) { (context as? AppCompatActivity)?.onSupportNavigateUp() }
                            },
                            actions = {
                                if (groupNames.isNotEmpty()) {
                                    val testing = groups.getOrNull(selectedPage)?.testing == true
                                    if (!testing) ToolbarIcon(
                                        ImageVector.vectorResource(R.drawable.ic_baseline_flash_on),
                                        stringResource(R.string.delay_test),
                                    ) { requestUrlTesting() }
                                }
                                ToolbarIcon(
                                    ImageVector.vectorResource(R.drawable.ic_baseline_more_vert),
                                    stringResource(R.string.more),
                                ) { menu.show() }
                            },
                        )
                    },
                ) { padding ->
                    val contentPadding = scrollBehavior.state.contentPadding(padding, density)
                    Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(padding)) {
                        if (groupNames.isEmpty()) {
                            Text(
                                text = stringResource(R.string.proxy_empty_tips),
                                kind = CouiTextKind.PreferenceSummary,
                                modifier = Modifier.padding(24.dp),
                            )
                        } else {
                            ProxyTabs(
                                groupNames,
                                selectedPage.coerceIn(groupNames.indices),
                            ) { index ->
                                selectedPage = index
                                uiStore.proxyLastGroup = groupNames[index]
                            }
                            val current = groups.getOrNull(selectedPage)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(proxyLine.coerceIn(1, 3)),
                                state = gridState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                itemsIndexed(current?.proxies.orEmpty()) { _, proxy ->
                                    val selected = proxy.name == current?.parent?.now
                                    val description = if (proxy.isGroup) {
                                        val linked = current?.links?.get(proxy.name)?.now
                                        if (linked != null) "${proxy.type} (${linked.ifEmpty { "*" }})"
                                        else proxy.type
                                    } else proxy.subtitle
                                    val delayText = proxy.delay.takeIf {
                                        it in 0..Short.MAX_VALUE
                                    }?.let { " · ${it} ms" }.orEmpty()
                                    CouiPreferenceRow(
                                        title = if (proxy.isGroup) proxy.name else proxy.title,
                                        summary = description + delayText,
                                        icon = null,
                                        enabled = current?.selectable == true,
                                        onClick = if (current?.selectable == true) {
                                            { requests.trySend(Request.Select(selectedPage, proxy.name)) }
                                        } else null,
                                        trailing = {
                                            if (selected) Text(
                                                text = "✓", kind = CouiTextKind.PreferenceValue,
                                                color = CouiTheme.colorScheme.accentOnSurface,
                                            )
                                        },
                                    )
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
                it[position] = ProxyGroup(proxies, selectable, parent, links, testing = false)
            }
        }
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) { revision++ }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
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
