package com.github.kr328.clash.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.preference.OnChangedListener
import com.github.kr328.clash.design.preference.SwitchPreference
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.view.ActionLabel
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
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
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun activity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppThemeLight)
        controller.setup()
        return controller.get()
    }

    private class ToggleModel {
        @Volatile var enabled = false
    }

    @Test fun switchWritesOnceAndRespectsDisabledState() {
        val activity = activity()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val model = ToggleModel()
        val changed = AtomicInteger()
        lateinit var preference: SwitchPreference
        val screen = scope.preferenceScreen(activity) {
            preference = switch(model::enabled, title = R.string.mobile_enabled) {
                listener = OnChangedListener { changed.incrementAndGet() }
            }
        }
        activity.setContentView(screen.root)
        val label = activity.getString(R.string.mobile_enabled)
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithText(label).assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            model.enabled && changed.get() == 1
        }
        preference.enabled = false
        compose.onNodeWithText(label).assertIsNotEnabled()
        assertEquals(1, changed.get())
        scope.cancel()
        activity.setContentView(View(activity))
    }

    @Test fun actionIconsPreserveAspectRatio() {
        val activity = activity()
        val drawable = BitmapDrawable(activity.resources, Bitmap.createBitmap(60, 30, Bitmap.Config.ARGB_8888))
        val row = ActionLabel(activity).apply {
            text = "Test action"
            icon = drawable
            setOnClickListener { }
        }
        activity.setContentView(row)
        compose.onNodeWithText("Test action").assertIsDisplayed()

        fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
            }
        }

        val image = descendants(row).filterIsInstance<ImageView>().first { it.drawable === drawable }
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
        assertTrue(image.width > 0 && image.height > 0)
        activity.setContentView(View(activity))
    }

    @Test fun profileRowKeepsSelectionAndMenuSeparate() {
        val activity = activity()
        val profile = Profile(
            uuid = UUID.randomUUID(),
            name = "一个较长的配置名称，用来检查文字布局",
            type = Profile.Type.Url,
            source = "",
            active = false,
            interval = 0,
            upload = 0,
            download = 0,
            total = 0,
            expire = 0,
            updatedAt = System.currentTimeMillis(),
            imported = true,
            pending = false,
        )
        var activated: Profile? = null
        var menu: Profile? = null
        val adapter = ProfileAdapter(activity, { activated = it }, { menu = it })
        adapter.profiles = listOf(profile)
        val list = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }
        activity.setContentView(list)

        compose.onNodeWithText(profile.name).assertIsDisplayed().performClick()
        assertEquals(profile, activated)
        activated = null
        compose.onNodeWithContentDescription(activity.getString(R.string.more)).performClick()
        assertEquals(profile, menu)
        assertEquals(null, activated)
        activity.setContentView(View(activity))
    }
}
