package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** COUI error and recovery page; retaining the original request channel. */
class ApkBrokenDesign(context: Context) : Design<ApkBrokenDesign.Request>(context) {
    data class Request(val url: String)

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsSubPage(
                title = (context as? AppCompatActivity)?.title?.toString()
                    ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.application_name),
                onBack = { context.colorOsNavigateBack() },
            ) {
                Text(
                    text = stringResource(R.string.application_broken_tips),
                    kind = CouiTextKind.PreferenceSummary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Group(title = stringResource(R.string.reinstall)) {
                    Item(
                        onClick = {
                            requests.trySend(Request(context.getString(R.string.meta_github_url)))
                        },
                        summary = {
                            Text(text = stringResource(R.string.meta_github_url),
                                kind = CouiTextKind.PreferenceSummary)
                        },
                    ) {
                        Text(text = stringResource(R.string.github_releases),
                            kind = CouiTextKind.PreferenceTitle)
                    }
                }
            }
        }
    }
}
