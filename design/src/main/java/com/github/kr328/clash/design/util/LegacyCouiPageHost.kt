package com.github.kr328.clash.design.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ToolbarIcon
import com.github.kr328.clash.design.view.ActivityBarLayout
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

    // Preserve the original view instances, LiveData/data binding, click
    // listeners, progress spinners and dynamic visible/enabled state.
    val actionIds = intArrayOf(
        R.id.action_layout, R.id.url_test_layout, R.id.update_layout,
        R.id.clear_view, R.id.delete_view, R.id.new_view, R.id.add_view,
        R.id.update_view, R.id.menu_view,
    )
    val actions = mutableListOf<View>()
    fun inChildTree(parent: View, target: View): Boolean {
        var current: View? = target
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }
    for (id in actionIds) {
        val action = toolbar.findViewById<View>(id) ?: continue
        if (actions.any { inChildTree(it, action) }) continue
        // One View cannot have two parents. Move toolbar action containers
        // rather than reproducing business requests with duplicate callbacks.
        (action.parent as? ViewGroup)?.removeView(action)
        actions += action
    }
    // INVISIBLE, not GONE: legacy LinearLayouts still reserve the title
    // height; CoordinatorLayouts still keep their scroll/header contracts.
    toolbar.visibility = View.INVISIBLE

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
                                        val slotWidth = if (action.id == R.id.action_layout &&
                                            ((action as? ViewGroup)?.childCount ?: 0) > 2) 112.dp else 48.dp
                                        AndroidView(
                                            modifier = Modifier.width(slotWidth).height(48.dp),
                                            factory = {
                                                (action.parent as? ViewGroup)?.removeView(action)
                                                action
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
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
