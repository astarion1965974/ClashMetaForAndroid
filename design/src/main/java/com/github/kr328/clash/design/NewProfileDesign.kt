package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.preference.CouiPreferenceRow

/** Native COUI configuration-source picker; all original import actions are preserved. */
class NewProfileDesign(context: Context) : Design<NewProfileDesign.Request>(context) {
    sealed class Request {
        data class Create(val provider: ProfileProvider) : Request()
        data class OpenDetail(val provider: ProfileProvider.External) : Request()
        data class LaunchScanner(val provider: ProfileProvider.QR) : Request()
    }

    private var sources by mutableStateOf<List<ProfileProvider>>(emptyList())

    override val root: View = ComposeView(context).apply {
        setContent {
            ColorOsLazySubPage(
                title = stringResource(R.string.new_profile),
                onBack = { context.colorOsNavigateBack() },
            ) {
                itemsIndexed(sources, key = { index, provider ->
                    when (provider) {
                        is ProfileProvider.File -> "file"
                        is ProfileProvider.Url -> "url"
                        is ProfileProvider.QR -> "qr"
                        is ProfileProvider.External -> "external-${index}-${provider.name}"
                    }
                }) { _, provider ->
                    CouiPreferenceRow(
                        title = provider.name,
                        summary = provider.summary,
                        icon = provider.icon,
                        enabled = true,
                        onClick = { requestCreate(provider) },
                        onLongClick = if (provider is ProfileProvider.External) {
                            { requestDetail(provider); Unit }
                        } else null,
                    )
                }
            }
        }
    }

    suspend fun patchProviders(providers: List<ProfileProvider>) {
        sources = providers
    }

    private fun requestCreate(provider: ProfileProvider) {
        if (provider is ProfileProvider.QR) {
            requests.trySend(Request.LaunchScanner(provider))
        } else {
            requests.trySend(Request.Create(provider))
        }
    }

    private fun requestDetail(provider: ProfileProvider): Boolean {
        if (provider !is ProfileProvider.External) return false
        requests.trySend(Request.OpenDetail(provider))
        return true
    }
}
