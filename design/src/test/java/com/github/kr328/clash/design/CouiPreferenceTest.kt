package com.github.kr328.clash.design

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.kr328.clash.design.preference.OnChangedListener
import com.github.kr328.clash.design.preference.SwitchPreference
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.switch
import com.github.kr328.clash.design.view.ActionLabel
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        var changed = 0
        lateinit var preference: SwitchPreference
        val screen = scope.preferenceScreen(activity) {
            preference = switch(model::enabled, title = R.string.mobile_enabled) {
                listener = OnChangedListener { changed++ }
            }
        }
        activity.setContentView(screen.root)
        val label = activity.getString(R.string.mobile_enabled)
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onNodeWithText(label).assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(timeoutMillis = 10_000) { model.enabled && changed == 1 }
        preference.enabled = false
        compose.onNodeWithText(label).assertIsNotEnabled()
        assertEquals(1, changed)
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
}
