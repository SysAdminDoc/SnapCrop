package com.sysadmindoc.snapcrop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private var sourceUri: Uri? = null
    private val rotationKey = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sourceUri = when {
            intent.data != null -> intent.data
            intent.action == Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }
        if (sourceUri == null) { finish(); return }

        val showFlash = intent.getBooleanExtra(EXTRA_SHOW_FLASH, false)
        if (showFlash) vibrateShort()

        // Async bitmap load
        CoroutineScope(Dispatchers.IO).launch {
            loadBitmap(sourceUri!!)
            withContext(Dispatchers.Main) { isLoading.value = false }
        }

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
                            onSave = { rect, pix -> saveCropped(bmp, rect, pix, deleteOriginal = getDeletePref()) },
                            onSaveCopy = { rect, pix -> saveCropped(bmp, rect, pix, deleteOriginal = false) },
                            onShare = { rect, pix -> shareCropped(bmp, rect, pix) },
                            onCopyClipboard = { rect, pix -> copyToClipboard(bmp, rect, pix) },
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
                            onRotate = { rotateBitmap() },
                            onFlipH = { flipBitmap(horizontal = true) },
                            onFlipV = { flipBitmap(horizontal = false) }
                        )
                    }

                    if (showFlash) {
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
        getSharedPreferences("snapcrop", MODE_PRIVATE).getBoolean("delete_original", true)

    private fun getSaveFormat(): Pair<Bitmap.CompressFormat, Int> {
        val prefs = getSharedPreferences("snapcrop", MODE_PRIVATE)
        return if (prefs.getBoolean("use_jpeg", false)) {
            Bitmap.CompressFormat.JPEG to prefs.getInt("jpeg_quality", 95)
        } else {
            Bitmap.CompressFormat.PNG to 100
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
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

            val maxDim = 4096
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

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
        if (current != originalBitmap) current.recycle()
        bitmapState.value = rotated
        cropRect.value = Rect(0, 0, rotated.width, rotated.height)
        cropMethod.value = ""
        rotationKey.intValue++
    }

    private fun flipBitmap(horizontal: Boolean) {
        val current = bitmapState.value ?: return
        val matrix = Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        if (current != originalBitmap) current.recycle()
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
            // Scale down then back up for mosaic effect
            val region = Bitmap.createBitmap(result, l, t, r - l, b - t)
            val tiny = Bitmap.createScaledBitmap(region,
                ((r - l) / blockSize).coerceAtLeast(1),
                ((b - t) / blockSize).coerceAtLeast(1), false)
            val mosaic = Bitmap.createScaledBitmap(tiny, r - l, b - t, false)
            canvas.drawBitmap(mosaic, l.toFloat(), t.toFloat(), null)
            region.recycle(); tiny.recycle(); mosaic.recycle()
        }
        return result
    }

    private fun createCroppedBitmap(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>): Bitmap {
        val pixelated = applyPixelate(bitmap, pixRects)
        val cropped = Bitmap.createBitmap(pixelated,
            rect.left.coerceAtLeast(0), rect.top.coerceAtLeast(0),
            rect.width().coerceAtMost(pixelated.width - rect.left.coerceAtLeast(0)),
            rect.height().coerceAtMost(pixelated.height - rect.top.coerceAtLeast(0)))
        if (pixelated !== bitmap) pixelated.recycle()
        return cropped
    }

    private fun saveCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>, deleteOriginal: Boolean) {
        val cropped = createCroppedBitmap(bitmap, rect, pixRects)
        saveToGallery(cropped, "SnapCrop_${System.currentTimeMillis()}", deleteOriginal)
        cropped.recycle()
    }

    private fun copyToClipboard(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>) {
        val cropped = createCroppedBitmap(bitmap, rect, pixRects)
        val cacheDir = File(cacheDir, "clipboard")
        cacheDir.mkdirs()
        val file = File(cacheDir, "clip.png")
        try {
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            val clipUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val clip = ClipData.newUri(contentResolver, "SnapCrop", clipUri)
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Copy failed", Toast.LENGTH_SHORT).show()
            cropped.recycle()
        }
    }

    private fun shareCropped(bitmap: Bitmap, rect: Rect, pixRects: List<Rect>) {
        val cropped = createCroppedBitmap(bitmap, rect, pixRects)
        val shareDir = File(cacheDir, "shared_crops"); shareDir.mkdirs()
        val shareFile = File(shareDir, "snapcrop_share.png")
        try {
            shareFile.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            val shareUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", shareFile)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
            cropped.recycle()
        }
    }

    private fun saveToGallery(bitmap: Bitmap, name: String, deleteOriginal: Boolean) {
        val (format, quality) = getSaveFormat()
        val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
        val mime = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show(); return }

        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)

            if (deleteOriginal) {
                Toast.makeText(this, "Saved to Pictures/SnapCrop", Toast.LENGTH_SHORT).show()
                deleteOriginalFile()
            } else {
                Toast.makeText(this, "Copy saved to Pictures/SnapCrop", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            contentResolver.delete(uri, null, null)
        }
        finish()
    }

    private fun deleteOriginalFile() {
        sourceUri?.let { uri -> try { contentResolver.delete(uri, null, null) } catch (_: Exception) {} }
    }

    override fun onDestroy() {
        val current = bitmapState.value
        if (current != null && current != originalBitmap) current.recycle()
        originalBitmap = null; bitmapState.value = null
        super.onDestroy()
    }
}
