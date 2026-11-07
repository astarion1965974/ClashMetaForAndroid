package com.github.kr328.clash.design

import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.github.kr328.clash.design.MainDesign.Request as HomeRequest
import com.github.kr328.clash.design.SettingsDesign.Request as SettingsRequest
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.design.util.resolveThemedColor
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.TopAppBarButton

@Composable
internal fun ClashHome(
    running: Boolean,
    paused: Boolean,
    profileName: String?,
    quota: HomeQuota?,
    days: List<HomeTrafficDay>,
    dns: List<HomeDnsResult>,
    dnsLoading: Boolean,
    forwarded: String?,
    mode: String?,
    hasProviders: Boolean,
    onRequest: (HomeRequest) -> Unit,
) {
    ClashPage(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    ToolbarIcon(CouiIcons.Settings, stringResource(R.string.settings)) {
                        onRequest(HomeRequest.OpenSettings)
                    }
                },
            )
        },
        bottomBar = { HomeBottomBar(onRequest) },
    ) {
        Text(text = stringResource(R.string.application_name), kind = CouiTextKind.HomeTitle,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        HomeConnectionCard(running, paused, profileName) { onRequest(HomeRequest.ToggleStatus) }
        HomeQuotaCard(quota)
        HomeTrafficCard(days, forwarded, running && !paused)
        HomeDnsCard(mode, dns, dnsLoading, running && !paused) { onRequest(HomeRequest.CheckDns) }

        if (running) {
            Group(title = stringResource(R.string.coui_home_section_connection)) {
                PageRow(R.drawable.ic_baseline_apps, stringResource(R.string.coui_home_nodes_title),
                    stringResource(R.string.live_nodes_hint), resourceName("home_nodes_view")) {
                    onRequest(HomeRequest.OpenProxy)
                }
                if (hasProviders) {
                    Divider()
                    PageRow(R.drawable.ic_baseline_swap_vertical_circle, stringResource(R.string.coui_home_providers_title),
                        stringResource(R.string.providers), resourceName("home_providers_view")) {
                        onRequest(HomeRequest.OpenProviders)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ClashSettings(onBack: () -> Unit, onRequest: (SettingsRequest) -> Unit) {
    ClashPage(topBar = {
        TopAppBar(
            title = { Text(text = stringResource(R.string.settings), kind = CouiTextKind.Toolbar) },
            navigationIcon = {
                ToolbarIcon(
                    icon = CouiIcons.Back,
                    label = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    onClick = onBack,
                )
            },
        )
    }) {
        Group(title = stringResource(R.string.live_settings_intro),
            footer = stringResource(R.string.live_settings_intro_hint)) {
            PageRow(R.drawable.ic_baseline_settings, stringResource(R.string.app),
                stringResource(R.string.live_setting_app_hint)) { onRequest(SettingsRequest.StartApp) }
            Divider()
            PageRow(R.drawable.ic_baseline_dns, stringResource(R.string.network),
                stringResource(R.string.live_setting_network_hint)) { onRequest(SettingsRequest.StartNetwork) }
            Divider()
            PageRow(R.drawable.ic_baseline_extension, stringResource(R.string.override),
                stringResource(R.string.live_setting_override_hint)) { onRequest(SettingsRequest.StartOverride) }
            Divider()
            PageRow(R.drawable.ic_baseline_meta, stringResource(R.string.meta_features),
                stringResource(R.string.live_setting_meta_hint)) { onRequest(SettingsRequest.StartMetaFeature) }
        }

        Group(title = stringResource(R.string.coui_settings_routing)) {
            PageRow(R.drawable.ic_baseline_apps, stringResource(R.string.live_app_routing),
                stringResource(R.string.live_app_hint), resourceName("app_routing_view")) {
                onRequest(SettingsRequest.OpenAppRouting)
            }
            Divider()
            PageRow(R.drawable.ic_baseline_dns, stringResource(R.string.live_rules),
                stringResource(R.string.live_rules_hint), resourceName("rule_routing_view")) {
                onRequest(SettingsRequest.OpenRouting)
            }
        }

        Group(title = stringResource(R.string.coui_settings_support)) {
            PageRow(R.drawable.ic_baseline_assignment, stringResource(R.string.logs),
                stringResource(R.string.live_logs_hint)) { onRequest(SettingsRequest.OpenLogs) }
            Divider()
            Item(onClick = { onRequest(SettingsRequest.OpenHelp) }) {
                Text(text = stringResource(R.string.help), kind = CouiTextKind.PreferenceTitle)
            }
            Divider()
            Item(onClick = { onRequest(SettingsRequest.OpenAbout) }) {
                Text(text = stringResource(R.string.about), kind = CouiTextKind.PreferenceTitle)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ClashPage(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    contentArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            topBar = topBar,
            bottomBar = bottomBar,
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize()
                    // Apply Scaffold insets *outside* the scroll viewport so
                    // content cannot receive taps through the floating bottom bar.
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .couiVerticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = contentArrangement,
                content = content,
            )
        }
    }
}

@Composable
private fun HomeBottomBar(onRequest: (HomeRequest) -> Unit) {
    val home = ImageVector.vectorResource(R.drawable.ic_navigation_home)
    val profiles = ImageVector.vectorResource(R.drawable.ic_baseline_view_list)
    val items = listOf(
        CouiNavigationItem(stringResource(R.string.coui_home_tab), home, home),
        CouiNavigationItem(stringResource(R.string.profiles), profiles, profiles),
        CouiNavigationItem(stringResource(R.string.settings), CouiIcons.Settings, CouiIcons.Settings),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }
    val lifecycle = LocalView.current.findViewTreeLifecycleOwner()?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selectedIndex = 0
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val select: (Int) -> Unit = { index ->
        selectedIndex = index
        val request = when (index) {
            1 -> HomeRequest.OpenProfiles
            2 -> HomeRequest.OpenSettings
            else -> null
        }
        request?.let(onRequest)
    }
    val modifier = resourceName("home_navigation")
    if (Build.VERSION.SDK_INT >= 34) {
        val renderer = remember { createFloatingRenderer { CouiFloatingRenderer() } }
        if (renderer != null) {
            NavigationBar(
                modifier = modifier,
                renderer = renderer,
                items = items,
                selectedIndex = selectedIndex,
                onSelection = select,
            )
            return
        }
    }

    Box(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val colors = CouiTheme.colorScheme
        Row(
            Modifier.widthIn(max = 250.dp).fillMaxWidth()
                .shadow(4.dp, CircleShape)
                .background(colors.container, CircleShape)
                .border(1.dp, colors.outlineVariant, CircleShape)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val color = if (selected) colors.accentOnSurface else colors.onSurfaceVariant
                Column(
                    modifier = Modifier.weight(1f)
                        .selectable(selected, role = Role.Tab, onClick = { select(index) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(item.icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(color))
                    Text(text = item.title, kind = CouiTextKind.Description, color = color,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    running: Boolean,
    paused: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val colors = CouiTheme.colorScheme
    val connected = running && !paused
    val statusColor = Color(context.resolveThemedColor(
        if (connected) R.attr.colorLiveSuccess else R.attr.colorLiveMuted,
    ))
    val title = stringResource(when {
        paused -> R.string.mobile_home_paused
        running -> R.string.coui_home_connected
        else -> R.string.coui_home_disconnected
    })
    val action = stringResource(when {
        paused -> R.string.mobile_home_stop
        running -> R.string.live_disconnect
        else -> R.string.live_connect
    })
    val press = rememberCouiPressMotion()
    Group {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier.size(96.dp)
                    .background(statusColor.copy(alpha = 0.06f), CircleShape)
                    .border(1.dp, statusColor.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(72.dp).background(statusColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PageIcon(R.drawable.ic_vshield_shield, statusColor, Modifier.size(38.dp))
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    kind = CouiTextKind.PageTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                if (!connected) {
                    Text(
                        text = stringResource(if (paused) R.string.mobile_home_paused_hint else R.string.live_connect_hint),
                        kind = CouiTextKind.Description,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                modifier = resourceName("toggle_view").fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .graphicsLayer {
                        scaleX = press.scale.value
                        scaleY = press.scale.value
                    }
                    .couiPressMotion(press, cancelOnConsumedMove = true)
                    .clip(CircleShape).background(colors.accent)
                    .clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageIcon(R.drawable.ic_live_power, colors.onAccent, Modifier.size(20.dp))
                Text(text = action, kind = CouiTextKind.PreferenceTitle, color = colors.onAccent)
            }
        }
    }
}

@Composable
private fun ConnectionOverview(profileName: String?, forwarded: String?, mode: String?, connected: Boolean) {
    Group(title = stringResource(R.string.coui_home_overview)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Metric(
                stringResource(R.string.coui_home_profile_title),
                profileName ?: stringResource(R.string.no_profile_selected),
            )
            Divider(startIndent = 0.dp, endIndent = 0.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(stringResource(R.string.live_traffic), forwarded.takeIf { connected } ?: "—", Modifier.weight(1f))
                Metric(stringResource(R.string.live_mode), mode.takeIf { connected } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, kind = CouiTextKind.PreferenceSummary, color = CouiTheme.colorScheme.onSurfaceVariant)
        Text(text = value, kind = CouiTextKind.PreferenceTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PageRow(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    Item(
        modifier = modifier,
        icon = { PageIcon(icon, colors.accentOnSurface, Modifier.size(24.dp)) },
        summary = {
            Text(text = summary, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailing = { PageIcon(R.drawable.ic_live_chevron, colors.onSurfaceVariant, Modifier.size(18.dp)) },
        contentPadding = PaddingValues(horizontal = CouiPageDefaults.rowPadding, vertical = 12.dp),
        onClick = onClick,
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
    }
}

@Composable
internal fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 34) {
        val renderer = remember { createFloatingRenderer { CouiFloatingRenderer.standalone() } }
        if (renderer != null) {
            TopAppBarButton(onClick = onClick, renderer = renderer) {
                Image(icon, label, Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(LocalCouiContentColor.current))
            }
            return
        }
    }
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, label, Modifier.size(24.dp), colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurface))
    }
}

private fun createFloatingRenderer(factory: () -> CouiFloatingRenderer): CouiFloatingRenderer? =
    try {
        factory()
    } catch (error: RuntimeException) {
        Log.w("ClashCoui", "Unable to initialize COUI floating material", error)
        null
    }

@Composable
private fun PageIcon(@DrawableRes icon: Int, tint: Color, modifier: Modifier, label: String? = null) {
    Image(
        painter = painterResource(icon),
        contentDescription = label,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

// Preserve the resource names that UI Automator used on the XML views.
@Composable
private fun resourceName(name: String): Modifier =
    Modifier.testTag("${LocalContext.current.packageName}:id/$name")
