package com.github.kr328.clash.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.adapter.ProfileListItem
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.preference.CouiPreferenceRow
import com.github.kr328.clash.design.preference.NativeCouiSwitch
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

    @Test fun nativeRowKeepsExternalDrawableAspectRatio() {
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
        fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
            }
        }
        val image = descendants(root).filterIsInstance<ImageView>().first { it.drawable === drawable }
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
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
