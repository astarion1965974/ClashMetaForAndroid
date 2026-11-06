package com.github.kr328.clash.design

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.util.layoutInflater

class SettingsDesign(context: AppCompatActivity) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
        OpenAppRouting, OpenRouting, OpenLogs, OpenHelp, OpenAbout,
    }

    fun showAbout(versionName: String) {
        val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
            this.versionName = versionName
        }
        AlertDialog.Builder(context).setView(binding.root).show()
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
