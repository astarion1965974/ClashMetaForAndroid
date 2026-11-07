package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.app.Activity
import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.Scaffold
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import com.github.kr328.clash.design.util.resolveThemedBoolean
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.adapter.ProfileListItem
import com.github.kr328.clash.design.databinding.DesignProfilesBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.databinding.DialogProfileQrCodeBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.dialog.CouiMenuOption
import com.github.kr328.clash.design.dialog.showCouiMenu
import com.github.kr328.clash.design.dialog.showCouiContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog as CouiDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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

    private val binding = DesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = ProfileAdapter(context, this::requestActive, this::showMenu)

    private var allUpdating: Boolean
        get() = adapter.states.allUpdating;
        set(value) {
            adapter.states.allUpdating = value
        }
    private val rotateAnimation : Animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)
    private var canUpdate by mutableStateOf(false)
    private var visibleProfiles by mutableStateOf<List<Profile>>(emptyList())
    private var displayedTime by mutableLongStateOf(System.currentTimeMillis())

    // The existing RecyclerView stays as content under a real native COUI
    // Scaffold. Its toolbar and navigation now sample the same page source.
    override val root: View = ComposeView(context).apply {
        setContent {
            CouiTheme(darkTheme = !context.resolveThemedBoolean(androidx.appcompat.R.attr.isLightTheme)) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(text = context.getString(R.string.profiles), kind = CouiTextKind.Toolbar)
                            },
                            navigationIcon = {
                                ToolbarIcon(CouiIcons.Back,
                                    context.getString(androidx.appcompat.R.string.abc_action_bar_up_description)) {
                                    (context as? AppCompatActivity)?.onSupportNavigateUp()
                                        ?: (context as? Activity)?.finish()
                                }
                            },
                            actions = {
                                if (canUpdate) {
                                    ToolbarIcon(ImageVector.vectorResource(R.drawable.ic_baseline_sync),
                                        context.getString(R.string.update)) { requestUpdateAll() }
                                }
                                ToolbarIcon(ImageVector.vectorResource(R.drawable.ic_baseline_add),
                                    context.getString(R.string._new)) { requestCreate() }
                            },
                        )
                    },
                    bottomBar = {
                        PrimaryBottomBar(selected = 1, modifier = Modifier.testTag("profiles_navigation")) { index ->
                            when (index) {
                                0 -> requests.trySend(Request.OpenHome)
                                2 -> requests.trySend(Request.OpenSettings)
                            }
                        }
                    },
                ) {
                    // Keep the content behind native COUI floating surfaces.
                    // The recycler's last-row decoration avoids hit overlap.
                    AndroidView(factory = { binding.root }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Render COUI profile rows within the main tab's single Scaffold. */
    @androidx.compose.runtime.Composable
    internal fun PrimaryTabContent(padding: PaddingValues) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(visibleProfiles, key = { it.uuid.toString() }) { profile ->
                ProfileListItem(profile, displayedTime, ::requestActive, ::showMenu)
            }
        }
    }

    @androidx.compose.runtime.Composable
    internal fun PrimaryTabActions() {
        if (canUpdate) {
            ToolbarIcon(
                ImageVector.vectorResource(R.drawable.ic_baseline_sync),
                context.getString(R.string.update),
            ) { requestUpdateAll() }
        }
        ToolbarIcon(
            ImageVector.vectorResource(R.drawable.ic_baseline_add),
            context.getString(R.string._new),
        ) { requestCreate() }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        adapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            canUpdate = updatable
            visibleProfiles = profiles
            binding.updateView.visibility = if (updatable) View.VISIBLE else View.GONE
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() {
        adapter.updateElapsed()
        displayedTime = System.currentTimeMillis()
    }

    init {
        binding.self = this

        binding.activityBarLayout.visibility = View.GONE

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
            it.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State,
                ) {
                    if (parent.getChildAdapterPosition(view) == (parent.adapter?.itemCount ?: 0) - 1) {
                        outRect.bottom = (104f * context.resources.displayMetrics.density).toInt()
                    }
                }
            })
        }
    }

    private fun showMenu(profile: Profile) {
        val options = mutableListOf<CouiMenuOption>()
        fun add(res: Int, request: Request) { options.add(CouiMenuOption(context.getString(res)) { requests.trySend(request) }) }
        if (profile.imported && profile.type != Profile.Type.File) add(R.string.update, Request.Update(profile))
        add(R.string.edit, Request.Edit(profile))
        if (profile.imported) add(R.string.duplicate, Request.Duplicate(profile))
        if (profile.imported && profile.type == Profile.Type.Url && profile.source.isNotEmpty()) add(R.string.share_qr_code, Request.ShareQrCode(profile))
        add(R.string.delete, Request.Delete(profile))
        context.showCouiMenu(options)
    }

    fun requestUpdateAll() {
        allUpdating = true;
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false;
        changeUpdateAllButtonStatus()
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))

        dialog.dismiss()
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))

        dialog.dismiss()
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))

        dialog.dismiss()
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))

        dialog.dismiss()
    }

    fun requestShareQrCode(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.ShareQrCode(profile))

        dialog.dismiss()
    }

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

    private fun changeUpdateAllButtonStatus() {
        if (allUpdating) {
            binding.updateView.startAnimation(rotateAnimation)
        } else {
            binding.updateView.clearAnimation()
        }
    }
}
