package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.dialog.ModelProgressBarConfigure
import com.github.kr328.clash.design.dialog.requestCouiConfirm
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.preference.CouiRichText
import com.github.kr328.clash.design.preference.NativeCouiAction
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Native COUI configuration editor; profile and commit contract retained. */
class PropertiesDesign(context: Context) : Design<PropertiesDesign.Request>(context) {
    sealed class Request {
        object Commit : Request()
        object BrowseFiles : Request()
    }

    private var currentProfile by mutableStateOf<Profile?>(null)
    var profile: Profile
        get() = requireNotNull(currentProfile) { "Profile must be loaded before editing" }
        set(value) {
            currentProfile = value
            onBackStateChanged()
        }

    var progressing by mutableStateOf(false)
        private set

    override val root: View = ComposeView(context).apply {
        setContent {
            val active = currentProfile
            ColorOsSubPage(
                title = stringResource(R.string.properties),
                onBack = { context.colorOsNavigateBack() },
                actions = {
                    if (active != null && !progressing) {
                        ToolbarIcon(
                            ImageVector.vectorResource(R.drawable.ic_baseline_save),
                            stringResource(R.string.save),
                        ) { requestCommit() }
                    }
                },
            ) {
                if (active != null) {
                    CouiRichText(
                        value = context.getHtml(R.string.tips_properties),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                    Group {
                        NativeCouiAction(
                            title = stringResource(R.string.name),
                            summary = active.name.ifBlank { stringResource(R.string.profile_name) },
                            icon = R.drawable.ic_outline_label,
                            enabled = !progressing,
                        ) { inputName() }
                        Divider()
                        NativeCouiAction(
                            title = stringResource(R.string.url),
                            summary = active.source.ifBlank { stringResource(R.string.accept_http_content) },
                            icon = R.drawable.ic_outline_inbox,
                            enabled = !progressing &&
                                active.type != Profile.Type.File && active.type != Profile.Type.External,
                        ) { inputUrl() }
                        Divider()
                        NativeCouiAction(
                            title = stringResource(R.string.age_secret_key),
                            summary = active.ageSecretKey ?: stringResource(R.string.age_secret_key_hint),
                            icon = R.drawable.ic_baseline_key,
                            enabled = !progressing,
                        ) { inputAgeSecretKey() }
                        Divider()
                        NativeCouiAction(
                            title = stringResource(R.string.auto_update),
                            summary = if (active.interval == 0L) stringResource(R.string.disabled)
                                else context.getString(
                                    R.string.format_minutes,
                                    TimeUnit.MILLISECONDS.toMinutes(active.interval),
                                ),
                            icon = R.drawable.ic_outline_update,
                            enabled = !progressing && active.type != Profile.Type.File,
                        ) { inputInterval() }
                    }
                    Group {
                        NativeCouiAction(
                            title = stringResource(R.string.browse_files),
                        navigation = true,
                            summary = stringResource(R.string.browse_configuration_providers),
                            icon = R.drawable.ic_outline_folder,
                            enabled = !progressing,
                        ) { requestBrowseFiles() }
                    }
                }
            }
        }
    }

    suspend fun withProcessing(executeTask: suspend (suspend (FetchStatus) -> Unit) -> Unit) {
        try {
            progressing = true
            onBackStateChanged()
            context.withModelProgressBar {
                configure {
                    isIndeterminate = true
                    text = context.getString(R.string.initializing)
                }
                executeTask { configure { applyFrom(it) } }
            }
        } finally {
            progressing = false
            onBackStateChanged()
        }
    }

    suspend fun requestExitWithoutSaving(): Boolean =
        withContext(Dispatchers.Main) {
            context.requestCouiConfirm(R.string.exit_without_save, R.string.exit_without_save_warning)
        }

    fun inputName() {
        launch {
            val name = context.requestModelTextInput(
                initial = profile.name, title = context.getText(R.string.name),
                hint = context.getText(R.string.properties),
                error = context.getText(R.string.should_not_be_blank),
                validator = ValidatorNotBlank,
            )
            if (name != profile.name) profile = profile.copy(name = name)
        }
    }

    fun inputUrl() {
        if (profile.type == Profile.Type.External || profile.type == Profile.Type.File) return
        launch {
            val url = context.requestModelTextInput(
                initial = profile.source, title = context.getText(R.string.url),
                hint = context.getText(R.string.profile_url),
                error = context.getText(R.string.accept_http_content),
                validator = ValidatorHttpUrl,
            )
            if (url != profile.source) profile = profile.copy(source = url)
        }
    }

    fun inputAgeSecretKey() {
        launch {
            val key = context.requestModelTextInput(
                initial = profile.ageSecretKey ?: "",
                title = context.getText(R.string.age_secret_key),
                hint = context.getText(R.string.age_secret_key_hint),
                error = context.getText(R.string.age_secret_key_error),
                validator = ValidatorAgeSecretKey,
            ).ifBlank { null }
            if (key != profile.ageSecretKey) profile = profile.copy(ageSecretKey = key)
        }
    }

    fun inputInterval() {
        launch {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(profile.interval)
            val newMinutes = context.requestModelTextInput(
                initial = if (minutes == 0L) "" else minutes.toString(),
                title = context.getText(R.string.auto_update),
                hint = context.getText(R.string.auto_update_minutes),
                error = context.getText(R.string.at_least_15_minutes),
                validator = ValidatorAutoUpdateInterval,
            ).toLongOrNull() ?: 0L
            val interval = TimeUnit.MINUTES.toMillis(newMinutes)
            if (interval != profile.interval) profile = profile.copy(interval = interval)
        }
    }

    fun requestCommit() { requests.trySend(Request.Commit) }
    fun requestBrowseFiles() { requests.trySend(Request.BrowseFiles) }

    private fun ModelProgressBarConfigure.applyFrom(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                text = context.getString(R.string.format_fetching_configuration, status.args[0])
                isIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                text = context.getString(R.string.format_fetching_provider, status.args[0])
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
            FetchStatus.Action.SubscriptionInfo -> Unit
            FetchStatus.Action.Verifying -> {
                text = context.getString(R.string.verifying)
                isIndeterminate = false
                max = status.max
                progress = status.progress
            }
        }
    }
}
