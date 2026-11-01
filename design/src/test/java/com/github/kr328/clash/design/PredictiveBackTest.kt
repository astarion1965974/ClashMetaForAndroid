package com.github.kr328.clash.design

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.design.dialog.FullScreenDialog
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.DirectoryBackCallback
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import com.github.kr328.clash.service.store.MobileNetworkSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 35])
class PredictiveBackTest {
    private fun activity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppThemeLight)
        return controller.setup().get()
    }

    private fun event(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) =
        BackEventCompat(0f, 200f, progress, edge)

    @Test fun previewCancelRestoresBothEdgesWithoutPoppingAndCommitPopsOnce() {
        val activity = activity()
        val parent = FrameLayout(activity)
        val page = View(activity).apply { setBackgroundColor(Color.BLUE) }
        parent.addView(page)
        activity.setContentView(parent)
        parent.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 400, 800)
        val snapshot = DirectoryBackCallback.capture(page)!!
        var pops = 0
        val callback = DirectoryBackCallback({ page }, { snapshot }) { pops++ }
        callback.isEnabled = true
        val dispatcher = OnBackPressedDispatcher()
        dispatcher.addCallback(callback)
        for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
            page.translationX = 7f
            page.scaleX = 0.98f
            dispatcher.dispatchOnBackStarted(event(0f, edge))
            dispatcher.dispatchOnBackProgressed(event(0.7f, edge))
            assertEquals(2, parent.childCount)
            assertTrue(page.scaleX < 0.98f)
            if (edge == BackEventCompat.EDGE_LEFT) assertTrue(page.translationX > 7f)
            else assertTrue(page.translationX < 7f)
            dispatcher.dispatchOnBackCancelled()
            assertEquals(0, pops)
            assertEquals(1, parent.childCount)
            assertEquals(7f, page.translationX, 0f)
            assertEquals(0.98f, page.scaleX, 0f)
            assertEquals(1f, page.scaleY, 0f)
        }
        dispatcher.dispatchOnBackStarted(event(0f))
        dispatcher.dispatchOnBackProgressed(event(0.8f))
        dispatcher.onBackPressed()
        dispatcher.onBackPressed() // A slow refresh must not pop two directories.
        assertEquals(1, pops)
        callback.complete()
        assertEquals(1, parent.childCount)
        dispatcher.onBackPressed() // Buttons / Android <= 13 need no progress event.
        assertEquals(2, pops)
    }

    @Test fun disabledDirectoryCallbackLeavesRootToSystemAndStopRemovesPreview() {
        val activity = activity()
        val parent = FrameLayout(activity)
        val page = View(activity)
        parent.addView(page)
        activity.setContentView(parent)
        parent.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 100, 200)
        var fallback = 0
        var pops = 0
        val snapshot = DirectoryBackCallback.capture(page)
        val callback = DirectoryBackCallback({ page }, { snapshot }) { pops++ }
        val dispatcher = OnBackPressedDispatcher { fallback++ }
        dispatcher.addCallback(callback)
        dispatcher.onBackPressed()
        assertEquals(1, fallback)
        callback.isEnabled = true
        dispatcher.dispatchOnBackStarted(event(0.5f))
        assertEquals(2, parent.childCount)
        callback.onStop(activity)
        assertEquals(1, parent.childCount)
        assertEquals(0, pops)
        assertEquals(1f, page.scaleX, 0f)
    }

    @Test fun dirtyAndBusyChangesNotifyBeforeTheNextBackGesture() {
        val activity = activity()
        val apps = AccessControlDesign(activity, UiStore(activity), mutableSetOf(), AccessControlMode.AcceptSelected, false)
        var notifications = 0
        apps.onBackStateChanged = { notifications++ }
        apps.select(com.github.kr328.clash.service.model.AppAccessPolicy.Selection.Invert)
        apps.setSaving(true)
        apps.setSaving(false)
        assertTrue(notifications >= 3)

        val mobile = MobileNetworkDesign(activity, MobileNetworkSettings(), emptyList(), false)
        var blocked = false
        mobile.onBackStateChanged = { blocked = mobile.dirty }
        mobile.bind("Home")
        assertTrue(blocked)
        mobile.markSaved()
        assertFalse(blocked)

        val rules = ManagedRoutingDesign(activity, ManagedRoutingSettings(), emptyList(), false)
        var changes = 0
        rules.onBackStateChanged = { changes++ }
        rules.setSaving(true)
        rules.setSaving(false)
        rules.setUpdating(true)
        rules.setUpdating(false)
        rules.markSaved()
        assertEquals(5, changes)
    }

    @Test fun toolbarUsesTheSameDispatcherAsSystemBack() {
        val activity = activity()
        val design = MobileNetworkDesign(activity, MobileNetworkSettings(), emptyList(), false)
        var back = 0
        activity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { back++ }
        })
        design.root.findViewById<ImageView>(R.id.activity_bar_close_view).performClick()
        assertEquals(1, back)
    }

    @Test fun fullScreenDialogCancelDoesNotDismissAndNonCancelableDialogStaysOpen() {
        val activity = activity()
        val dialog = FullScreenDialog(activity)
        dialog.setContentView(FrameLayout(activity))
        var canceled = 0
        dialog.setOnCancelListener { canceled++ }
        dialog.show()
        dialog.onBackPressedDispatcher.dispatchOnBackStarted(event(0f))
        dialog.onBackPressedDispatcher.dispatchOnBackProgressed(event(0.6f))
        dialog.onBackPressedDispatcher.dispatchOnBackCancelled()
        assertTrue(dialog.isShowing)
        assertEquals(0, canceled)
        dialog.onBackPressedDispatcher.onBackPressed()
        assertFalse(dialog.isShowing)
        dialog.show()
        dialog.setCancelable(false)
        dialog.onBackPressedDispatcher.onBackPressed()
        assertTrue(dialog.isShowing)
        dialog.setCancelable(true)
        dialog.onBackPressedDispatcher.onBackPressed()
        assertFalse(dialog.isShowing)
    }
}
