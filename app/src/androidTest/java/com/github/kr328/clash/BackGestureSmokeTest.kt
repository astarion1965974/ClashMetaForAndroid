package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.kr328.clash.design.MobileNetworkDesign
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.dialog.FullScreenDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android 15 touchscreen gestures. Runs only on the disposable CI emulator. */
@RunWith(AndroidJUnit4::class)
class BackGestureSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue(message, condition())
    }

    private fun screenshot(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "predictive-back").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun gesture(cancel: Boolean, name: String, right: Boolean = false) {
        val width = device.displayWidth
        val y = device.displayHeight * 0.55f
        val start = if (right) width - 1f else 1f
        val direction = if (right) -1f else 1f
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, start)
        for (i in 1..12) {
            SystemClock.sleep(25)
            inject(MotionEvent.ACTION_MOVE, start + direction * width * 0.55f * i / 12)
        }
        SystemClock.sleep(250)
        screenshot("$name-preview")
        if (cancel) {
            for (i in 11 downTo 0) {
                SystemClock.sleep(25)
                inject(MotionEvent.ACTION_MOVE, start + direction * width * 0.55f * i / 12)
            }
            SystemClock.sleep(150)
        }
        inject(MotionEvent.ACTION_UP, if (cancel) start else start + direction * width * 0.55f)
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        screenshot("$name-after")
    }

    private fun <T : Activity> open(parent: Activity, type: Class<T>): T {
        val monitor = instrumentation.addMonitor(type.name, null, false)
        instrumentation.runOnMainSync { parent.startActivity(Intent(parent, type)) }
        val activity = monitor.waitForActivityWithTimeout(10_000)
        instrumentation.removeMonitor(monitor)
        assertNotNull("Activity did not open: ${type.name}", activity)
        SystemClock.sleep(1_000)
        @Suppress("UNCHECKED_CAST")
        return activity as T
    }

    @Test fun systemAndDialogGesturesCancelAndCommitWithoutCorruptingTheStack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var main: MainActivity? = null
            scenario.onActivity { main = it }
            device.wait(Until.hasObject(By.res("com.astarion.clash.live", "toggle_view")), 15_000)
            // Grant is supplied by CI before instrumentation, so no permission window covers gestures.
            val settings = open(main!!, SettingsActivity::class.java)
            screenshot("settings-before")
            gesture(true, "settings-cancel")
            assertFalse(settings.isFinishing)
            assertEquals(Lifecycle.State.RESUMED, settings.lifecycle.currentState)
            gesture(false, "settings-commit", right = true)
            waitFor("Settings did not return to home") { settings.isDestroyed }

            val nodes = open(main!!, ProxyActivity::class.java)
            gesture(true, "nodes-cancel", right = true)
            assertFalse(nodes.isFinishing)
            gesture(false, "nodes-commit")
            waitFor("Node page did not return") { nodes.isDestroyed }

            val mobile = open(main!!, MobileNetworkActivity::class.java)
            instrumentation.runOnMainSync {
                val field = BaseActivity::class.java.getDeclaredField("design").apply { isAccessible = true }
                (field.get(mobile) as MobileNetworkDesign).bind("CI-only-unsaved")
            }
            gesture(true, "dirty-cancel")
            assertFalse(device.hasObject(By.text(mobile.getString(com.github.kr328.clash.design.R.string.route_discard))))
            gesture(false, "dirty-commit")
            val keep = device.wait(Until.findObject(By.text(mobile.getString(com.github.kr328.clash.design.R.string.route_keep_editing))), 5_000)
            assertNotNull("Unsaved confirmation missing", keep)
            keep!!.click()
            assertFalse(mobile.isFinishing)
            instrumentation.runOnMainSync { mobile.finish() }
            waitFor("Mobile settings did not close") { mobile.isDestroyed }

            var dialog: FullScreenDialog? = null
            instrumentation.runOnMainSync {
                dialog = FullScreenDialog(main!!).apply {
                    setContentView(TextView(context).apply { text = "Predictive preference editor" })
                    show()
                }
            }
            gesture(true, "fullscreen-cancel")
            assertTrue(dialog!!.isShowing)
            gesture(false, "fullscreen-commit")
            assertFalse(dialog!!.isShowing)

            var sheet: AppBottomSheetDialog? = null
            instrumentation.runOnMainSync {
                sheet = AppBottomSheetDialog(main!!).apply {
                    setContentView(FrameLayout(context).apply { minimumHeight = 800 })
                    show()
                }
            }
            gesture(true, "sheet-cancel")
            assertTrue(sheet!!.isShowing)
            gesture(false, "sheet-commit")
            waitFor("Bottom sheet did not close") { !sheet!!.isShowing }

            gesture(true, "home-cancel")
            assertEquals("com.astarion.clash.live", device.currentPackageName)
            gesture(false, "home-commit")
            waitFor("Root back did not return to launcher") { device.currentPackageName != "com.astarion.clash.live" }
        }
    }
}
