package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class CropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_FLASH = "show_flash"
    }

    private var originalBitmap: Bitmap? = null
    private val bitmapState = mutableStateOf<Bitmap?>(null)
    private val cropRect = mutableStateOf(Rect(0, 0, 0, 0))
    private val cropMethod = mutableStateOf("")
    private val isLoading = mutableStateOf(true)
    private val isSaving = mutableStateOf(false)
    private val showFlash = mutableStateOf(false)
    private var sourceUri: Uri? = null
    private val rotationKey = mutableIntStateOf(0)

    private fun handleIntent(incomingIntent: Intent) {
        val newUri = when {
            incomingIntent.data != null -> incomingIntent.data
            incomingIntent.action == Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }
        if (newUri == null) { finish(); return }

        // Reset state for the new image
        sourceUri = newUri
        isLoading.value = true
        bitmapState.value = null
        cropMethod.value = ""

        showFlash.value = incomingIntent.getBooleanExtra(EXTRA_SHOW_FLASH, false)
        if (showFlash.value) vibrateShort()

        CoroutineScope(Dispatchers.IO).launch {
            loadBitmap(newUri)
            withContext(Dispatchers.Main) { isLoading.value = false }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            SnapCropTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Primary
                        )
                    }

                    bitmapState.value?.let { bmp ->
                        CropEditorScreen(
                            bitmap = bmp,
                            initialCropRect = cropRect.value,
                            cropMethod = cropMethod.value,
                            onSave = { rect, pix, draw, adj -> saveCropped(bmp, rect, pix, draw, adj, deleteOriginal = getDeletePref()) },
                            onSaveCopy = { rect, pix, draw, adj -> saveCropped(bmp, rect, pix, draw, adj, deleteOriginal = false) },
                            onShare = { rect, pix, draw, adj -> shareCropped(bmp, rect, pix, draw, adj) },
                            onCopyClipboard = { rect, pix, draw, adj -> copyToClipboard(bmp, rect, pix, draw, adj) },
                            onDiscard = { finish() },
                            onDelete = {
                                deleteOriginalFile()
                                Toast.makeText(this@CropActivity, "Deleted", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onAutoCrop = {
                                val sbPx = SystemBars.statusBarHeight(resources)
                                val nbPx = SystemBars.navigationBarHeight(resources)
                                val result = AutoCrop.detectWithMethod(bmp, sbPx, nbPx)
                                cropMethod.value = result.method
                                result.rect
                            },
                            onSmartCrop = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val rect = SmartCropEngine.detect(bmp)
                                    cropRect.value = rect
                                    cropMethod.value = "ai"
                                }
                            },
                            onResize = { maxDim ->
                                val current = bitmapState.value ?: return@CropEditorScreen
                                if (current.width <= maxDim && current.height <= maxDim) return@CropEditorScreen
                                val scale = maxDim.toFloat() / maxOf(current.width, current.height)
                                val newW = (current.width * scale).toInt()
                                val newH = (current.height * scale).toInt()
                                val resized = Bitmap.createScaledBitmap(current, newW, newH, true)
                                if (current !== originalBitmap) current.recycle()
                                originalBitmap?.recycle(); originalBitmap = null
                                bitmapState.value = resized
                                cropRect.value = Rect(0, 0, newW, newH)
                                cropMethod.value = ""
                            },
                            onRemoveBg = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val result = BackgroundRemover.remove(bmp)
                                    if (result !== bmp) {
                                        val old = bitmapState.value
                                        if (old != null && old !== originalBitmap) old.recycle()
                                        originalBitmap = null
                                        bitmapState.value = result
                                        cropRect.value = android.graphics.Rect(0, 0, result.width, result.height)
                                        cropMethod.value = ""
                                    }
                                }
                            },
                            onRotate = { rotateBitmap() },
                            onFlipH = { flipBitmap(horizontal = true) },
                            onFlipV = { flipBitmap(horizontal = false) }
                        )
                    }

                    // Saving overlay
                    if (isSaving.value) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }

                    if (showFlash.value) {
                        val flashAlpha = remember { Animatable(0.9f) }
                        LaunchedEffect(Unit) { flashAlpha.animateTo(0f, tween(300)) }
                        if (flashAlpha.value > 0.01f) {
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
                        }
                    }
                }
            }
        }
    }

    private fun getDeletePref(): Boolean =
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("delete_original", false)

    private fun getSaveFormat(): Pair<Bitmap.CompressFormat, Int> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val quality = prefs.getInt("jpeg_quality", 95)
        return when {
            prefs.getBoolean("use_webp", false) -> {
                @Suppress("DEPRECATION")
                val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                          else Bitmap.CompressFormat.WEBP
                fmt to quality
            }
            prefs.getBoolean("use_jpeg", false) -> Bitmap.CompressFormat.JPEG to quality
            else -> Bitmap.CompressFormat.PNG to 100
        }
    }

    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun loadBitmap(uri: Uri) {
        try {
            // First pass: get dimensions
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

            // Scale down if very large
            val maxDim = 4096
            var sampleSize = 1
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
            }

            // Second pass: decode
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            contentResolver.openInputStream(uri)?.use {
                originalBitmap = BitmapFactory.decodeStream(it, null, decodeOpts)
            }

            originalBitmap?.let { bmp ->
                val statusBarPx = SystemBars.statusBarHeight(resources)
                val navBarPx = SystemBars.navigationBarHeight(resources)
                val result = AutoCrop.detectWithMethod(bmp, statusBarPx, navBarPx)
                bitmapState.value = bmp
                cropRect.value = result.rect
                cropMethod.value = result.method
            } ?: run {
                runOnUiThread {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun rotateBitmap() {
        val current = bitmapState.value ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        // Recycle the old bitmap (including originalBitmap if it was the current one)
        if (rotated !== current) current.recycle()
        if (originalBitmap != null && originalBitmap !== current && originalBitmap !== rotated) {
            originalBitmap?.recycle()
        }
        originalBitmap = null
        bitmapState.value = rotated
        cropRect.value = Rect(0, 0, rotated.width, rotated.height)
        cropMethod.value = ""
        rotationKey.intValue++
    }

    private fun flipBitmap(horizontal: Boolean) {
        val current = bitmapState.value ?: return
        val matrix = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        if (flipped !== current) current.recycle()
        if (originalBitmap != null && originalBitmap !== current && originalBitmap !== flipped) {
            originalBitmap?.recycle()
        }
        originalBitmap = null
        bitmapState.value = flipped
    }

    private fun applyPixelate(bitmap: Bitmap, pixRects: List<Rect>): Bitmap {
        if (pixRects.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val blockSize = 12
        for (pr in pixRects) {
            val l = pr.left.coerceIn(0, result.width)
            val t = pr.top.coerceIn(0, result.height)
            val r = pr.right.coerceIn(0, result.width)
            val b = pr.bottom.coerceIn(0, result.height)
            if (r - l < 2 || b - t < 2) continue
            var region: Bitmap? = null
            var tiny: Bitmap? = null
            var mosaic: Bitmap? = null
            try {
                region = Bitmap.createBitmap(result, l, t, r - l, b - t)
                tiny = Bitmap.createScaledBitmap(region,
                    ((r - l) / blockSize).coerceAtLeast(1),
                    ((b - t) / blockSize).coerceAtLeast(1), false)
                mosaic = Bitmap.createScaledBitmap(tiny, r - l, b - t, false)
                canvas.drawBitmap(mosaic, l.toFloat(), t.toFloat(), null)
            } finally {
                region?.recycle(); tiny?.recycle(); mosaic?.recycle()
            }
        }
        return result
    }

    private fun applyDraw(bitmap: Bitmap, paths: List<DrawPath>): Bitmap {
        if (paths.isEmpty()) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (dp in paths) {
            if (dp.points.isEmpty()) continue
            paint.color = dp.color
            paint.strokeWidth = dp.strokeWidth
            paint.alpha = 255
            paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null

            // Line tool — straight line between two points
            if (dp.shapeType == "line" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                paint.pathEffect = if (dp.dashed) android.graphics.DashPathEffect(floatArrayOf(dp.strokeWidth * 3, dp.strokeWidth * 2), 0f) else null
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                continue
            }

            // Eraser — paint transparent along stroke path
            if (dp.shapeType == "eraser" && dp.points.size >= 2) {
                val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    strokeWidth = dp.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val eraserPath = Path()
                eraserPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) eraserPath.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(eraserPath, eraserPaint)
                continue
            }

            // Blur brush — Gaussian blur along the stroke path
            if (dp.shapeType == "blur" && dp.points.size >= 2) {
                val radius = (dp.strokeWidth * 2).toInt().coerceAtLeast(4)
                for (pt in dp.points) {
                    val cx = pt.x.toInt(); val cy = pt.y.toInt()
                    val half = radius
                    val l = (cx - half).coerceAtLeast(0)
                    val t = (cy - half).coerceAtLeast(0)
                    val r = (cx + half).coerceAtMost(result.width)
                    val b = (cy + half).coerceAtMost(result.height)
                    val w = r - l; val h = b - t
                    if (w < 3 || h < 3) continue
                    // Box blur by downscale + upscale
                    var region: Bitmap? = null; var tiny: Bitmap? = null; var blurred: Bitmap? = null
                    try {
                        region = Bitmap.createBitmap(result, l, t, w, h)
                        val scale = 4
                        tiny = Bitmap.createScaledBitmap(region, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
                        blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
                        canvas.drawBitmap(blurred, l.toFloat(), t.toFloat(), null)
                    } finally {
                        region?.recycle(); tiny?.recycle(); blurred?.recycle()
                    }
                }
                continue
            }

            // Emoji overlay
            if (dp.shapeType == "emoji" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = dp.strokeWidth * 5
                }
                canvas.drawText(dp.text, p.x, p.y, emojiPaint)
                continue
            }

            // Callout (numbered circle)
            if (dp.shapeType == "callout" && dp.text != null && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val radius = dp.strokeWidth * 2
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.FILL
                }
                canvas.drawCircle(p.x, p.y, radius, fillPaint)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (dp.color == 0xFFFFFFFF.toInt() || dp.color == 0xFFFFFF00.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    textSize = radius * 1.2f
                    textAlign = Paint.Align.CENTER
                    style = Paint.Style.FILL
                }
                canvas.drawText(dp.text, p.x, p.y + radius * 0.4f, textPaint)
                continue
            }

            // Neon glow pen
            if (dp.shapeType == "neon" && dp.points.size >= 2) {
                val neonPath = Path()
                neonPath.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) neonPath.lineTo(dp.points[i].x, dp.points[i].y)
                // Outer glow
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth * 3; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 80
                    maskFilter = android.graphics.BlurMaskFilter(dp.strokeWidth * 2, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(neonPath, glowPaint)
                // Mid layer
                val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = dp.strokeWidth; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; alpha = 200
                }
                canvas.drawPath(neonPath, midPaint)
                // Bright core
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); strokeWidth = dp.strokeWidth * 0.6f; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(neonPath, corePaint)
                continue
            }

            // Highlighter (semi-transparent wide stroke)
            if (dp.shapeType == "highlight" && dp.points.size >= 2) {
                paint.alpha = 100 // ~40% opacity
                val path = Path()
                path.moveTo(dp.points[0].x, dp.points[0].y)
                for (i in 1 until dp.points.size) path.lineTo(dp.points[i].x, dp.points[i].y)
                canvas.drawPath(path, paint)
                paint.alpha = 255
                continue
            }

            // Magnifier loupe — circular zoomed inset
            if (dp.shapeType == "magnifier" && dp.points.isNotEmpty()) {
                val p = dp.points.first()
                val loupeRadius = 120f // pixels in bitmap space
                val zoomFactor = 2f
                val loupeCx = p.x; val loupeCy = p.y - loupeRadius - 20f

                // Border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius + 4f, borderPaint)

                // Clip and draw zoomed content
                canvas.save()
                val clipPath = Path()
                clipPath.addCircle(loupeCx, loupeCy, loupeRadius, Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.translate(loupeCx - p.x * zoomFactor, loupeCy - p.y * zoomFactor)
                canvas.scale(zoomFactor, zoomFactor)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()

                // Ring border
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawCircle(loupeCx, loupeCy, loupeRadius, ringPaint)

                // Crosshair
                val chPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color; strokeWidth = 1.5f
                }
                canvas.drawLine(loupeCx - 15f, loupeCy, loupeCx + 15f, loupeCy, chPaint)
                canvas.drawLine(loupeCx, loupeCy - 15f, loupeCx, loupeCy + 15f, chPaint)
                continue
            }

            // Spotlight — dim entire image except the selected rectangle
            if (dp.shapeType == "spotlight" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val sl = minOf(p1.x, p2.x); val st = minOf(p1.y, p2.y)
                val sr = maxOf(p1.x, p2.x); val sb = maxOf(p1.y, p2.y)
                val dimPaint = Paint().apply { color = 0x99000000.toInt(); style = Paint.Style.FILL }
                canvas.drawRect(0f, 0f, result.width.toFloat(), st, dimPaint)
                canvas.drawRect(0f, sb, result.width.toFloat(), result.height.toFloat(), dimPaint)
                canvas.drawRect(0f, st, sl, sb, dimPaint)
                canvas.drawRect(sr, st, result.width.toFloat(), sb, dimPaint)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawRect(sl, st, sr, sb, borderPaint)
                continue
            }

            // Text
            if (dp.shapeType == "text" && dp.text != null && dp.points.isNotEmpty()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = dp.color
                    textSize = dp.strokeWidth * 3
                    style = Paint.Style.FILL
                }
                val p = dp.points.first()
                if (dp.filled) {
                    val bounds = android.graphics.Rect()
                    textPaint.getTextBounds(dp.text, 0, dp.text.length, bounds)
                    val pad = textPaint.textSize * 0.3f
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt(); style = Paint.Style.FILL }
                    canvas.drawRoundRect(p.x - pad, p.y + bounds.top - pad, p.x + bounds.width() + pad,
                        p.y + bounds.bottom + pad, pad, pad, bgPaint)
                }
                canvas.drawText(dp.text, p.x, p.y, textPaint)
                continue
            }

            // Shape types
            if (dp.shapeType == "rect" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawRect(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawRect(l, t, r, b, paint)
                continue
            }
            if (dp.shapeType == "circle" && dp.points.size >= 2) {
                val p1 = dp.points.first(); val p2 = dp.points.last()
                val l = minOf(p1.x, p2.x); val t = minOf(p1.y, p2.y)
                val r = maxOf(p1.x, p2.x); val b = maxOf(p1.y, p2.y)
                if (dp.filled) { paint.style = Paint.Style.FILL; canvas.drawOval(l, t, r, b, paint); paint.style = Paint.Style.STROKE }
                else canvas.drawOval(l, t, r, b, paint)
                continue
            }

            // Flood fill — fill contiguous region at tap point with selected color
            if (dp.shapeType == "fill" && dp.points.isNotEmpty()) {
                val fx = dp.points[0].x.toInt().coerceIn(0, result.width - 1)
                val fy = dp.points[0].y.toInt().coerceIn(0, result.height - 1)
                floodFill(result, fx, fy, dp.color)
                continue
            }

            // Heal — content-aware inpainting along stroke (average surrounding pixels)
            if (dp.shapeType == "heal" && dp.points.size >= 2) {
                val radius = (dp.strokeWidth * 1.5f).toInt().coerceAtLeast(3)
                val w = result.width; val h = result.height
                val pixels = IntArray(w * h)
                result.getPixels(pixels, 0, w, 0, 0, w, h)
                for (pt in dp.points) {
                    val cx = pt.x.toInt(); val cy = pt.y.toInt()
                    // Sample from ring around the point (radius*2 to radius*3)
                    for (dy in -radius..radius) {
                        for (dx in -radius..radius) {
                            val px = cx + dx; val py = cy + dy
                            if (px < 0 || px >= w || py < 0 || py >= h) continue
                            if (dx * dx + dy * dy > radius * radius) continue
                            // Sample from ring outside radius (offset by radius in each direction)
                            var sr = 0; var sg = 0; var sb = 0; var count = 0
                            val sampleR = radius * 2
                            for (sy in -sampleR..sampleR step 3) {
                                for (sx in -sampleR..sampleR step 3) {
                                    val dist = sx * sx + sy * sy
                                    if (dist < radius * radius || dist > sampleR * sampleR) continue
                                    val spx = (px + sx).coerceIn(0, w - 1)
                                    val spy = (py + sy).coerceIn(0, h - 1)
                                    val sp = pixels[spy * w + spx]
                                    sr += (sp shr 16) and 0xFF; sg += (sp shr 8) and 0xFF; sb += sp and 0xFF
                                    count++
                                }
                            }
                            if (count > 0) {
                                val orig = pixels[py * w + px]
                                pixels[py * w + px] = (orig and 0xFF000000.toInt()) or
                                    ((sr / count) shl 16) or ((sg / count) shl 8) or (sb / count)
                            }
                        }
                    }
                }
                result.setPixels(pixels, 0, w, 0, 0, w, h)
                continue
            }

            // Freehand path
            val path = Path()
            path.moveTo(dp.points[0].x, dp.points[0].y)
            for (i in 1 until dp.points.size) {
                path.lineTo(dp.points[i].x, dp.points[i].y)
            }
            canvas.drawPath(path, paint)

            // Arrow head
            if (dp.isArrow && dp.points.size >= 2) {
                val last = dp.points.last()
                val prev = dp.points[dp.points.size - 2]
                val dx = last.x - prev.x; val dy = last.y - prev.y
                val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len > 0) {
                    val ux = dx / len; val uy = dy / len
                    val hl = dp.strokeWidth * 4; val hw = dp.strokeWidth * 2.5f
                    val arrowPath = Path()
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl + uy * hw, last.y - uy * hl - ux * hw)
                    arrowPath.moveTo(last.x, last.y)
                    arrowPath.lineTo(last.x - ux * hl - uy * hw, last.y - uy * hl + ux * hw)
                    canvas.drawPath(arrowPath, paint)
                }
            }
        }
        return result
    }

    private fun floodFill(bitmap: Bitmap, x: Int, y: Int, fillColor: Int) {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val targetColor = pixels[y * w + x]
        if (targetColor == fillColor) return
        val tolerance = 30 // color distance tolerance
        fun colorClose(c1: Int, c2: Int): Boolean {
            val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
            val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
            val db = (c1 and 0xFF) - (c2 and 0xFF)
            return dr * dr + dg * dg + db * db <= tolerance * tolerance * 3
        }
        val queue = ArrayDeque<Int>(w * h / 4)
        val visited = BooleanArray(w * h)
        queue.add(y * w + x)
        visited[y * w + x] = true
        var filled = 0
        val maxFill = w * h / 2 // safety limit
        while (queue.isNotEmpty() && filled < maxFill) {
            val idx = queue.removeFirst()
            pixels[idx] = fillColor
            filled++
            val cx = idx % w; val cy = idx / w
            for ((nx, ny) in listOf(cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)) {
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val ni = ny * w + nx
                if (!visited[ni] && colorClose(pixels[ni], targetColor)) {
                    visited[ni] = true
                    queue.add(ni)
                }
            }
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun getFilterColorMatrix(filterIndex: Int): ColorMatrix? {
        return when (filterIndex) {
            1 -> ColorMatrix().apply { setSaturation(0f) } // Mono
            2 -> ColorMatrix().apply { // Sepia
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,40f, 0f,1f,0f,0f,20f, 0f,0f,1f,0f,-10f, 0f,0f,0f,1f,0f)))
            }
            3 -> ColorMatrix(floatArrayOf(0.9f,0f,0f,0f,0f, 0f,0.95f,0f,0f,0f, 0f,0f,1.1f,0f,20f, 0f,0f,0f,1f,0f)) // Cool
            4 -> ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,15f, 0f,1.05f,0f,0f,5f, 0f,0f,0.9f,0f,-10f, 0f,0f,0f,1f,0f)) // Warm
            5 -> ColorMatrix().apply { // Vivid
                setSaturation(1.5f)
                postConcat(ColorMatrix(floatArrayOf(1.1f,0f,0f,0f,10f, 0f,1.1f,0f,0f,10f, 0f,0f,1.1f,0f,10f, 0f,0f,0f,1f,0f)))
            }
            6 -> ColorMatrix().apply { // Muted
                setSaturation(0.4f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,15f, 0f,1f,0f,0f,15f, 0f,0f,1f,0f,15f, 0f,0f,0f,1f,0f)))
            }
            7 -> ColorMatrix().apply { // Vintage
                setSaturation(0.5f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.05f,0f,0f,20f, 0f,1f,0.05f,0f,10f, 0f,0f,0.9f,0f,0f, 0f,0f,0f,1f,0f)))
            }
            8 -> ColorMatrix().apply { // Noir
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1.4f,0f,0f,0f,-30f, 0f,1.4f,0f,0f,-30f, 0f,0f,1.4f,0f,-30f, 0f,0f,0f,1f,0f)))
            }
            9 -> ColorMatrix().apply { // Fade
                setSaturation(0.6f)
                postConcat(ColorMatrix(floatArrayOf(1f,0f,0f,0f,30f, 0f,1f,0f,0f,30f, 0f,0f,1f,0f,30f, 0f,0f,0f,0.9f,0f)))
            }
            10 -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)) // Invert
            11 -> ColorMatrix(floatArrayOf(1.438f,-0.062f,-0.062f,0f,0f, -0.122f,1.378f,-0.122f,0f,0f, -0.016f,-0.016f,1.483f,0f,0f, 0f,0f,0f,1f,0f)) // Polaroid
            12 -> ColorMatrix().apply { // Grain
                setSaturation(0.8f)
                postConcat(ColorMatrix(floatArrayOf(1.05f,0.02f,0f,0f,8f, 0f,1.02f,0f,0f,4f, 0f,0f,0.95f,0f,-5f, 0f,0f,0f,1f,0f)))
            }
            // 13-16: per-pixel filters handled in applyAdjustments
            else -> null
        }
    }

    private fun applyAdjustments(bitmap: Bitmap, adj: FloatArray): Bitmap {
        val brightness = adj[0]; val contrast = adj[1]; val saturation = adj[2]
        val warmth = if (adj.size > 4) adj[4] else 0f
        val vignetteAmt = if (adj.size > 5) adj[5] else 0f
        val filterIndex = if (adj.size > 6) adj[6].toInt() else 0
        val sharpenAmt = if (adj.size > 7) adj[7] else 0f
        val highlightsAmt = if (adj.size > 9) adj[9] else 0f
        val shadowsAmt = if (adj.size > 10) adj[10] else 0f
        val tiltShiftAmt = if (adj.size > 11) adj[11] else 0f
        val denoiseAmt = if (adj.size > 12) adj[12] else 0f
        val curveRAmt = if (adj.size > 14) adj[14] else 0f
        val curveGAmt = if (adj.size > 15) adj[15] else 0f
        val curveBAmt = if (adj.size > 16) adj[16] else 0f
        if (brightness == 0f && contrast == 1f && saturation == 1f && warmth == 0f && vignetteAmt == 0f && filterIndex == 0 && sharpenAmt == 0f && highlightsAmt == 0f && shadowsAmt == 0f && tiltShiftAmt == 0f && denoiseAmt == 0f && curveRAmt == 0f && curveGAmt == 0f && curveBAmt == 0f) return bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix()
        // Apply image filter first
        val filterMat = getFilterColorMatrix(filterIndex)
        if (filterMat != null) cm.postConcat(filterMat)
        if (saturation != 1f) { val sat = ColorMatrix(); sat.setSaturation(saturation); cm.postConcat(sat) }
        if (contrast != 1f) {
            val t = (1f - contrast) / 2f * 255f
            cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        }
        if (brightness != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, brightness, 0f, 1f, 0f, 0f, brightness, 0f, 0f, 1f, 0f, brightness, 0f, 0f, 0f, 1f, 0f)))
        }
        if (warmth != 0f) {
            cm.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, warmth, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, -warmth, 0f, 0f, 0f, 1f, 0f)))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        // Vignette: radial gradient overlay
        if (vignetteAmt > 0.01f) {
            val vigPaint = Paint().apply {
                shader = android.graphics.RadialGradient(
                    result.width / 2f, result.height / 2f,
                    maxOf(result.width, result.height) * 0.7f,
                    intArrayOf(0x00000000, (vignetteAmt * 200).toInt().coerceAtMost(200) shl 24),
                    floatArrayOf(0.4f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), vigPaint)
        }
        // Highlights/Shadows: per-pixel luminance-based adjustment
        if (highlightsAmt != 0f || shadowsAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val px = pixels[i]
                var r = (px shr 16) and 0xFF; var g = (px shr 8) and 0xFF; var b = px and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                // Highlights affect bright pixels (lum > 128), shadows affect dark pixels (lum < 128)
                val hiFactor = ((lum - 128f) / 128f).coerceIn(0f, 1f) // 0 for darks, 1 for brights
                val shFactor = ((128f - lum) / 128f).coerceIn(0f, 1f) // 1 for darks, 0 for brights
                val adj2 = highlightsAmt * hiFactor * 0.5f + shadowsAmt * shFactor * 0.5f
                r = (r + adj2).toInt().coerceIn(0, 255)
                g = (g + adj2).toInt().coerceIn(0, 255)
                b = (b + adj2).toInt().coerceIn(0, 255)
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Curves: per-channel gamma adjustment
        if (curveRAmt != 0f || curveGAmt != 0f || curveBAmt != 0f) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            // Build LUTs for each channel: gamma curve from -100..+100 mapped to gamma 0.5..2.0
            fun buildLut(amount: Float): IntArray {
                val lut = IntArray(256)
                if (amount == 0f) { for (i in 0..255) lut[i] = i; return lut }
                val gamma = if (amount > 0) 1f / (1f + amount / 50f) else 1f + (-amount / 50f)
                for (i in 0..255) {
                    lut[i] = (255.0 * Math.pow(i / 255.0, gamma.toDouble())).toInt().coerceIn(0, 255)
                }
                return lut
            }
            val lutR = buildLut(curveRAmt); val lutG = buildLut(curveGAmt); val lutB = buildLut(curveBAmt)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = lutR[(px shr 16) and 0xFF]
                val g = lutG[(px shr 8) and 0xFF]
                val b = lutB[px and 0xFF]
                pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Glitch effect (16): RGB channel shift
        if (filterIndex == 16) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            val shift = (w * 0.02f).toInt().coerceAtLeast(3) // 2% of width
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    val rIdx = y * w + (x + shift).coerceAtMost(w - 1)
                    val bIdx = y * w + (x - shift).coerceAtLeast(0)
                    val r = (pixels[rIdx] shr 16) and 0xFF
                    val g = (pixels[idx] shr 8) and 0xFF
                    val b = pixels[bIdx] and 0xFF
                    out[idx] = (pixels[idx] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
            }
            result.setPixels(out, 0, w, 0, 0, w, h)
        }
        // Selective color pop filters (13=RedPop, 14=BluePop, 15=GreenPop)
        if (filterIndex in 13..15) {
            val w = result.width; val h = result.height
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            val targetHue = when (filterIndex) { 13 -> 0f; 14 -> 220f; else -> 120f } // Red/Blue/Green
            val hueRange = 40f // degrees of hue to keep
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val hueDiff = kotlin.math.abs(hsv[0] - targetHue).let { if (it > 180) 360 - it else it }
                if (hueDiff > hueRange || hsv[1] < 0.15f) {
                    // Desaturate — convert to grayscale
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                    pixels[i] = (px and 0xFF000000.toInt()) or (gray shl 16) or (gray shl 8) or gray
                }
            }
            result.setPixels(pixels, 0, w, 0, 0, w, h)
        }
        // Noise reduction: blend with slightly blurred version
        if (denoiseAmt > 0.01f) {
            val w = result.width; val h = result.height
            val scale = (2 + denoiseAmt * 4).toInt().coerceIn(2, 6)
            val tiny = Bitmap.createScaledBitmap(result, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
            val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
            tiny.recycle()
            val origPx = IntArray(w * h); val blurPx = IntArray(w * h)
            result.getPixels(origPx, 0, w, 0, 0, w, h)
            blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
            val blend = denoiseAmt.coerceIn(0f, 0.8f) // never fully replace
            for (i in origPx.indices) {
                val o = origPx[i]; val b = blurPx[i]
                val r = ((o shr 16 and 0xFF) * (1 - blend) + (b shr 16 and 0xFF) * blend).toInt()
                val g = ((o shr 8 and 0xFF) * (1 - blend) + (b shr 8 and 0xFF) * blend).toInt()
                val bl = ((o and 0xFF) * (1 - blend) + (b and 0xFF) * blend).toInt()
                origPx[i] = (o and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
            result.setPixels(origPx, 0, w, 0, 0, w, h)
            blurred.recycle()
        }
        // Sharpen: 3x3 convolution kernel
        if (sharpenAmt > 0.01f) {
            val sharpened = applySharpen(result, sharpenAmt)
            if (sharpened !== result) result.recycle()
            return applyTiltShift(sharpened, tiltShiftAmt)
        }
        return applyTiltShift(result, tiltShiftAmt)
    }

    private fun applyTiltShift(bitmap: Bitmap, amount: Float): Bitmap {
        if (amount < 0.01f) return bitmap
        val w = bitmap.width; val h = bitmap.height
        // Create heavily blurred version via downscale/upscale
        val scale = (8 * amount).toInt().coerceIn(2, 16)
        val tiny = Bitmap.createScaledBitmap(bitmap, (w / scale).coerceAtLeast(1), (h / scale).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        tiny.recycle()
        // Blend: center band stays sharp, top/bottom use blurred version
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val focusBand = 0.3f // 30% of height is the sharp center
        val focusTop = (h * (0.5f - focusBand / 2)).toInt()
        val focusBottom = (h * (0.5f + focusBand / 2)).toInt()
        val sharpPixels = IntArray(w * h)
        val blurPixels = IntArray(w * h)
        bitmap.getPixels(sharpPixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val blendFactor = when {
                y < focusTop -> 1f - (y.toFloat() / focusTop).coerceIn(0f, 1f) // top blur
                y > focusBottom -> ((y - focusBottom).toFloat() / (h - focusBottom)).coerceIn(0f, 1f) // bottom blur
                else -> 0f // center sharp
            }
            for (x in 0 until w) {
                val idx = y * w + x
                if (blendFactor < 0.01f) { outPixels[idx] = sharpPixels[idx]; continue }
                if (blendFactor > 0.99f) { outPixels[idx] = blurPixels[idx]; continue }
                val sp = sharpPixels[idx]; val bp = blurPixels[idx]
                val r = ((sp shr 16 and 0xFF) * (1 - blendFactor) + (bp shr 16 and 0xFF) * blendFactor).toInt()
                val g = ((sp shr 8 and 0xFF) * (1 - blendFactor) + (bp shr 8 and 0xFF) * blendFactor).toInt()
                val b = ((sp and 0xFF) * (1 - blendFactor) + (bp and 0xFF) * blendFactor).toInt()
                outPixels[idx] = (sp and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        blurred.recycle()
        return result
    }

    private fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        // Unsharp mask: center = 1 + 4*amount, neighbors = -amount
        val center = 1f + 4f * amount
        val edge = -amount
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val t = pixels[(y - 1) * w + x]
                val b = pixels[(y + 1) * w + x]
                val l = pixels[y * w + (x - 1)]
                val r = pixels[y * w + (x + 1)]
                fun ch(px: Int, shift: Int) = (px shr shift) and 0xFF
                val nr = (ch(c, 16) * center + ch(t, 16) * edge + ch(b, 16) * edge + ch(l, 16) * edge + ch(r, 16) * edge).toInt().coerceIn(0, 255)
                val ng = (ch(c, 8) * center + ch(t, 8) * edge + ch(b, 8) * edge + ch(l, 8) * edge + ch(r, 8) * edge).toInt().coerceIn(0, 255)
                val nb = (ch(c, 0) * center + ch(t, 0) * edge + ch(b, 0) * edge + ch(l, 0) * edge + ch(r, 0) * edge).toInt().coerceIn(0, 255)
                out[idx] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        // Copy edges unchanged
        for (x in 0 until w) { out[x] = pixels[x]; out[(h - 1) * w + x] = pixels[(h - 1) * w + x] }
        for (y in 0 until h) { out[y * w] = pixels[y * w]; out[y * w + w - 1] = pixels[y * w + w - 1] }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun createCroppedBitmap(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray = floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)): Bitmap {
        // Apply free rotation first (before adjustments/crop)
        val rotAngle = if (adj.size > 8) adj[8] else 0f
        val rotated = if (rotAngle != 0f) {
            val matrix = Matrix().apply { postRotate(rotAngle, bitmap.width / 2f, bitmap.height / 2f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap

        val adjusted = applyAdjustments(rotated, adj)
        if (rotated !== bitmap && adjusted !== rotated) rotated.recycle()
        val pixelated = applyPixelate(adjusted, pixRects)
        if (pixelated !== adjusted && adjusted !== rotated && adjusted !== bitmap) adjusted.recycle()
        val drawn = applyDraw(pixelated, drawPaths)
        if (drawn !== pixelated && pixelated !== bitmap) pixelated.recycle()
        val cl = rect.left.coerceIn(0, drawn.width - 1)
        val ct = rect.top.coerceIn(0, drawn.height - 1)
        val cw = rect.width().coerceAtMost(drawn.width - cl).coerceAtLeast(1)
        val ch = rect.height().coerceAtMost(drawn.height - ct).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(drawn, cl, ct, cw, ch)
        if (drawn !== bitmap) drawn.recycle()

        // Shape crop masking
        val shapeType = if (adj.size > 3) adj[3] else 0f
        val gradIdx = if (adj.size > 13) adj[13].toInt() else 0
        val shaped: Bitmap = if (shapeType == 1f) {
            // Circle
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawCircle(size / 2f, size / 2f, size / 2f, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 2f) {
            // Rounded rect
            val s = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = minOf(cropped.width, cropped.height) * 0.08f
            c.drawRoundRect(RectF(0f, 0f, cropped.width.toFloat(), cropped.height.toFloat()), radius, radius, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, 0f, 0f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 3f) {
            // Star (5-point)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val starPath = Path()
            val cx = size / 2f; val cy = size / 2f; val outerR = size / 2f; val innerR = outerR * 0.38f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = Math.toRadians((i * 36.0 - 90.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
            }
            starPath.close()
            c.drawPath(starPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 4f) {
            // Heart
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val heartPath = Path()
            val w = size.toFloat(); val h = size.toFloat()
            heartPath.moveTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.15f, h * -0.05f, -w * 0.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.moveTo(w / 2, h * 0.25f)
            heartPath.cubicTo(w * 0.85f, h * -0.05f, w * 1.1f, h * 0.45f, w / 2, h * 0.95f)
            heartPath.close()
            c.drawPath(heartPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 5f) {
            // Triangle (equilateral)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val triPath = Path()
            triPath.moveTo(size / 2f, size * 0.05f)
            triPath.lineTo(size * 0.95f, size * 0.95f)
            triPath.lineTo(size * 0.05f, size * 0.95f)
            triPath.close()
            c.drawPath(triPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 6f) {
            // Hexagon
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val hexPath = Path()
            val cx = size / 2f; val cy = size / 2f; val r = size / 2f * 0.95f
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60.0 - 30.0))
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            c.drawPath(hexPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else if (shapeType == 7f) {
            // Diamond (rotated square)
            val size = minOf(cropped.width, cropped.height)
            val s = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(s)
            val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val diaPath = Path()
            val half = size / 2f * 0.95f
            val cx = size / 2f; val cy = size / 2f
            diaPath.moveTo(cx, cy - half)
            diaPath.lineTo(cx + half, cy)
            diaPath.lineTo(cx, cy + half)
            diaPath.lineTo(cx - half, cy)
            diaPath.close()
            c.drawPath(diaPath, shapePaint)
            shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cropped, -(cropped.width - size) / 2f, -(cropped.height - size) / 2f, shapePaint)
            cropped.recycle()
            s
        } else {
            cropped
        }

        // Gradient background fill for transparent areas (shape crops only)
        if (gradIdx > 0 && shapeType >= 1f) {
            return applyGradientBackground(shaped, gradIdx)
        }

        return shaped
    }

    private fun applyGradientBackground(bitmap: Bitmap, gradIdx: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Draw gradient background
        val (startColor, endColor) = when (gradIdx) {
            1 -> 0xFFFF6B35.toInt() to 0xFFF7C948.toInt() // Sunset (orange->yellow)
            2 -> 0xFF0077B6.toInt() to 0xFF00B4D8.toInt() // Ocean (deep blue->cyan)
            3 -> 0xFF7B2FBE.toInt() to 0xFFE040FB.toInt() // Purple (purple->pink)
            4 -> 0xFF1A1A2E.toInt() to 0xFF16213E.toInt() // Dark (dark blue shades)
            5 -> 0xFF00B09B.toInt() to 0xFF96C93D.toInt() // Mint (teal->green)
            6 -> 0xFFFF416C.toInt() to 0xFFFF4B2B.toInt() // Fire (red->orange)
            else -> return bitmap
        }
        val gradPaint = Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                startColor, endColor, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradPaint)
        // Draw the shape-cropped image on top (transparent areas show gradient)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return result
    }

    private fun saveCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray, deleteOriginal: Boolean) {
        if (isSaving.value) return
        isSaving.value = true
        CoroutineScope(Dispatchers.IO).launch {
            var cropped = createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj)
            val bordered = applyBorder(cropped)
            if (bordered !== cropped) cropped.recycle()
            cropped = bordered
            val watermarked = applyWatermark(cropped)
            if (watermarked !== cropped) cropped.recycle()
            cropped = watermarked
            val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
            withContext(Dispatchers.Main) {
                saveToGallery(cropped, resolveFilename(), deleteOriginal, forcePng = hasShapeCrop)
                cropped.recycle()
                isSaving.value = false
            }
        }
    }

    private fun copyToClipboard(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val cropped = createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj)
            val clipDir = File(cacheDir, "clipboard")
            clipDir.mkdirs()
            val file = File(clipDir, "clip.png")
            try {
                file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                withContext(Dispatchers.Main) {
                    val clipUri = FileProvider.getUriForFile(this@CropActivity, "${packageName}.fileprovider", file)
                    val clip = ClipData.newUri(contentResolver, "SnapCrop", clipUri)
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(clip)
                    Toast.makeText(this@CropActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                cropped.recycle()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, "Copy failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, drawPaths: List<DrawPath>, adj: FloatArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val cropped = createCroppedBitmap(bitmap, rect, pixRects, drawPaths, adj)
            val (format, quality) = getSaveFormat()
            val hasShapeCrop = adj.size > 3 && adj[3] >= 1f
            val (shareFmt, shareQual) = if (hasShapeCrop) Bitmap.CompressFormat.PNG to 100 else format to quality
            @Suppress("DEPRECATION")
            val isWebp = shareFmt == Bitmap.CompressFormat.WEBP_LOSSY || shareFmt == Bitmap.CompressFormat.WEBP_LOSSLESS || shareFmt == Bitmap.CompressFormat.WEBP
            val ext = when { shareFmt == Bitmap.CompressFormat.JPEG -> "jpg"; isWebp -> "webp"; else -> "png" }
            val mime = when { shareFmt == Bitmap.CompressFormat.JPEG -> "image/jpeg"; isWebp -> "image/webp"; else -> "image/png" }
            val shareDir = File(cacheDir, "shared_crops"); shareDir.mkdirs()
            val shareFile = File(shareDir, "snapcrop_share.$ext")
            try {
                shareFile.outputStream().use { cropped.compress(shareFmt, shareQual, it) }
                cropped.recycle()
                val shareUri = FileProvider.getUriForFile(this@CropActivity, "${packageName}.fileprovider", shareFile)
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, null))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, "Share failed", Toast.LENGTH_SHORT).show()
                }
                cropped.recycle()
            }
        }
    }

    private fun applyBorder(bitmap: Bitmap): Bitmap {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val borderSize = prefs.getInt("border_size", 0)
        if (borderSize <= 0) return bitmap
        val borderColorIdx = prefs.getInt("border_color", 0)
        val borderColors = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E1E2E.toInt(),
            0xFF89B4FA.toInt(), 0xFFA6E3A1.toInt(), 0xFFF38BA8.toInt()
        )
        val bgColor = borderColors[borderColorIdx.coerceIn(0, borderColors.size - 1)]
        val newW = bitmap.width + borderSize * 2
        val newH = bitmap.height + borderSize * 2
        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(bitmap, borderSize.toFloat(), borderSize.toFloat(), null)
        return result
    }

    private fun applyWatermark(bitmap: Bitmap): Bitmap {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        if (!prefs.getBoolean("watermark_enabled", false)) return bitmap
        val text = prefs.getString("watermark_text", "SnapCrop") ?: return bitmap
        if (text.isBlank()) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40FFFFFF // 25% white
            textSize = (bitmap.width * 0.04f).coerceAtLeast(24f)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-30f, bitmap.width / 2f, bitmap.height / 2f)
        val spacing = paint.textSize * 3
        val diag = kotlin.math.sqrt((bitmap.width.toDouble() * bitmap.width + bitmap.height.toDouble() * bitmap.height)).toFloat()
        var y = -diag / 2
        while (y < diag * 1.5f) {
            var x = -diag / 2
            while (x < diag * 1.5f) {
                canvas.drawText(text, x, y, paint)
                x += paint.measureText(text) + spacing
            }
            y += spacing
        }
        canvas.restore()
        return result
    }

    private fun resolveFilename(): String {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val template = prefs.getString("filename_template", "SnapCrop_%timestamp%") ?: "SnapCrop_%timestamp%"
        val counter = prefs.getInt("save_counter", 1)
        prefs.edit().putInt("save_counter", counter + 1).apply()
        val now = System.currentTimeMillis()
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val timeFmt = java.text.SimpleDateFormat("HH-mm-ss", java.util.Locale.US)
        return template
            .replace("%timestamp%", now.toString())
            .replace("%date%", dateFmt.format(java.util.Date(now)))
            .replace("%time%", timeFmt.format(java.util.Date(now)))
            .replace("%counter%", String.format("%04d", counter))
    }

    private fun compressToTargetSize(bitmap: Bitmap, format: Bitmap.CompressFormat, targetKb: Int): Pair<ByteArray, Int> {
        // Binary search for quality that meets target file size
        var lo = 10; var hi = 100; var bestBytes: ByteArray? = null; var bestQuality = hi
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(format, mid, baos)
            val bytes = baos.toByteArray()
            if (bytes.size <= targetKb * 1024) {
                bestBytes = bytes; bestQuality = mid; lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (bestBytes == null) {
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(format, 10, baos)
            bestBytes = baos.toByteArray(); bestQuality = 10
        }
        return bestBytes to bestQuality
    }

    private fun saveToGallery(bitmap: Bitmap, name: String, deleteOriginal: Boolean, forcePng: Boolean = false) {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        val (format, quality) = if (forcePng) Bitmap.CompressFormat.PNG to 100 else getSaveFormat()
        @Suppress("DEPRECATION")
        val isWebp = format == Bitmap.CompressFormat.WEBP_LOSSY || format == Bitmap.CompressFormat.WEBP_LOSSLESS || format == Bitmap.CompressFormat.WEBP
        val ext = when { format == Bitmap.CompressFormat.JPEG -> "jpg"; isWebp -> "webp"; else -> "png" }
        val mime = when { format == Bitmap.CompressFormat.JPEG -> "image/jpeg"; isWebp -> "image/webp"; else -> "image/png" }

        val savePath = prefs.getString("save_path", "Pictures/SnapCrop") ?: "Pictures/SnapCrop"

        // Target file size compression (JPEG/WebP only, not PNG)
        val targetSizeEnabled = prefs.getBoolean("target_size_enabled", false)
        val targetSizeKb = prefs.getInt("target_size_kb", 500)
        val useTargetSize = targetSizeEnabled && !forcePng && format != Bitmap.CompressFormat.PNG

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show(); return }

        try {
            if (useTargetSize) {
                val (bytes, usedQuality) = compressToTargetSize(bitmap, format, targetSizeKb)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                val sizeKb = bytes.size / 1024
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                val msg = "Saved (${sizeKb}KB, q=$usedQuality)"
                if (deleteOriginal) {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    deleteOriginalFile()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            } else {
                contentResolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                if (deleteOriginal) {
                    Toast.makeText(this, "Saved to $savePath", Toast.LENGTH_SHORT).show()
                    deleteOriginalFile()
                } else {
                    Toast.makeText(this, "Copy saved to $savePath", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        finish()
    }

    private fun deleteOriginalFile() {
        val uri = sourceUri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // MANAGE_EXTERNAL_STORAGE: direct delete without system confirmation
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                @Suppress("DEPRECATION")
                startIntentSenderForResult(pendingIntent.intentSender, 99, null, 0, 0, 0)
            } catch (_: Exception) {
                // Fallback: try direct delete (works for our own files)
                try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
        } else {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 99) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Original deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        val current = bitmapState.value
        if (current != null && current !== originalBitmap) current.recycle()
        originalBitmap?.recycle()
        originalBitmap = null; bitmapState.value = null
        super.onDestroy()
    }
}
