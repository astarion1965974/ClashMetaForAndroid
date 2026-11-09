package com.github.kr328.clash.design.preference

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiContent
import com.itos.coui.`switch`.Switch
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

/**
 * Shared COUI-Kit alpha8 settings rows.
 * One icon slot, native disabled colours and stable navigation/value affordances
 * are used on all settings pages; business callbacks remain owned by the caller.
 */
@Composable
internal fun NativeCouiAction(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    navigation: Boolean = false,
    trailingAction: (() -> Unit)? = null,
    trailingDescription: String? = null,
    onClick: () -> Unit,
) {
    val colors = CouiPageDefaults.themeColors()
    Item(
        enabled = enabled,
        onClick = onClick,
        icon = icon?.let { id -> { NativePreferenceIcon(id, enabled) } },
        summary = summary?.let { text -> {
            Text(text = text, kind = CouiTextKind.PreferenceSummary,
                color = if (enabled) colors.summary else colors.disabled,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        } },
        trailing = {
            if (trailingAction != null) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .testTag("coui-setting-reset")
                        .clickable(enabled = enabled, role = Role.Button, onClick = trailingAction)
                        .then(if (trailingDescription != null) Modifier.semantics {
                            contentDescription = trailingDescription
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_baseline_replay),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(if (enabled) colors.summary else colors.disabled),
                    )
                }
            }
            if (navigation) NativePreferenceChevron(enabled)
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle,
            color = if (enabled) colors.title else colors.disabled)
    }
}

@Composable
private fun NativePreferenceIcon(@DrawableRes icon: Int, enabled: Boolean) {
    Image(
        painter = painterResource(icon), contentDescription = null,
        modifier = Modifier.size(CouiPageDefaults.iconSize),
        colorFilter = ColorFilter.tint(
            if (enabled) CouiTheme.colorScheme.onSurfaceVariant
            else CouiPageDefaults.themeColors().disabled,
        ),
    )
}

@Composable
private fun NativePreferenceChevron(enabled: Boolean) {
    val colors = CouiPageDefaults.themeColors()
    Image(
        painter = painterResource(R.drawable.ic_live_chevron),
        contentDescription = null,
        modifier = Modifier.padding(start = 8.dp).size(16.dp).testTag("coui-setting-chevron"),
        colorFilter = ColorFilter.tint(if (enabled) colors.summary else colors.disabled),
    )
}

@Composable
internal fun NativeCouiSwitch(
    title: String,
    summary: String? = null,
    value: KMutableProperty0<Boolean>,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    var checked by remember(value) { mutableStateOf(value.get()) }
    var saving by remember(value) { mutableStateOf(false) }
    val active = enabled && !saving
    val colors = CouiPageDefaults.themeColors()
    val change: (Boolean) -> Unit = { next ->
        if (active && checked != next) {
            val previous = checked
            checked = next
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { value.set(next) }
                    onChanged(next)
                } catch (error: Exception) {
                    checked = previous
                } finally {
                    saving = false
                }
            }
        }
    }
    Item(
        modifier = Modifier
            .couiButtonFeedback(CouiTheme.isDark, source, active, list = true)
            .toggleable(checked, source, null, active, Role.Switch) { next ->
                view.performCouiHapticFeedback(CouiHapticFeedback.Switch)
                change(next)
            },
        enabled = active,
        icon = icon?.let { id -> { NativePreferenceIcon(id, active) } },
        summary = summary?.let { text -> {
            Text(text = text, kind = CouiTextKind.PreferenceSummary,
                color = if (active) colors.summary else colors.disabled,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        } },
        trailing = {
            // The row owns click semantics; the switch only handles drag gestures.
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = active,
                interactionSource = source,
                onDragCheckedChange = change,
            )
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle,
            color = if (active) colors.title else colors.disabled)
    }
}

@Composable
internal fun <T> NativeCouiSelect(
    title: String,
    value: KMutableProperty0<T>,
    values: List<T>,
    labels: List<String>,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onChanged: (T) -> Unit = {},
) {
    require(values.isNotEmpty() && values.size == labels.size)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(value) { mutableStateOf(value.get()) }
    var saving by remember(value) { mutableStateOf(false) }
    val active = enabled && !saving
    val colors = CouiPageDefaults.themeColors()
    val currentValue = labels.getOrNull(values.indexOf(selected)) ?: selected?.toString().orEmpty()
    Item(
        enabled = active,
        onClick = {
            context.showCouiContent { dismiss ->
                Dialog(
                    onDismissRequest = dismiss, title = title,
                    confirmAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                ) {
                    values.forEachIndexed { index, choice ->
                        Item(
                            onClick = {
                                dismiss()
                                if (!saving && choice != selected) {
                                    saving = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { value.set(choice) }
                                            selected = choice
                                            onChanged(choice)
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            },
                            trailing = { RadioButton(selected = choice == selected) },
                        ) {
                            Text(text = labels[index], kind = CouiTextKind.PreferenceTitle)
                        }
                    }
                }
            }
        },
        icon = icon?.let { id -> { NativePreferenceIcon(id, active) } },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentValue,
                    kind = CouiTextKind.PreferenceValue,
                    color = if (active) colors.summary else colors.disabled,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 152.dp).padding(start = 12.dp),
                )
                NativePreferenceChevron(active)
            }
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle,
            color = if (active) colors.title else colors.disabled)
    }
}
