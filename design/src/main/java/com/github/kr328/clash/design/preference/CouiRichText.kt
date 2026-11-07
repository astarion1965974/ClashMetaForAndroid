package com.github.kr328.clash.design.preference

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme

/** Preserve Android HTML bold/italic spans while drawing text with COUI typography. */
@Composable
internal fun CouiRichText(value: CharSequence?, modifier: Modifier = Modifier) {
    val annotated = remember(value) {
        buildAnnotatedString {
            append(value?.toString().orEmpty())
            if (value is Spanned) {
                value.getSpans(0, value.length, StyleSpan::class.java).forEach { span ->
                    val start = value.getSpanStart(span)
                    val end = value.getSpanEnd(span)
                    if (start in 0 until end && end <= value.length) {
                        if (span.style and Typeface.BOLD != 0) {
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        }
                        if (span.style and Typeface.ITALIC != 0) {
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        }
                    }
                }
            }
        }
    }
    BasicText(
        text = annotated,
        modifier = modifier,
        style = rememberCouiTextStyle(CouiTextKind.PreferenceSummary).copy(
            color = CouiTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
