package com.github.kr328.clash.design.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/** A read-only destination snapshot. No navigation happens until commit. */
class DirectoryBackCallback(
    private val current: () -> View,
    private val destination: () -> ByteArray?,
    private val pop: () -> Unit,
) : OnBackPressedCallback(false), DefaultLifecycleObserver {
    private var view: View? = null
    private var preview: ImageView? = null
    private var bitmap: Bitmap? = null
    private var translation = 0f
    private var scaleX = 1f
    private var scaleY = 1f
    private var direction = 1f
    private var pending = false

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        if (pending) return
        complete()
        val bytes = destination() ?: return
        val foreground = current()
        // The destination must overlay, rather than change a LinearLayout's child positions.
        val parent = foreground.parent as? FrameLayout ?: return
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        bitmap = decoded
        view = foreground
        translation = foreground.translationX
        scaleX = foreground.scaleX
        scaleY = foreground.scaleY
        direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        preview = ImageView(foreground.context).apply {
            setImageBitmap(decoded)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Keep the preview below the real interactive view, including its original scroll state.
            parent.addView(this, parent.indexOfChild(foreground), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        handleOnBackProgressed(backEvent)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        val progress = backEvent.progress.coerceIn(0f, 1f)
        view?.apply {
            scaleX = this@DirectoryBackCallback.scaleX * (1f - 0.1f * progress)
            scaleY = this@DirectoryBackCallback.scaleY * (1f - 0.1f * progress)
            translationX = translation + direction * width * 0.25f * progress
        }
    }

    override fun handleOnBackCancelled() = resetPreview()

    override fun handleOnBackPressed() {
        // Also used by buttons and pre-Android 14 devices, with no preview session required.
        if (pending) return
        pending = true
        pop()
    }

    /** Call after the committed directory refresh (including failure), or on lifecycle stop. */
    fun complete() {
        pending = false
        resetPreview()
    }

    private fun resetPreview() {
        view?.apply {
            translationX = translation
            scaleX = this@DirectoryBackCallback.scaleX
            scaleY = this@DirectoryBackCallback.scaleY
        }
        preview?.let { (it.parent as? ViewGroup)?.removeView(it); it.setImageDrawable(null) }
        bitmap?.recycle()
        view = null
        preview = null
        bitmap = null
    }

    override fun onStop(owner: LifecycleOwner) = resetPreview()
    override fun onDestroy(owner: LifecycleOwner) = complete()

    companion object {
        /** Store compressed, bounded snapshots rather than full-screen bitmaps per stack entry. */
        fun capture(view: View): ByteArray? {
            if (view.width <= 0 || view.height <= 0) return null
            val ratio = min(1f, 512f / maxOf(view.width, view.height))
            val bitmap = Bitmap.createBitmap(maxOf(1, (view.width * ratio).roundToInt()),
                maxOf(1, (view.height * ratio).roundToInt()), Bitmap.Config.ARGB_8888)
            return try {
                view.draw(Canvas(bitmap).apply { scale(ratio, ratio) })
                ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
