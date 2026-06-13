package com.sysadmindoc.snapcrop

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import kotlin.math.roundToInt

class FloatingScreenshotService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView != null) {
            removeOverlay()
        }

        val uriStr = intent?.getStringExtra(EXTRA_IMAGE_URI) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        val uri = Uri.parse(uriStr)
        val bitmap = try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }

        if (bitmap == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        val maxDim = (displayMetrics.widthPixels * 0.4f).roundToInt()
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val imgW = (bitmap.width * scale).roundToInt()
        val imgH = (bitmap.height * scale).roundToInt()

        val container = FrameLayout(this)
        container.setBackgroundColor(0xFF1A1A1A.toInt())

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        container.addView(imageView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(12, 12, 12, 12)
            setColorFilter(0xFFE0E0E0.toInt())
            setOnClickListener { removeOverlay(); stopSelf() }
        }
        val closeLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        container.addView(closeBtn, closeLp)

        val params = WindowManager.LayoutParams(
            imgW,
            imgH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayMetrics.widthPixels / 2 - imgW / 2
            y = displayMetrics.heightPixels / 4
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (dx * dx + dy * dy) > 100) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx.roundToInt()
                        params.y = initialY + dy.roundToInt()
                        windowManager?.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        removeOverlay()
                        stopSelf()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(container, params)
        overlayView = container

        return START_NOT_STICKY
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"

        fun pin(context: Context, imageUri: Uri) {
            context.startService(
                Intent(context, FloatingScreenshotService::class.java)
                    .putExtra(EXTRA_IMAGE_URI, imageUri.toString())
            )
        }
    }
}
