package com.github.kr328.clash.design.preference

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiContent
import com.itos.coui.`switch`.Switch
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

/** Native Compose preferences: no per-row ComposeView, RecyclerView, or XML layouts. */
@Composable
internal fun NativeCouiAction(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    val colors = CouiTheme.colorScheme
    Item(
        enabled = enabled, onClick = onClick,
        icon = icon?.let { id -> { NativePreferenceIcon(id) } },
        summary = summary?.let { text -> {
            Text(text = text, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
        } },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
    }
}

@Composable
private fun NativePreferenceIcon(@DrawableRes icon: Int) {
    Image(
        painter = painterResource(icon), contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurfaceVariant),
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
    var checked by remember(value) { mutableStateOf(value.get()) }
    var saving by remember(value) { mutableStateOf(false) }
    val change: (Boolean) -> Unit = { next ->
        if (enabled && !saving && checked != next) {
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
    val active = enabled && !saving
    val colors = CouiTheme.colorScheme
    Item(
        enabled = active, onClick = { change(!checked) },
        icon = icon?.let { id -> { NativePreferenceIcon(id) } },
        summary = summary?.let { text -> {
            Text(text = text, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
        } },
        trailing = {
            Switch(checked = checked, onCheckedChange = change, enabled = active)
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
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
    val colors = CouiTheme.colorScheme
    Item(
        enabled = enabled && !saving,
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
        icon = icon?.let { id -> { NativePreferenceIcon(id) } },
        summary = {
            val label = labels.getOrNull(values.indexOf(selected)).orEmpty()
            Text(text = label, kind = CouiTextKind.PreferenceSummary, color = colors.onSurfaceVariant)
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
    }
}
