package com.github.kr328.clash.design

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
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
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.TopAppBarButton

@Composable
internal fun ClashHome(
    running: Boolean,
    paused: Boolean,
    profileName: String?,
    quota: HomeQuota?,
    dns: List<HomeDnsResult>,
    dnsLoading: Boolean,
    mode: String?,
    hasProviders: Boolean,
    onRequest: (HomeRequest) -> Unit,
) {
    val scroll = rememberScrollState()
    val scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scroll.maxValue > 0 })
    ClashPage(
        scrollBehavior = scrollBehavior,
        scrollState = scroll,
        topBar = {
            ColorOsLargeTopBar(
                title = stringResource(R.string.coui_home_header),
                scrollBehavior = scrollBehavior,
                actions = {
                    ToolbarIcon(CouiIcons.Filled.SettingDetail, stringResource(R.string.settings)) {
                        onRequest(HomeRequest.OpenSettings)
                    }
                },
            )
        },
        bottomBar = {
            PrimaryBottomBar(selected = 0, modifier = resourceName("home_navigation")) { index ->
                when (index) {
                    1 -> onRequest(HomeRequest.OpenProfiles)
                    2 -> onRequest(HomeRequest.OpenSettings)
                }
            }
        },
    ) {
        HomeTabContent(
            running, paused, profileName, quota, dns, dnsLoading,
            mode, hasProviders, onRequest,
        )
    }
}

@Composable
internal fun HomeTabContent(
    running: Boolean,
    paused: Boolean,
    profileName: String?,
    quota: HomeQuota?,
    dns: List<HomeDnsResult>,
    dnsLoading: Boolean,
    mode: String?,
    hasProviders: Boolean,
    onRequest: (HomeRequest) -> Unit,
) {
        HomeConnectionCard(running, paused, profileName) { onRequest(HomeRequest.ToggleStatus) }
        HomeQuotaCard(quota)
        HomeDnsCard(mode, dns, dnsLoading, running && !paused) { onRequest(HomeRequest.CheckDns) }

        if (running) {
            Group(title = stringResource(R.string.coui_home_section_connection)) {
                PageRow(R.drawable.ic_baseline_apps, stringResource(R.string.coui_home_nodes_title),
                    stringResource(R.string.live_nodes_hint), resourceName("home_nodes_view")) {
                    onRequest(HomeRequest.OpenProxy)
                }
                if (hasProviders) {
                    SettingDivider(withIcon = true)
                    PageRow(R.drawable.ic_baseline_swap_vertical_circle, stringResource(R.string.coui_home_providers_title),
                        stringResource(R.string.providers), resourceName("home_providers_view")) {
                        onRequest(HomeRequest.OpenProviders)
                    }
                }
            }
        }
}

@Composable
internal fun ClashSettings(
    onBack: () -> Unit,
    onRequest: (SettingsRequest) -> Unit,
) {
    val scroll = rememberScrollState()
    ClashPage(
        scrollState = scroll,
        bottomBar = {
            PrimaryBottomBar(selected = 2, modifier = resourceName("settings_navigation")) { index ->
                when (index) {
                    0 -> onRequest(SettingsRequest.OpenHome)
                    1 -> onRequest(SettingsRequest.OpenProfiles)
                }
            }
        },
        topBar = {
            ColorOsCompactTopBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    ToolbarIcon(
                        icon = CouiIcons.Filled.Back,
                        label = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = onBack,
                    )
                },
            )
        },
    ) {
        SettingsTabContent(onRequest)
    }
}

/** Consistent group separator: span the icon gutter but retain COUI's card insets. */
@Composable
internal fun SettingDivider(withIcon: Boolean = false) {
    // alpha8 DemoCatalog: icon-bearing separators begin at the title, not the icon.
    val start = CouiPageDefaults.rowPadding +
        if (withIcon) CouiPageDefaults.iconSize + CouiPageDefaults.iconSpacing else 0.dp
    // Put semantics on the painted segment, not its full-width padding box,
    // so geometry regression tests measure the visible line (alpha8 contract).
    Divider(
        modifier = Modifier.padding(
            start = start, end = CouiPageDefaults.rowPadding,
        ).testTag("coui-setting-divider"),
        startIndent = 0.dp,
        endIndent = 0.dp,
    )
}

@Composable
internal fun SettingsTabContent(onRequest: (SettingsRequest) -> Unit) {
        Group(title = stringResource(R.string.live_settings_intro),
            footer = stringResource(R.string.live_settings_intro_hint)) {
            PageRow(R.drawable.ic_baseline_settings, stringResource(R.string.app),
                stringResource(R.string.live_setting_app_hint)) { onRequest(SettingsRequest.StartApp) }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_dns, stringResource(R.string.network),
                stringResource(R.string.live_setting_network_hint)) { onRequest(SettingsRequest.StartNetwork) }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_extension, stringResource(R.string.override),
                stringResource(R.string.live_setting_override_hint)) { onRequest(SettingsRequest.StartOverride) }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_meta, stringResource(R.string.meta_features),
                stringResource(R.string.live_setting_meta_hint)) { onRequest(SettingsRequest.StartMetaFeature) }
        }

        Group(title = stringResource(R.string.coui_settings_routing)) {
            PageRow(R.drawable.ic_baseline_apps, stringResource(R.string.live_app_routing),
                stringResource(R.string.live_app_hint), resourceName("app_routing_view")) {
                onRequest(SettingsRequest.OpenAppRouting)
            }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_dns, stringResource(R.string.live_rules),
                stringResource(R.string.live_rules_hint), resourceName("rule_routing_view")) {
                onRequest(SettingsRequest.OpenRouting)
            }
        }

        Group(title = stringResource(R.string.coui_settings_support)) {
            PageRow(R.drawable.ic_baseline_assignment, stringResource(R.string.logs),
                stringResource(R.string.live_logs_hint)) { onRequest(SettingsRequest.OpenLogs) }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_help_center,
                stringResource(R.string.help), null) { onRequest(SettingsRequest.OpenHelp) }
            SettingDivider(withIcon = true)
            PageRow(R.drawable.ic_baseline_info,
                stringResource(R.string.about), null) { onRequest(SettingsRequest.OpenAbout) }
        }
}

/**
 * Stable main-tab shell: COUI's glass NavigationBar remains composed while only
 * the page body transitions. The blur is applied to real content on Android,
 * never to the floating bar or its backdrop source itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ClashPrimaryTabs(
    selected: Int,
    onSelected: (Int) -> Unit,
    profiles: ProfilesDesign,
    running: Boolean,
    paused: Boolean,
    profileName: String?,
    quota: HomeQuota?,
    dns: List<HomeDnsResult>,
    dnsLoading: Boolean,
    mode: String?,
    hasProviders: Boolean,
    onHomeRequest: (HomeRequest) -> Unit,
    onSettingsRequest: (SettingsRequest) -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val homeScroll = rememberScrollState()
    val profilesScroll = rememberLazyListState()
    val settingsScroll = rememberScrollState()
    // Keep the three large-title states independent while changing tabs.
    val homeTopBar = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { homeScroll.maxValue > 0 })
    val profilesTopBar = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { profilesScroll.canScrollForward || profilesScroll.canScrollBackward })
    val settingsTopBar = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { settingsScroll.maxValue > 0 })
    val activeTopBar = when (selected) {
        1 -> profilesTopBar
        2 -> settingsTopBar
        else -> homeTopBar
    }
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true }
                .nestedScroll(activeTopBar.nestedScrollConnection),
            topBar = {
                ColorOsLargeTopBar(
                    title = stringResource(when (selected) {
                        1 -> R.string.profiles
                        2 -> R.string.settings
                        else -> R.string.coui_home_header
                    }),
                    scrollBehavior = activeTopBar,
                    actions = {
                        when (selected) {
                            0 -> ToolbarIcon(CouiIcons.Filled.SettingDetail, stringResource(R.string.settings)) {
                                onSelected(2)
                            }
                            1 -> profiles.PrimaryTabActions()
                        }
                    },
                )
            },
            bottomBar = {
                PrimaryBottomBar(
                    selected = selected,
                    modifier = resourceName("home_navigation"),
                    onSelection = onSelected,
                )
            },
        ) { padding ->
            val density = LocalDensity.current
            val homePadding = homeTopBar.state.contentPadding(padding, density)
            val profilesPadding = profilesTopBar.state.contentPadding(padding, density)
            val settingsPadding = settingsTopBar.state.contentPadding(padding, density)
            // COUI keeps the toolbar and glass navigation stable. Animate only the
            // owned tab body, with a rounded page surface during movement rather than
            // cross-fading a full-screen rectangular texture.
            val pageTransition = updateTransition(targetState=selected, label="coloros-primary-page")
            val inMotion = pageTransition.currentState != pageTransition.targetState
            pageTransition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 270),
                        initialOffsetX = { width -> if (forward) width / 4 else -width / 4 },
                    ) + fadeIn(animationSpec = tween(durationMillis = 190)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 270),
                                targetOffsetX = { width -> if (forward) -width / 5 else width / 5 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 150))
                        ).using(SizeTransform(clip = false))
                },
            ) { page ->
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            clip = inMotion
                            shape = RoundedCornerShape(26.dp)
                        }
                        .background(CouiPageDefaults.themeColors().background),
                ) {
                    when (page) {
                        0 -> Column(
                            Modifier.fillMaxSize().couiVerticalScroll(homeScroll)
                                .padding(homePadding).consumeWindowInsets(padding)
                                .padding(bottom = CouiPageDefaults.groupSpacing),
                        ) {
                            HomeTabContent(running, paused, profileName, quota, dns,
                                dnsLoading, mode, hasProviders, onHomeRequest)
                        }
                        1 -> profiles.PrimaryTabContent(padding, profilesPadding, profilesScroll)
                        2 -> Column(
                            Modifier.fillMaxSize().couiVerticalScroll(settingsScroll)
                                .padding(settingsPadding).consumeWindowInsets(padding)
                                .padding(bottom = CouiPageDefaults.groupSpacing),
                        ) {
                            SettingsTabContent(onSettingsRequest)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ClashPage(
    topBar: @Composable () -> Unit,
    scrollBehavior: CouiTopAppBarScrollBehavior? = null,
    scrollState: ScrollState,
    bottomBar: @Composable () -> Unit = {},
    contentArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true }
                .then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
            topBar = topBar,
            bottomBar = bottomBar,
        ) { padding ->
            val contentPadding = scrollBehavior?.state?.contentPadding(padding, density) ?: padding
            Column(
                modifier = Modifier.fillMaxSize()
                    // COUI Scaffold records this full viewport as the blur source.
                    // Inset padding belongs INSIDE the scrolling content so
                    // the translucent toolbar and navigation sample real rows.
                    // Their separate Scaffold slots remain above the content.
                    .couiVerticalScroll(scrollState)
                    .padding(contentPadding)
                    .consumeWindowInsets(padding)
                    .padding(bottom = 16.dp),
                verticalArrangement = contentArrangement,
                content = content,
            )
        }
    }
}

@Composable
internal fun PrimaryBottomBar(
    selected: Int,
    modifier: Modifier = Modifier,
    onSelection: (Int) -> Unit,
) {
    val home = ImageVector.vectorResource(R.drawable.ic_navigation_home_outline)
    val homeSelected = ImageVector.vectorResource(R.drawable.ic_navigation_home)
    val profiles = ImageVector.vectorResource(R.drawable.ic_navigation_profiles_outline)
    val profilesSelected = ImageVector.vectorResource(R.drawable.ic_baseline_view_list)
    val settingsSelected = ImageVector.vectorResource(R.drawable.ic_navigation_settings_filled)
    val items = listOf(
        CouiNavigationItem(stringResource(R.string.coui_home_tab), home, homeSelected),
        CouiNavigationItem(stringResource(R.string.profiles), profiles, profilesSelected),
        CouiNavigationItem(stringResource(R.string.settings), CouiIcons.Filled.SettingDetail, settingsSelected),
    )
    val select: (Int) -> Unit = { index ->
        if (index != selected) onSelection(index)
    }
    val renderer = remember { createFloatingRenderer { CouiFloatingRenderer() } }
    if (renderer != null) {
        NavigationBar(
            modifier = modifier,
            renderer = renderer,
            items = items,
            selectedIndex = selected,
            onSelection = select,
        )
        return
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
                val itemSelected = index == selected
                val color = if (itemSelected) colors.accentOnSurface else colors.onSurfaceVariant
                Column(
                    modifier = Modifier.weight(1f)
                        .selectable(itemSelected, role = Role.Tab, onClick = { select(index) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(if (itemSelected) item.selectedIcon else item.icon, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(color))
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
internal fun PageRow(
    @DrawableRes icon: Int,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    Item(
        modifier = modifier,
        icon = { PageIcon(icon, colors.accentOnSurface, Modifier.size(CouiPageDefaults.iconSize)) },
        summary = summary?.takeIf { it.isNotBlank() }?.let { description -> {
            Text(text = description, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        } },
        trailing = {
            Image(
                CouiIcons.Filled.Back, null,
                Modifier.padding(start = 4.dp).size(width = 12.dp, height = 24.dp).rotate(180f),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        },
        contentPadding = PaddingValues(horizontal = CouiPageDefaults.rowPadding, vertical = CouiPageDefaults.contentPadding),
        onClick = onClick,
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
    }
}

/** Secondary/tertiary pages use a compact, non-collapsing ColorOS toolbar. */
@Composable
internal fun ColorOsSubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val pageScroll = rememberScrollState()
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ColorOsCompactTopBar(
                    title = title,
                    navigationIcon = {
                        ToolbarIcon(CouiIcons.Filled.Back,
                            stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            onBack)
                    },
                    actions = actions,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().couiVerticalScroll(pageScroll)
                    .padding(padding).consumeWindowInsets(padding)
                    .padding(bottom = CouiPageDefaults.groupSpacing),
                content = content,
            )
        }
    }
}

/** Virtualized lists keep the same fixed-height toolbar and safe insets. */
@Composable
internal fun ColorOsLazySubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState? = null,
    reverseLayout: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val fallbackListState = rememberLazyListState()
    val actualListState = listState ?: fallbackListState
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ColorOsCompactTopBar(
                    title = title,
                    navigationIcon = {
                        ToolbarIcon(CouiIcons.Filled.Back,
                            stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            onBack)
                    },
                    actions = actions,
                )
            },
        ) { padding ->
            LazyColumn(
                state = actualListState,
                reverseLayout = reverseLayout,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = PaddingValues(
                    start = padding.calculateLeftPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateRightPadding(LocalLayoutDirection.current),
                    bottom = padding.calculateBottomPadding() + CouiPageDefaults.groupSpacing,
                ),
                content = content,
            )
        }
    }
}

/** COUI-Kit alpha8 compact toolbar: native 18dp title, no large-title morph. */
@Composable
internal fun ColorOsCompactTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color? = null,
) {
    TopAppBar(
        title = {
            BasicText(
                text = title,
                style = LocalCouiTextStyle.current.copy(color = CouiTheme.colorScheme.onSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = CouiTopAppBarDefaults.colors(
            backgroundColor = backgroundColor ?: CouiPageDefaults.themeColors().background,
        ),
    )
}

/** ColorOS 17 title: native COUI glass and collapse with a 32sp expanded visual size. */
@Composable
internal fun ColorOsLargeTopBar(
    title: String,
    scrollBehavior: CouiTopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Let COUI alpha8 own the expanded/collapsed type ramp, material and typography.
    LargeTopAppBar(
        title = {
            BasicText(
                text = title,
                style = LocalCouiTextStyle.current.copy(color = CouiTheme.colorScheme.onSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = CouiTopAppBarDefaults.colors(
            backgroundColor = CouiPageDefaults.themeColors().background,
        ),
    )
}

@Composable
internal fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val renderer = remember { createFloatingRenderer { CouiFloatingRenderer.standalone() } }
    if (renderer != null) {
        TopAppBarButton(onClick = onClick, renderer = renderer) {
            Image(icon, label, Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(LocalCouiContentColor.current))
        }
        return
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
