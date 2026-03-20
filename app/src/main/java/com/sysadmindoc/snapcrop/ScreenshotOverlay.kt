package com.sysadmindoc.snapcrop

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class ScreenshotOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    companion object {
        private const val THUMBNAIL_WIDTH = 160
        private const val THUMBNAIL_HEIGHT = 280
        private const val CORNER_RADIUS = 16f
        private const val MARGIN = 24
        private const val DISPLAY_DURATION_MS = 4000L
        private const val SWIPE_THRESHOLD = 100
    }

    fun show(bitmap: Bitmap, uri: Uri) {
        dismiss()

        val thumbnailView = createThumbnailView(bitmap, uri)
        val params = createLayoutParams()

        overlayView = thumbnailView
        windowManager.addView(thumbnailView, params)

        animateIn(thumbnailView)

        autoDismissRunnable = Runnable { animateOut() }
        handler.postDelayed(autoDismissRunnable!!, DISPLAY_DURATION_MS)
    }

    private fun createThumbnailView(bitmap: Bitmap, uri: Uri): View {
        val density = context.resources.displayMetrics.density
        val widthPx = (THUMBNAIL_WIDTH * density).toInt()
        val heightPx = (THUMBNAIL_HEIGHT * density).toInt()
        val cornerPx = CORNER_RADIUS * density

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        }

        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            clipToOutline = true
            outlineProvider = RoundedOutlineProvider(cornerPx)
            elevation = 12 * density
        }

        // Dark border frame
        val border = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = RoundedBorderDrawable(cornerPx, 2 * density, 0xFF333333.toInt())
        }

        container.addView(imageView)
        container.addView(border)

        setupGestures(container, uri)

        return container
    }

    private fun setupGestures(view: View, uri: Uri) {
        var startX = 0f
        var startTranslationX = 0f

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                cancelAutoDismiss()
                animateOut()
                val intent = Intent(context, CropActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                return true
            }
        })

        view.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startTranslationX = v.translationX
                    cancelAutoDismiss()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    // Only allow swiping left (off-screen)
                    v.translationX = (startTranslationX + dx).coerceAtMost(0f)
                    v.alpha = 1f - (abs(v.translationX) / (v.width * 1.5f))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - startX
                    if (dx < -SWIPE_THRESHOLD) {
                        // Swipe left — dismiss
                        animateSwipeOut(v)
                    } else {
                        // Snap back
                        v.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                        // Re-schedule auto dismiss
                        autoDismissRunnable = Runnable { animateOut() }
                        handler.postDelayed(autoDismissRunnable!!, DISPLAY_DURATION_MS)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val marginPx = (MARGIN * density).toInt()
        val widthPx = (THUMBNAIL_WIDTH * density).toInt()
        val heightPx = (THUMBNAIL_HEIGHT * density).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = marginPx
            y = marginPx + (48 * density).toInt() // above nav bar area
        }
    }

    private fun animateIn(view: View) {
        val density = context.resources.displayMetrics.density
        view.translationX = -(THUMBNAIL_WIDTH * density + MARGIN * density)
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun animateOut() {
        val view = overlayView ?: return
        cancelAutoDismiss()
        view.animate()
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeOverlay()
                }
            })
            .start()
    }

    private fun animateSwipeOut(view: View) {
        cancelAutoDismiss()
        val density = context.resources.displayMetrics.density
        view.animate()
            .translationX(-(view.width + MARGIN * density))
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeOverlay()
                }
            })
            .start()
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
    }

    fun dismiss() {
        cancelAutoDismiss()
        removeOverlay()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }
}
