package com.github.kr328.clash.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.adapter.ProfileListItem
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.NativeCouiSwitch
import com.github.kr328.clash.design.preference.NativeCouiSelect
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.preference.NativeCouiNullablePort
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.Profile
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CouiPreferenceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun activity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppThemeLight)
        return controller.setup().get()
    }

    private class ToggleModel {
        @Volatile var enabled = false
    }

    @Test fun nativeSwitchWritesOnceAndRespectsDisabledState() {
        val activity = activity()
        val model = ToggleModel()
        val changed = AtomicInteger()
        var interactive by mutableStateOf(true)
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    NativeCouiSwitch(
                        title = activity.getString(R.string.mobile_enabled),
                        value = model::enabled,
                        enabled = interactive,
                        onChanged = { changed.incrementAndGet() },
                    )
                }
            }
        })
        val label = activity.getString(R.string.mobile_enabled)
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            model.enabled && changed.get() == 1
        }
        interactive = false
        compose.waitForIdle()
        compose.onNodeWithText(label).assertIsNotEnabled()
        assertEquals(1, changed.get())
        activity.setContentView(View(activity))
    }

    @Test fun nativeRowRendersExternalDrawableWithComposeImage() {
        val activity = activity()
        val drawable = BitmapDrawable(
            activity.resources,
            Bitmap.createBitmap(60, 30, Bitmap.Config.ARGB_8888),
        )
        val root = ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    CouiPreferenceRow(
                        title = "Test action",
                        summary = "Drawable source",
                        icon = drawable,
                        enabled = true,
                        onClick = {},
                    )
                }
            }
        }
        activity.setContentView(root)
        compose.onNodeWithText("Test action").assertIsDisplayed()
        // The icon is now a Compose Image, not a nested Android ImageView.
        // The implementation draws the bitmap using ContentScale.Fit, preserving
        // its original 60×30 aspect ratio within the 24dp icon bounds.
        compose.onNodeWithTag("coui-preference-icon", useUnmergedTree = true)
            .assertIsDisplayed()
        activity.setContentView(View(activity))
    }

    @Test fun profileRowKeepsSelectionAndMenuSeparate() {
        val activity = activity()
        val profile = Profile(
            uuid = UUID.randomUUID(),
            name = "一个较长的配置名称，用来检查文字布局",
            type = Profile.Type.Url,
            source = "", active = false, interval = 0,
            upload = 0, download = 0, total = 0,
            expire = 0, updatedAt = System.currentTimeMillis(),
            imported = true, pending = false,
        )
        var activated: Profile? = null
        var menu: Profile? = null
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    ProfileListItem(
                        profile = profile,
                        currentTime = System.currentTimeMillis(),
                        onClicked = { activated = it },
                        onMenuClicked = { menu = it },
                    )
                }
            }
        })
        compose.onNodeWithText(profile.name).assertIsDisplayed().performClick()
        assertEquals(profile, activated)
        activated = null
        compose.onNodeWithContentDescription(activity.getString(R.string.more)).performClick()
        assertEquals(profile, menu)
        assertEquals(null, activated)
        activity.setContentView(View(activity))
    }

    @Test fun iconBearingDividerMatchesDemoTitleInset() {
        val activity = activity()
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    com.itos.coui.layout.Group {
                        com.itos.coui.layout.Item(
                            icon = {
                                androidx.compose.foundation.layout.Box(
                                    androidx.compose.ui.Modifier.size(
                                        com.itos.coui.layout.CouiPageDefaults.iconSize,
                                    )
                                )
                            },
                        ) { com.itos.coui.text.Text(
                            text = "Before icon row",
                            kind = com.itos.coui.text.CouiTextKind.PreferenceTitle,
                        ) }
                        SettingDivider(withIcon = true)
                        com.itos.coui.layout.Item(
                            icon = {
                                androidx.compose.foundation.layout.Box(
                                    androidx.compose.ui.Modifier.size(
                                        com.itos.coui.layout.CouiPageDefaults.iconSize,
                                    )
                                )
                            },
                        ) { com.itos.coui.text.Text(
                            text = "Aligned icon title",
                            kind = com.itos.coui.text.CouiTextKind.PreferenceTitle,
                        ) }
                    }
                }
            }
        })
        val divider = compose.onNodeWithTag("coui-setting-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Aligned icon title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Icon divider must begin at COUI row text, never icon area", title.left, divider.left, 1f)
        activity.setContentView(View(activity))
    }

    @Test fun plainDividerMatchesDemoTitleInset() {
        val activity = activity()
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    com.itos.coui.layout.Group {
                        com.itos.coui.layout.Item { com.itos.coui.text.Text(
                            text = "Before plain row", kind = com.itos.coui.text.CouiTextKind.PreferenceTitle,
                        ) }
                        SettingDivider()
                        com.itos.coui.layout.Item { com.itos.coui.text.Text(
                            text = "Aligned plain title", kind = com.itos.coui.text.CouiTextKind.PreferenceTitle,
                        ) }
                    }
                }
            }
        })
        val divider = compose.onNodeWithTag("coui-setting-divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Aligned plain title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Plain divider must align with row title", title.left, divider.left, 1f)
        activity.setContentView(View(activity))
    }

    @Test fun nativeSettingsAccessoriesMatchAlpha8Demo() {
        val activity = activity()
        class Choice { var value = "mixed" }
        val model = Choice()
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    com.itos.coui.layout.Group {
                        NativeCouiSelect(
                            title = "TUN stack",
                            value = model::value,
                            values = listOf("system", "mixed"),
                            labels = listOf("System", "Mixed"),
                        )
                        NativeCouiAction(title = "Mobile routing", navigation = true, onClick = {})
                    }
                }
            }
        })
        compose.onNodeWithText("Mixed", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag("coui-setting-chevron", useUnmergedTree = true)
            .assertCountEquals(2)
        val title = compose.onNodeWithText("TUN stack", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("Mixed", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(value.left > title.left)
        activity.setContentView(View(activity))
    }

    @Test fun nativeInputResetKeepsUnsetValueDistinctFromEmpty() {
        val activity = activity()
        class Model { var port: Int? = 7890 }
        val model = Model()
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    NativeCouiNullablePort(
                        title = "Port",
                        property = model::port,
                        placeholder = "Do not modify",
                        emptyLabel = "Disabled",
                    )
                }
            }
        })
        compose.onNodeWithTag("coui-setting-reset", useUnmergedTree = true).performClick()
        assertEquals(null, model.port)
        activity.setContentView(View(activity))
    }

    @Test fun proxyCardsHaveEqualHeightForOneAndTwoLineNames() {
        val activity = activity()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = UiStore(activity).apply { proxyLine = 2 }
        val design = ProxyDesign(activity, null, listOf("Selector"), store)
        activity.setContentView(design.root)
        val proxies = listOf(
            Proxy("short", "日本", "Tuic", "Tuic", 12, false),
            Proxy("long", "俄罗斯[1.5x]-冷门", "Tuic", "Tuic", 120, false),
        )
        scope.launch { design.updateGroup(0, proxies, true, ProxyState("short"), emptyMap()) }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithTag("proxy-node-short").assertIsSelected()
        compose.onNodeWithTag("proxy-node-long").assertIsNotSelected()
        val left = compose.onNodeWithTag("proxy-node-short").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("proxy-node-long").fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 1f)
        assertEquals(left.height, right.height, 1f)
        val tabs = compose.onNodeWithTag("proxy-group-tabs", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Nodes must begin below the actual measured group tabs", left.top >= tabs.bottom - 1f)
        scope.cancel()
        activity.setContentView(View(activity))
    }

    @Test fun nativeProxySelectionPreservesEnabledState() {
        val activity = activity()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val design = ProxyDesign(activity, null, listOf("Selector"), UiStore(activity))
        activity.setContentView(design.root)
        val proxy = Proxy("node-a", "Node A", "Tokyo", "Selector", 30, false)

        scope.launch { design.updateGroup(0, listOf(proxy), true, ProxyState(""), emptyMap()) }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText("Node A").assertIsDisplayed().performClick()
        assertEquals(ProxyDesign.Request.Select(0, "node-a"), design.requests.tryReceive().getOrNull())
        compose.onNodeWithTag("proxy-node-node-a").assertIsNotSelected()

        // Selection must repaint without refreshing the entire proxy group from Clash.
        design.updateSelectedProxy(0, "node-a")
        compose.waitForIdle()
        compose.onNodeWithTag("proxy-node-node-a").assertIsSelected()

        scope.launch { design.updateGroup(0, listOf(proxy), false, ProxyState(""), emptyMap()) }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        // A disabled COUI proxy row no longer submits the request.
        compose.onNodeWithText("Node A").performClick()
        assertEquals(null, design.requests.tryReceive().getOrNull())
        scope.cancel()
        activity.setContentView(View(activity))
    }
}
