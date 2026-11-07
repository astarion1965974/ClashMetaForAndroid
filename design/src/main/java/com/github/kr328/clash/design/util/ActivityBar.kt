package com.github.kr328.clash.design.util

import android.app.Activity
import androidx.activity.OnBackPressedDispatcherOwner
import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ToolbarIcon
import com.github.kr328.clash.design.view.ActivityBarLayout
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import com.itos.coui.topappbar.CouiTopAppBarDefaults

fun ActivityBarLayout.applyFrom(context: Context) {
    if (context is Activity) {
        findViewById<ImageView>(R.id.activity_bar_close_view)?.apply {
            setOnClickListener {
                (context as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher?.onBackPressed()
                    ?: context.finish()
            }
        }
        findViewById<TextView>(R.id.activity_bar_title_view)?.apply {
            text = context.title
        }
        findViewById<ComposeView>(R.id.coui_activity_bar)?.setContent {
            val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
            val screenWidth = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1).dp
            CouiTheme(darkTheme = dark) {
                Scaffold(
                    modifier = Modifier.widthIn(max = screenWidth).height(56.dp),
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        TopAppBar(
                            colors = CouiTopAppBarDefaults.colors(backgroundColor = Color.Transparent),
                            windowInsets = WindowInsets(0, 0, 0, 0),
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
                                    onClick = { findViewById<ImageView>(R.id.activity_bar_close_view)?.performClick() },
                                )
                            },
                        )
                    },
                ) { }
            }
        }
    }
}
