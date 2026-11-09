package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.HomeDnsTargets
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.NetworkPrivacyDiagnostics
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import com.github.kr328.clash.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    private var activeProfilesDesign: ProfilesDesign? = null

    override suspend fun main() {
        val design = MainDesign(this)
        // Three main tabs now share one window and one native COUI glass bar.
        // Keep the original Design controllers and their request channels so
        // profile, home and settings business actions still behave identically.
        val profilesDesign = ProfilesDesign(this)
        activeProfilesDesign = profilesDesign
        val settingsDesign = SettingsDesign(this)
        design.installPrimaryTabs(profilesDesign, settingsDesign)
        interceptBack(design, { design.selectedPrimaryTab != 0 }) {
            design.selectPrimaryTab(0)
        }

        setContentDesign(design)

        design.fetch()
        profilesDesign.fetch()

        val profileTicker = ticker(60_000L)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileLoaded, Event.ProfileChanged -> {
                            design.fetch()
                            profilesDesign.fetch()
                        }
                        Event.ServiceRecreated, Event.ClashStop, Event.ClashStart -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            design.selectPrimaryTab(1)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenSettings ->
                            design.selectPrimaryTab(2)
                        MainDesign.Request.CheckDns -> launch { design.checkDns() }
                        MainDesign.Request.CheckPrivacy -> launch { design.checkPrivacy() }
                        MainDesign.Request.OpenIpleak -> startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://ipleak.net/"))
                        )
                    }
                }
                profilesDesign.requests.onReceive { action ->
                    when (action) {
                        ProfilesDesign.Request.OpenHome -> design.selectPrimaryTab(0)
                        ProfilesDesign.Request.OpenSettings -> design.selectPrimaryTab(2)
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { profile ->
                                        if (profile.imported && profile.type != Profile.Type.File)
                                            update(profile.uuid)
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) { profilesDesign.finishUpdateAll() }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(action.profile.uuid) }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(action.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(action.profile.uuid))
                        is ProfilesDesign.Request.Active ->
                            withProfile {
                                if (action.profile.imported) setActive(action.profile)
                                else profilesDesign.requestSave(action.profile)
                            }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(action.profile.uuid) }
                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        is ProfilesDesign.Request.ShareQrCode ->
                            profilesDesign.showProfileQrCode(action.profile)
                    }
                }
                settingsDesign.requests.onReceive { action ->
                    when (action) {
                        SettingsDesign.Request.OpenHome -> design.selectPrimaryTab(0)
                        SettingsDesign.Request.OpenProfiles -> design.selectPrimaryTab(1)
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                        SettingsDesign.Request.OpenAppRouting ->
                            startActivity(AccessControlActivity::class.intent)
                        SettingsDesign.Request.OpenRouting ->
                            startActivity(ManagedRoutingActivity::class.intent)
                        SettingsDesign.Request.OpenLogs -> {
                            val page = if (LogcatService.running)
                                LogcatActivity::class else LogsActivity::class
                            startActivity(page.intent)
                        }
                        SettingsDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        SettingsDesign.Request.OpenAbout ->
                            settingsDesign.showAbout(queryAppVersionName())
                    }
                }
                if (activityStarted && design.selectedPrimaryTab == 1) {
                    profileTicker.onReceive { profilesDesign.updateElapsed() }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile { patchProfiles(queryAll()) }
    }

    private suspend fun queryAppVersionName(): String = withContext(Dispatchers.IO) {
        packageManager.getPackageInfo(packageName, 0).versionName + "\n" +
            Bridge.nativeCoreVersion().replace("_", "-")
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)
        setHomePaused(com.github.kr328.clash.remote.StatusClient(this@MainActivity).homePaused())

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile { setProfile(queryActive()) }
    }

    private suspend fun MainDesign.checkDns() {
        if (!clashRunning || pausedAtHome) return
        updateDnsLoading(true)
        try {
            val results = coroutineScope {
                HomeDnsTargets.domains.map { domain ->
                    async { domain to runCatching { withClash { queryDnsDiagnostic(domain) } }.getOrDefault("{}") }
                }.awaitAll()
            }
            if (clashRunning && !pausedAtHome) setDnsResults(results)
        } finally {
            updateDnsLoading(false)
        }
    }

    /** User-initiated third-party checks, never scheduled as a background probe. */
    private suspend fun MainDesign.checkPrivacy() {
        if (!clashRunning || pausedAtHome) return
        updatePrivacyLoading(true)
        try {
            val result = NetworkPrivacyDiagnostics.check()
            if (clashRunning && !pausedAtHome) setPrivacyResult(result)
        } finally {
            updatePrivacyLoading(false)
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(DesignR.string.no_profile_selected, ToastDuration.Long) {
                setAction(DesignR.string.profiles) {
                    selectPrimaryTab(1)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(DesignR.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    override fun onProfileUpdateCompleted(uuid: java.util.UUID?) {
        super.onProfileUpdateCompleted(uuid)
        if (uuid == null) return
        launch {
            val name = withProfile { queryByUUID(uuid)?.name }
            val batch = activeProfilesDesign?.recordUpdateResult(uuid, null)
            when {
                batch == null -> design?.showToast(
                    getString(DesignR.string.toast_profile_updated_complete, name),
                    ToastDuration.Short,
                )
                batch.complete -> design?.showToast(
                    getString(DesignR.string.coui_profile_update_summary, batch.succeeded, batch.failed),
                    ToastDuration.Short,
                )
            }
        }
    }

    override fun onProfileUpdateFailed(uuid: java.util.UUID?, reason: String?) {
        super.onProfileUpdateFailed(uuid, reason)
        if (uuid == null) return
        launch {
            val name = withProfile { queryByUUID(uuid)?.name }
            val batch = activeProfilesDesign?.recordUpdateResult(
                uuid, reason ?: getString(DesignR.string.coui_profile_update_failed)
            )
            when {
                batch == null -> design?.showToast(
                    getString(DesignR.string.toast_profile_updated_failed, name, reason),
                    ToastDuration.Long,
                ) {
                    setAction(DesignR.string.edit) {
                        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                    }
                }
                batch.complete -> design?.showToast(
                    getString(DesignR.string.coui_profile_update_summary, batch.succeeded, batch.failed),
                    ToastDuration.Short,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setupShortcuts()
    }

    private fun setupShortcuts() {
        // Skip dynamic shortcut setup when the app icon is hidden.
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}
