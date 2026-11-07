package com.github.kr328.clash.design

import android.view.View
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.kr328.clash.design.dialog.CouiNoticeCard
import com.github.kr328.clash.design.dialog.showCouiNotice
import com.github.kr328.clash.design.ui.ToastDuration
import com.itos.coui.theme.CouiTheme
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Validate the overlay host separately from its composable click target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CouiNoticeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun activity() = Robolectric.buildActivity(AppCompatActivity::class.java).also {
        it.get().setTheme(R.style.AppThemeLight)
        it.setup()
    }

    @Test fun noticeOverlayRendersOnAnActivity() {
        val controller = activity()
        val activity = controller.get()
        activity.setContentView(View(activity))
        activity.showCouiNotice("COUI 状态消息", ToastDuration.Indefinite)
        compose.onNodeWithText("COUI 状态消息").assertIsDisplayed()
        controller.pause().stop().destroy()
    }

    @Test fun noticeCardActionInvokesCallback() {
        val controller = activity()
        val activity = controller.get()
        val actions = AtomicInteger()
        activity.setContentView(ComposeView(activity).apply {
            setContent {
                CouiTheme(darkTheme = false) {
                    CouiNoticeCard(
                        message = "COUI 状态消息",
                        actionLabel = "查看详情",
                        onAction = { actions.incrementAndGet() },
                    )
                }
            }
        })
        compose.onNodeWithText("COUI 状态消息").assertIsDisplayed()
        compose.onNodeWithTag("coui-notice-action").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            actions.get() == 1
        }
        assertEquals(1, actions.get())
        controller.pause().stop().destroy()
    }
}
