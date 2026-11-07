package com.github.kr328.clash.design

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.kr328.clash.design.dialog.CouiNoticeOptions
import com.github.kr328.clash.design.dialog.showCouiNotice
import com.github.kr328.clash.design.ui.ToastDuration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

        var actions = 0
        val options = CouiNoticeOptions(activity).apply {
            setAction("查看详情") { actions++ }
        }
        activity.showCouiNotice("COUI 状态消息", ToastDuration.Indefinite, options)

        compose.onNodeWithText("COUI 状态消息").assertIsDisplayed()
        compose.onNodeWithTag("coui-notice-action").performClick()
        // Compose semantics actions can be dispatched on the next idle frame.
        // Verify the actual callback, rather than allowing a silent click.
        compose.waitUntil(timeoutMillis = 5_000) { actions == 1 }
        assertEquals(1, actions)

        controller.pause().stop().destroy()
    }
}
