package com.github.kr328.clash.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.databinding.DataBindingUtil
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ManagedRoutingSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import androidx.appcompat.app.AppCompatActivity
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Resource inflation and interaction checks, using the actual Android view hierarchy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveUiTest {
    private fun context(dark: Boolean): Context {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(if (dark) R.style.AppThemeDark else R.style.AppThemeLight)
        controller.setup()
        return controller.get()
    }

    private fun render(view: View, name: String, width: Int = 412, height: Int = 915) {
        val parent = FrameLayout(view.context).apply {
            val color = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.windowBackground, color, true)
            setBackgroundColor(color.data)
            addView(view)
        }
        var owner = view.context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        if (owner is android.app.Activity) owner.setContentView(parent)
        shadowOf(Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS)
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        fun settle(v: View) {
            v.refreshDrawableState()
            v.background?.state = v.drawableState
            v.jumpDrawablesToCurrentState()
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) settle(v.getChildAt(i))
        }
        settle(parent)
        parent.draw(Canvas(bitmap))
        val directory = File("build/ui-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(view.measuredWidth > 0)
    }

    @Test fun homeReflectsConnectionAndRoutesActions() {
        for (dark in listOf(false, true)) {
            val design = MainDesign(context(dark))
            val binding = DataBindingUtil.getBinding<DesignMainBinding>(design.root)!!
            binding.clashRunning = true
            binding.forwarded = "128.4 MiB"
            binding.mode = "规则模式"
            binding.profileName = "我的日常订阅"
            binding.hasProviders = true
            binding.executePendingBindings()
            assertTrue(binding.toggleView.isEnabled)
            render(design.root, if (dark) "home-dark" else "home-light")
            binding.toggleView.performClick()
            assertEquals(MainDesign.Request.ToggleStatus, design.requests.tryReceive().getOrNull())
            binding.appRoutingView.performClick()
            assertEquals(MainDesign.Request.OpenAppRouting, design.requests.tryReceive().getOrNull())
            binding.ruleRoutingView.performClick()
            assertEquals(MainDesign.Request.OpenRouting, design.requests.tryReceive().getOrNull())
            binding.clashRunning = false
            binding.executePendingBindings()
            assertEquals(context(dark).getString(R.string.live_connect), binding.toggleView.text.toString())
        }
    }

    @Test fun appRoutingPreservesSelectionAndSaveState() {
        val ctx = context(true)
        (ctx as android.app.Activity).title = ctx.getString(R.string.live_app_routing)
        val selected = mutableSetOf("org.example.chat")
        val design = AccessControlDesign(ctx, UiStore(ctx), selected, AccessControlMode.AcceptSelected, true)
        design.patchApps(listOf(
            AppInfo("org.example.chat", "聊天应用", android.graphics.drawable.ColorDrawable(0xff5e6ad2.toInt()), 0, 0),
            AppInfo("org.example.browser", "浏览器", android.graphics.drawable.ColorDrawable(0xff268c66.toInt()), 0, 0),
            AppInfo("org.example.music", "音乐", android.graphics.drawable.ColorDrawable(0xffbd5b6e.toInt()), 0, 0)
        ))
        render(design.root, "apps-dark")
        assertFalse(design.root.findViewById<View>(R.id.save_view).isEnabled)
        assertTrue(selected.contains("org.example.chat"))
        val list = design.root.findViewById<RecyclerView>(R.id.main_list)
        val second = list.findViewHolderForAdapterPosition(1)!!
        second.itemView.performClick()
        assertTrue(selected.contains("org.example.browser"))
        assertTrue(design.root.findViewById<View>(R.id.save_view).isEnabled)
        assertEquals(ctx.getString(R.string.route_save_reconnect), design.root.findViewById<TextView>(R.id.save_view).text)
    }

    @Test fun ruleSettingsInflateInBothThemesAndKeepSaveReachable() {
        for (dark in listOf(false, true)) {
            val ctx = context(dark)
            (ctx as android.app.Activity).title = ctx.getString(R.string.live_rules)
            val design = ManagedRoutingDesign(ctx, ManagedRoutingSettings(), listOf("自动选择", "香港节点"), false)
            render(design.root, if (dark) "rules-dark" else "rules-light")
            val save = design.root.findViewById<View>(R.id.save_view)
            assertFalse(save.isEnabled)
            assertTrue(save.bottom <= design.root.height)
            assertTrue(save.height >= 48)
        }
    }

    @Test @Config(qualifiers = "zh-rCN-w800dp-h360dp-land-mdpi")
    fun appRoutingLandscapeKeepsListAndSaveVisible() {
        val ctx = context(false)
        val design = AccessControlDesign(ctx, UiStore(ctx), mutableSetOf(), AccessControlMode.DenySelected, false)
        render(design.root, "apps-landscape", 800, 360)
        assertTrue(design.root.findViewById<View>(R.id.main_list).height >= 100)
        assertTrue(design.root.findViewById<View>(R.id.save_view).bottom <= 360)
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun homeSupportsNarrowScreensAndLargeText() {
        val ctx = context(false)
        val configuration = android.content.res.Configuration(ctx.resources.configuration).apply { fontScale = 1.3f }
        val scaled = ContextThemeWrapper(ctx.createConfigurationContext(configuration), R.style.AppThemeLight)
        val design = MainDesign(scaled)
        val binding = DataBindingUtil.getBinding<DesignMainBinding>(design.root)!!
        binding.profileName = "一个很长的订阅配置名称，用于检查窄屏文字"
        binding.executePendingBindings()
        render(design.root, "home-small-large-font", 320, 640)
        assertTrue(binding.toggleView.height >= 48)
        assertTrue(binding.toggleView.width > 0)
    }
}
