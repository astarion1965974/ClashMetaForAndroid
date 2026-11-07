package com.github.kr328.clash.design

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression: the network page used to put a Compose notification on the Activity
 * during construction, before BaseActivity.setContentDesign mounted the root view.
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

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { design.showRunningNotice() }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText(activity.getString(R.string.options_unavailable)).assertIsDisplayed()

        scope.cancel()
        activity.setContentView(View(activity))
    }
}
