package com.github.kr328.clash.design

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.ui.ToastDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** All transient notifications must use the native system toast, not a custom overlay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CouiNoticeTest {
    @Test fun ordinaryFeedbackUsesSystemToast() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppThemeLight)
        val activity = controller.setup().get()
        val design = object : Design<Unit>(activity) {
            override val root: View = View(activity)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { design.showToast("Update completed", ToastDuration.Short) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Update completed", ShadowToast.getTextOfLatestToast())
        scope.cancel()
        controller.pause().stop().destroy()
    }
}
