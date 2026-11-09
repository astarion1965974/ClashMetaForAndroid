package com.github.kr328.clash.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults

/** A single COUI structure for editable secondary/tertiary pages with a fixed save action. */
@Composable
internal fun ColorOsFormPage(
    title: String,
    saveLabel: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scroll.maxValue > 0 })
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
            topBar = {
                ColorOsLargeTopBar(
                    title = title, scrollBehavior = behavior,
                    navigationIcon = {
                        ToolbarIcon(
                            CouiIcons.Filled.Back,
                            stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            onBack,
                        )
                    },
                )
            },
            bottomBar = {
                val colors = CouiTheme.colorScheme
                Box(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Item(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(CouiPageDefaults.shape)
                            .background(colors.accent.copy(alpha = if (canSave) 1f else .38f)),
                        enabled = canSave,
                        onClick = onSave,
                        contentPadding = PaddingValues(horizontal = CouiPageDefaults.rowPadding, vertical = CouiPageDefaults.contentPadding + 6.dp),
                    ) {
                        Text(
                            text = saveLabel, kind = CouiTextKind.PreferenceTitle,
                            color = colors.onAccent, modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        ) { padding ->
            val scrollPadding = behavior.state.contentPadding(padding, density)
            Column(
                modifier = Modifier.fillMaxSize().couiVerticalScroll(scroll)
                    .padding(scrollPadding).consumeWindowInsets(padding)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.Top,
                content = content,
            )
        }
    }
}

/** Large mutable collections share the same COUI form frame, but virtualize rows. */
@Composable
internal fun ColorOsLazyFormPage(
    title: String,
    saveLabel: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val density = LocalDensity.current
    val state = rememberLazyListState()
    val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { state.canScrollForward || state.canScrollBackward })
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
            topBar = {
                ColorOsLargeTopBar(title = title, scrollBehavior = behavior, navigationIcon = {
                    ToolbarIcon(
                        CouiIcons.Filled.Back,
                        stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onBack,
                    )
                })
            },
            bottomBar = {
                val colors = CouiTheme.colorScheme
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Item(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(CouiPageDefaults.shape)
                            .background(colors.accent.copy(alpha = if (canSave) 1f else .38f)),
                        enabled = canSave, onClick = onSave,
                        contentPadding = PaddingValues(horizontal = CouiPageDefaults.rowPadding, vertical = CouiPageDefaults.contentPadding + 6.dp),
                    ) {
                        Text(
                            text = saveLabel, kind = CouiTextKind.PreferenceTitle,
                            color = colors.onAccent, modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        ) { padding ->
            val scrollingPadding = behavior.state.contentPadding(padding, density)
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = PaddingValues(
                    start = scrollingPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = scrollingPadding.calculateTopPadding(),
                    end = scrollingPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = scrollingPadding.calculateBottomPadding() + CouiPageDefaults.groupSpacing,
                ),
                content = content,
            )
        }
    }
}
