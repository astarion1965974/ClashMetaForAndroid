package com.github.kr328.clash.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import androidx.appcompat.app.AppCompatActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Resource inflation and interaction checks, using the actual Android view hierarchy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun context(dark: Boolean): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(if (dark) R.style.AppThemeDark else R.style.AppThemeLight)
        controller.setup()
        return controller.get()
    }

    private fun render(view: View, name: String, width: Int = 412, height: Int = 915) {
        val parent = FrameLayout(view.context).apply {
            val color = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.windowBackground, color, true)
            setBackgroundColor(color.data)
            addView(view)
        }
        var owner = view.context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        if (owner is android.app.Activity) owner.setContentView(parent)
        shadowOf(Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS)
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        fun settle(v: View) {
            v.refreshDrawableState()
            v.background?.state = v.drawableState
            v.jumpDrawablesToCurrentState()
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) settle(v.getChildAt(i))
        }
        settle(parent)
        parent.draw(Canvas(bitmap))
        savePreview(bitmap, name)
        assertTrue(view.measuredWidth > 0)
    }

    private fun savePreview(bitmap: Bitmap, name: String) {
        val directory = File("build/ui-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun updateHome(update: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Main).launch { update() }
        // Profile list updates compute a RecyclerView diff on Dispatchers.Default.
        // Drain the Main looper until that worker completes instead of assuming
        // every coroutine finishes in a single synchronous Robolectric idle().
        for (attempt in 0 until 200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (job.isCompleted) break
            Thread.sleep(10)
        }
        assertTrue("Main coroutine should complete after background diffing", job.isCompleted && !job.isCancelled)
    }

    private fun nodeWithResourceName(context: Context, name: String) =
        compose.onNodeWithTag("${context.packageName}:id/$name")

    @Test fun proxyHeaderRemainsSolidAndGroupSelectorIsAccessible() {
        for (dark in listOf(false, true)) {
            val ctx = context(dark)
            val groups = listOf("自动选择", "节点选择", "香港地区")
            val design = ProxyDesign(ctx, null, groups, UiStore(ctx))
            ctx.setContentView(design.root)
            compose.onNodeWithTag("proxy-fixed-header").assertIsDisplayed()
            compose.onNodeWithTag("proxy-group-tabs").assertIsDisplayed()
            compose.onNodeWithTag("proxy-tab-0").assertIsSelected()
            compose.onNodeWithTag("proxy-tab-1").performClick().assertIsSelected()
            compose.onNodeWithTag("proxy-tab-0").assertExists()
            savePreview(compose.onRoot().captureToImage().asAndroidBitmap(),
                if (dark) "proxy-fixed-header-dark" else "proxy-fixed-header-light")
            ctx.setContentView(View(ctx))
        }
    }

    @Test fun dashboardShowsQuotaAndRealDnsResults() {
        val ctx = context(false)
        val design = MainDesign(ctx)
        val profile = com.github.kr328.clash.service.model.Profile(
            uuid = java.util.UUID.randomUUID(), name = "测试订阅", type = com.github.kr328.clash.service.model.Profile.Type.Url,
            source = "https://example.com", active = true, interval = 0, upload = 100, download = 200,
            total = 1000, expire = 0, updatedAt = 0, imported = true, pending = false)
        updateHome {
            design.setProfile(profile)
            design.setClashRunning(true)
            design.setDnsResults(listOf(
                "baidu.com" to """{"addresses":["192.0.2.1"],"millis":42,"dnsRoute":"direct","dnsUpstream":"udp://223.5.5.5"}""",
                "chatgpt.com" to """{"addresses":["192.0.2.2"],"millis":61,"dnsRoute":"proxy","dnsProxy":"AI-Auto → Tokyo","dnsUpstream":"https://dns.google"}""",
            ))
        }
        ctx.setContentView(design.root)
        assertEquals(9, HomeDnsTargets.domains.size)
        assertEquals(9, HomeDnsTargets.domains.distinct().size)
        assertTrue(HomeDnsTargets.domains.containsAll(
            listOf("dianping.com", "douyin.com", "chatgpt.com", "grok.com", "claude.ai")))
        compose.onNodeWithText(ctx.getString(R.string.coui_home_header)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_traffic_week)).assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(R.string.coui_dns_site_dianping)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_dns_site_douyin)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_dns_site_chatgpt)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_dns_site_grok)).assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_dns_site_claude)).assertExists()
        compose.onNodeWithText("配置 DNS 代理：AI-Auto → Tokyo").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.coui_quota_title)).assertIsDisplayed()
        savePreview(compose.onRoot().captureToImage().asAndroidBitmap(), "dashboard-light")
        compose.onNodeWithText("192.0.2.1 · 42 毫秒").performScrollTo().assertIsDisplayed()
        ctx.setContentView(View(ctx))
    }

    @Test fun mobileSettingsBothThemesAndHomePauseStatus() {
        for(dark in listOf(false,true)) {
            val ctx=context(dark)
            ctx.title=ctx.getString(R.string.mobile_title)
            val settings=com.github.kr328.clash.service.store.MobileNetworkSettings()
            val design=MobileNetworkDesign(ctx,settings,listOf("东京 1","东京 2"),true)
            render(design.root,if(dark) "mobile-dark" else "mobile-light")
            assertFalse(design.dirty)
            design.bind("Home Wi-Fi")
            assertTrue(design.dirty)
            compose.onNodeWithText(ctx.getString(R.string.route_save_reconnect)).assertIsDisplayed()
            val home=MainDesign(ctx)
            updateHome {
                home.setClashRunning(true)
                home.setHomePaused(true)
            }
            ctx.setContentView(home.root)
            nodeWithResourceName(ctx, "toggle_view").assertTextContains(ctx.getString(R.string.mobile_home_stop))
            ctx.setContentView(View(ctx))
        }
    }

    @Test fun homeReflectsConnectionAndRoutesActions() {
        for (dark in listOf(false, true)) {
            val ctx = context(dark)
            val design = MainDesign(ctx)
            updateHome {
                design.setClashRunning(true)
                design.setMode(TunnelState.Mode.Rule)
                design.setProfileName("我的日常订阅")
                design.setHasProviders(true)
            }
            ctx.setContentView(design.root)
            val toggle = nodeWithResourceName(ctx, "toggle_view")
            toggle.assertTextContains(ctx.getString(R.string.live_disconnect))
            savePreview(compose.onRoot().captureToImage().asAndroidBitmap(), if (dark) "home-dark" else "home-light")
            compose.onNodeWithText("我的日常订阅").assertIsDisplayed().assertHasNoClickAction()
            nodeWithResourceName(ctx, "app_routing_view").assertDoesNotExist()
            nodeWithResourceName(ctx, "rule_routing_view").assertDoesNotExist()
            val bottomBar = hasAnyAncestor(hasTestTag("${ctx.packageName}:id/home_navigation"))
            compose.onNode(bottomBar and hasText(ctx.getString(R.string.coui_home_tab))).assertIsSelected()
            compose.onNode(bottomBar and hasText(ctx.getString(R.string.profiles))).performClick()
            assertEquals(MainDesign.Request.OpenProfiles, design.requests.tryReceive().getOrNull())
            compose.onNode(bottomBar and hasText(ctx.getString(R.string.settings))).performClick()
            assertEquals(MainDesign.Request.OpenSettings, design.requests.tryReceive().getOrNull())
            toggle.performClick()
            assertEquals(MainDesign.Request.ToggleStatus, design.requests.tryReceive().getOrNull())
            // The COUI glass bottom navigation overlays a *full-viewport*
            // backdrop. Scroll the actual rows into its unobstructed area
            // before tapping; do not click the label covered by the overlay.
            val bottomNavigationTop =
                nodeWithResourceName(ctx, "home_navigation").getUnclippedBoundsInRoot().top
            fun safeHomeRow(tag: String): androidx.compose.ui.test.SemanticsNodeInteraction {
                val row = nodeWithResourceName(ctx, tag).performScrollTo()
                repeat(4) {
                    if (row.getUnclippedBoundsInRoot().bottom <= bottomNavigationTop) {
                        return row.assertIsDisplayed()
                    }
                    compose.onRoot().performTouchInput {
                        swipeUp(startY = height * 0.70f, endY = height * 0.23f)
                    }
                }
                assertTrue("Home row must remain above the floating COUI navigation",
                    row.getUnclippedBoundsInRoot().bottom <= bottomNavigationTop)
                return row.assertIsDisplayed()
            }
            val nodes = safeHomeRow("home_nodes_view")
            nodes.performClick()
            assertEquals(MainDesign.Request.OpenProxy, design.requests.tryReceive().getOrNull())
            val providers = safeHomeRow("home_providers_view")
            providers.performClick()
            assertEquals(MainDesign.Request.OpenProviders, design.requests.tryReceive().getOrNull())
            updateHome { design.setClashRunning(false) }
            toggle.assertTextContains(ctx.getString(R.string.live_connect))
            compose.onNodeWithText(ctx.getString(R.string.coui_home_nodes_title)).assertDoesNotExist()
            ctx.setContentView(View(ctx))
        }
    }

    @Test fun settingsRoutesMovedActions() {
        val ctx = context(false)
        val design = SettingsDesign(ctx)
        ctx.setContentView(design.root)

        nodeWithResourceName(ctx, "app_routing_view").performScrollTo().performClick()
        assertEquals(SettingsDesign.Request.OpenAppRouting, design.requests.tryReceive().getOrNull())
        nodeWithResourceName(ctx, "rule_routing_view").performScrollTo().performClick()
        assertEquals(SettingsDesign.Request.OpenRouting, design.requests.tryReceive().getOrNull())
        compose.onNodeWithText(ctx.getString(R.string.logs)).performScrollTo().performClick()
        assertEquals(SettingsDesign.Request.OpenLogs, design.requests.tryReceive().getOrNull())
        compose.onNodeWithText(ctx.getString(R.string.help)).performScrollTo().performClick()
        assertEquals(SettingsDesign.Request.OpenHelp, design.requests.tryReceive().getOrNull())
        compose.onNodeWithText(ctx.getString(R.string.about)).performScrollTo().performClick()
        assertEquals(SettingsDesign.Request.OpenAbout, design.requests.tryReceive().getOrNull())
    }

    @Test fun appRoutingPreservesSelectionAndSaveState() {
        val ctx = context(true)
        ctx.title = ctx.getString(R.string.live_app_routing)
        val selected = mutableSetOf("org.example.chat")
        val design = AccessControlDesign(ctx, UiStore(ctx), selected, AccessControlMode.AcceptSelected, true)
        design.patchApps(listOf(
            AppInfo("org.example.chat", "聊天应用", android.graphics.drawable.ColorDrawable(0xff5e6ad2.toInt()), 0, 0),
            AppInfo("org.example.browser", "浏览器", android.graphics.drawable.ColorDrawable(0xff268c66.toInt()), 0, 0),
            AppInfo("org.example.music", "音乐", android.graphics.drawable.ColorDrawable(0xffbd5b6e.toInt()), 0, 0)
        ))
        render(design.root, "apps-dark")
        assertFalse(design.dirty)
        assertTrue(selected.contains("org.example.chat"))
        compose.onNodeWithText("浏览器").performClick()
        assertTrue(selected.contains("org.example.browser"))
        assertTrue(design.dirty)
        compose.onNodeWithText(ctx.getString(R.string.route_save_reconnect)).assertIsDisplayed()
    }

    @Test fun ruleSettingsInflateInBothThemesAndKeepSaveReachable() {
        for (dark in listOf(false, true)) {
            val ctx = context(dark)
            ctx.title = ctx.getString(R.string.live_rules)
            val design = ManagedRoutingDesign(ctx, ManagedRoutingSettings(), listOf("自动选择", "香港节点"), false)
            render(design.root, if (dark) "rules-dark" else "rules-light")
            assertFalse(design.dirty)
            compose.waitForIdle()
            // The COUI text may appear in merged and unmerged semantics, or in
            // multiple attached Robolectric Activity roots. Assert that the
            // current screen exposes a save label without requiring uniqueness.
            assertTrue(
                "Native COUI Save action should remain reachable",
                compose.onAllNodesWithText(
                    ctx.getString(R.string.route_save), useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty(),
            )
            ctx.setContentView(View(ctx))
            compose.waitForIdle()
        }
    }

    @Test @Config(qualifiers = "zh-rCN-w800dp-h360dp-land-mdpi")
    fun appRoutingLandscapeKeepsListAndSaveVisible() {
        val ctx = context(false)
        val design = AccessControlDesign(ctx, UiStore(ctx), mutableSetOf(), AccessControlMode.DenySelected, false)
        render(design.root, "apps-landscape", 800, 360)
        compose.onNodeWithText(ctx.getString(R.string.route_save)).assertIsDisplayed()
        compose.onNodeWithText(ctx.getString(R.string.app_mode_bypass)).assertIsDisplayed()
    }

    @Test fun primaryTabsKeepOneCOUINavigationAndSwitchWithinHost() {
        val ctx = context(false)
        val main = MainDesign(ctx)
        val profiles = ProfilesDesign(ctx)
        val settings = SettingsDesign(ctx)
        main.installPrimaryTabs(profiles, settings)
        ctx.setContentView(main.root)

        val bar = hasAnyAncestor(hasTestTag("${ctx.packageName}:id/home_navigation"))
        compose.onNode(bar and hasText(ctx.getString(R.string.coui_home_tab))).assertIsSelected()

        compose.onNode(bar and hasText(ctx.getString(R.string.settings))).performClick()
        assertEquals(2, main.selectedPrimaryTab)
        compose.waitForIdle()
        compose.onNode(bar and hasText(ctx.getString(R.string.settings))).assertIsSelected()

        compose.onNode(bar and hasText(ctx.getString(R.string.profiles))).performClick()
        assertEquals(1, main.selectedPrimaryTab)
        compose.waitForIdle()
        compose.onNode(bar and hasText(ctx.getString(R.string.profiles))).assertIsSelected()

        compose.onNode(bar and hasText(ctx.getString(R.string.coui_home_tab))).performClick()
        assertEquals(0, main.selectedPrimaryTab)
        compose.waitForIdle()
        compose.onNode(bar and hasText(ctx.getString(R.string.coui_home_tab))).assertIsSelected()
        // Navigation is a persistent COUI Scaffold child, not a fresh Activity.
        assertTrue(ctx.window.decorView.findViewById<View>(android.R.id.content).isAttachedToWindow)
        ctx.setContentView(View(ctx))
    }

    @Test fun nativeCouiLogViewerRetainsActionsAndMessages() {
        val ctx = context(false)
        val viewer = LogcatDesign(ctx, streaming = false)
        ctx.setContentView(viewer.root)
        val msg = com.github.kr328.clash.core.model.LogMessage(
            level = com.github.kr328.clash.core.model.LogMessage.Level.Info,
            message = "native COUI log row", time = java.util.Date(0),
        )
        updateHome { viewer.patchMessages(listOf(msg), 0, 1) }
        compose.onNodeWithText("native COUI log row").assertIsDisplayed()
        compose.onNodeWithContentDescription(ctx.getString(R.string.export)).performClick()
        assertEquals(LogcatDesign.Request.Export, viewer.requests.tryReceive().getOrNull())
        ctx.setContentView(View(ctx))
        val streaming = LogcatDesign(ctx, streaming = true)
        ctx.setContentView(streaming.root)
        compose.onNodeWithContentDescription(ctx.getString(R.string.close)).performClick()
        assertEquals(LogcatDesign.Request.Close, streaming.requests.tryReceive().getOrNull())
        ctx.setContentView(View(ctx))
    }

    @Test fun nativeCouiProviderListKeepsRefreshRequests() {
        val ctx = context(false)
        val remote = com.github.kr328.clash.core.model.Provider(
            name = "provider test", type = com.github.kr328.clash.core.model.Provider.Type.Proxy,
            vehicleType = com.github.kr328.clash.core.model.Provider.VehicleType.HTTP,
            updatedAt = 0,
        )
        val inline = remote.copy(name = "inline provider",
            vehicleType = com.github.kr328.clash.core.model.Provider.VehicleType.Inline)
        val design = ProvidersDesign(ctx, listOf(remote, inline))
        ctx.setContentView(design.root)
        compose.onNodeWithText("provider test").assertIsDisplayed()
        design.requestUpdateAll()
        assertEquals(ProvidersDesign.Request.Update(0, remote), design.requests.tryReceive().getOrNull())
        assertNull("Inline providers must not be refreshed", design.requests.tryReceive().getOrNull())
        design.requestUpdateAll()
        assertNull("Already refreshing providers must not be duplicated", design.requests.tryReceive().getOrNull())
        updateHome { design.notifyUpdated(0) }
        design.requestUpdateAll()
        assertEquals(ProvidersDesign.Request.Update(0, remote), design.requests.tryReceive().getOrNull())
        ctx.setContentView(View(ctx))
    }

    @Test fun nativeCouiNewProfileOffersAllImportSources() {
        val ctx = context(false)
        val design = NewProfileDesign(ctx)
        val file = com.github.kr328.clash.design.model.ProfileProvider.File(ctx)
        val url = com.github.kr328.clash.design.model.ProfileProvider.Url(ctx)
        val qr = com.github.kr328.clash.design.model.ProfileProvider.QR(ctx)
        updateHome { design.patchProviders(listOf(file, url, qr)) }
        ctx.setContentView(design.root)
        compose.onNodeWithText(ctx.getString(R.string.file)).performClick()
        assertEquals(NewProfileDesign.Request.Create(file), design.requests.tryReceive().getOrNull())
        compose.onNodeWithText(ctx.getString(R.string.url)).performClick()
        assertEquals(NewProfileDesign.Request.Create(url), design.requests.tryReceive().getOrNull())
        compose.onNodeWithText(ctx.getString(R.string.qr)).performClick()
        assertEquals(NewProfileDesign.Request.LaunchScanner(qr), design.requests.tryReceive().getOrNull())
        ctx.setContentView(View(ctx))
    }

    @Test fun nativeCouiLogsKeepStartAndFileActions() {
        val ctx = context(false)
        val design = LogsDesign(ctx)
        ctx.setContentView(design.root)
        compose.onNodeWithText(ctx.getString(R.string.clash_logcat)).performClick()
        assertEquals(LogsDesign.Request.StartLogcat, design.requests.tryReceive().getOrNull())
        val log = com.github.kr328.clash.design.model.LogFile("clash-1730000.log", java.util.Date(1730000L))
        updateHome { design.patchLogs(listOf(log)) }
        compose.onNodeWithText(log.fileName).assertIsDisplayed().performClick()
        assertEquals(LogsDesign.Request.OpenFile(log), design.requests.tryReceive().getOrNull())
        ctx.setContentView(View(ctx))
    }

    @Test fun nativeCouiHelpLinksAndRecoveryPageKeepActions() {
        val ctx = context(false)
        var opened: android.net.Uri? = null
        val help = HelpDesign(ctx) { opened = it }
        ctx.setContentView(help.root)
        compose.onNodeWithText("<strong>", substring = true).assertDoesNotExist()
        compose.onNodeWithText(ctx.getString(R.string.clash_wiki)).performClick()
        assertEquals(ctx.getString(R.string.clash_wiki_url), opened.toString())
        val recovery = ApkBrokenDesign(ctx)
        ctx.setContentView(recovery.root)
        compose.onNodeWithText(ctx.getString(R.string.github_releases)).performClick()
        assertEquals(ctx.getString(R.string.meta_github_url),
            recovery.requests.tryReceive().getOrNull()?.url)
        val crash = AppCrashedDesign(ctx)
        crash.setAppLogs("COUI exception sample")
        ctx.setContentView(crash.root)
        compose.onNodeWithText("COUI exception sample").assertIsDisplayed()
        ctx.setContentView(View(ctx))
    }

    @Test fun primaryProfilesListUsesScaffoldInsets() {
        val ctx = context(false)
        val main = MainDesign(ctx)
        val profiles = ProfilesDesign(ctx)
        val settings = SettingsDesign(ctx)
        main.installPrimaryTabs(profiles, settings)
        val profile = com.github.kr328.clash.service.model.Profile(
            uuid = java.util.UUID.randomUUID(), name = "COUI布局测试配置",
            type = com.github.kr328.clash.service.model.Profile.Type.Url,
            source = "https://example.com", active = true, interval = 0,
            upload = 0, download = 0, total = 0, expire = 0,
            updatedAt = 0, imported = true, pending = false,
        )
        updateHome { profiles.patchProfiles(listOf(profile)) }
        main.selectPrimaryTab(1)
        ctx.setContentView(main.root)
        val profileTitle = compose.onNodeWithText("COUI布局测试配置").assertIsDisplayed()
        assertTrue("Profile content must be below the COUI top bar",
            profileTitle.getUnclippedBoundsInRoot().top >= 60.dp)
        ctx.setContentView(View(ctx))
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun homeSupportsNarrowScreensAndLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        val ctx = context(false)
        val design = MainDesign(ctx)
        updateHome { design.setProfileName("一个很长的订阅配置名称，用于检查窄屏文字") }
        ctx.setContentView(design.root)
        nodeWithResourceName(ctx, "toggle_view").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        savePreview(compose.onRoot().captureToImage().asAndroidBitmap(), "home-small-large-font")
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.74f, endY = height * 0.30f)
        }
        val dnsTitle = compose.onNodeWithText(ctx.getString(R.string.coui_dns_title))
            .performScrollTo().getUnclippedBoundsInRoot()
        val bottomBar = nodeWithResourceName(ctx, "home_navigation").getUnclippedBoundsInRoot()
        assertTrue("The DNS diagnostic should scroll above the floating navigation",
            dnsTitle.bottom <= bottomBar.top)
    }
}
