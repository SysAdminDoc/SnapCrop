package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class CropActivity : ComponentActivity() {

    private var originalBitmap: Bitmap? = null
    private val cropRect = mutableStateOf(Rect(0, 0, 0, 0))
    private val cropMethod = mutableStateOf("")
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sourceUri = intent.data
        if (sourceUri == null) {
            finish()
            return
        }

        loadBitmap(sourceUri!!)

        setContent {
            SnapCropTheme {
                originalBitmap?.let { bmp ->
                    CropEditorScreen(
                        bitmap = bmp,
                        initialCropRect = cropRect.value,
                        cropMethod = cropMethod.value,
                        onSave = { rect -> saveCropped(bmp, rect) },
                        onShare = { rect -> shareCropped(bmp, rect) },
                        onDiscard = { finish() },
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
                        }
                    )
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                originalBitmap = BitmapFactory.decodeStream(stream)
                originalBitmap?.let { bmp ->
                    val statusBarPx = SystemBars.statusBarHeight(resources)
                    val navBarPx = SystemBars.navigationBarHeight(resources)
                    val result = AutoCrop.detectWithMethod(bmp, statusBarPx, navBarPx)
                    cropRect.value = result.rect
                    cropMethod.value = result.method
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun createCroppedBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            rect.width().coerceAtMost(bitmap.width - rect.left.coerceAtLeast(0)),
            rect.height().coerceAtMost(bitmap.height - rect.top.coerceAtLeast(0))
        )
    }

    private fun saveCropped(bitmap: Bitmap, rect: Rect) {
        val cropped = createCroppedBitmap(bitmap, rect)
        saveToGallery(cropped, "SnapCrop_${System.currentTimeMillis()}")
        cropped.recycle()
    }

    private fun shareCropped(bitmap: Bitmap, rect: Rect) {
        val cropped = createCroppedBitmap(bitmap, rect)

        // Save to cache for sharing
        val shareDir = File(cacheDir, "shared_crops")
        shareDir.mkdirs()
        val shareFile = File(shareDir, "snapcrop_share.png")

        try {
            shareFile.outputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            cropped.recycle()

            val shareUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, null))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
            cropped.recycle()
        }
    }

    private fun saveToGallery(bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Saved to Pictures/SnapCrop", Toast.LENGTH_SHORT).show()

            deleteOriginal()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            contentResolver.delete(uri, null, null)
        }

        finish()
    }

    private fun deleteOriginal() {
        sourceUri?.let { uri ->
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }
}
