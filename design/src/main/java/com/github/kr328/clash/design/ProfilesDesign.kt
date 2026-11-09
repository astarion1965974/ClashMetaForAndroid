package com.github.kr328.clash.design

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.adapter.ProfileListItem
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiContent
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.resolveThemedBoolean
import com.github.kr328.clash.service.model.Profile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog as CouiDialog
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Both standalone and primary configuration tabs now share pure COUI list rows. */
class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object OpenHome : Request()
        object OpenSettings : Request()
        object UpdateAll : Request()
        object Create : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class ShareQrCode(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    private var allUpdating by mutableStateOf(false)
    private var canUpdate by mutableStateOf(false)
    private var visibleProfiles by mutableStateOf<List<Profile>>(emptyList())
    private var displayedTime by mutableLongStateOf(System.currentTimeMillis())

    override val root: View = ComposeView(context).apply {
        setContent {
            val dark = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)
            val listState = rememberLazyListState()
            val behavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                canScroll = { listState.canScrollForward || listState.canScrollBackward })
            val density = LocalDensity.current
            CouiTheme(darkTheme = dark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
                    topBar = {
                        ColorOsLargeTopBar(
                            title = context.getString(R.string.profiles),
                            scrollBehavior = behavior,
                            navigationIcon = {
                                ToolbarIcon(
                                    CouiIcons.Filled.Back,
                                    context.getString(androidx.appcompat.R.string.abc_action_bar_up_description),
                                ) {
                                    context.colorOsNavigateBack()
                                        ?: (context as? Activity)?.finish()
                                }
                            },
                            actions = { PrimaryTabActions() },
                        )
                    },
                    bottomBar = {
                        PrimaryBottomBar(selected = 1) { index ->
                            when (index) {
                                0 -> requests.trySend(Request.OpenHome)
                                2 -> requests.trySend(Request.OpenSettings)
                            }
                        }
                    },
                ) { padding ->
                    PrimaryTabContent(padding, behavior.state.contentPadding(padding, density), listState)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    internal fun PrimaryTabContent(
        padding: PaddingValues,
        contentPadding: PaddingValues,
        listState: LazyListState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = contentPadding.calculateLeftPadding(LocalLayoutDirection.current),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateRightPadding(LocalLayoutDirection.current),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(visibleProfiles, key = { it.uuid.toString() }) { profile ->
                ProfileListItem(profile, displayedTime, ::requestActive, ::showMenu, menuOptions = ::menuOptions)
            }
        }
    }

    @androidx.compose.runtime.Composable
    internal fun PrimaryTabActions() {
        if (allUpdating) {
            Text(
                text = context.getString(R.string.coui_provider_updating),
                kind = CouiTextKind.PreferenceSummary,
            )
        } else if (canUpdate) {
            ToolbarIcon(ImageVector.vectorResource(R.drawable.ic_baseline_sync),
                context.getString(R.string.update)) { requestUpdateAll() }
        }
        ToolbarIcon(ImageVector.vectorResource(R.drawable.ic_baseline_add),
            context.getString(R.string._new)) { requestCreate() }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }
        withContext(Dispatchers.Main) {
            canUpdate = updatable
            visibleProfiles = profiles
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() { displayedTime = System.currentTimeMillis() }

    private fun menuOptions(profile: Profile): List<CouiMenuOption> {
        val options = mutableListOf<CouiMenuOption>()
        fun add(res: Int, request: Request) {
            options.add(CouiMenuOption(context.getString(res)) { requests.trySend(request) })
        }
        if (profile.imported && profile.type != Profile.Type.File)
            add(R.string.update, Request.Update(profile))
        add(R.string.edit, Request.Edit(profile))
        if (profile.imported) add(R.string.duplicate, Request.Duplicate(profile))
        if (profile.imported && profile.type == Profile.Type.Url && profile.source.isNotEmpty())
            add(R.string.share_qr_code, Request.ShareQrCode(profile))
        add(R.string.delete, Request.Delete(profile))
        return options
    }


    private fun showMenu(profile: Profile) {
        // Legacy test callback retained; production overflows use anchored COUI Menu.
        menuOptions(profile)
    }

    fun requestUpdateAll() {
        if (allUpdating) return
        allUpdating = true
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() { allUpdating = false }
    fun requestCreate() { requests.trySend(Request.Create) }
    private fun requestActive(profile: Profile) { requests.trySend(Request.Active(profile)) }

    suspend fun showProfileQrCode(profile: Profile) {
        if (profile.type != Profile.Type.Url || profile.source.isBlank()) return

        val bitmap = try {
            withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(
                    profile.source, BarcodeFormat.QR_CODE, 768, 768,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
                )
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (_: WriterException) {
            showToast(R.string.qr_code_generation_failed, ToastDuration.Long)
            return
        }

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val close = context.showCouiContent { dismiss ->
                    val finish = {
                        dismiss()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    CouiDialog(onDismissRequest = finish, title = context.getString(R.string.share_qr_code),
                        confirmAction = CouiDialogAction(context.getString(R.string.close), finish)) {
                        Image(bitmap.asImageBitmap(), contentDescription = context.getString(R.string.share_qr_code),
                            modifier = Modifier.fillMaxWidth().padding(24.dp).height(240.dp).background(androidx.compose.ui.graphics.Color.White),
                            contentScale = ContentScale.Fit)
                    }
                }
                continuation.invokeOnCancellation { close() }
            }
        }
    }


}
