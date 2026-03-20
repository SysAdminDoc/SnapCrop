package com.sysadmindoc.snapcrop

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class ScreenshotOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    companion object {
        private const val THUMBNAIL_W_DP = 110
        private const val THUMBNAIL_H_DP = 200
        private const val CORNER_RADIUS_DP = 14f
        private const val MARGIN_DP = 16
        private const val DISPLAY_MS = 4500L
        private const val FLING_VELOCITY = 200
        private const val FLING_DISTANCE = 60
    }

    fun show(bitmap: Bitmap, uri: Uri) {
        dismiss()

        val density = context.resources.displayMetrics.density
        val widthPx = (THUMBNAIL_W_DP * density).toInt()
        val heightPx = (THUMBNAIL_H_DP * density).toInt()
        val cornerPx = CORNER_RADIUS_DP * density
        val marginPx = (MARGIN_DP * density).toInt()

        // Build thumbnail view
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

        val border = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = RoundedBorderDrawable(cornerPx, 2 * density, 0xFF333333.toInt())
        }

        container.addView(imageView)
        container.addView(border)

        // Gesture handling: tap to edit, fling to dismiss
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                dismiss()
                val intent = Intent(context, CropActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                e1 ?: return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // Fling left or down to dismiss
                if ((dx < -FLING_DISTANCE && abs(velocityX) > FLING_VELOCITY) ||
                    (dy > FLING_DISTANCE && abs(velocityY) > FLING_VELOCITY)) {
                    animateSwipeOut(if (abs(dx) > abs(dy)) -1f else 0f,
                                   if (abs(dy) >= abs(dx)) 1f else 0f)
                    return true
                }
                return false
            }
        })

        // Track drag for visual feedback
        var startX = 0f
        var startY = 0f
        container.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    cancelAutoDismiss()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    // Allow dragging left or down with visual feedback
                    v.translationX = dx.coerceAtMost(0f)
                    v.translationY = dy.coerceAtLeast(0f)
                    val dist = Math.hypot(
                        v.translationX.toDouble(),
                        v.translationY.toDouble()
                    ).toFloat()
                    v.alpha = (1f - dist / (v.width * 2f)).coerceAtLeast(0.3f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // If not flung, snap back
                    if (overlayView != null) {
                        v.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                        scheduleAutoDismiss()
                    }
                    true
                }
                else -> false
            }
        }

        // Window params
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = marginPx
            y = marginPx + (56 * density).toInt()
        }

        overlayView = container
        windowManager.addView(container, params)

        // Slide in from left
        container.translationX = -(widthPx + marginPx).toFloat()
        container.alpha = 0f
        container.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(0.6f))
            .start()

        scheduleAutoDismiss()
    }

    private fun scheduleAutoDismiss() {
        cancelAutoDismiss()
        autoDismissRunnable = Runnable { animateFadeOut() }
        handler.postDelayed(autoDismissRunnable!!, DISPLAY_MS)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
    }

    private fun animateFadeOut() {
        val view = overlayView ?: return
        cancelAutoDismiss()
        view.animate()
            .alpha(0f)
            .translationX(-(view.width * 0.3f))
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { removeOverlay() }
            })
            .start()
    }

    private fun animateSwipeOut(dirX: Float, dirY: Float) {
        val view = overlayView ?: return
        cancelAutoDismiss()
        val density = context.resources.displayMetrics.density
        val distance = 300 * density
        view.animate()
            .translationX(view.translationX + dirX * distance)
            .translationY(view.translationY + dirY * distance)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { removeOverlay() }
            })
            .start()
    }

    fun dismiss() {
        cancelAutoDismiss()
        removeOverlay()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }
}
