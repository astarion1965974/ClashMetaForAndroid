package com.github.kr328.clash.design

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.dialog.showCouiAlert

class SettingsDesign(context: AppCompatActivity) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
        OpenAppRouting, OpenRouting, OpenLogs, OpenHelp, OpenAbout,
        OpenHome, OpenProfiles,
    }

    fun showAbout(versionName: String) {
        context.showCouiAlert(context.getText(R.string.application_name), versionName)
    }

    override val root: View = ComposeView(context).apply {
        setContent {
            ClashSettings(
                onBack = { context.onSupportNavigateUp() },
                onRequest = ::request,
            )
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
