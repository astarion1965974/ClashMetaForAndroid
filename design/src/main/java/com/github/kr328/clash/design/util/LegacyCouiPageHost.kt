package com.github.kr328.clash.design.util

import android.os.Build
import android.util.Log
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import android.view.ViewTreeObserver
import com.github.kr328.clash.design.view.CouiIconButtonView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ToolbarIcon
import com.github.kr328.clash.design.view.ActivityBarLayout
import com.github.kr328.clash.design.view.CouiFabView
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar

/**
 * Page-level COUI migration bridge for legacy XML designs.
 *
 * The old page is hosted as the content of a genuine COUI Scaffold, while the
 * top app bar is a separate backdrop consumer. This keeps scroll views,
 * view bindings, and toolbar actions alive during staged conversion to pure
 * Compose/COUI components. It replaces the previous isolated, empty
 * 56dp Scaffold, which could only sample transparent pixels.
 *
 * This bridge is NOT a full conversion of the XML rows. AndroidView rendering
 * into the COUI GraphicsLayer must still be checked on a physical device.
 */
fun wrapLegacyCouiPage(context: Context, original: View): View {
    if (original is ComposeView || context !is AppCompatActivity) return original
    val toolbar = original.findViewById<ActivityBarLayout>(R.id.activity_bar_layout)
        ?: return original

    // Preserve the real toolbar buttons in their original View hierarchy:
    // binding and visibility transitions still update those instances.
    // Only their visual surfaces are drawn as native COUI TopAppBarButtons,
    // sharing the SAME page backdrop as the standard Settings back button.
    val actionIds = intArrayOf(
        R.id.action_layout, R.id.url_test_layout, R.id.update_layout,
        R.id.clear_view, R.id.delete_view, R.id.new_view, R.id.add_view,
        R.id.update_view, R.id.menu_view,
    )
    val actions = mutableListOf<CouiIconButtonView>()
    fun harvest(view: View) {
        if (view is CouiIconButtonView) {
            if (actions.none { it === view }) actions.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) harvest(view.getChildAt(i))
        }
    }
    for (id in actionIds) toolbar.findViewById<View>(id)?.let(::harvest)
    // INVISIBLE keeps the legacy header's padding/scroll contracts intact.
    // It does not create a second opaque or clickable toolbar.
    toolbar.visibility = View.INVISIBLE

    val oldFloatingFab = original.findViewById<CouiFabView>(R.id.url_test_float_view)
    oldFloatingFab?.let {
        it.beginHost()
        (it.parent as? ViewGroup)?.removeView(it)
    }

    return ComposeView(context).apply {
        setContent {
            val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
            CouiTheme(darkTheme = dark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CouiTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = context.title.toString(),
                                    kind = CouiTextKind.Toolbar,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                ToolbarIcon(
                                    icon = CouiIcons.Back,
                                    label = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                                    onClick = {
                                        (context as OnBackPressedDispatcherOwner)
                                            .onBackPressedDispatcher.onBackPressed()
                                    },
                                )
                            },
                            actions = {
                                actions.forEach { action ->
                                    key(action.id) {
                                        LegacyToolbarAction(original, toolbar, action)
                                    }
                                }
                            },
                        )
                    },
                    floatingActionButton = if (oldFloatingFab != null) {
                        {
                            if (oldFloatingFab.visibleInHost) {
                                val renderer = if (Build.VERSION.SDK_INT >= 34) remember {
                                    runCatching { CouiFloatingRenderer.standalone() }
                                        .onFailure { Log.w("LegacyCouiHost", "COUI FAB unavailable", it) }
                                        .getOrNull()
                                } else null
                                val buttonModifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                                if (renderer != null) {
                                    Fab(
                                        modifier = buttonModifier,
                                        renderer = renderer,
                                        onClick = { oldFloatingFab.performClick() },
                                    ) {
                                        if (oldFloatingFab.iconForHost != 0) Image(
                                            painterResource(oldFloatingFab.iconForHost),
                                            oldFloatingFab.labelForHost,
                                            modifier = Modifier.size(24.dp),
                                            colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onAccent),
                                        )
                                    }
                                } else {
                                    Box(
                                        buttonModifier.size(56.dp).clip(CircleShape)
                                            .background(CouiTheme.colorScheme.accent)
                                            .clickable { oldFloatingFab.performClick() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (oldFloatingFab.iconForHost != 0) Image(
                                            painterResource(oldFloatingFab.iconForHost),
                                            oldFloatingFab.labelForHost,
                                            modifier = Modifier.size(24.dp),
                                            colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onAccent),
                                        )
                                    }
                                }
                            }
                        }
                    } else null,
                ) { _ ->
                    // Keep the View under the whole viewport, including
                    // COUI top-bar gradient. Existing XML already accounts
                    // for system insets and its legacy header height.
                    Box(Modifier.fillMaxSize().background(CouiTheme.colorScheme.background)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                                (original.parent as? ViewGroup)?.removeView(original)
                                original
                            },
                        )
                    }
                }
            }
        }
    }
}


/** Native COUI glass action, retaining the legacy click handler and states. */
@Composable
private fun LegacyToolbarAction(original: View, toolbar: View, action: CouiIconButtonView) {
    fun isAvailable(): Boolean {
        var current: View? = action
        while (current != null && current !== toolbar) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return action.isEnabled
    }
    var available by remember(action) { mutableStateOf(isAvailable()) }
    DisposableEffect(original, action) {
        var observer: ViewTreeObserver? = null
        val onLayout = ViewTreeObserver.OnGlobalLayoutListener {
            val latest = isAvailable()
            if (available != latest) available = latest
        }
        fun attachObserver() {
            if (observer == null) {
                original.viewTreeObserver.takeIf { it.isAlive }?.let {
                    it.addOnGlobalLayoutListener(onLayout)
                    observer = it
                }
            }
        }
        fun removeObserver() {
            observer?.let { if (it.isAlive) it.removeOnGlobalLayoutListener(onLayout) }
            observer = null
        }
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { attachObserver() }
            override fun onViewDetachedFromWindow(v: View) { removeObserver() }
        }
        original.addOnAttachStateChangeListener(attach)
        if (original.isAttachedToWindow) attachObserver()
        onDispose {
            original.removeOnAttachStateChangeListener(attach)
            removeObserver()
        }
    }
    if (available && action.iconForHost != 0) {
        ToolbarIcon(
            ImageVector.vectorResource(action.iconForHost),
            action.contentDescription?.toString().orEmpty()
                .ifEmpty { action.labelForHost.orEmpty() },
        ) {
            if (isAvailable()) action.performClick()
        }
    }
}
