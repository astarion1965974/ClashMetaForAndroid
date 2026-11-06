package com.github.kr328.clash

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.design.AccessControlDesign
import com.github.kr328.clash.design.Design
import com.github.kr328.clash.design.ManagedRoutingDesign
import com.github.kr328.clash.design.MobileNetworkDesign
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import com.github.kr328.clash.service.store.MobileNetworkSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class BackGuardTest {
    private val dispatcher = StandardTestDispatcher()
    private val cleanup = mutableListOf<() -> Unit>()

    @Before fun prepare() {
        Global.init(RuntimeEnvironment.getApplication())
        Dispatchers.setMain(dispatcher)
    }

    @After fun finish() {
        cleanup.reversed().forEach { it() }
        Dispatchers.resetMain()
    }

    private fun <T : BaseActivity<*>> activity(type: Class<T>): T {
        val controller = Robolectric.buildActivity(type).create().start().resume()
        val activity = controller.get()
        // Avoid service/IO work: these tests exercise the real exit guards in isolation.
        activity.coroutineContext[Job]!!.children.forEach { it.cancel() }
        cleanup.add { controller.pause().stop().destroy() }
        return activity
    }

    private fun attach(activity: BaseActivity<*>, design: Design<*>) {
        BaseActivity::class.java.getDeclaredField("design").apply { isAccessible = true }.set(activity, design)
        activity.setContentView(design.root)
    }

    private fun back(activity: BaseActivity<*>) {
        activity.javaClass.getDeclaredMethod("confirmBack").apply { isAccessible = true }.invoke(activity)
    }

    private fun busy(activity: BaseActivity<*>, field: String, value: Boolean) {
        activity.javaClass.getDeclaredField(field).apply { isAccessible = true }.setBoolean(activity, value)
    }

    class GuardHarnessActivity : BaseActivity<MobileNetworkDesign>() {
        var blocked = true
        var intercepted = 0
        override suspend fun main() = Unit
        fun configure(page: MobileNetworkDesign) {
            design = page
            interceptBack(page, { blocked }) { intercepted++ }
        }
    }

    @Test fun saveCompletingDuringGestureFallsThroughExactlyOnce() {
        val page = activity(GuardHarnessActivity::class.java)
        val design = MobileNetworkDesign(page, MobileNetworkSettings(), emptyList(), false)
        var parentBack = 0
        page.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { parentBack++ }
        })
        page.configure(design)
        page.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
        page.blocked = false
        design.onBackStateChanged()
        page.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, parentBack)
        assertEquals(0, page.intercepted)
    }

    @Test fun dirtyPagesKeepEditingOrDiscardAndBusyWorkCannotExit() {
        val apps = activity(AccessControlActivity::class.java)
        val selected = mutableSetOf<String>()
        val appDesign = AccessControlDesign(apps, UiStore(apps), selected, AccessControlMode.AcceptSelected, false)
        selected.add("example.app")
        attach(apps, appDesign)

        val rules = activity(ManagedRoutingActivity::class.java)
        val settings = ManagedRoutingSettings()
        val ruleDesign = ManagedRoutingDesign(rules, settings, emptyList(), false)
        settings.enabled = !settings.enabled
        attach(rules, ruleDesign)

        val mobile = activity(MobileNetworkActivity::class.java)
        val mobileDesign = MobileNetworkDesign(mobile, MobileNetworkSettings(), emptyList(), false)
        mobileDesign.bind("Home")
        attach(mobile, mobileDesign)

        for (page in listOf(apps, rules, mobile)) {
            back(page)
            val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(confirmation.isShowing)
            confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(page.isFinishing)
            busy(page, "saving", true)
            back(page)
            assertFalse(page.isFinishing)
            assertFalse(confirmation.isShowing)
            assertSame(confirmation, ShadowDialog.getLatestDialog())
            busy(page, "saving", false)
            if (page === rules) {
                busy(page, "updating", true)
                back(page)
                assertSame(confirmation, ShadowDialog.getLatestDialog())
                busy(page, "updating", false)
            }
            back(page)
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dispatcher.scheduler.runCurrent()
            assertTrue(page.isFinishing)
        }
    }
}
