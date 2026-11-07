package com.github.kr328.clash.design

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression: the network page used to put a Compose notification on the Activity
 * during construction, before BaseActivity.setContentDesign mounted the root view.
 * The warning now renders inline, even when the service is running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NetworkSettingsLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun runningNetworkPageMountsBeforeShowingItsNotice() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.AppThemeLight)
        }.setup().get()
        val design = NetworkSettingsDesign(
            activity, UiStore(activity), ServiceStore(activity), running = true,
        )
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        assertNull(parent.findViewWithTag<View>("clash-live-coui-notice"))

        activity.setContentView(design.root)
        compose.onNodeWithText(activity.getString(R.string.route_system_traffic)).assertIsDisplayed()

        // No floating notice: the running-service warning remains visible as an
        // inline COUI group footer, without attaching a second ComposeView.
        compose.onNodeWithText(activity.getString(R.string.options_unavailable)).assertExists()
        assertNull(parent.findViewWithTag<View>("clash-live-coui-notice"))

        activity.setContentView(View(activity))
    }
}
