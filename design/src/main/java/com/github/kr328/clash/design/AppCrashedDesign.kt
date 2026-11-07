package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** Native selectable crash logs inside the same COUI subpage shell. */
class AppCrashedDesign(context: Context) : Design<Unit>(context) {
    private var crashLogsText by mutableStateOf("")

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsSubPage(
                title = (context as? AppCompatActivity)?.title?.toString()
                    ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.application_name),
                onBack = { (context as? AppCompatActivity)?.onSupportNavigateUp() },
            ) {
                SelectionContainer {
                    Text(
                        text = crashLogsText,
                        kind = CouiTextKind.Body,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    fun setAppLogs(logs: String) {
        crashLogsText = logs
    }
}
