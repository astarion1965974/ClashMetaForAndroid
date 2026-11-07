package com.github.kr328.clash.design

import android.view.View
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.github.kr328.clash.design.dialog.CouiNoticeOptions
import com.github.kr328.clash.design.dialog.showCouiNotice
import com.github.kr328.clash.design.ui.ToastDuration
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The COUI replacement for Material Snackbar preserves its interactive action. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CouiNoticeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun noticeRendersAndRoutesAction() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppThemeLight)
        controller.setup()
        activity.setContentView(View(activity))

        val actions = AtomicInteger(0)
        val options = CouiNoticeOptions(activity).apply {
            setAction("查看详情") { actions.incrementAndGet() }
        }
        activity.showCouiNotice("COUI 状态消息", ToastDuration.Indefinite, options)

        compose.onNodeWithText("COUI 状态消息").assertIsDisplayed()
        // Dispatch a real pointer gesture through the overlay ComposeView;
        // this also exercises the actual touch hit target, not just semantics.
        compose.onNodeWithTag("coui-notice-action").performTouchInput { click() }
        // Robolectric pauses the Android main Looper, while Compose's click
        // action can be posted to it after the accessibility event.
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            actions.get() == 1
        }
        assertEquals(1, actions.get())

        controller.pause().stop().destroy()
    }
}
