package com.github.kr328.clash.design.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiContent
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.dialog.InputDialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.reflect.KMutableProperty0

/**
 * alpha8's native InputDialog is used for single-line properties. Long list/map
 * editors keep a scrollable multiline COUI dialog and the existing Reset action.
 * Both editors preserve the three-way unset/empty/value override contract.
 */
@Composable
internal fun <T : Any> NativeCouiValueEditor(
    title: String,
    property: KMutableProperty0<T?>,
    parse: (String) -> T,
    format: (T) -> String,
    placeholder: String,
    emptyLabel: String = "",
    enabled: Boolean = true,
    multiLine: Boolean = false,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    var localVersion by remember(property) { mutableIntStateOf(0) }
    localVersion
    val value = property.get()
    val summary = when {
        value == null -> placeholder
        format(value).isEmpty() -> emptyLabel
        multiLine -> {
            val length = format(value).lines().count { it.isNotBlank() }
            context.getString(R.string.format_elements, length)
        }
        else -> format(value)
    }
    val reset: () -> Unit = {
        property.set(null)
        localVersion++
        onChanged()
    }
    NativeCouiAction(
        title = title,
        summary = summary,
        enabled = enabled,
        trailingAction = if (enabled && !multiLine && value != null) reset else null,
        trailingDescription = context.getString(R.string.coui_editor_reset_override),
    ) {
        context.showCouiContent { dismiss ->
            var input by remember { mutableStateOf(TextFieldValue(value?.let(format).orEmpty())) }
            var invalid by remember { mutableStateOf(false) }
            val save = {
                try {
                    property.set(parse(input.text))
                    localVersion++
                    onChanged()
                    dismiss()
                } catch (_: IllegalArgumentException) {
                    invalid = true
                }
            }
            if (!multiLine) {
                InputDialog(
                    value = input,
                    onValueChange = { input = it; invalid = false },
                    onDismissRequest = dismiss,
                    title = title,
                    placeholder = placeholder,
                    message = if (invalid) context.getString(R.string.coui_editor_invalid_value) else null,
                    clearContentDescription = context.getString(R.string.coui_editor_clear_input),
                    confirmAction = CouiDialogAction(context.getString(R.string.route_save), save),
                    dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                )
            } else {
                Dialog(
                    onDismissRequest = dismiss,
                    title = title,
                    dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                    confirmAction = CouiDialogAction(context.getString(R.string.route_save), save),
                ) {
                    val shape = RoundedCornerShape(18.dp)
                    val colors = CouiPageDefaults.themeColors()
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it; invalid = false },
                        textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = colors.title),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            .heightIn(min = 120.dp)
                            .clip(shape).background(colors.card)
                            .border(1.dp, colors.divider, shape),
                        cursorBrush = SolidColor(CouiTheme.colorScheme.accent),
                        minLines = 5,
                        maxLines = 12,
                        singleLine = false,
                        decorationBox = { inner ->
                            Box(Modifier.padding(12.dp)) {
                                if (input.text.isEmpty()) Text(
                                    text = placeholder,
                                    kind = CouiTextKind.PreferenceSummary,
                                    color = colors.summary,
                                )
                                inner()
                            }
                        },
                    )
                    if (invalid) Text(
                        text = context.getString(R.string.coui_editor_invalid_value),
                        kind = CouiTextKind.Description,
                        color = CouiTheme.colorScheme.accent,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Item(onClick = { reset(); dismiss() }) {
                        Text(text = context.getString(R.string.reset), kind = CouiTextKind.PreferenceTitle)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeCouiNullableString(
    title: String,
    property: KMutableProperty0<String?>,
    placeholder: String,
    emptyLabel: String = "",
    enabled: Boolean = true,
    onChanged: () -> Unit = {},
) = NativeCouiValueEditor(
    title = title, property = property,
    parse = { it.trim() }, format = { it },
    placeholder = placeholder, emptyLabel = emptyLabel, enabled = enabled,
    onChanged = onChanged,
)

@Composable
internal fun NativeCouiNullablePort(
    title: String,
    property: KMutableProperty0<Int?>,
    placeholder: String,
    emptyLabel: String,
    enabled: Boolean = true,
    onChanged: () -> Unit = {},
) = NativeCouiValueEditor(
    title = title, property = property,
    parse = {
        if (it.isBlank()) 0
        else (it.trim().toIntOrNull()?.takeIf { number -> number in 0..65535 }
            ?: throw IllegalArgumentException("Invalid port"))
    },
    format = { if (it == 0) "" else it.toString() },
    placeholder = placeholder, emptyLabel = emptyLabel, enabled = enabled,
    onChanged = onChanged,
)

@Composable
internal fun NativeCouiStringList(
    title: String,
    property: KMutableProperty0<List<String>?>,
    placeholder: String,
    enabled: Boolean = true,
    onChanged: () -> Unit = {},
) = NativeCouiValueEditor(
    title = title, property = property,
    parse = { it.lines().map(String::trim).filter(String::isNotBlank) },
    format = { it.joinToString("\n") },
    placeholder = placeholder, emptyLabel = "", enabled = enabled,
    multiLine = true, onChanged = onChanged,
)

@Composable
internal fun NativeCouiStringMap(
    title: String,
    property: KMutableProperty0<Map<String, String>?>,
    placeholder: String,
    enabled: Boolean = true,
    onChanged: () -> Unit = {},
) = NativeCouiValueEditor(
    title = title, property = property,
    parse = { input ->
        input.lines().map(String::trim).filter(String::isNotBlank).associate { row ->
            val separator = row.indexOf('=')
            require(separator > 0) { "Expected key=value" }
            row.take(separator).trim().also { require(it.isNotBlank()) } to
                row.drop(separator + 1).trim()
        }
    },
    format = { map -> map.entries.joinToString("\n") { (key, value) -> "${key}=${value}" } },
    placeholder = placeholder, emptyLabel = "", enabled = enabled,
    multiLine = true, onChanged = onChanged,
)
