package com.github.kr328.clash.design

import android.graphics.BlendMode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.itos.coui.icons.CouiIcons
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.layout.Scaffold
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import com.itos.coui.topappbar.TopAppBarButton
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.CouiTopAppBarDefaults

/**
 * Bottom primary action for editable ColorOS 17 pages.
 *
 * Uses the same public COUI capsule and press-mask primitives as the alpha8
 * Demo rather than styling a settings Item as a button. One surface, one
 * interactionSource and one Button semantics owner. Kept outside scrolling
 * content in the Scaffold bottomBar slot.
 */
@Composable
internal fun ColorOsSaveAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val fill = if (enabled) colors.accent else colors.onSurface.copy(
        alpha = if (CouiTheme.isDark) 0.14f else 0.10f,
    )
    val labelColor = if (enabled) colors.onAccent else colors.onSurfaceVariant.copy(alpha = 0.64f)
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(
            horizontal = CouiPageDefaults.horizontalMargin,
            vertical = 8.dp,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .clip(CouiCapsuleShape)
                .background(fill)
                .couiButtonFeedback(CouiTheme.isDark, source, enabled)
                .clickable(
                    interactionSource = source, indication = null,
                    enabled = enabled, role = Role.Button, onClick = onClick,
                )
                .testTag("coui-form-save-action"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                kind = CouiTextKind.PreferenceTitle,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A single native COUI alpha9 40dp top-right Save/Save & Reconnect control.
 * No changed fields: neutral translucent toolbar glass, disabled; changed fields:
 * light-blue solid glass and navy icon. Use toolbar shadow/spotlight geometry,
 * NOT the FAB's oversized blue shadow (which caused the previous halo artifact).
 */
@Composable
internal fun ColorOsToolbarSaveAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiTheme.colorScheme
    val density = LocalDensity.current.density
    val blue = if (dark) Color(0xFF9BD5FF) else Color(0xFFC3E6FF)
    val foreground = if (dark) Color(0xFF153E65) else Color(0xFF185A91)
    val renderer = remember { runCatching { CouiFloatingRenderer.standalone() }.getOrNull() }
    val icon = ImageVector.vectorResource(R.drawable.ic_baseline_save)
    if (renderer != null) {
        TopAppBarButton(
            modifier = Modifier.testTag("app-routing-save"),
            onClick = onClick,
            enabled = enabled,
            renderer = renderer,
            style = CouiTopAppBarButtonDefaults.style(dark).copy(
                contentColor = if (enabled) foreground else colors.onSurfaceVariant,
                material = { capsule ->
                    if (enabled) {
                        CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule).copy(
                            firstColor = blue,
                            firstBlendMode = BlendMode.SRC_OVER,
                            secondColor = Color(0x15FFFFFF),
                            secondBlendMode = BlendMode.OVERLAY,
                            blurRadius = 0f,
                            edgeAlpha = 0.16f,
                            innerGlowAlpha = 0.08f,
                        )
                    } else {
                        CouiTopAppBarButtonDefaults.material(dark, capsule)
                    }
                },
                shadow = { capsule ->
                    CouiTopAppBarButtonDefaults.shadow(
                        dark, capsule, density, contour = false,
                        alphaScale = if (enabled) 0.42f else 0.28f,
                    )
                },
            ),
        ) {
            Image(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(LocalCouiContentColor.current),
            )
        }
    } else {
        // Deterministic headless preview only; ColorOS 17 renders native light.
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(if (enabled) blue else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag("app-routing-save"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = icon, contentDescription = label,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(if (enabled) foreground else colors.onSurfaceVariant),
            )
        }
    }
}

/** A single COUI structure for editable secondary/tertiary pages with a fixed save action. */
@Composable
internal fun ColorOsFormPage(
    title: String,
    saveLabel: String,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveInToolbar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val scroll = rememberScrollState()
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ColorOsCompactTopBar(
                    title = title,
                    navigationIcon = {
                        ToolbarIcon(
                            CouiIcons.Filled.Back,
                            stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            onBack,
                        )
                    },
                    actions = {
                        if (saveInToolbar) {
                            ColorOsToolbarSaveAction(
                                label = saveLabel, enabled = canSave, onClick = onSave,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!saveInToolbar) ColorOsSaveAction(label = saveLabel, enabled = canSave, onClick = onSave)
            },
        ) { padding ->
            val scrollPadding = padding
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
    saveInToolbar: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
    val state = rememberLazyListState()
    CouiTheme(darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ColorOsCompactTopBar(
                    title = title,
                    navigationIcon = {
                        ToolbarIcon(
                            CouiIcons.Filled.Back,
                            stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                            onBack,
                        )
                    },
                    actions = {
                        if (saveInToolbar) {
                            ColorOsToolbarSaveAction(
                                label = saveLabel, enabled = canSave, onClick = onSave,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!saveInToolbar) ColorOsSaveAction(label = saveLabel, enabled = canSave, onClick = onSave)
            },
        ) { padding ->
            val scrollingPadding = padding
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = PaddingValues(
                    start = scrollingPadding.calculateLeftPadding(LocalLayoutDirection.current),
                    top = scrollingPadding.calculateTopPadding(),
                    end = scrollingPadding.calculateRightPadding(LocalLayoutDirection.current),
                    bottom = scrollingPadding.calculateBottomPadding() + CouiPageDefaults.groupSpacing,
                ),
                content = content,
            )
        }
    }
}
