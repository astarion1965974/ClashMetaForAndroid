package com.github.kr328.clash.design.preference

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dialog.showCouiContent
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import kotlin.reflect.KMutableProperty0

/**
 * Native COUI editing surface for nullable override properties. The explicit
 * Reset action restores the unset (null) override; saving an empty string/list
 * keeps a real empty value, matching the core's tri-state override model.
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
        else -> if (multiLine) {
            val length = format(value).lines().count { it.isNotBlank() }
            context.getString(R.string.format_elements, length)
        } else format(value)
    }
    NativeCouiAction(title = title, summary = summary, enabled = enabled) {
        context.showCouiContent { dismiss ->
            var input by remember { mutableStateOf(value?.let(format).orEmpty()) }
            var invalid by remember { mutableStateOf(false) }
            Dialog(
                onDismissRequest = dismiss,
                title = title,
                dismissAction = CouiDialogAction(context.getString(R.string.cancel), dismiss),
                confirmAction = CouiDialogAction(context.getString(R.string.route_save), {
                    try {
                        property.set(parse(input))
                        localVersion++
                        onChanged()
                        dismiss()
                    } catch (_: IllegalArgumentException) {
                        invalid = true
                    }
                }),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it; invalid = false },
                    textStyle = rememberCouiTextStyle(CouiTextKind.Body)
                        .copy(color = CouiTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .heightIn(min = if (multiLine) 120.dp else 48.dp)
                        .background(CouiTheme.colorScheme.background).padding(12.dp),
                    cursorBrush = SolidColor(CouiTheme.colorScheme.accent),
                    minLines = if (multiLine) 5 else 1,
                    maxLines = if (multiLine) 12 else 1,
                    singleLine = !multiLine,
                )
                if (invalid) Text(
                    text = "Invalid value", kind = CouiTextKind.Description,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Item(onClick = {
                    property.set(null)
                    localVersion++
                    onChanged()
                    dismiss()
                }) {
                    Text(text = context.getString(R.string.reset), kind = CouiTextKind.PreferenceTitle)
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
