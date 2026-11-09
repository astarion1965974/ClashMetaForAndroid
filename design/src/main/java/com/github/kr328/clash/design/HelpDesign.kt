package com.github.kr328.clash.design

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.preference.CouiRichText
import com.github.kr328.clash.design.util.getHtml
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text

/** Fully native COUI help page; no XML preference rows or AndroidView bridge. */
class HelpDesign(
    context: Context,
    openLink: (Uri) -> Unit,
) : Design<Unit>(context) {
    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsSubPage(
                title = stringResource(R.string.help),
                onBack = { context.colorOsNavigateBack() },
            ) {
                CouiRichText(
                    value = context.getHtml(R.string.tips_help),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Group(title = stringResource(R.string.document)) {
                    HelpLink(
                        title = stringResource(R.string.clash_wiki),
                        url = stringResource(R.string.clash_wiki_url),
                        onOpen = openLink,
                    )
                    SettingDivider()
                    HelpLink(
                        title = stringResource(R.string.clash_meta_wiki),
                        url = stringResource(R.string.clash_meta_wiki_url),
                        onOpen = openLink,
                    )
                }
                Group(title = stringResource(R.string.sources)) {
                    HelpLink(
                        title = stringResource(R.string.clash_meta_core),
                        url = stringResource(R.string.clash_meta_core_url),
                        onOpen = openLink,
                    )
                    SettingDivider()
                    HelpLink(
                        title = stringResource(R.string.clash_meta_for_android),
                        url = stringResource(R.string.meta_github_url),
                        onOpen = openLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpLink(title: String, url: String, onOpen: (Uri) -> Unit) {
    Item(
        onClick = { onOpen(Uri.parse(url)) },
        summary = {
            Text(text = url, kind = CouiTextKind.PreferenceSummary)
        },
    ) {
        Text(text = title, kind = CouiTextKind.PreferenceTitle)
    }
}
